# Ramuvi — revisão de auditoria, marca, documentos legais e entrada pública

- **Data:** 19/11/2026 · **Branch:** `refactor/ramuvi-full-audit-brand-legal` (de `origin/main` @ `a75e398`)
- **Escopo executado:** landing e login (redesign), documentos legais, rebranding visível, uma correção de segurança no backend, testes e medições.
- **Fora do escopo desta entrega:** PRs #140 (offline) e #144 (Learn), ambos **draft**. Nada foi copiado deles; V68 é a última migration em `main` (V69 não existe em `main`). Nenhum push, merge, deploy, SSH ou acesso a produção.
- **Status:** **parcial.** Não é uma auditoria completa nem declara ausência de defeitos. Itens não verificados estão marcados como hipótese ou pendência.

## 1. Achados

Legenda: **C** = confirmado com evidência · **H** = hipótese, ainda não reproduzida.

| ID | Gravidade | Tipo | Achado | Estado |
|---|---|---|---|---|
| SEC-01 | P1 | C | Senha padrão pública do professor (`arena-dev-change-me`, presente em `application.yml`, `compose.yaml` e README) era aceita com `SPRING_PROFILES_ACTIVE=prod`. `compose.prod.yaml` exige que a variável exista, mas não impede o valor padrão. | **Corrigido** |
| SEC-02 | P2 | C | A tela de login do professor mostrava "As credenciais do MVP são definidas no arquivo `.env`" e vinha com o usuário `professor` já preenchido (expunha a configuração da instância). | **Corrigido** |
| A11Y-01 | P2 | C | O erro do login do professor (modo página) não tinha `role="alert"`, então leitores de tela não anunciavam a falha. | **Corrigido** |
| DEP-01 | P2 | C | `next@16.4.2` está na faixa de GHSA-mfr8-8qww-v76h / GHSA-vc7v-qv4w-wrv8 (moderado, `npm audit`). As duas afetam `use cache`/`unstable_cache`, que o projeto **não usa** (`rg` sem ocorrências). O `next build` aborta **intermitentemente** com `SecurityFatalError` (aviso de upgrade) e passa na nova tentativa: risco de CI instável. Recomendação: atualizar para ≥ 16.4.6 em PR próprio (Dependabot #133 está aberto). | Documentado |
| SEC-03 | P3 | C | `GET /api/projector/{code}/qr?baseUrl=` gera QR para **qualquer** origem http(s) informada (`QrCodeService.validateBaseUrl` só checa esquema e host). Com um código de aula válido, alguém pode gerar no servidor da Ramuvi um QR que leva a um domínio externo. Recomendação: restringir `baseUrl` a `app.allowed-origin-patterns`/`app.frontend-url`. | Documentado |
| PERF-01 | P2 | C (medido) | A landing pública carrega o CSS do app inteiro (`globals.css`, cerca de 2.900 linhas, mais estilos do professor) porque o layout raiz o importa: cerca de 247–273 kB de CSS sem compressão (tabela da seção 5). Recomendação: separar o CSS do app em layouts de `/teach` e `/learn`. Não foi otimizado aqui porque mexe na cascata do app inteiro. | Documentado |
| QA-01 | P2 | C | Dois E2E em `webkit-mobile` falham **também na `main`** (reproduzido com `git archive origin/main`): `accessibility-context-audit.spec.ts:33` (conta autenticada em `/learn?account=1`) e `student-entry-routing.spec.ts:22` (procura o link "Minha conta" em `aside`, que fica oculto no layout mobile). | Preexistente |
| LGPD-01 | P1 | C | Não há rotina automática de retenção ou expurgo. Só existe anonimização manual do aluno (`StudentAnonymizationService`). A política agora diz isso, sem prometer prazos. | Pendência do responsável |
| LGPD-02 | P2 | C | O assistente de avaliação por IA (`OpenAiAssessmentProvider`, ativo quando `ARENA_AI_API_KEY` está definido) envia enunciado, rubrica e respostas da entrega ao provedor configurado (padrão OpenAI), sem o nome do aluno. Isso não estava divulgado; agora está na política. | Divulgado |
| CFG-01 | P3 | H | `SecurityConfig` mantém o padrão `app.teacher.username=professor`. Fora de `prod` isso é aceitável; em `prod`, `compose.prod.yaml` já exige a variável. | Sem ação |

### Correções com evidência

- **SEC-01:** `backend/.../config/TeacherCredentialPolicy.java` + chamada em `SecurityConfig.teacherUserDetailsService`. No perfil `prod`, a aplicação **não sobe** com senha vazia ou igual ao padrão público. Fora de `prod` nada muda, então dev, CI e e2e continuam funcionando. Instalações que já usam senha própria não são afetadas (não foi imposto tamanho mínimo, para não quebrar ninguém). Teste: `TeacherCredentialPolicyTest` (4 casos: padrão rejeitado, inclusive com espaços e `PROD` em maiúsculas; vazio/nulo rejeitado; senha própria aceita; perfis não-prod inalterados).
- **SEC-02 / A11Y-01:** `components/TeacherLogin.tsx`. Testes: `tests/ramuvi-entry-routes.test.ts` ("teacher login does not disclose…") e `e2e/ramuvi-entry-points.spec.ts` (campo Usuário vazio, nenhum texto `.env`).

## 2. Entregas de produto

- **Landing (`app/page.tsx`, `app/landing.module.css`):** hero "Prepare a aula, conduza ao vivo e acompanhe cada aluno.", CTAs professor/aluno e um formulário GET nativo para `/join?code=` (que já lê `?code=`; sem JS próprio). Inclui:
  - seção "Uma aula, dos dois lados" (Preparar → Conduzir → Acompanhar, do lado do professor e do aluno);
  - painéis Teach (navy) e Learn (petróleo) diferenciados;
  - "Decisões que você percebe no uso";
  - FAQ real com `<details>`;
  - rodapé com "plataforma da Verit" e uma linha legal.

  Todas as frases foram conferidas no código. O estudo offline **não** é anunciado (a FAQ diz que ainda não existe).
- **Paleta:** são as cores **candidatas** registradas no Notion (Identidade Visual, "em validação"): navy `#142B60`, petróleo `#075F60` e dourado `#D4A74B`, este reservado e usado só no marcador da etapa "Conduzir". Ficam escopadas na landing (`--lp-*`) porque a identidade não está homologada. Os tokens globais (`styles/theme.css`) não foram alterados. A Manrope (fonte candidata) **não** foi adotada: exigiria fonte externa ou self-host e homologação. O Figma não foi consultado nesta sessão.
- **Login do aluno e do professor:** marca + produto, link "Voltar para a Ramuvi", foco na conta/formulário e links legais reunidos em **uma linha discreta** (`components/legal/LegalLinks.tsx`, compartilhado com a landing e os documentos).
- **Rebranding visível:** a única string visível restante era o nome do arquivo de backup (`arena-dev-backup-*.json` → `ramuvi-backup-*.json`). Não há "Arena Dev" visível em `/`, `/learn`, `/teach`, `/termos` e `/privacidade` (verificado por E2E).

## 3. Status legal (Trilha C)

- **Identificação:** os documentos nomeiam **Verit Soluções Digitais LTDA ("Verit")**, conforme determinação do proprietário. As constantes ficam em `lib/brand.ts` (`BRAND.company`, `BRAND.publisher`).
- **CNPJ não publicado.** O número 26.972.184/0001-45 aparece na página do Notion "Verit Soluções Digitais LTDA" (não verificada, editada em 15/08/2026). Essa mesma página registra uma razão social em nome de pessoa física ("… – Verit Soluções Digitais"), o que **diverge** de "LTDA". A ficha "Cliente › Verit Soluções Digitais" tem o campo CPF/CNPJ vazio. **Conferir no cartão CNPJ oficial da Receita Federal** antes de publicar o número ou a natureza jurídica.
- **Gate:** `LEGAL_STATUS.approved = false`. Enquanto estiver assim, as páginas:
  - ficam com `noindex,nofollow`;
  - mostram "Ainda sem data de vigência";
  - listam pendências específicas: CNPJ e endereço, papel controladora/operadora por contrato, canal de privacidade e encarregado, prazos de retenção, fornecedores e transferência internacional, aprovação jurídica.

  Para publicar: preencher os dados confirmados e mudar a flag.
- **Inventário de dados usado no texto:** veio de migrations e serviços:
  - `students` (nome, matrícula, e-mail opcional);
  - `student_account_identities` (issuer/subject OIDC);
  - `student_enrollment_invites` (apenas hash);
  - entregas com `FILE`/`LINK`/`CODE`;
  - ranking público com `displayName` (ocultável e anonimizável);
  - cookies de sessão `HttpOnly`/`Secure`/`SameSite=Lax` em `prod`;
  - `localStorage` (tema, texto, movimento, recentes, acesso lembrado);
  - Service Worker que guarda só o shell, nunca `/api` ou `/ws`;
  - integrações Google/Microsoft/Notion;
  - IA opcional.

  **IndexedDB não é usado na `main`.** A anonimização substitui nome, matrícula e e-mail (`Student.anonymize`).

## 4. Legados técnicos intencionais (não renomeados)

| Item | Motivo |
|---|---|
| Pacote Java `br.com.arenadev.*` | Refactor amplo sem valor para o usuário; quebra imports, histórico e testes. |
| Banco/usuário padrão `arena_dev` / `arena` (`compose.yaml`, `.env.example`, `application.yml`) | Nomes de banco e volume já persistidos em instalações; renomear perde dados. |
| `arena-dev-change-me` (padrão dev) | Mantido para dev/CI/e2e; **bloqueado em `prod`** (SEC-01). |
| Chaves de navegador `arena-dev-theme`, `arena-dev:*`, `arena-dev-device:*`, `arena-dev-student:*`, `arena-dev-v1` | Renomear apagaria tema, preferências e acessos lembrados (ver `lib/brand.ts`). |
| `LEGACY_CACHE_PREFIXES = ["arena-dev-"]` em `public/sw.js` | Serve para **limpar** caches antigos. |
| `ARENA_AI_*` (variáveis de ambiente) | Contrato de configuração de instalações existentes. |
| Migrations V1–V68 | Imutáveis (checksum do Flyway). |
| ADRs, CHANGELOG, `LICENSING_HISTORY.md`, links `wesscosta/arena-dev` no README | Registro histórico. |

## 5. Performance (medida, sem otimização aplicada)

Método: `next build` + `next start` local, Chromium headless, viewport 390×844, sem Service Worker, 5 execuções com mediana, `waitUntil: networkidle`. Bytes sem compressão, incluindo prefetch de rotas. Ambiente local; não representa produção.

| Landing `/` | LCP (ms) | CLS | HTML | JS | CSS |
|---|---|---|---|---|---|
| `main` (a75e398) | 84 | 0 | 30,0 kB | 944 kB | 247 kB |
| esta branch | 64 | 0 | 35,6 kB | 970 kB | 273 kB |

Leitura: o LCP local continua baixo e o CLS segue 0. O HTML cresce cerca de 5 kB (mais conteúdo). JS e CSS são dominados pelo app global (PERF-01). Core Web Vitals de campo e o profiling de endpoints do backend **não** foram medidos.

## 6. Gates executados

| Gate | Resultado |
|---|---|
| `npm run typecheck` | OK |
| `npm run lint` | 0 erros, 95 avisos (preexistentes; arquivos alterados sem avisos) |
| `npm test` | 439/439 |
| `npm run build` | OK (ver DEP-01: aborto intermitente pelo aviso de segurança do Next) |
| `mvn verify` (Testcontainers/PostgreSQL 17, Flyway 68 migrations) | OK — 412 testes, 0 falhas |
| E2E Chromium: entry-points, landing-legal (novo), a11y-context, student-oidc-login, student-entry-routing | 25/25 (backend local + Postgres descartável em 127.0.0.1:55432) |
| E2E Chromium: critical-flows, pwa-upgrade-recovery, csp-websocket | 12/12 |
| E2E WebKit (iPhone 13), mesmos 5 specs | 22/25. As 3 falhas: 1 era do teste novo (prefetch abortado; corrigido, 10/10 com repetição) e 2 são **preexistentes na `main`** (QA-01). |

Novos testes de regressão:
- `e2e/ramuvi-landing-legal.spec.ts`: formulário de código; overflow em 320/360/390/430/768/1024/1440/1920 nos dois temas com movimento reduzido; axe com FAQ aberto; páginas legais (Verit, sem CNPJ, sem vigência, `noindex`, sumário válido, 320 px); login do aluno.
- `tests/ramuvi-entry-routes.test.ts`: identidade legal e login do professor.

**Não verificados:** Google OAuth real, iPhone físico, zoom 200–400% manual, leitor de tela manual, CI oficial (pode estar bloqueada por cobrança) e revisão jurídica.

## 7. Próximos passos

1. Conferir o CNPJ e a natureza jurídica na Receita, definir o canal de privacidade e o encarregado, os prazos de retenção e os fornecedores. Depois, revisão jurídica e `LEGAL_STATUS.approved = true`.
2. Atualizar o Next para ≥ 16.4.6 (DEP-01).
3. Restringir `baseUrl` do QR (SEC-03).
4. Separar o CSS do app da landing (PERF-01) e medir de novo.
5. Corrigir os E2E WebKit preexistentes (QA-01).
6. Homologar a paleta e a tipografia no Figma antes de levar os tokens `--lp-*` ao app.
7. Seguir a auditoria nas áreas ainda não cobertas: IDOR por matrícula em todos os controllers, concorrência de XP/ranking, backup/restauração e N+1.
