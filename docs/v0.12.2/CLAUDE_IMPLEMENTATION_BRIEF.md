# Ramuvi v0.12.2 - Instrucoes de implementacao para Claude Code

## Autorizacao e limites
Usuario autorizou implementar a release v0.12.2 integralmente neste worktree. Edite codigo, testes e documentos; commits locais por incremento e PR revisavel no GitHub. Nao faca merge em main, nao faca deploy na VPS, nao toque em producao, nao leia segredos, nao execute SQL destrutivo sobre dados reais. Nunca contorne gates de seguranca/CI para declarar sucesso.

## Fontes
Leia INTEGRALMENTE docs/adr/ADR-0076-classroom-session-journey-library-v0122.md, docs/v0.12.1/RELEASE_CANDIDATE_HANDOFF_2026-11-19.md, ADR-0071, ADR-0073, ADR-0074, ADR-0075 e codigo/testes atuais. Base: candidata 0aad407d da PR #154, acima da main atual (a75e398). ADR #76 cherry-picked. Verifique git e schema Flyway V72 antes de novas migrations.

## I0 - Fundacao
Registrar docs/v0.12.2/BASELINE_AND_CONTRACTS.md: contrato de Turma/Sessao/Jornada/Biblioteca, estados, RBAC, baseline de PRs/branches, testes existentes, flags, riscos. Reconciliar branch de codigo e estado da main; nao mesclar outras PRs automaticamente. Testes de caracterizacao quando falta cobertura.

## I1 - Turma e aulas
Turma passa a ser central em /teach: visao geral, aulas com historico, Jornadas, atividades e alunos, Biblioteca docente separada. Iniciar/retomar/encerrar aula em fluxo curto. Sessao esquecida gera opcoes retomar ou encerrar e iniciar outra COM CONFIRMACAO; nunca terminar por timeout silencioso. Cada encontro ClassSession distinto, dados presencia/quiz/XP/eventos imutaveis no contexto; no maximo uma ativa por turma, com tratamento de concorrencia real PostgreSQL e 409 tipado; avaliar indice parcial. Matrícula tardia deve incluir participante idempotentemente, nao marcar presenca ficticia. Testes IT e E2E.

## I2 - Aluno sem codigo repetido
Com Google/Microsoft OIDC verificado, Student/Enrollment valido e turma ativa, aluno encontra Minhas turmas e aula ativa em /learn e entra sem novo codigo por encontro; backend valida conta, estudante, matricula, turma, sessao em cada request, controle CSRF e isolamento IDOR. Preservar /join com codigo temporario em modo separado para convidados; NUNCA tornar codigo em prova de identidade persistente. NUNCA reativar bootstrap inseguro de Jornada; preservar 410 e ADR-0075. OIDC real na VPS e gate operacional, nao assuma disponibilidade. Testar duas aulas, outro dispositivo, revogacao, conta sem matricula e conflito.

## I3a - Compartilhar Biblioteca
Preservar LearningResource editorial e seus formatos MARKDOWN PLAIN_TEXT LINK. Criar snapshots imutaveis por resource/revision (hash/conteudo ou referencia para URL), tokens VIEW/SAVE com 128 bits aleatorios no minimo, guardar somente hash e nunca retornar em listagens, expirar/revogar, rate limit, no-store, noindex, Referrer-Policy restritiva, sanitizacao, nao indexar nem vazar PII, notas, perfis, matriculas. Professor cria link sem turma. Preview anonimo autorizado apenas para VIEW; SAVE exige conta verificada. Link/QR via fragmento URL com token enviado em corpo POST e removido da barra.

## I3b - Minha Biblioteca
Colecao individual vinculada diretamente ao StudentAccount da conta OIDC, mesmo sem Student ou Enrollment. Concessao idempotente UNIQUE(account, resource) com snapshot/revisao congelada; leitura/remocao apenas titular, cross-device. Nao criar aluno ou matricula falsa. Aluno salva itens de varios professores, professor nao recebe relatorio de nomes dos leitores. Encerrar/excluir turma nao apaga item pessoal legitimo, verificar hardDelete, FK, retencao e LGPD. UI com Minha Biblioteca, estados vazios, erros, leitura/remocao, professor com Compartilhar diferente de Usar na Jornada e Apresentar. Nao contabilizar XP por biblioteca.

## I4 - Integracao e homologacao
Manter Jornada como percurso da Turma e progresso formal, sem depender de sessao LIVE e sem converter toda Jornada em Markdown. Preservar diferentes progressos legados sem fusao por nome ou token. Offline Jornada segue ADR-0073; Biblioteca offline fora. Relatorios de presenca/XP/progresso independentes. Flags separadas para central da Turma, ingresso de membro, compartilhamento e Minha Biblioteca, OFF em producao ate aceite. Migrations aditivas V73+ se necessario; testar PostgreSQL real isolado, nao reescrever V68-V72.

Testes: backend Maven verify integracao PostgreSQL, frontend tests TypeScript lint build, E2E Playwright Chrome/WebKit em rotas principais, responsive 320px e acesso por teclado, CSRF, IDOR, concorrencia, race, revogacao, sem regressao. Se ambiente bloquear, registrar comandos e erros exatos, nao declarar PASS.

## Relatorio e controle
Gerar docs/v0.12.2/IMPLEMENTATION_REPORT.md com inventario: commits, funcionalidades por I0-I4, contratos, migrations, scripts/flags, evidencia de testes, gates aprovados, pendencias, riscos, GO/NO-GO. Publicar branch e PR apos validacao, mas nao mesclar nem publicar em producao. Nao alterar outras worktrees.
Execute de fato, nao responda somente com plano. Priorize implementacoes completas, testadas e incrementais; se incapaz de completar todo escopo, documente precisamente o restante.