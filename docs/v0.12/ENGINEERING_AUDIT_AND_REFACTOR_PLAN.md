# D12-ENG-01 — Auditoria arquitetural e refatoração segura (v0.12)

**Decisão de escopo — 25/10/2026:** a v0.12 terá duas frentes igualmente obrigatórias: **A — maturidade da UI/UX e Design System** (Figma-first A0–A4) e **B — organização e qualidade do código** (auditoria E0–E4). Esta aprovação é de **planejamento e diagnóstico**; não autoriza reescrever a aplicação estável v0.11.0.

**Baseline:** tag `v0.11.0`, commit `1f23df66945c409085c7ba8af653a717a479b4b5`. Auditoria somente leitura pode começar antes da aprovação do Figma; alterações JSX/CSS vinculadas ao redesign continuam condicionadas a A0–A4, e contratos de domínio necessitam de autorização própria.

## Objetivos de engenharia

- **DRY:** identificar e remover duplicações reais de regras, validações, fluxos e componentes; **não** criar abstrações para códigos que parecem similares mas têm semântica diferente.
- **SOLID:** avaliar SRP (responsabilidade definida), OCP (extensibilidade de mecânicas), LSP e ISP (contratos e interfaces coerentes) e DIP (fronteiras testáveis); exigir evidência por módulo, não apenas número de classes.
- **KISS e YAGNI:** simplicidade e evolução incremental, preferindo composição, separação de responsabilidades, tipos explícitos e baixo acoplamento a infraestrutura genérica ou padrões excessivos.
- **Segurança e integridade:** preservar autorização por usuário/turma, revogação, idempotência, snapshots imutáveis, transações, CSP, PWA/reconexão e logs sem dados pessoais.
- **UI/UX:** componentes reutilizáveis, design tokens e CSS com limites claros **após** aprovação do Figma, com acessibilidade e comportamento responsivo preservados.
- **Desempenho:** medir antes/depois, especialmente o bootstrap/fan-out N+1 da [issue #36](https://github.com/wesscosta/arena-dev/issues/36) com centenas de turmas/sessões.

## Sinais iniciais para investigar (não são violações confirmadas)

| Superfície | Observação da baseline | Hipótese a validar |
| --- | --- | --- |
| `frontend/components/ArenaApp.tsx` | 787 linhas | Orquestração, bootstrap e estado estão excessivamente acoplados? |
| `frontend/components/teacher/ActivitiesView.tsx` | 683 linhas | Editor, dados, validações e efeitos podem ter responsabilidades mais coesas? |
| `frontend/app/globals.css` e `frontend/styles/arena-polish.css` | 2.908 e 4.458 linhas | Há redundâncias, seletor global excessivo ou conflito de cascata? |
| `frontend/lib/{classroom,session,score,activity}-api.ts` | Participam do bootstrap | Existem requisições redundantes e hidratação excessiva? |
| `backend/src/main` | 476 arquivos Java | As fronteiras Controller/Service/Domínio/Repository, segurança e transações estão bem definidas? |

**Atenção:** LOC e quantidade de arquivos são indicadores de investigação, nunca prova de baixa qualidade. Todo diagnóstico deverá citar o método afetado, dependências, evidência reproduzível, risco e ganho esperado.

## Legado da auditoria SOLID de 20/10/2026: revalidar, não presumir

A [auditoria SOLID documentada no Notion](https://app.notion.com/p/3fff3eb198c38142964fe89bb9be729e) identificou, em uma versão anterior à v0.11.0 final, hipóteses e achados sobre perda de grupos não salvos, concorrência no Boss/roteiro, reconciliação de palco no aluno, tipos de eventos de WebSocket, fallbacks que mascaram corrupção de estado, duplicação de leitura de opções, ciclos de dependência Java e testes de frontend excessivamente apoiados em regex de código. Para E0/E1, **inspecionar e reproduzir cada achado no commit da v0.11.0 estável** antes de classificá-lo como problema vigente. Vários módulos e contagens podem ter evoluído após 20/10; não converter automaticamente achados históricos em pendências reais.

## Etapas E0–E4 e artefatos

| Etapa | Entregável | Gate |
| --- | --- | --- |
| **E0 — Mapeamento e baseline** | Inventário por domínio, dependências, contratos REST/DTO, estado e efeitos React, CSS, testes, segurança e medições de desempenho | Relatório somente leitura, mapa Figma ↔ rota ↔ componente ↔ API e evidências |
| **E1 — Diagnóstico técnico** | Matriz de duplicação, SRP/SOLID, complexidade, acoplamento, testabilidade e risco **P0–P3**; decisão preservar/extrair/refatorar/remover | Revisão técnica; cada hipótese confirmada ou rejeitada com prova e ADR quando necessário |
| **E2 — Plano e priorização** | Fatias pequenas por domínio, ordem, dependências, caracterização de comportamento, riscos, critérios de aceite e rollback | Aprovação do responsável para cada conjunto; separar refactor equivalente de mudança funcional |
| **E3 — Execução autorizada** | Modularização de componentes/hooks, serviços e infraestrutura; eliminação de duplicações, CSS/tokens após aprovação visual | Para JSX/CSS dependentes do redesign: **A0–A4 aprovado**. Backend/contratos/migrations somente por gate específico |
| **E4 — Hardening/validação** | Comparação antes/depois, regressão visual, performance, segurança, E2E, acessibilidade, documentação e rollback | CI backend + frontend + Chromium/WebKit, teste funcional/docente e GO por SHA |

**Sequenciamento:** E0/E1 podem ocorrer em paralelo a A0/A1 sem alterar código. E2 depende do diagnóstico e orienta a implementação, enquanto A4 libera as partes que dependem do redesign. E3/E4 ocorrem em PRs pequenas e reversíveis. A linha de engenharia não autoriza automaticamente identidade única do aluno, correção objetiva, Mastery, novas dinâmicas ou SaaS.

## Contratos de refatoração por área

### Frontend React/Next/TypeScript

- Isolar shell e orquestração de estado, hooks por domínio, serviços de API, regras de negócio e apresentação.
- Reduzir duplicações estáveis; evitar props extensas, efeitos frágeis e abstrações prematuras; manter validações decisivas no servidor.
- Após A1–A4, estruturar tokens semânticos, variantes, componentes e camadas de CSS conforme o Design System aprovado.
- Validar estados vazio/loading/erro/offline, teclado, foco, contraste, zoom 200/400%, 320/360/390px, tablet, desktop, ultrawide e projetor.

### Backend Java/PostgreSQL

- Revisar camadas, contratos DTO, responsabilidades de services, regras de autorização, transações, idempotência, paginação, caches e concorrência.
- Em performance, reproduzir a [issue #36](https://github.com/wesscosta/arena-dev/issues/36) com pelo menos 350 turmas/340 sessões e medir fan-out, requests, p95 e erros antes/depois; não esconder problema com retries.
- Qualquer migration requer contrato, backup, rollback, autorização e teste de compatibilidade; nenhum dado real pode ser apagado na auditoria.

### Testes e governança

- Produzir testes de caracterização **antes** de alterar comportamento crítico, preferindo asserções funcionais a regexs frágeis de JSX/CSS.
- Medir qualidade com ferramentas já existentes de lint, TypeScript, testes e CI; sugerir análise estática adicional apenas com justificativa e custo conhecidos.
- Cada PR relata problema, hipótese, evidência, impacto, contratos preservados, testes, limite de escopo, comparação de resultados e plano de rollback.

## Definition of Done — engenharia na v0.12

- [ ] E0: mapa e baseline reproduzíveis, com superfícies UI e backend.
- [ ] E1: matriz P0–P3 com duplicações/violações confirmadas ou rejeitadas, sem julgamentos apenas por LOC.
- [ ] E2: backlog técnico aprovado com relação aos pilotos Figma e dependências.
- [ ] E3: refatorações pequenas aprovadas; sem mudança involuntária de fluxos, APIs, autorização, progresso, XP ou Mastery.
- [ ] E4: CI 3/3, equivalência dos fluxos afetados, segurança, acessibilidade, desempenho mensurado, rollback e documentação/handoff.
- [ ] A0–A4: protótipos visuais aprovados antes de aplicar refatoração de interface.

**Fora do escopo:** reescrita integral, migração de framework por preferência, microserviços/padrões sem necessidade, redesign antes do Figma ou introdução oportunista de novas funcionalidades.

**Fontes:** [Roadmap v0.12](../ROADMAP_V0_12.md), [Figma-first no Notion](https://app.notion.com/p/3f9f3eb198c38136b319e5807170f8bf), [reavaliação de produto](../PRODUCT_REASSESSMENT_2026-10-24.md), [issue #36](https://github.com/wesscosta/arena-dev/issues/36). **Status:** frente planejada; E0–E4 ainda não executados.
