# Ramuvi v0.12 — Plano executivo, gates e governança da triagem

**Criado em:** 25/10/2026. **Estado:** planejamento; nenhuma refatoração estrutural ou feature nova autorizada. **Baseline:** v0.11.0 estável. **Catálogo:** [Registro de triagem](V012_SCOPE_TRIAGE_REGISTER.md), com 133 tópicos.

## 1. Objetivo e fronteira

Elevar a maturidade do produto em **duas frentes obrigatórias com peso equivalente**: (A) UI/UX, identidade e Design System, via Figma-first A0–A4; (E) qualidade do código, DRY/SOLID, modularidade, performance, segurança e testes, via E0–E4. O catálogo contém também candidatos em discovery, postergação ou revalidação, **não funcionalidades comprometidas**.

**Contratos preservados:** Jornada = estudo sequencial; Atividades = realizar/entregar; Arena = interação ao vivo; Biblioteca = recurso reutilizável. Progress, XP, nota, evidência e Mastery não são sinônimos.

## 2. Método de decisão item por item

Primeiro responder **"vale investigar agora?"**; depois **"quando e sob quais gates implementar?"**. As alternativas de decisão definitiva são `V0.12-CODE`, `V0.12-DESIGN-ONLY`, `POSTERIOR`, `REVALIDAR` e `REJEITAR`, justificadas com evidência, responsável e data. Classificações no catálogo são provisórias.

Pontuar 0–3 com justificativa em: valor pedagógico, fricção real para o usuário, ganho de qualidade/segurança, reuso, esforço, risco de regressão e dependências. **Índice de ordenação experimental:** `3×valor + 2×fricção + 2×qualidade + reuso − 2×esforço − 2×risco − dependências`. Ele **não substitui** decisão humana nem a prioridade P0 de defeitos confirmados. Faixas de triagem sugeridas: alta >=12, média 6–11, baixa <=5, a calibrar após diagnóstico.

**Princípio de escopo:** desenhar um fluxo no Figma não significa autorizar migration/API; uma abstração DRY apenas entra se resolver duplicação semântica real e se seu custo for justificável.

## 3. Plano de gates e evidências

| Gate | Frente | Evidência exigida | Decisão que libera |
| --- | --- | --- | --- |
| G0 — Baseline | Engenharia/produto | Tag v0.11.0, snapshot de Git, CI funcional, riscos operacionais, backup | Auditoria confiável |
| A0 — Auditoria UX | Design | Inventário de rotas, estados, três personas, Figma ↔ código e mapa preservar/refatorar/remover | A1/A2 com fatos |
| E0 — Auditoria arquitetura | Engenharia | Mapa React/Next + Java, módulos, dependências, APIs, testes, performance e dados | Diagnóstico E1 |
| A1 — Design System | Design | Brand validada, tokens, componentes, variantes, temas, acessibilidade | Pilotos consistentes |
| E1 — Diagnóstico | Engenharia | DRY/SOLID confirmados/rejeitados, concorrência, testes, segurança, P0–P3 | Backlog E2 |
| A2 — Três pilotos | Produto | Professor Home, Aluno Jornada mapa/timeline, Arena/Projetor com dados coerentes | Testes pedagógicos/UX |
| E2 — Fatiamento | Engenharia | Prioridade, solução, ADR se precisar, testes de caracterização, risco e rollback | Refatorações autorizadas |
| A3 — Cobertura | Design | Rotas/estados Professor, Aluno e Projetor mapeados sem lacunas de padrão | Gate visual A4 |
| A4 — Aceite Figma | Produto | APROVADO/AJUSTAR/REJEITADO por piloto/tela, responsividade e a11y | Código JSX/CSS do redesign |
| E3 — Implementação | Engenharia | PRs pequenas, autorização por fatia, contratos preservados | Testes E4 |
| E4/A5 — Homologação | Produto/engenharia | CI, E2E Chromium/WebKit, Safari físico, 2 projetores, security, p95, rollback | GO/NO-GO da release |

**Paralelismo controlado:** A0/A1 e E0/E1 podem avançar como diagnóstico sem alterar código. E2 orienta a ordem da implementação; A4 bloqueia ajustes de JSX/CSS ligados ao redesign. Autenticação, correção, IA e mudanças de domínio exigem gates próprios, mesmo com Figma aprovado.

## 4. Pilotos que definem o padrão

**Professor Home:** 0, 1, 24 e centenas de turmas; preparar conteúdo, localizar turma, iniciar aula, acessar resultados e devolutivas, evitar ranking redundante. Medir erros, cliques, foco e conclusão de tarefa.

**Aluno Jornada:** PTD real revisado, mapa panorâmico horizontal versus timeline vertical sobre os **mesmos dados**, capítulos liberados/bloqueados, material extenso, 320/390px, 200–400% zoom, redução de movimento, navegação teclado/touch.

**Arena/Projetor:** preparar uma sequência, abrir questão, acompanhar respostas sem exibir gabarito, intervenção e avanço manual; conectividade degradada, dois projetores, preservação de dados privados, reconciliação e retomada.

**Não mostrar porcentagens, notas, alunos online ou Mastery ilustrativos como métricas reais.** Toda métrica tem origem, denominador, período e significado.

## 5. Rodadas de deliberação do registro

| Rodada | Itens | Pergunta de decisão |
| --- | --- | --- |
| R0 — Fundamentos | A01–A17, E01–E22 | Quais gates, dívida confirmada, Brand e riscos precisam de solução antes da UI? |
| R1 — Professor | P01–P09 | O que simplifica tarefas docentes, cards e navegação? |
| R2 — Jornada/PTD | J01–J12 | O que é UI da Jornada e o que exige motor curricular novo? |
| R3 — Identidade | I01–I08 | O que fica apenas em design e o que depende de auth verificada? |
| R4 — Atividades | Q01–Q14 | Correção objetiva, proteção de gabarito e avaliação são compromisso ou discovery? |
| R5 — Arena | D01–D18 | Quais dinâmicas convergem e o que requer alteração de domínio/realtime? |
| R6 — Editorial/IA | L01–L12 | O que exige licenças, provenance e revisão humana? |
| R7 — Aprendizagem | M01–M11 | O que é trilha 17.0–17.8 posterior? |
| R8 — Operação | O01–O10 | O que exige multi-tenant, integração, perf/infra e homologação? |
| R9 — Escopo final | Todos | Quais itens **comprometidos** entram na v0.12 e quais vão para outros ciclos? |

**Ritmo sugerido:** blocos de 10–15 itens por avaliação, com decisões fundamentadas. Não criar 133 issues automaticamente. Falta de evidência mantém status PENDENTE, não vira aprovação presumida.

## 6. Documentos e governança

| Documento | Responsabilidade |
| --- | --- |
| [Roadmap v0.12](../ROADMAP_V0_12.md) | Visão, fronteiras, regras e decisões |
| [Registro de triagem](V012_SCOPE_TRIAGE_REGISTER.md) | 133 propostas em dez frentes, ID estável, triagem inicial e fonte |
| [D12-ENG-01](ENGINEERING_AUDIT_AND_REFACTOR_PLAN.md) | Plano E0–E4 de DRY/SOLID, refatoração e testes |
| [D12-AR-01](STUDENT_ASSESSMENT_REVIEW_DESIGN.md) | Correção objetiva como design/discovery pendente |
| [Templates](V012_AUDIT_TEMPLATES.md) | Instrumentos para auditar, avaliar e aprovar |
| [Issue #48](https://github.com/wesscosta/arena-dev/issues/48) | Guarda-chuva da release e dos gates |
| [Issue #125](https://github.com/wesscosta/arena-dev/issues/125) | Frente engenharia |
| [Notion v0.12](https://app.notion.com/p/3f9f3eb198c38136b319e5807170f8bf) | Registro executivo e aceites do responsável |

Para cada tópico com decisão definitiva registrar: `ID | situação atual | hipótese | alternativas | evidência | valor | esforço | risco | dependências | decisão | dono | data | critério de aceite | gate | versão`. Não criar uma nova versão/tag apenas por reserva preliminar.

## 7. Bloqueios e dependências imediatas

1. **CI GitHub Actions:** [run 40232922481](https://github.com/wesscosta/arena-dev/actions/runs/40232922481) recusada *antes* dos steps por faturamento/limite, não por teste; regularizar GitHub Billing e reexecutar antes de PRs funcionais.
2. **Performance (#36):** reproduzir bootstrap com >=350 turmas e >=340 sessões e registrar requests/p95/erros. Se for P0 atual, propor patch próprio e não mascarar com retries.
3. **Figma Starter:** cobertura parcial conhecida. Obter inventário A0 confiável de rotas/telas/estados, anotando lacunas de acesso.
4. **Auditoria SOLID anterior:** B1–B6 e problemas de acoplamento são achados em baseline antiga; revalidar no SHA v0.11.0 final antes de declará-los bugs atuais.
5. **Identidade, motor de correção, IA e SaaS:** exigem segurança, licenciamento, rubricas, privacidade, ADRs e aprovação separados.
6. **Histórico de redesign descartado:** branch antiga arquivada em Git bundle. Usar apenas como referência para prevenção de regressão, nunca como baseline executável.

## 8. Definition of Done desta fase documental

- [x] Inventário mestre e tipologia de decisões.
- [x] Plano de gates, entregáveis, roteiro de deliberação e responsáveis por aceite.
- [x] Templates para inventário, engenharia e evidências.
- [ ] G0: CI funcional e baseline instrumentado.
- [ ] A0 e E0 realizados com evidências reais.
- [ ] Discussão e decisão definitiva por ID, aprovada pelo responsável.
- [ ] A1–A4, E1–E4, implementação, testes e release somente após gates.

**Este plano não autoriza mudança funcional e não declara a v0.12 implementada.**
