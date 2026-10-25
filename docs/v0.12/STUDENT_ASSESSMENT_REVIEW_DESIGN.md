# D12-AR-01 — Revisão pós-entrega e correção objetiva

**Estado:** planejado para v0.12 ([issue #121](https://github.com/wesscosta/arena-dev/issues/121)), ainda NÃO implementado. Respeitar o gate Figma-first A0–A4. A v0.11 apenas exibe o feedback já publicado, sem novo motor de correção (ver [11.13](../v0.11/11.13_STUDENT_PUBLISHED_FEEDBACK.md)).

## Problema e competência

Depois de entregar a atividade, o aluno precisa identificar acertos, compreender erros e revisar conteúdos. Uma correção incorreta ou prematura prejudica a aprendizagem e pode vazar avaliações em andamento. Separar resultado de questões objetivas, avaliação de questões abertas, nota docente e XP.

## Alternativas para política docente

1. **TEACHER_RELEASE (default candidato):** apuração interna de itens objetivos após envio e liberação explícita pelo professor. Permite avaliar atividades mistas sem divulgar gabarito antecipadamente.
2. **AFTER_SUBMISSION:** apuração e disponibilização ao aluno imediatamente após seu envio confirmado. Adequado a exercícios de estudo, com alerta de compartilhamento de gabarito enquanto outros ainda respondem.
3. **AFTER_CLASS_CLOSE:** futuro, somente quando existir encerramento global formal da atividade. Não confundir com deadline individual da tentativa.

As duas primeiras são propostas para os pilotos de Figma e teste com docentes; nomes e defaults ainda precisam de aceite.

## Estados de aluno

- Em andamento: sem qualquer acesso a resposta esperada, explicação ou correção.
- Entregue: recibo e respostas próprias somente leitura (v0.11); não exibir nota ou acerto especulativo.
- Aguardando liberação: sem gabarito, mesmo com avaliação interna calculada.
- Objetivas liberadas: por item Correta / Incorreta / Não respondida / Não avaliável, alternativa enviada, gabarito e explicação **somente se permitidos**. Resultado parcial deve ter denominador explícito.
- Discursiva, código, cenário e prática: Aguardando avaliação do professor, com devolutiva publicada depois de aprovação humana.
- Erro, conexão perdida, timer finalizado: reconsultar estado autoritativo; não reabrir envio nem inferir pontuação no cliente.
- Reabertura posterior: visualizar resultado permitido sem código de sessão adicional se a identidade estiver validada.

## Contratos e ameaças

- Política persistente, versionada e auditável por Activity, autorizada somente ao professor. Separar estado de correção interna da publicação.
- Backend calcula objetivas determinísticas MULTIPLE_CHOICE e TRUE_FALSE a partir das respostas persistidas. Conferir formato booleano e opções opacas da tentativa, inclusive embaralhamento individual.
- Capturar versão ou snapshot do gabarito por tentativa; edições docentes posteriores não podem mudar uma correção histórica silenciosamente.
- API do participante retorna apenas dados autorizados de **sua própria tentativa finalizada**. Testar requisições indevidas, tentativas abertas, mudança de política, cache, compartilhamento e resposta opaca. Não esconder só via CSS.
- Sem gabarito válido, estado NÃO AVALIÁVEL, nunca errado. Questões de texto/código sem execução isolada não recebem avaliação automática.
- Nota final e feedback de IA seguem o processo docente existente. XP é outro domínio e não é recalculado pelo recurso.
- O campo explanation já armazenado pode ser publicado de forma segura e sem HTML ativo, com controle docente.
- A política de liberação deve considerar alunos com tentativas ainda ativas e mitigação do compartilhamento de gabarito.
- Preservar ActivitySubmission / Assessment / ScoreEvent e idempotência das APIs.

## Pilotos Figma

Professor, editor da atividade: Correção e devolutiva, escolha de política, ajuda contextual, estados sem gabarito, publicação e revisão. Professor, painel de entregas: liberação, confirmação, elegibilidade e trilha de auditoria. Aluno, atividade: entregue, aguardando, corrigida, parcial, feedback docente, conteúdo longo, mobile, 200%/400%, VoiceOver e teclado.

## Critérios de aceite pós-gate

1. Nenhum gabarito liberado antes de envio e autorização, incluindo API e erros.
2. Apenas alternativa correta comparada no backend após normalização e mapeamento id opaco.
3. Correção objetiva parcial sem falsa nota final, sem ScoreEvent/XP automático.
4. Respostas discursivas e de código não são tratadas como incorretas automaticamente.
5. Feedback publicado pela v0.6/v0.11 permanece visível, distinguindo origem humana e automática.
6. Reabertura/refresh/reconexão respeitam estado e versão idênticos.
7. Testes PostgreSQL/CI, E2E WebKit/Chromium, piloto real Safari/iOS e aceite docente.

## Sequência técnica, somente após gate A0–A4

Figma estados e contratos → análise de riscos → migração pequena para política/snapshot → serviço backend e testes → API segura de revisão → controles professor → interface aluno → E2E e homologação. Não introduzir segundo módulo LMS, execução de código insegura ou correção discursiva automática.
