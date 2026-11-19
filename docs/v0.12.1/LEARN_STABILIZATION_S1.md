# Ramuvi v0.12.1 — estabilização incremental S1 (Learn)

## Problema e evidência

- Após logout voluntário, a interface marcava a sessão como revogada e mostrava alerta incorreto. A proteção I3 exige manter a barreira que impede recuperar automaticamente o token de dispositivo de outra pessoa.
- O agregador de aulas ao vivo retornava lista vazia mesmo quando todas as consultas de turmas falhavam, mascarando indisponibilidade da API.
- A página /learn/live podia produzir rejeição de promessa sem tratamento.
- O formulário de vínculo por convite não diferenciava erro de limite de tentativas do convite inválido.

## Implementação

1. Indicador local não secreto de saída voluntária, mantendo a marca de conta observada. O indicador é removido após uma autenticação bem-sucedida ou escolha explícita por modo de código.
2. Estados claros de logout voluntário versus revogação/expiração, inclusive após recarregar a página.
3. Agregador de aulas ao vivo sinaliza erro quando todas as turmas consultadas falham; mantém respostas parciais se pelo menos uma consulta passar e lista vazia quando não há turmas lembradas.
4. Feedback de carregamento, erro e nova tentativa na Home e em /learn/live. Entrada por código permanece disponível.
5. Retry de Jornadas e orientação sobre primeiro vínculo, diferenciando código individual e código de aula. Erros preservam política anti-enumeração.
6. Regressões E2E para logout, novo login, sessão revogada, vínculo, falhas e respostas parciais.

## Limites e aceite

- Sem migração Flyway, alteração de backend, permissões, autenticação OIDC, integração offline ou deploy nesta PR.
- O catálogo de aulas ao vivo continua utilizando claims de dispositivo. Uma consulta a aulas baseada apenas em OIDC exige contrato/autorização próprios em incremento futuro.
- Testar localmente frontend, build, Chromium e WebKit; CI oficial quando liberada. Homologação OIDC Google real e Safari físico seguem pendentes.
- PR #140 de Jornada offline permanece isolada, sem merge.
- Exigir revisão de PR e gate de release antes de publicar.
