# v0.12 — Registro mestre de escopo e triagem de propostas

**Data-base:** 25/10/2026. **Baseline:** [v0.11.0 estável](https://github.com/wesscosta/arena-dev/releases/tag/v0.11.0) no SHA `1f23df66945c409085c7ba8af653a717a479b4b5`. **Owner:** responsável pelo produto. **Status deste documento:** inventário consolidado para revisão, **não escopo funcional aprovado**.

Este documento reúne as propostas registradas nos roadmaps, Notion, arquitetura, ADRs e issues. A classificação é **preliminar e revisável**: qualquer item só entra na implementação quando houver decisão de produto específica, gate aplicável e critério de aceite. A v0.12 já aprovou como **frentes de trabalho** a auditoria/modernização UI/UX e engenharia; não aprovou a construção de todos os candidatos abaixo.

## 1. Significado das classificações

| Código | Significado | Autoriza código agora? |
| --- | --- | --- |
| **12-GATE** | Governança/diagnóstico/condição obrigatória da v0.12 | Não; obriga produzir evidência |
| **12-CORE** | Candidato de execução aderente ao foco UI/engenharia, após validação | Não; exige A4/E2/aceite |
| **12-DESIGN** | Explorar e validar no Figma durante v0.12 | Não; protótipo ≠ feature |
| **12-DISC** | Analisar e decidir na v0.12; implementação depende de decisão posterior | Não |
| **P0-PATCH** | Potencial defeito de escala: revalidar e, se reproduzível, considerar patch separado | Não; exige reproduzir |
| **REVALIDAR** | Função já entregue em v0.11 ou achado antigo: medir diferença/residual | Não |
| **PÓS** | Candidato preliminar a ciclo posterior, **sem prometer v0.13/v0.14** | Não |
| **BLOQUEIO** | Dependência de operação que precisa ser removida para começar PRs | Não |
| **NÃO ADOTAR** | Ideia rejeitada como padrão; somente reabrir por decisão explícita | Não |

**Triagem quantitativa:** 133 registros agrupados em 10 frentes. 12-GATE: 12; 12-DESIGN: 20; 12-CORE: 21; 12-DISC: 31; REVALIDAR: 13; P0-PATCH: 1; BLOQUEIO: 1; PÓS: 33; NÃO ADOTAR: 1. Quantidades mostram inventário de decisões, **não volume de features prometidas**.

## 2. Regras de decisão (aplicar a cada item)

1. **Valor para professor/aluno:** qual tarefa concreta melhora? há uso real e fricção demonstrada?
2. **Aderência ao MVP:** resolve UI/UX, manutenção/escala ou risco operacional da v0.11? Ou introduz novo domínio?
3. **Dependências:** requer conta verificada, taxonomia, direitos autorais, IA, Flyway, sandbox, ADR ou Design System?
4. **Impacto e risco:** classificar P0–P3, reversibilidade, autorização, PII, gabarito, projeção, rede e mobile.
5. **Custo/benefício:** hipótese de ganho observável (tempo, erros, requests, clareza, manutenção); esforço e prazo só após diagnóstico.
6. **Gates:** A0–A4 para mudança visual; E0–E4 para arquitetura; segurança/pedagogia/aceite por domínio quando aplicável.
7. **Decisão explícita:** `V0.12 / DESIGN-ONLY / POSTERIOR / REVALIDAR / REJEITAR` + justificativa + responsável + data + evidência. **Status preliminar aqui não substitui a decisão.**

## 3. Inventário de tópicos

### 01 — Governança, marca e Design System

**Fontes desta seção:** [roadmap v0.12](../ROADMAP_V0_12.md) · [auditoria Figma](https://app.notion.com/p/3f9f3eb198c38136b319e5807170f8bf) · [Design System](https://app.notion.com/p/3f9f3eb198c38141b995d80952c1bc79) · [identidade](https://app.notion.com/p/3f9f3eb198c381e5bd1fe3f18a8f6ab0).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `A01` | Inventário real de telas/rotas, personas e estados | **12-GATE** | Base para preservar/refatorar/remover; sem refazer telas sem diagnóstico | A0 + E0; mapa rota→componente→API |
| `A02` | Auditoria do Figma existente e paridade com código real | **12-GATE** | Protótipos parciais não representam o produto completo | A0; registro lacunas Figma Starter |
| `A03` | Brand Ramuvi: distintividade, logo, símbolo, tipografia e naming | **12-DESIGN** | Identidade candidata ainda não congelada; evitar cópia de paletas padrão | A1; teste de leitura e contraste |
| `A04` | Paleta navy/petróleo/dourado, claro/escuro e neutralidade | **12-DESIGN** | Validar cor em contexto e evitar neon/gradiente/cassino | A1; tokens semânticos e WCAG |
| `A05` | Tipografia licenciada/local e hierarquia editorial | **12-DESIGN** | Consistência, legibilidade desktop/projetor/mobile | A1; estilos, licenciamento e 200–400% zoom |
| `A06` | Tokens de espaço, raio, elevação, densidade, cores e movimento | **12-CORE** | Fundação de implementação visual coerente | A1/A4; mapeamento token Figma→código |
| `A07` | Componentes UI com variantes/estados e acessibilidade | **12-CORE** | Evitar múltiplos botões, inputs, tabelas e cards sem padrão | A1/A4; keyboard/a11y + API de componente |
| `A08` | Cards tipados: Operational, Learning, Activity, Resource, Live, Metric | **12-DESIGN** | Mostrar conteúdo operacional real, não ornamento genérico | A1/A2; protótipos com dados verdadeiros |
| `A09` | Navegação lateral, header redundante, menus e breadcrumbs | **12-DESIGN** | Reduzir troca de telas e redundância professor/aluno | A2; piloto com tarefas reais |
| `A10` | Padrão Preparar → Conduzir → Analisar (professor) | **12-DESIGN** | Alinhar UI ao trabalho docente e reduzir painéis | A2; simulação de aula |
| `A11` | Três pilotos: Início docente, Jornada aluno, Arena/Projetor | **12-GATE** | Obrigatórios antes de propagar redesign | A2; responsivo e protótipos navegáveis |
| `A12` | Cobertura completa Professor/Aluno/Projetor, vazios e erro | **12-CORE** | Não deixar fluxos críticos com duas linguagens visuais | A3/A4; matriz de estados |
| `A13` | Filtros/tabelas/densidade, formulários iPhone e safe areas | **12-CORE** | Prioridade UX, sem regressão do Safari já estabilizado | A2/A3; 320–390px, zoom, toque |
| `A14` | Feedback local, estados loading/reconexão e erros contextuais | **12-CORE** | Evitar redirecionamentos sem retorno e toasts que cobrem ações | A3/A4; testes por estado |
| `A15` | Zoom de conteúdo vs zoom de página e opção movimento reduzido | **12-CORE** | A11y e preferência com semântica coerente | A1/A4; zoom 200–400%, WCAG 2.2 |
| `A16` | Presets de gamificação por sessão, não no professor | **12-DISC** | Ajustar tom adulto, sem painel de jogador docente | A2; comparação com fluxo de aula |
| `A17` | Gates Figma A0–A4; aceite individual por tela | **12-GATE** | Primeiro aprovar experiência, depois código | A0→A4; decisão APROVADO/AJUSTAR/REJEITADO |

### 02 — Engenharia, qualidade e segurança

**Fontes desta seção:** [D12-ENG-01](ENGINEERING_AUDIT_AND_REFACTOR_PLAN.md) · [auditoria SOLID 20/10](https://app.notion.com/p/3fff3eb198c38142964fe89bb9be729e) · [issue #36](https://github.com/wesscosta/arena-dev/issues/36).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `E01` | Inventário de módulos/dependências frontend e backend | **12-GATE** | Base DRY/SOLID e responsabilidades verificadas | E0; mapa e baseline |
| `E02` | ArenaApp: estado global, bootstrap e ações nomeadas | **12-CORE** | Auditar orquestrador e patch global; extrair por responsabilidade | E1/E2; testes de caracterização |
| `E03` | ActivitiesView: editor, validações, efeitos e apresentação | **12-CORE** | Coesão e componentes reutilizáveis; evitar extrair indiscriminadamente | E1/E2; regressão de formulário |
| `E04` | MechanicsService: Boss, grupos, questões e roteiro | **12-CORE** | Separar responsabilidades e concorrência sem mudar APIs | E1/E2; transações e testes |
| `E05` | Cliente único de WebSocket e recuperação aluno/projetor/docente | **12-CORE** | Auditoria anterior encontrou divergência; verificar SHA atual | E1; E2E rede/reconciliação |
| `E06` | Estado de dinâmica tipado, guarda concorrente e versões | **12-CORE** | Evitar perda/duplicação de evento; hipótese histórica SOLID | E1; teste concorrente idempotente |
| `E07` | Bugs antigos: gruposDirty, concorrência Boss, versão do palco aluno | **REVALIDAR** | Achados SOLID 20/10 podem ter sido corrigidos na 11 final | E0; reproduzir antes de priorizar P0 |
| `E08` | Contrato de eventos realtime e união discriminada | **12-CORE** | Evitar eventos arbitrários sem cobertura de tipos | E1/E2; backend/frontend com tipos fechados |
| `E09` | Centralização do requireActive e mapeamento 409 | **12-CORE** | Duplicações de sessão e semântica de erro potencialmente divergente | E1/E2; contratos de erro |
| `E10` | Leitura e persistência de opções JSON por vários fluxos | **12-CORE** | Inconsistências históricas de serialização; avaliar invariant | E1; testes por modalidade |
| `E11` | Tratamento de corrupção de estado e fallbacks silenciosos | **12-CORE** | Estados inválidos não podem desaparecer sem logs | E1; cenário de conteúdo legado |
| `E12` | Erros HTTP de frontend e classes duplicadas | **12-CORE** | Consolidar API errors sem destruir casos incertos de sorteio | E1/E2; testes de erro/retry |
| `E13` | Ciclos de dependências Java, ArchUnit e LiveMechanic SPI | **12-CORE** | Extensão de dinâmicas pode exigir mudanças em muitos módulos | E1/E2; evitar abstração antecipada |
| `E14` | Ponte shadow Learning Core e plano cutover do Quiz | **12-DISC** | Tem redundância, mas remoção exige equivalência e rollback | E1 + D12-DYN-01; shadow |
| `E15` | Testes DOM vs regex do código-fonte | **12-CORE** | Reduzir fragilidade ao tocar componentes; manter testes estáticos úteis | E2/E4; tests comportamento |
| `E16` | Refatoração CSS globals/arena-polish/cascata/tokens | **12-CORE** | Reduzir regras redundantes durante redesign aprovado | A1/A4 + E2; regressão visual |
| `E17` | SOLID/DRY/KISS/YAGNI e ADRs estritamente justificadas | **12-GATE** | Princípios guiam decisão, não contagem de classes/linhas | E0–E4; relatório por fato |
| `E18` | P0 bootstrap N+1/fan-out >=350 turmas/340 sessões | **P0-PATCH** | Issue #36 aberta: desempenho de entrada antes de ampliar uso | Reproduzir e decidir patch separado |
| `E19` | Backups, Flyway, autorização, CSP e segurança por domínio | **12-CORE** | Preservar integridade, idempotência e privacy | E1/E4; contrato, rollback e abuso |
| `E20` | Observabilidade, p95, logs, rate limit e sessão docente | **12-DISC** | Confrontar plano técnico anterior com baseline já homologada | E0; medição sem PII |
| `E21` | Handoff, documentação enxuta, CI e rollback por SHA | **12-GATE** | Evitar documentação contraditória e PRs enormes | E4; CI e registro de evidências |
| `E22` | CI bloqueada por Billing & plans de GitHub Actions | **BLOQUEIO** | Nenhum teste inicia no SHA documental b329ff6 | Resolver faturamento e reexecutar CI |

### 03 — Professor, turmas e operações

**Fontes desta seção:** [roadmap v0.12](../ROADMAP_V0_12.md) · [Design System](https://app.notion.com/p/3f9f3eb198c38141b995d80952c1bc79) · [reavaliação 24/10](../PRODUCT_REASSESSMENT_2026-10-24.md).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `P01` | Home docente como centro de triagem operacional | **12-DESIGN** | Resumir próximas ações sem duplicar Resultados/Ranking | A2; jornada do docente |
| `P02` | Navegação por tarefas: preparar, conduzir e analisar | **12-DESIGN** | Convergir espaços sem apagar capacidades | A2; mapa de IA e card sorting |
| `P03` | Tabela de participantes: colunas email, nome/nickname, ações | **12-DESIGN** | Organização e legibilidade, sem confundir status e papel | A3; 24 alunos e ultrawide |
| `P04` | Ranking separado de participantes e notas | **12-DESIGN** | Evitar sobreposição semântica de painéis | A3; teste persona |
| `P05` | Editar turmas/atividades sem modais extensos | **REVALIDAR** | Cadastro de Atividades em página já foi entregue na 11 | A0; identificar modais ainda inadequados |
| `P06` | Configurações docente e aluno com seções claras | **12-DESIGN** | Reduzir redundância de controles/menus | A3; fluxo real |
| `P07` | Navegação por seção/etapa persistida em URL | **12-DISC** | Recuperar tela e deep link sem estado invisível | A2/E1; reconexão/permissões |
| `P08` | Roteiro de aula opcional, não fluxo imposto | **12-DESIGN** | Apoiar improvisação e Ação–Reflexão–Ação | A2; piloto docente |
| `P09` | Seleção de materiais, preparação e prévia ao projetor | **12-DESIGN** | Rota professor→arena sem deslocamento excessivo | A2; fluxo ponta a ponta |

### 04 — Jornada, PTD e biblioteca

**Fontes desta seção:** [Jornada PTD](https://app.notion.com/p/3f9f3eb198c3816d9e12d12d97cd17f9) · [roadmap v0.12](../ROADMAP_V0_12.md) · [Design System](https://app.notion.com/p/3f9f3eb198c38141b995d80952c1bc79) · [Learning 17.x](../LEARNING_VALIDATION_ROADMAP.md).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `J01` | Jornada em mapa horizontal com checkpoints/parallax moderado | **12-DESIGN** | Aprovado para prototipação, não implementação | A2; sem scroll-jacking global |
| `J02` | Jornada em timeline vertical equivalente | **12-DESIGN** | Mesmos dados, acessibilidade e alternativa móvel | A2; 200–400%, teclado |
| `J03` | Bloqueio/liberação docente de capítulos | **REVALIDAR** | Fundação funcional já implementada na 11 | A0; melhorar UX sem duplicar regra |
| `J04` | Estado da etapa: objetivos, conteúdo, atividades e continuar | **12-DESIGN** | Próxima ação e progresso reais, sem XP especulativo | A2; teste curricular |
| `J05` | Importação Plano de Curso/PTD para roadmap curricular | **12-DISC** | Extração, incerteza, revisão humana, versões e provenance | A2; protótipo com PTD real |
| `J06` | Reordenação/edição curricular sem perder progresso | **12-DISC** | Exige snapshots/migration e semântica de histórico | E1 + piloto, posterior provável |
| `J07` | Conteúdo de Biblioteca com origem/versão e publicação imutável | **REVALIDAR** | Snapshot e autorização já existem parcialmente na 11 | A0/E0; diferença vs requisito |
| `J08` | Código na Jornada: restringir menu direito opcional | **12-DISC** | Decisão D12-JR-CODE-01, opt-in por jornada | A2/A4; 4 combinações de toggles |
| `J09` | Código na Jornada: restringir Ctrl/Cmd+C só em pre/code | **12-DISC** | Controle didático, não DRM nem antifraude | A2/E1; não bloquear texto normal |
| `J10` | Retorno do aluno à Jornada e modo leitor/projetor | **REVALIDAR** | Correções mobile/header e tokens de reingresso entregues | A0; medir lacunas reais |
| `J11` | Mapa de competências/indicadores do PTD por etapa | **12-DISC** | Depende de fonte curricular e validação docente | A2; dados auditáveis |
| `J12` | Roadmaps independentes de turma e editais | **PÓS** | 17.2; não duplicar Journey contextual | Taxonomia 17.1 e rights |

### 05 — Identidade, aluno e autenticação

**Fontes desta seção:** [issue #119](https://github.com/wesscosta/arena-dev/issues/119) · [reavaliação 24/10](../PRODUCT_REASSESSMENT_2026-10-24.md).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `I01` | Primeiro acesso por convite/código de turma com verificação | **12-DISC** | Desenhar fluxo e ameaças, sem auth nova automática | A2; confirmação individual |
| `I02` | Conta única do aluno (SSO institucional, OTP ou passkey) | **PÓS** | Mudança estrutural de autorização/identidade; release própria sugerida | Threat model, cross-device, LGPD |
| `I03` | Vínculo StudentAccount↔Enrollment em N turmas | **PÓS** | Contrato e migração complexos; não criar antes de aceite | E1, migração, testes de permissão |
| `I04` | Aluno logado acessa Jornada, Atividades e Arena sem novo código | **12-DISC** | Prototipar continuidade; implementação depende de identidade verificada | A2 + I02/I03 |
| `I05` | Reingresso lembrado no mesmo navegador/dispositivo | **REVALIDAR** | Base v0.11 existente, não é login universal | A0; segurança e UX |
| `I06` | Convidado temporário separado de aluno matriculado | **PÓS** | Modelo de permissões independente, risco de acesso duradouro | Threat model, revogação |
| `I07` | Dispositivo compartilhado, sessão, revogação e LGPD | **12-DISC** | Fluxo deve ser definido visualmente; backend só após auth ADR | A2/E1; privacidade |
| `I08` | Acessibilidade e preferências pessoais centralizadas | **REVALIDAR** | Já consolidadas na 11, verificar UX residual | A0; a11y/privacidade |

### 06 — Atividades, questões e avaliação

**Fontes desta seção:** [D12-AR-01](STUDENT_ASSESSMENT_REVIEW_DESIGN.md) · [reavaliação 24/10](../PRODUCT_REASSESSMENT_2026-10-24.md) · [Learning 17.x](../LEARNING_VALIDATION_ROADMAP.md).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `Q01` | Editor Atividades por etapas com revisão ampla | **REVALIDAR** | Implementado na 11; não recriar | A0; testes UI |
| `Q02` | Importação JSON e cópia de Atividade entre turmas | **REVALIDAR** | Implementado na 11; analisar UX/escala | E0/A0 |
| `Q03` | Correção objetiva determinística MULTIPLE_CHOICE/TRUE_FALSE | **12-DISC** | D12-AR-01: prototipar; backend após contrato + segurança | A2/A4; sem leakage |
| `Q04` | Política docente TEACHER_RELEASE ou AFTER_SUBMISSION | **12-DISC** | Escolha requer análise de gabarito entre alunos | A2; piloto avaliações |
| `Q05` | Pós-entrega: resposta própria, acerto, pendente e explicação | **12-DISC** | Proteção por matrícula/tentativa e revisão humana | A2/A4; states WebKit |
| `Q06` | Correção aberta, prática, análise e código por rubrica humana | **12-DISC** | Não inferir incorreto por ausência de gabarito literal | Rubrica docente e privacidade |
| `Q07` | Rubrica, nota, XP, mastery e progresso não equivalentes | **12-GATE** | Invariante pedagógica e arquitetural | E1; teste dos domínios |
| `Q08` | Banco de questões reutilizáveis com provenance/snapshot | **PÓS** | Catálogo próprio requer rights/taxonomia/editorial | 17.3 + G01/G02 |
| `Q09` | Assessment Blueprint e domínio por conjunto de evidências | **PÓS** | 17.4: múltiplas questões e cobertura; não leitura | 17.1/17.3 |
| `Q10` | Importação supervisionada de questões e direito de uso | **PÓS** | Editorial G01–G08, autoria e revisão | Política jurídico-editorial |
| `Q11` | Avaliação SQL/código em sandbox isolado | **PÓS** | Alto risco: CPU/tempo/rede/filesystem/testes determinísticos | Isolamento, limites e custo |
| `Q12` | Submissão em grupo com autoria/rastreabilidade | **12-DISC** | Active Learning: análise de valor sem duplicar Activity | Piloto e modelo de contribuição |
| `Q13` | Questões produzidas por alunos e curadoria docente | **12-DISC** | Active Learning: revisão, moderação e autoria | Piloto pedagógico |
| `Q14` | Explicação obrigatória configurável por questão | **12-DISC** | Não inviabilizar acessibilidade/tipos de resposta | Piloto e rubrica |

### 07 — Arena, dinâmicas e sala ao vivo

**Fontes desta seção:** [reavaliação 24/10](../PRODUCT_REASSESSMENT_2026-10-24.md) · [issue #48](https://github.com/wesscosta/arena-dev/issues/48) · [auditoria SOLID 20/10](https://app.notion.com/p/3fff3eb198c38142964fe89bb9be729e).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `D01` | Consolidar Quiz e Verificar aprendizagem | **12-DISC** | D12-DYN-01: redundância semântica; evitar dois módulos | Figma + shadow/equivalência/rollback |
| `D02` | Votação como modalidade sem gabarito | **12-DISC** | Pode reduzir entradas de navegação | Piloto docente; anonimato |
| `D03` | Sorteio Grid/Spotlight e feedback do sorteado | **REVALIDAR** | Substituições já existentes; não recriar estética cassino | A0; coerência de uso |
| `D04` | Boss, Timer, Grupos e Ranking como ferramentas contextuais | **12-DESIGN** | Evitar navegação inchada; preservar mecânicas | A2; não alterar XP |
| `D05` | Preparação de sequência de questões e intervenções | **12-DESIGN** | Professor decide objetivo e fluxo, não widget isolado | A2; plano de aula |
| `D06` | Ritmo configurável de 30s por questão/sem limite | **12-DISC** | Prazo de rodada ≠ deadline da tentativa individual | A2; acessibilidade e backend clock |
| `D07` | Avanço manual, pausar/debater/reexplicar/repetir | **12-DESIGN** | Sem avanço automático no fim do tempo | A2; cenário ao vivo |
| `D08` | Distribuição em tempo real sem revelar gabarito | **12-DESIGN** | Docente acompanha interpretação antes de resultado | A2; privacidade/projetor |
| `D09` | Deadline autoritativo por rodada e reconciliação | **PÓS** | Mudança backend/realtime; não no simples redesign | Testes concorrência/tempo/offset |
| `D10` | Missions contextualizadas com rubrica/evidência | **12-DISC** | Eixo Active Learning futuro, não recompensa vazia | Piloto e blueprint |
| `D11` | Rodadas de recuperação avançada | **12-DISC** | Evidência antes/depois de intervenção, não pontuação isolada | Idempotência e rubrica |
| `D12` | Rich response types e interação multimodal | **12-DISC** | Novo formato depende de objetivo e avaliador | A2; revisão técnica |
| `D13` | Group by Response e Meta Coletiva | **PÓS** | Ganho pedagógico incerto; cuidado bets/consentimento | Piloto contrafactual |
| `D14` | Nuvem de conceitos de respostas abertas, moderada | **12-DISC** | Nuvem não é avaliação/nota; texto original preservado | PII, consentimento, moderação |
| `D15` | Síntese de argumentos/discordâncias com IA e professor | **PÓS** | IA só assistiva, sem expor autoria/PII | Proveniência, qualidade, custos |
| `D16` | Uso de dois projetores e estados offline/reconexão | **REVALIDAR** | Contrato/estabilização v0.11, regressão obrigatória | A2/E4; 2 projetores |
| `D17` | Questões objetivas/discursivas/criação de atividades ao vivo | **12-DISC** | Avaliar convergência de componentes, não duplicar tela | A2; mapa prepare/conduct/analyze |
| `D18` | SPI de mecânicas e cutover para InteractionRun | **PÓS** | Dependente da auditoria SOLID e shadow | E1 + D01; ADR e rollback |

### 08 — Biblioteca, IA, conteúdo e propriedade intelectual

**Fontes desta seção:** [reavaliação 24/10](../PRODUCT_REASSESSMENT_2026-10-24.md) · [11.5 decisões](../v0.11/11.5_DECISIONS_AND_BACKLOG.md) · [Jornada PTD](https://app.notion.com/p/3f9f3eb198c3816d9e12d12d97cd17f9) · [Learning 17.x](../LEARNING_VALIDATION_ROADMAP.md).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `L01` | Material de Jornada/Biblioteca como fonte de questão | **12-DISC** | Conceito de geração com revisão docente antes de publicar | A2; piloto com BD real |
| `L02` | IA propõe questões, rubricas e explicações com revisão humana | **PÓS** | Assistivo, sem publicar gabarito automático | Proveniência, segurança, custo |
| `L03` | Upload e extração PDF/PPTX/DOCX com conteúdo não confiável | **PÓS** | Tipos/tamanho/MIME/direitos/prompt injection | Security review e policy |
| `L04` | Snapshot/hash/revisão/autoria da origem das questões | **PÓS** | Trilha de proveniência antes de banco reutilizável | G01/G02/G03 |
| `L05` | Matriz de direitos G01 e política de revisores G02 | **PÓS** | Não inferir permissão de exibição pelo formato ou sourceType | Jurídico/editorial |
| `L06` | Backfill 3 hashes ausentes G03 (somente após plano) | **PÓS** | Risco de dados: diagnóstico read-only primeiro | Backup e rollback |
| `L07` | Mapeamento de objetivos G04 e edital piloto G05 | **PÓS** | Validação docente e catálogo sem duplicação | Duas áreas e edital oficial |
| `L08` | Catálogo opcional G06/importação supervisionada G07 | **PÓS** | Não implementar sem ganho comprovado | G01/G02/G04 |
| `L09` | Métricas editoriais G08 e rastreabilidade de avaliações | **PÓS** | Tempo de catalogação/qualidade com PII minimizada | Amostra e auditoria |
| `L10` | Canva/HTML incorporado em sessão/material | **PÓS** | Iframe necessita isolamento/CSP e ganho real vs link | Threat model |
| `L11` | Ingestão Markdown segura e reader nativo | **REVALIDAR** | Infraestrutura v0.11 existente; auditar lacunas reais | E0 + sanitização |
| `L12` | Biblioteca como recurso reutilizável entre turmas | **12-DISC** | Vínculo editorial sem duplicar objetos | A2; snapshot e permissão |

### 09 — Learning Architecture, concursos e progressão

**Fontes desta seção:** [Learning 17.x](../LEARNING_VALIDATION_ROADMAP.md) · [referências gamefication](https://app.notion.com/p/3f9f3eb198c381d5b070c8469f1fcf3d) · [11.5 decisões](../v0.11/11.5_DECISIONS_AND_BACKLOG.md).

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `M01` | 17.0 Audit e LearningEvidence/KnowledgeNode | **PÓS** | Arquitetura de domínio posterior sem release atribuída | Domain review independente |
| `M02` | 17.1 Taxonomia de conhecimento | **PÓS** | Nós reutilizáveis entre curso e edital | Versão, ownership e rights |
| `M03` | 17.2 Roadmaps e editais independentes da Jornada | **PÓS** | Não duplicar o currículo da turma | Taxonomia e blueprint |
| `M04` | 17.3 Banco de questões por banca/ano/prova/origem | **PÓS** | Licenças, validade da questão, correção verificável | Editorial e provenance |
| `M05` | 17.4 Mastery e Assessment Blueprint | **PÓS** | Não inferir domínio por leitura, XP ou questão única | Evidência auditável |
| `M06` | 17.5 Review & Retention | **PÓS** | Revalidar conhecimentos, sem esquecimento automático | Mastery estável |
| `M07` | 17.6 Teacher Learning Dashboard | **PÓS** | Separar consumo, domínio, tentativas e dificuldades | Taxonomia+evidência |
| `M08` | 17.7 Progressão visual, conquistas e avatar | **PÓS** | Somente marcos comprovados, sem infantilizar adulto | Mastery, UX piloto |
| `M09` | 17.8 Recomendações adaptativas auditáveis | **PÓS** | Regra clara antes de IA, sem modelo opaco | Evidências reais |
| `M10` | Roadmaps por carreira e concursos como segmento | **PÓS** | Produto/monetização requer discovery B2C | Piloto e editorial |
| `M11` | Moedas, loja, stamina, ranking obrigatório, streak punitivo | **NÃO ADOTAR** | Contradiz foco de estudo/adulto e evidência | Somente reconsiderar com decisão explícita |

### 10 — Integrações, operação e governança de produto

**Fontes desta seção:** [reavaliação 24/10](../PRODUCT_REASSESSMENT_2026-10-24.md) · [roadmap v0.12](../ROADMAP_V0_12.md) · ISS36.

| ID | Tópico | Triagem inicial | Justificativa / pergunta | Validação necessária |
| --- | --- | --- | --- | --- |
| `O01` | Microsoft Teams/Google Classroom e logs de importação | **REVALIDAR** | Base v0.7; verificar suporte e gaps antes de nova camada | A0/E0; contratos integração |
| `O02` | Multi-tenant SaaS com isolamento entre organizações | **PÓS** | Mudança arquitetural/security própria, não só UI | Threat model, autenticação, storage |
| `O03` | Paridade web/PWA Android/iOS e permissões | **12-CORE** | Manter instalabilidade, safe areas e sincronização | A3/E4; hardware real |
| `O04` | Observabilidade por sessão/turma, sem painel fictício | **12-DISC** | Métricas precisam de denominador e fonte | E0; privacidade |
| `O05` | Export/import de recursos com direitos e provenance | **PÓS** | Operação editorial e segurança ainda não aprovada | G01/G02 e testes |
| `O06` | Decomposição do produto em Learning/Activity/Arena/Library | **12-GATE** | Não fundir domínios em UI nem no código | A0/E0; ADR-0045 |
| `O07` | Conformidade e licenciamento/copyright da marca/conteúdo | **12-DISC** | Brand governance e editorial sem inventar direitos | A1 + jurídico |
| `O08` | Acessibilidade AAA onde possível, WCAG AA mínimo | **12-GATE** | Visibilidade/teclado/zoom/screen reader por tela | A1–A4/E4 |
| `O09` | Uso em turmas reais com rubrica de feedback docente/aluno | **12-GATE** | Critério de validação de valor antes de expansão | A2/A4 e piloto |
| `O10` | Documentação, registro de ADRs, handoff e release gates | **12-GATE** | Fonte de verdade, status e continuidade | E4; aprovação por SHA |

## 4. Decisões de precedência e cortes de escopo

- **Produto visual (A0–A4)** e **arquitetura/DRY/SOLID (E0–E4)** são dois eixos obrigatórios. Auditoria primeiro, implementação apenas depois do gate; não redesenhar tudo indiscriminadamente.
- **Jornada = estudar, Atividades = fazer/entregar, Arena = interagir ao vivo, Biblioteca = recurso reutilizável**. Não reescrever regras por semelhança de interface.
- **Autenticação unificada, correção objetiva, banco de questões, IA/RAG, Mastery, sandbox e multi-tenant** não entram automaticamente em execução na v0.12. Podem ter protótipos/discovery no A2 sem virar API/migration.
- **Atividades em página, entrada lembrada no mesmo dispositivo, publicação de feedback, controle de capítulos, leitor/projetor e ajustes Safari** são **baseline v0.11**. Reavaliar somente lacunas reais, não duplicar entregas.
- **Achados SOLID anteriores (B1–B6, classes-deus, sockets, regex tests)** são hipóteses históricas até revalidar contra o commit estável; não declarar regressão presente sem reprodução.
- **Issue #36** recebe prioridade de diagnóstico P0 independente de estética. Se for defeito real atual, decidir patch de manutenção separado da v0.12.
- **Issue #112** Safari/iPhone ainda aberta deve ser reconciliada com as correções da v0.11 antes de abrir trabalho duplicado.
- **GitHub Actions** mostrou bloqueio de faturamento na run documental `40232922481`; sem CI executável, novos merges funcionais ficam bloqueados.
- **Brand direction:** evitar neon, casino, ranking compulsório, moedas e gamificação docente; preferir identidade madura, navegação por tarefa e conquistas justificadas.

## 5. Registro de deliberação por item

Em cada reunião/revisão, registrar numa linha do [log de decisões](V012_EXECUTION_PLAN.md):

`ID | decisão definitiva | motivo | evidência | responsável | data | esforço estimado | dependência | gate/issue | versionamento decidido`

**Regras:** não converter `12-DISC` ou `12-DESIGN` em "promessa da v0.12"; não atribuir uma versão posterior definitiva sem aceite. Marcar descartado sem apagar histórico; se aprovado, adicionar critérios de aceite e teste à issue/PR correspondente.

## 6. Fontes e limites

- [Roadmap v0.12](../ROADMAP_V0_12.md) · [Plano de engenharia](ENGINEERING_AUDIT_AND_REFACTOR_PLAN.md) · [D12-AR-01](STUDENT_ASSESSMENT_REVIEW_DESIGN.md).
- [Matriz editorial G01–G08](../v0.11/11.5_DECISIONS_AND_BACKLOG.md) · [Learning Architecture 17.0–17.8](../LEARNING_VALIDATION_ROADMAP.md) · [Reavaliação mestre](../PRODUCT_REASSESSMENT_2026-10-24.md).
- [Notion da v0.12](https://app.notion.com/p/3f9f3eb198c38136b319e5807170f8bf) · [Auditoria SOLID histórica](https://app.notion.com/p/3fff3eb198c38142964fe89bb9be729e) · [Jornada PTD](https://app.notion.com/p/3f9f3eb198c3816d9e12d12d97cd17f9).
- [Issues #48](https://github.com/wesscosta/arena-dev/issues/48), [#125](https://github.com/wesscosta/arena-dev/issues/125), [#119](https://github.com/wesscosta/arena-dev/issues/119), [#121](https://github.com/wesscosta/arena-dev/issues/121), [#36](https://github.com/wesscosta/arena-dev/issues/36).
- O registro consolida os tópicos localizados nas fontes consultadas em 25/10. **Não afirma que todas as ideias de chats ou históricos não indexados foram recuperadas.** Novos achados entram com ID estável, origem e triagem, sem sobrescrever decisões anteriores.
