# Ramuvi — desempenho P1: isolamento de estilos públicos

## Escopo e arquitetura
PR empilhada sobre a PR #146 (redesign), sem merge nem deploy. O layout raiz carrega apenas cores/tokens, base e acessibilidade. Um pacote de CSS compartilhado completo é importado apenas pelos layouts das rotas /learn, /teach, /join e /projector. Landing, Termos e Privacidade deixam de carregar estilos de turmas, Arena e temporizador. Não foram removidos seletores ou regras de interface educacional.

## Medição reproduzível (19/11/2026)
Build Next 16.4.2 em dois servidores locais isolados; HTML da rota e arquivos de stylesheet referenciados por link rel=stylesheet, somatório de corpos CSS não comprimidos. Não são medições Core Web Vitals reais de usuários nem incluem custos adicionais de JS.

| Rota | Antes, bytes | Depois, bytes | Resultado |
| / | 272452 | 21576 | redução 92,1% |
| /termos | 263891 | 13015 | redução 95,1% |
| /privacidade | 263891 | 13015 | redução 95,1% |
| /teach | 518030 | 518336 | +306 bytes, preserva estilos |
| /learn | 346618 | 346795 | +177 bytes, preserva estilos |
| /join | 464687 | 464993 | +306 bytes, preserva estilos |
| /projector | 282749 | 283055 | +306 bytes, preserva estilos |

O layout público não importa mais arena-polish, globals, classroom-overview, timer-panel ou mobile-reflow. Os ambientes educacionais continuam a carregar esses arquivos explicitamente. Valores se referem à transferência sem compressão em primeiro carregamento.

## Verificações
- 441 testes unitários/contratuais passaram, TypeScript, lint sem erros e build de produção aprovados.
- E2E públicos: 10/10 em Chromium e WebKit móvel (landing, FAQ, acessibilidade, navegação, privacidade e login aluno).
- Comparação de estilos computados básicos em todas as sete rotas: cores e fundo do body, fonte, token de tema e overflow horizontal iguais antes/depois no viewport 390 px.
- Bateria E2E ampliada: 23/32, nove falhas. Testes docentes dependem de backend não iniciado nesta execução; testes WebKit de sessão/aprovação já falhavam na branch-base. NÃO declarar a bateria ampliada aprovada.
- Ainda é necessário executar E2E completos com backend autenticado, testar transição entre rotas com carregamento de CSS, tema claro/escuro e iPhone físico.

## Gates
PR em draft; PR #146 é dependência explícita. Correção do Next.js pertence à PR #147 e falhas WebKit prévias à PR #148. Revalidar o commit resultante depois de integrar as mudanças e depois de executar CI oficial, que ainda apresentava bloqueio operacional. Sem mudanças de banco, autenticação ou Flyway.
