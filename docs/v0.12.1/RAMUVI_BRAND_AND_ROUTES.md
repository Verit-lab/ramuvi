# Ramuvi — identidade, landing pública e entradas Learn/Teach

**Escopo:** rebranding Arena Dev → Ramuvi, landing pública em `/`, entrada docente em `/teach` e padronização da navegação. **Decisão:** [ADR-0072](../adr/ADR-0072-landing-publica-e-entradas-learn-teach.md). **Não inclui:** alteração de banco, de contratos de API, de autenticação ou de permissões; deploy, tag ou release.

Base: `main@cbc65ec` (inclui a entrada unificada do aluno em `/learn`, PR #142 — ver [STUDENT_ENTRY_ROUTING.md](STUDENT_ENTRY_ROUTING.md)).

## 1. Arquitetura de navegação

| Rota | Nome | Finalidade | Autenticação |
| --- | --- | --- | --- |
| `/` | Ramuvi | Landing pública, estática, sem consulta de sessão | Nenhuma |
| `/learn` | Ramuvi Learn | Entrada do aluno: conta OIDC, painel de estudo e gestão da conta (`?account=1`) | OIDC do aluno (`ROLE_STUDENT`) ou acesso legado por código |
| `/teach` | Ramuvi Teach | Área do professor (o mesmo `ArenaApp` antes servido em `/`) | Sessão docente (`ROLE_TEACHER`) |
| `/join` | Ramuvi Learn | Participação ao vivo por código/QR | Token temporário da sessão |
| `/projector` | Ramuvi Teach | Tela pública da aula | Token de projetor |

`/learn` e `/teach` são pontos de entrada: as sub-rotas existentes permanecem organizadas pelo domínio (`/learn/journeys/...`, `/learn/live`, telas docentes por query `?tela=...`). Nenhum caminho interno foi renomeado em massa.

## 2. Inventário de rotas e mapa de migração

| Categoria | Rota | Antes | Depois |
| --- | --- | --- | --- |
| Pública | `/` | Área docente (login do professor) | **Landing pública** |
| Pública | `/join`, `/projector`, `/offline.html` | Inalteradas | Inalteradas (marca atualizada) |
| Autenticação | `/learn/login` | Redireciona para `/learn?account=1` (v0.12.1) | Inalterada |
| Autenticação | `/api/auth/*` (professor), `/api/student-oidc/authorization/*`, `/api/student-oidc/callback/*` | — | **Inalteradas** (provedores OIDC não precisam de ajuste) |
| Aluno | `/learn`, `/learn/journeys/**`, `/learn/live`, `/learn/achievements`, `/learn/profile` | — | Inalteradas; título "Ramuvi Learn" |
| Professor | `/?tela=…&turma=…&aba=…&jornada=nova` | Telas docentes | **307 → `/teach?<mesma query>`** |
| Professor | `/?journey=<id>` (botão "Editar jornada" da prévia) | Editor da jornada | Links novos apontam para `/teach?journey=<id>`; links antigos: **307 → `/teach?journey=<id>`** |
| Integrações | `/?google=connected`, `/?microsoft=connected`, `/?notion=connected` | Retorno OAuth das integrações | Backend agora retorna para `/teach?<provider>=connected`; URLs antigas: **307 → `/teach?…`** |
| Integrações | `/api/integrations/*/oauth/callback` | — | **Inalteradas** (redirect URIs registradas nos provedores continuam válidas) |
| APIs | `/api/**`, `/ws/**` | — | Inalteradas |
| Compartilhamento | `/join?code=…`, QR codes, `/projector?…`, `/learn/journeys/<id>/content/<id>` | — | Inalteradas |

Regras do redirecionamento legado (`frontend/lib/legacy-routes.ts`, aplicado em `frontend/proxy.ts`):

- Só se aplica a `pathname === "/"` com pelo menos um parâmetro conhecido (`tela`, `turma`, `aba`, `jornada`, `journey`, `google`, `microsoft`, `notion`).
- Destino fixo e de mesma origem (`/teach`); a query é copiada sem interpretação. Nenhum parâmetro escolhe o destino (sem open redirect). `/?next=https://…` continua na landing.
- **307 temporário** enquanto a navegação não for homologada; trocar por 308 somente após o aceite.

## 3. Autenticação e autorização

- Nenhum serviço de login novo. `/teach` usa o mesmo `TeacherLogin` + `/api/auth/session`; `/learn` usa o mesmo fluxo OIDC (`StudentAccountAccess`). Sessões existentes (cookie `JSESSIONID`, CSRF por cookie) continuam válidas: a mudança é apenas de caminho de UI.
- Autorização continua no backend (`SecurityConfig`): `/api/**` exige `ROLE_TEACHER`, `/api/student-account/**` exige `ROLE_STUDENT`. O nome da rota não autoriza nada: um aluno que digita `/teach` vê o login docente e qualquer chamada administrativa responde 401/403.
- Logout docente continua em `/teach` (a tela de login reaparece no lugar); expiração no meio do uso mantém o diálogo de reautenticação.
- OIDC: `loginPage`, sucesso (`/learn?connected=1`) e falha (`/learn?error=oidc`) inalterados.
- Não há redirecionamento baseado em parâmetro arbitrário em nenhuma das novas rotas.
- Papéis múltiplos: o modelo atual tem conta docente única por implantação (ADR-0053) e contas de aluno OIDC separadas; não existe identidade com os dois papéis. A landing e os links "Sou aluno"/"Sou professor" permitem alternar ambientes sem criar identidade nova.

## 4. Registro do rebranding

### A. Identidade pública — renomeada

| Área | Alteração |
| --- | --- |
| Metadados | `app/layout.tsx`: título padrão/template "Ramuvi", `applicationName`, Apple Web App; `/teach` → "Ramuvi Teach"; `/learn` → "Ramuvi Learn"; landing com Open Graph/Twitter (sem imagem: falta `metadataBase` público em build) |
| PWA | `manifest.ts` (nome, descrição, ícones), `sw.js` (prefixo `ramuvi-`, versão `v0.12-ramuvi-1`, remoção dos caches `arena-dev-*` antigos), `offline.html` |
| Ícones | `public/icons/ramuvi*.svg/png` (quadrado de destaque com "R"), substituindo `arena*` |
| Cabeçalhos/marcas | Topbar docente e cockpit da Arena ("RAMUVI · Teach"), login docente ("RAMUVI TEACH"), shell de estudo ("RAMUVI · LEARN"), entrada de conta do aluno ("Ramuvi Learn · Área do aluno"), `/join`, painel de atividades do aluno, projetor e timer |
| Textos | Mensagens de carregamento, configurações, integrações, importações, prompt de questões, aviso de instalação/atualização da PWA |
| Documentação ativa | README (identidade e mapa de rotas), índice documental, Design System, template de ADR, `.env.example`, mensagens de `scripts/arena.sh` e do gate de release |

### B. Identificadores internos não persistentes — renomeados quando seguros

| Identificador | Antes | Depois | Observação |
| --- | --- | --- | --- |
| Pacote npm | `arena-dev-web` | `ramuvi-web` | Privado, não publicado |
| Maven `artifactId`/`name` | `arena-dev-api` / "Arena Dev API" | `ramuvi-api` / "Ramuvi API" | Dockerfile copia `target/*.jar`; scripts de release e teste atualizados |
| `spring.application.name` e `application` do health | `arena-dev-api` | `ramuvi-api` | Verificar monitoração externa que compare o corpo do health (não há no repositório) |

### C. Preservados por serem persistidos ou contratos — **não renomear sem migração**

| Identificador | Motivo |
| --- | --- |
| Chaves `localStorage`/`sessionStorage` `arena-dev-*` (`arena-dev-device:*`, `arena-dev-student:*`, `arena-dev-theme`, `arena-dev:study-*`, `arena-dev-v1`, recentes) | Renomear apagaria silenciosamente o acesso lembrado do aluno, tema e preferências. Exige migração no cliente em incremento próprio |
| Banco `arena_dev`, usuário `arena`, volume `postgres_data`, projeto Compose da VPS | Dados persistidos em produção; renomear exige migração/cópia de volume e janela de manutenção |
| `APP_TEACHER_PASSWORD` padrão `arena-dev-change-me` | Valor sentinela reconhecido por `scripts/arena.sh` como inseguro e usado pelos E2E; não é exibido na interface |
| Pacote Java `br.com.arenadev` e `ArenaDevApplication` | ~600 arquivos, nenhum benefício funcional, conflito com PRs abertos (ex.: #140); candidato a refatoração isolada |
| Variáveis `ARENA_HTTPS`, `ARENA_HOSTNAME`, `scripts/arena.sh` | Interface operacional usada em `.env` existentes da sala/VPS |
| Eventos de navegador `arena:*`, mensagens `ARENA_PWA_*` | Contratos entre componentes/abas e service worker já instalado |

### D. Histórico — preservado

Migrations Flyway (`V26`, `V33` citam "Arena Dev" em comentários), ADRs, notas de release, diários `docs/INCREMENT_*`, `docs/v0.*`, links para `wesscosta/arena-dev` em seções históricas do README (o GitHub redireciona), tags e commits. `LICENSE` e `LICENSING_HISTORY.md` mantêm "Arena Dev Proprietary License"/"Arena Dev Community": a troca do nome jurídico da licença depende do titular e não é feita por substituição textual.

### Conceitos funcionais que **não** são a marca antiga

"Arena" continua sendo o nome da funcionalidade de aula ao vivo (tela `?tela=arena`, cockpit, "Entre na Arena", "Ferramentas da Arena"), assim como componentes `ArenaApp`, `ArenaView` e afins. Não foram renomeados.

## 5. Landing page

- Componente de servidor estático (`○ /` no build), sem JavaScript próprio nem chamadas à API; CSS module com tokens do Design System (`--ds-*`, `--space-*`, `--font-size-*`), temas claro/escuro herdados.
- HTML semântico: `header`/`nav` rotulada/`main`/`section`/`footer`, link "Pular para o conteúdo", foco visível global, alvos de 44px, `prefers-reduced-motion`.
- Responsivo a partir de 320 px (verificado por E2E: sem rolagem horizontal).
- Conteúdo restrito a recursos existentes. Sem depoimentos, números, parcerias, políticas ou termos (não existem páginas válidas).

## 6. Implantação e rollback

- **Nginx:** encaminhar `/teach` ao Next.js, como já ocorre com `/`, `/learn` e `/join`. Se a configuração da VPS usa uma lista explícita de prefixos, incluir `/teach` antes do deploy. Nenhuma mudança em HTTPS/certificados/domínio.
- **OAuth/OIDC:** nenhuma redirect URI muda. Retornos de integrações docentes passam a usar `/teach?…`; `APP_FRONTEND_URL` permanece `https://ramuvi.io`.
- **PWA:** o novo service worker remove caches `arena-dev-*` na ativação; PWAs instaladas atualizam nome e ícone conforme a política de cada sistema operacional (pode exigir reinstalação no iOS).
- **Banco:** sem migration. Rollback é apenas de imagens (frontend + backend), sem restauração de dados. Após rollback, `/teach` deixa de existir e `/` volta a ser a área docente; os links legados continuam funcionando nas duas direções, exceto os novos `/teach?…`.
- Sem deploy, tag ou release neste incremento.

## 7. Verificação

Ver a descrição da PR para resultados. Cobertura automatizada nova: `frontend/tests/ramuvi-entry-routes.test.ts` (mapa legado, ausência de open redirect, metadados, ícones, retornos OAuth, auditoria de marca nas interfaces ativas) e `frontend/e2e/ramuvi-entry-points.spec.ts` (landing anônima e links, 320 px/teclado, `/teach` anônimo, aluno em `/teach`, APIs docentes 401, professor autenticado com reload/logout, links legados, `/learn` com marca Learn).

Pendente de homologação humana: leitores de tela reais (VoiceOver/TalkBack), Safari/iOS físico com PWA instalada, revisão de copy/identidade visual pelo responsável e Nginx da VPS.
