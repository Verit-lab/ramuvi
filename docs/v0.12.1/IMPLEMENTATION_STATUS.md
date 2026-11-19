# Ramuvi v0.12.1 — Jornadas offline

**Estado em 18/11/2026: incrementos I1–I4 implementados em branch draft, ainda não publicados, Safari/iPhone real pendente.**

## I1 — Permissões docentes e migração V69

- Jornadas antigas offline OFF; novas offline ON por padrão. Controle de copiar/colar independente da permissão de download; material protegido mantém restrição.
- Edição de Jornada existente ou rascunho preserva estágios e progresso. Não há download automático.

## I2 — Manifesto autenticado

- Endpoint de capacidades e pacote opcional para identidade OIDC verificada, matrícula ativa, Jornada publicada, permissão docente e etapas desbloqueadas.
- Até 80 conteúdos de texto e 2 milhões de caracteres; externos e anexos não são baixados. Validade de sete dias; respostas no-store.
- Flag global APP_STUDENT_IDENTITY_OFFLINE_DOWNLOAD_ENABLED=false, desativada por padrão até liberação.

## I3 — Armazenamento e gestão no navegador

- Ação Disponibilizar offline somente para conta verificada, quando a Jornada permite download e a flag global está ativa.
- Leitor e gestão em /offline-journeys.html: senha offline independente (mínimo 12 caracteres), criptografia AES-GCM 256 e PBKDF2-SHA256 250 mil iterações.
- IndexedDB com pacotes criptografados particionados por conta e Jornada; senha e chave não ficam persistidas. O leitor oferece remoção e atualização manual, mantendo conclusões pendentes antes da substituição.

## I4 — Leitura e sincronização

- HTML, JS e CSS do leitor pré-cacheados pelo Service Worker. Nenhuma API de identidade, conteúdo acadêmico ou progresso passa pelo Cache API.
- Conteúdo textual / Markdown simplificado, leitura offline e conclusão local pendente. Conteúdos ainda bloqueados não são exportados nem desbloqueados offline.
- Sincronização manual com a mesma conta OIDC e CSRF; backend valida o progresso. Revogação confirmada remove pacote; erros transitórios preservam o trabalho.
- Não há garantia de revogação instantânea desconectado ou garantia absoluta contra captura/cópia de tela.

## Evidências

- Backend: compilação e testes de integração PostgreSQL 17 com Flyway V69, autorização por matrícula, restrição de capítulo e revogação da permissão.
- Frontend: typecheck, unitários, build e E2E em Chromium com download, WebCrypto, SW, IndexedDB e sincronização.
- WebKit headless: cache e leitura criptografada na aba já aberta com rede desligada, mas navegação fria offline não passou por erro interno do navegador automatizado.
- Permissão docente OFF oculta o download. Pacotes de alunos distintos ficam separados. Logout e troca de conta não dão acesso automático ao pacote, que exige senha offline.
- CI oficial GitHub Actions: verificações históricas bloqueadas por cobrança; revisar status atual da PR.

## Gates para release

1. Homologar PWA offline com abertura a frio em Safari/iPhone físico e Android físico, quota/evicção de armazenamento e persistência após encerrar o aplicativo.
2. Homologar OIDC Google real ponta a ponta, sincronização, conta revogada, dispositivo compartilhado e material alterado/retirado.
3. Ensaiar backup e rollback com Flyway V69 (não assumir downgrade automático para imagem V68).
4. Executar CI oficial no mesmo SHA, resolver apontamentos de segurança e aprovar formalmente a liberação.
5. Somente após esses gates considerar ativar APP_STUDENT_IDENTITY_OFFLINE_DOWNLOAD_ENABLED=true na VPS e anunciar o recurso como disponível.

**A PR #140 e o ADR-0073 não constituem aprovação de deploy. A VPS e a main não foram alteradas.**
