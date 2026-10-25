# Arena Dev v0.12 — Redesign, arquitetura e Active Learning | roadmap

**Baseline atual (25/10/2026):** a [v0.11.0 estável](https://github.com/wesscosta/arena-dev/releases/tag/v0.11.0) está publicada. A v0.12 passa a contemplar **duas frentes igualmente obrigatórias**: (A) auditoria e redesign UI/UX A0–A4 no Figma; (B) auditoria e refatoração arquitetural E0–E4, com DRY, SOLID, KISS, YAGNI, modularização, testes e performance. O diagnóstico técnico é permitido em paralelo ao Figma; alterações de interface continuam condicionadas ao aceite visual. [Plano D12-ENG-01](v0.12/ENGINEERING_AUDIT_AND_REFACTOR_PLAN.md). O estado de 20/10 abaixo é histórico.

**Estrutura de governança v0.12 (25/10/2026):** [Registro mestre com 133 propostas catalogadas](v0.12/V012_SCOPE_TRIAGE_REGISTER.md) · [Plano de execução e gates](v0.12/V012_EXECUTION_PLAN.md) · [Templates de auditoria e decisões](v0.12/V012_AUDIT_TEMPLATES.md). **Esses registros são a fila de discussão, não 133 entregas da release.** A execução de código só recebe itens após decisões por ID, A4/E2 e critérios de aceite. Até a triagem final, propostas de identidade, correção objetiva, IA, Learning Architecture, mecânicas novas e multi-tenant não estão comprometidas para desenvolvimento na v0.12.

**Estado (20/10/2026):** em **auditoria e design Figma-first**, com [issue #48 de acompanhamento](https://github.com/wesscosta/arena-dev/issues/48); não implementado, não homologado. A v0.11 está na pré-release `v0.11.0-rc.7`; a `0.11.0` ainda exige o GO operacional da 11.6.
**Fonte de verdade:** as decisões de produto e design da v0.12 vivem no Notion ([auditoria Figma-first e gate de implementação](https://app.notion.com/p/3f9f3eb198c38136b319e5807170f8bf)); este arquivo resume o que afeta o repositório.
**Figma:** [ARENA — Redesign UX v0.12 · Transportta](https://www.figma.com/design/gWMkarPwmeFY15jr0Tr6DN). A exploração é referência visual editável, **não** especificação funcional aprovada.

**Reavaliação de 24/10/2026:** [auditoria mestre dos gates e dos próximos incrementos](PRODUCT_REASSESSMENT_2026-10-24.md) e [registro canônico de produto no Notion](https://app.notion.com/p/403f3eb198c38187a735cb36f988fc80). A atualização expande D12-DYN-01 com condução docente por rodada, geração de avaliações baseada na Jornada e respostas abertas para debate; **não** antecipa código da v0.12. O texto de estado 20/10 acima é histórico.

## 0. Gate Figma-first (decisão de 14/10/2026)

A v0.12 é conduzida como **auditoria visual Figma-first e auditoria técnica simultâneas**. O código parte da baseline estável **`v0.11.0`** e não recebe mudanças de JSX/CSS vinculadas ao redesign até aprovação explícita do Design System e dos três pilotos. A análise técnica E0/E1 é somente leitura; refatorações internas equivalentes exigem justificativa, testes e autorização própria por fatia, e mudanças de domínio seguem os gates específicos. A 11.7 corrigiu defeitos contra o Design System atual (tokens de papel, controles de 44px, contraste AAA de texto); não é a nova linguagem visual e serve de ponto de partida para a migração.

**Processo:** Figma → revisão de UX/UI → protótipo → aceite explícito → código → testes.

- Não refatorar JSX/CSS, não adicionar camada visual ao legado, não implementar telas nem alterar tokens no produto antes do aceite. O mesmo vale para migrations, APIs e mudanças de domínio da v0.12.
- O [PR #54](https://github.com/wesscosta/arena-dev/pull/54) (`feat/v012-ramuvi-pro-cards`, `bc49dda`) foi **descartado como implementação**. Não mesclar; arquivar ou encerrar só depois de preservar a referência para recuperação seletiva.
- Fases do ciclo de design no Notion: **A0** inventário e diagnóstico, **A1** identidade e Design System, **A2** três pilotos, **A3** cobertura integral das telas, **A4** gate de aprovação antes do código (decisões `APROVADO / AJUSTAR / REJEITADO`).
- **Bloqueio atual:** o inventário completo do Figma depende de acesso além do limite da integração Figma Starter. Só os frames `P · Início docente` e `P · Minhas turmas` tiveram leitura estrutural parcial.

## 1. Objetivos e decisões preservadas

Evoluir a Arena Dev para uma plataforma educacional com identidade comercial madura, aplicável à educação profissional, concursos e outras áreas, sem estética de aposta/cassino nem excesso de gamificação. Priorizar verde institucional, superfícies claras, tipografia legível, ícones funcionais, espaçamento coerente, navegação intuitiva e acessibilidade.

**Domínios preservados (ADR-0045):** Jornada = estudo sequencial; Atividades = fazer/entregar; Arena = interação ao vivo. Mostrar conteúdo não comprova aprendizagem, não pontua XP e não confirma Mastery.

Professores: Início, Turmas, Preparar aula, Biblioteca, Arena ao vivo, Dinâmicas, Projetor, Resultados. Alunos: Início, Jornadas, estudo, prática, revisão, Atividades, entrega, devolutiva e progresso. Projetor: leitura, questões, nuvem de palavras, timer, espera, desconexão e revogação.

## 2. Linha A — Design System e migração visual (prioridade de entrada)

| Etapa proposta | Entregáveis | Aceite necessário |
| --- | --- | --- |
| D12.0 — Auditoria | inventário de rotas/telas reais, estados e correspondência Figma ↔ React/Next; decisões preservar/refatorar/remover | não reconstruir por estética; identificar dívida e redundâncias |
| D12.1 — Design System | tokens semânticos, tipografia, responsividade, cores, alto contraste, componentes Figma com Auto Layout, Variants, propriedades, estados e equivalentes React | WCAG 2.2 AA projetada como mínimo, AAA quando viável; foco, zoom 200–400%, toque e teclado |
| D12.2 — Três telas-piloto | **Professor — Início docente; Aluno — Jornada (mapa e linha do tempo); Arena/Projetor**, com estados, desktop/tablet/mobile e protótipos navegáveis no Figma | revisão visual, pedagógica, técnica e de acessibilidade antes de propagar padrões |
| D12.3 — Professor | turmas, preparação, Biblioteca, palco/dinâmicas, resultados | tarefas mais rápidas sem retirar timer, Grid/Spotlight, materiais, nuvem ou controles docentes |
| D12.4 — Aluno | continuidade do estudo, Jornada, Atividades/Entregas, revisão e progresso real | separar responsabilidades e não inventar indicadores de Mastery |
| D12.5 — Projetor | material longo, perguntas, tempo, espera, erro, reconexão, retirada e legibilidade em sala | projetor sem dados privados, HTML ativo ou indicadores individuais |
| D12.6 — Convergência | integração progressiva, design system em código, testes de regressão, performance, Safari/iOS e piloto | CI por SHA, rollback e aprovação docente |

**Não refazer todas as telas indiscriminadamente.** Evoluir o Figma existente preservando componentes úteis; não substituir frontends e APIs estáveis por uma maquete. Cada tela deve expressar seu objetivo real, evitando trocar apenas textos entre layouts genéricos.

**Estados obrigatórios:** normal, vazio, loading, erro, reconexão, ausência de permissão, disabled, hover, focus, sucesso e retomada. Responsividade em 320/360/390, 768, 1024, 1366/1440, ultrawide e projeção. Não exigir que um único layout do professor funcione sem adaptações no iPhone. O roteiro de aula é **opcional e flexível**, jamais um fluxo rígido que impeça improvisação.

Dados ilustrativos do Figma (alunos online, porcentagem concluída, média de acertos) não são métricas reais; cada indicador futuro exige fonte, denominador, período e significado pedagógico verificáveis.

## 2.1 Requisitos de design já decididos para a Jornada

Ambos estão aprovados **para prototipação**, não para código. Entram nos pilotos Professor e Jornada e seguem o gate da seção 0.

### Jornada do Aluno em mapa panorâmico e linha do tempo (14/10/2026)

Duas visualizações do **mesmo percurso curricular**, com os mesmos dados, estados e ações. Não duplicar Jornadas nem modelos de progresso. [Especificação no Notion](https://app.notion.com/p/3f9f3eb198c3816d9e12d12d97cd17f9).

| Visualização | Comportamento esperado | Guardrails |
| --- | --- | --- |
| **Mapa panorâmico horizontal** | trilha contínua de nós por etapa; no desktop, rolagem vertical convertida em deslocamento horizontal só dentro de uma área delimitada, liberando a página nos limites; parallax moderado só em planos decorativos | sem scroll-jacking global; `prefers-reduced-motion` e variante sem efeitos; anterior/próximo, acesso direto à etapa, indicador de posição, voltar à etapa atual e sair da exploração; no mobile, gesto horizontal só no mapa |
| **Linha do tempo vertical** | mesma ordem e estados em lista, agrupada por UC/capítulo; visualização inicial sugerida no mobile | funcional sem mapa e sem animação; melhor densidade, leitura longa e acessibilidade |
| **Detalhe da etapa** | foco em **Continuar meus estudos**, objetivos do PTD revisado, conteúdos da Biblioteca, Atividades vinculadas e devolutivas autorizadas | sem percentuais sem numerador, denominador e regra; cadeado só para pré-requisito pedagógico real |

Origem prevista: Plano de Curso + PTD → extração assistida → revisão docente → prévia → publicação versionada. Progresso de estudo, evidência de competência e XP permanecem indicadores separados. A Jornada contextual da turma não antecipa roadmaps reutilizáveis, KnowledgeNode ou Mastery.

**Aceite antes de implementar:** mapa e timeline em alta fidelidade (desktop/tablet/mobile) com protótipo navegável; demonstração de entrada e saída da área de scroll com mouse, touchpad e teclado; variantes sem movimento; contraste, zoom 200–400%, reflow e leitor de tela; simulação com PTD real sem inventar APIs.

### D12-JR-CODE-01 — Restrições de cópia em blocos de código da Jornada (16/10/2026)

**Estado:** requisito incluído no escopo de design. Não implementado, não testado, não homologado. [Requisito completo no Notion](https://app.notion.com/p/3f9f3eb198c38136b319e5807170f8bf).

- **Onde o professor configura:** Professor → Jornada → Editar Jornada → Configurações → **Leitura e código**, durante revisão/prévia e antes da publicação.
- **Dois toggles independentes, desligados por padrão (opt-in docente):** (A) restringir o menu do botão direito nos blocos de código; (B) bloquear cópia por `Ctrl+C`/`Cmd+C` e evento `copy` de seleção dentro de código.
- **Escopo:** política única **por Jornada** na v0.12 mínima. Não é preferência do aluno nem altera o Markdown ou o recurso canônico da Biblioteca. Vale para o Reader a partir do snapshot publicado, chegando pelo Mapa ou pela Timeline.
- **Não afeta:** texto comum, `input`, `textarea`, atividades, edição docente, seleção, foco, teclado, leitores de tela, `Ctrl+V`, impressão. Nenhuma interceptação global de `keydown`, `copy` ou `contextmenu`; o evento e a seleção precisam pertencer ao bloco `pre/code`.
- **Limite declarado:** controle didático de interface, não DRM nem antifraude. Sem registro de tentativas, pontuação, punição ou coleta de dados.
- **Contrato a validar:** dois booleanos na configuração existente da Jornada (nomes ilustrativos, como `codeBlockContextMenuRestricted` e `codeBlockClipboardCopyRestricted`); alteração autorizada só ao professor responsável e política enviada pelo backend no estado publicado.
- **Aceite:** Figma com as quatro combinações dos toggles e estados de salvamento/erro/permissão; testes automatizados das quatro combinações; E2E desktop/mobile com mouse, teclado, macOS e Windows/Linux; regressão do Reader sem restrições.
- **Piloto previsto:** UC 05 — Desenvolvimento Desktop / Projeto 01 Registro de Visitantes, convertido para Markdown canônico importável.

## 2.2 D12-AR-01 — Correção e devolutiva após entrega (25/10/2026)

**Escopo proposto v0.12, Figma-first e sem implementação autorizada.** A v0.11 passa a exibir apenas `publishedFeedback` e recibo da entrega, aproveitando a infraestrutura v0.6 e sem revelar gabaritos. A ampliação D12-AR-01 compreende apuração **determinística no backend** de `MULTIPLE_CHOICE` e `TRUE_FALSE`, política docente de liberação `TEACHER_RELEASE` ou `AFTER_SUBMISSION`, visualização por questão e explicações publicáveis. Questões discursivas, código e práticas continuam sob avaliação humana; acertos parciais não são nota final, XP nem Mastery.

**Gates obrigatórios:** Figma Professor/Aluno com estados de envio, espera, liberação, erro, avaliação mista, conteúdo longo e Safari/iPhone; autorização servidor-side e proteção do gabarito contra acesso por tentativa ainda ativa; snapshot/versionamento das respostas corretas apesar de edições docentes; idempotência; regra de liberação que considere outros alunos ainda respondendo; testes backend, E2E WebKit/Chromium e aceite pedagógico. O envio imediato é opção do professor, nunca política presumida; liberação após encerramento global fica fora deste primeiro corte.

[Especificação e critérios completos](v0.12/STUDENT_ASSESSMENT_REVIEW_DESIGN.md) · [Issue #121, planejamento e gates](https://github.com/wesscosta/arena-dev/issues/121). A implementação só poderá começar depois da aprovação A0–A4, preservando a [devolutiva simples da v0.11](v0.11/11.13_STUDENT_PUBLISHED_FEEDBACK.md).

## 2.3 Linha de engenharia — D12-ENG-01 (obrigatória, em paralelo ao design)

A v0.12 não tratará DRY/SOLID como ajustes oportunistas durante a troca de telas: a organização do código tem prioridade equivalente à UI/UX, com diagnóstico e critérios de aceite próprios.

- **E0/E1 (auditoria, sem alteração funcional):** investigar fronteiras de React/Next, backend Java, services/DTOs, estado e efeitos, CSS, dependências, duplicações reais, segurança, performance e acoplamento. Não classificar código apenas pelo número de linhas.
- **E2 (priorização):** matriz P0–P3 com evidência, responsável, risco, ganho esperado, testes de caracterização e rollback; decisões aprovadas por domínio.
- **E3 (refatoração aprovada):** simplificar orquestradores, extrair componentes/hooks e serviços, consolidar regras e tokens somente quando houver ganho demonstrado. Alteração de JSX/CSS dependente da nova UI fica bloqueada até A0–A4; migração/API exige decisão específica.
- **E4 (validação):** comportamento equivalente, CI, backend/integração, Chromium/WebKit, acessibilidade, performance medida, autorização e regressão de dados, rollback e handoff. Incluir reprodução da [issue #36](https://github.com/wesscosta/arena-dev/issues/36) com centenas de turmas/sessões.

**Plano completo:** [D12-ENG-01 — Auditoria arquitetural e refatoração segura](v0.12/ENGINEERING_AUDIT_AND_REFACTOR_PLAN.md). A [auditoria SOLID histórica de 20/10](https://app.notion.com/p/3fff3eb198c38142964fe89bb9be729e) levantou hipóteses sobre concorrência, sockets, dependências e testes; **revalidar contra a v0.11.0 estável** antes de declarar defeitos persistentes. A0/A1 e E0/E1 podem acontecer em paralelo; implementação em PRs pequenas depois dos gates.

## 3. Linha C — Active Learning já previsto anteriormente (discovery separado)

O [handoff v0.9](HANDOFF_V0_9.md) definiu a direção **v0.12 — Active Learning**, que deve ser mantida no planejamento:

| Capacidade candidata acordada | Guardrail pedagógico/técnico |
| --- | --- |
| **Missions** | missão contextualizada com objetivo, evidência e rubrica; não mera animação/XP |
| **Group submissions** | entrega coletiva com autoria, permissão e rastreabilidade de contribuição |
| **Student-created questions** | questões produzidas por alunos, sujeitas a curadoria/revisão docente |
| **Explanation required** | justificar respostas quando a rubrica e a acessibilidade tornarem isso útil |
| **Advanced recovery rounds** | retomar um instrumento após intervenção, com evidência comparável e idempotência |
| **Rich response types** | novos formatos de resposta condicionados a avaliadores e objetivos validados |

**Preservar esses seis eixos no roadmap não significa implementá-los todos na v0.12.** Suas fatias dependem de priorização, entrevistas, arquitetura, revisão de conteúdo e piloto. Não criar tabelas/APIs de avaliação sem essa decisão.

## 4. Itens antes adiados — discovery separado, não compromisso automático da v0.12

- **Group by Response e Meta Coletiva:** exigir ganho pedagógico e consentimento; evitar dinâmica de pontuação estilo bets/boss.
- **Canva e HTML incorporado:** comparar com link HTTPS seguro e retorno à Arena; iframe somente após ganho comprovado, CSP e isolamento.
- **Questões para concursos, editais e simulados:** partir do [discovery 11.5](v0.11/11.5_DECISIONS_AND_BACKLOG.md) e da [ADR-0068 proposta](adr/ADR-0068-editorial-provenance-taxonomy-proposal.md); revisão jurídico-editorial e docente antes de catálogo/importação.
- **IA avançada, RAG, Mastery longitudinal:** dependem de evidência, identidade, retenção, privacidade e revisão humana; não inferir domínio de exposição/progresso.
- **Multi-tenant/SaaS:** requer arquitetura de autorização/isolamento própria, não apenas telas novas.

As alternativas podem ser reavaliadas e rejeitadas por falta de benefício. Não promover decisões experimentais a funcionalidades prontas.

## 5. Definition of Done da nova experiência

1. Matriz Figma ↔ rota ↔ componente ↔ API ↔ estado ↔ ator auditada.
2. Design System real (tokens, componentes reutilizáveis, Auto Layout/Variants, estados), tipografia legível, contraste aferido e sem scroll preso.
3. Três telas-piloto aprovadas antes de replicar padrões para outras telas.
4. Testes desktop/tablet/mobile, teclado, zoom, Safari/iOS e leitores de tela; nenhum botão decorativo sem ação.
5. Mesmos contratos de sessão, idempotência, snapshot público seguro, revogação e controles docentes.
6. Métricas com interpretação correta; evitar números demonstrativos apresentados como dados reais.
7. Histórico de alterações visuais, CI regressivo, aceite pedagógico e plano de rollback.
8. Escopo da v0.11 preservado sem novas funcionalidades nessa release.
9. Frente de engenharia E0–E4 auditada, aprovada e aplicada em fatias com critérios DRY/SOLID, testes de equivalência, revisão de segurança e performance, sem reescrita indiscriminada.

**Insumo da auditoria de 19/10/2026** ([Notion](https://app.notion.com/p/3fef3eb198c381148bdcf38b4cdee1ab)): estado na URL e decomposição do `ArenaApp` junto das telas piloto; SPI de mecânicas ao vivo; tokens em 3 camadas com lint bloqueando literais; identidade clara sem neon e fonte auto-hospedada; presets de gamificação por sessão e metas coletivas; "você foi chamado(a)" só para o sorteado; migrar testes de regex para comportamento.

**Próxima ação da v0.12:** executar o **G0 de baseline/infraestrutura**, seguido das auditorias **A0 (Figma/UX)** e **E0 (arquitetura)** em paralelo, enquanto a equipe delibera [cada um dos 133 tópicos do inventário](v0.12/V012_SCOPE_TRIAGE_REGISTER.md) segundo o [roteiro R0–R9](v0.12/V012_EXECUTION_PLAN.md). O acesso integral ao Figma, a CI funcional, a questão de performance #36 e o aceite dos três pilotos continuam gates obrigatórios. Os futuros códigos do redesenho e das funcionalidades permanecem bloqueados até os aceites correspondentes. O desenvolvimento Active Learning avançará depois de decisões explicitamente aprovadas, não em paralelo por padrão.

**Fontes:** Notion: [auditoria Figma-first e gate](https://app.notion.com/p/3f9f3eb198c38136b319e5807170f8bf), [Jornada PTD: mapa, parallax e timeline](https://app.notion.com/p/3f9f3eb198c3816d9e12d12d97cd17f9), [Design System e handoff Figma-first](https://app.notion.com/p/3f9f3eb198c38141b995d80952c1bc79). Repositório: [fechamento v0.11](v0.11/11.6_TECHNICAL_CLOSURE_NO_GO_2026-10-10.md), [roadmap v0.11](ROADMAP_V0_11.md), [handoff v0.9](HANDOFF_V0_9.md), [UX v0.10](v0.10/UX_FLOWS.md), [Design System](DESIGN_SYSTEM.md).

## 6. Reavaliação de produto de 24/10/2026 — discovery para os pilotos (não implementado)

Priorizar o protótipo docente **Preparar → Conduzir → Analisar**, com foco no fluxo real de aula e sem multiplicar entradas independentes para Quiz, Verificar aprendizagem, Votação ou Nuvem. Evoluir **D12-DYN-01** preservando os registros e o cutover comprovado do Learning Core. Criar hipótese de avaliação a partir de material da Jornada/Biblioteca (com fonte, revisão, gabarito e rubrica aprovados), aplicação na Arena/Atividades e visualizações moderadas de respostas abertas. **Tempo por questão** do professor difere do **tempo total de tentativa** já na PR #101. Código/SQL necessita sandbox isolada. Os critérios detalhados de privacidade, estado, interação, acessibilidade, métricas e sequenciamento estão no [índice técnico](PRODUCT_REASSESSMENT_2026-10-24.md) e no [Notion](https://app.notion.com/p/403f3eb198c38187a735cb36f988fc80). Aprovação formal Figma A0–A4 e atribuição de release continuam pendentes.
