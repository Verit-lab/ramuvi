# Ramuvi — S3: idempotência de XP em lote e auditoria seletiva de matrícula

Data: 19/11/2026. Base: main a75e398. PR independente, sem migrações nem alterações da VPS.

## Defeito reproduzido

A API POST /api/score-events já aceita requestId por lançamento, mas ScoreEventService.createBatch ignorava essa chave. Reenviar um lote após timeout podia duplicar os eventos e a pontuação. O mesmo payload concorrente gerava múltiplos eventos.

## Correção no backend

- As chaves são lidas em uma única consulta por lote, após os locks; cada nova chave é persistida. Reenvios idênticos reutilizam os eventos existentes (evita uma consulta de existência por aluno).
- Chaves repetidas com payload diferente provocam conflito transacional: nenhuma linha nova do lote permanece.
- Antes das gravações, as chaves são bloqueadas no PostgreSQL em ordem estável, evitando deadlocks quando lotes concorrentes chegam em ordem invertida.
- O batch aceita a mesma chave repetida no próprio payload sem duplicar lançamento.
- Os lançamentos novos são flushados antes da publicação do ranking, que só acontece para sessões com alterações. Replays não geram nova publicação.
- Clientes sem requestId continuam aceitos, preservando a semântica legada não idempotente.

## Correção no frontend

- Tela docente de entregas agora emite um UUID por evento de XP.
- Em caso de timeout ou 5xx, a tentativa permanece em memória e somente o mesmo payload pode ser reenviado com as chaves originais. Novo payload exige conciliação do histórico.
- Botão e seleção ficam bloqueados enquanto o envio está em curso; evita cliques duplicados.
- Respostas do servidor são mescladas pelo identificador de evento, sem duplicação no ledger local.
- Respostas 400/404/409/422 de rejeição são apresentadas e liberam nova tentativa. Erros de sessão/autorização, 429, 5xx e rede mantêm as chaves e uma mensagem explícita de resultado incerto; sucesso limpa os identificadores pendentes.
- Limite conhecido: fechar/atualizar a aba descarta o registro em memória; caso o estado da transação seja incerto, conferir o histórico antes de criar outro lote. Não há fila persistente nem retry automático.

## Evidências

- ManualScoreIdempotencyIT: dez cenários, incluindo reenvio de lote, mistura de chave já confirmada com chave nova, conflito, concorrência em ordem inversa, duplicação no mesmo lote e compatibilidade.
- score-batch-idempotency.test.ts: quatro novos testes de chaves novas, retry, conflito e novos lançamentos.
- Regressões completas de backend/frontend e CI oficial devem ser registradas no SHA final.

## Revisão de IDOR: achados e limites

- VerifiedStudentStudyController usa principal OIDC fornecido pelo Spring Security, sem aceitar studentId/accountId como parâmetros de autorização.
- VerifiedStudentStudyService resolve o Student canônico pelo issuer+subject autenticados, e JourneyStudyService.requireAccess exige Jornada publicada, matrícula ativa e política específica de conta verificada.
- JourneyCoreIT já contém cenários de tentativa de acesso a Jornadas de outros alunos e de bloqueio após desativar turma/aluno/Jornada (linhas próximas a 790 e 826).
- Não foi encontrado IDOR reproduzível nesses caminhos inspecionados. A observação NÃO equivale à aprovação integral de todos os endpoints ou à auditoria da PR de Jornadas offline.
- Próximos gates: autorização nas APIs de aulas ao vivo e convites, API offline após merge e teste OIDC real em staging.

## Rollout

Não publicar enquanto a CI oficial estiver bloqueada e não houver aceite. Em caso de rollback da aplicação, eventos criados com requestId permanecem compatíveis com o banco existente V68; imagens antigas voltam a ignorar a chave em lote, razão para evitar retornar sem política de proteção.
