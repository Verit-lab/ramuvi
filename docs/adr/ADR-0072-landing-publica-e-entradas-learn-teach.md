# ADR-0072 — Landing pública e entradas Ramuvi Learn / Ramuvi Teach

- **Status:** Proposto (aguarda homologação da PR)
- **Data:** 2026-11-18
- **Decisores:** equipe Ramuvi
- **Escopo:** produto, UX, frontend, navegação

## Contexto

Até a v0.12.0 a rota `/` servia a área do professor (`ArenaApp`), e o produto se chamava Arena Dev. A v0.12.1 já unificou a entrada do aluno em `/learn` (PR #142). A identidade definitiva passa a ser **Ramuvi**, com dois ambientes: **Ramuvi Learn** (aluno) e **Ramuvi Teach** (professor). A raiz precisa apresentar a plataforma sem exigir login, sem quebrar links docentes já salvos (`/?tela=…`) nem os retornos OAuth das integrações (`/?google=connected` etc.).

O ADR-0017 permite organizar professor, aluno e projetor por rotas diferentes dentro da mesma aplicação Next.js. O ADR-0053 mantém um único operador docente autenticado por implantação.

## Decisão

1. `/` passa a ser uma landing pública estática (sem sessão, sem JavaScript próprio).
2. `/teach` passa a servir a mesma `ArenaApp`, sem duplicar estado, autenticação ou componentes. As telas docentes continuam na query string, agora relativa a `/teach`.
3. `/learn` permanece a entrada do aluno (ADR-0069/0071 e STUDENT_ENTRY_ROUTING).
4. Requisições a `/` com parâmetros docentes conhecidos recebem **307** para `/teach` com a mesma query, no `proxy.ts`. Lista fechada de parâmetros e destino fixo de mesma origem.
5. O backend passa a devolver as integrações OAuth docentes para `/teach?<provider>=connected`.
6. A marca pública é Ramuvi. Identificadores persistidos (chaves de armazenamento do navegador, banco, volumes) e o pacote Java permanecem até migração própria.

Não há conflito com ADRs anteriores: autenticação e autorização (ADR-0024, 0053, 0069, 0070) não mudam; o projetor e `/join` (ADR-0023, 0027) continuam nas mesmas rotas.

## Consequências

### Positivas

- Visitantes encontram a proposta da plataforma e escolhem o ambiente; professores e alunos têm URLs próprias e nomeadas.
- Nenhuma migração de dados, de sessão ou de provedor OIDC/OAuth.
- Links antigos continuam funcionando sem criar redirecionamento permanente antes do aceite.

### Custos e riscos

- Professores que abriam `/` diretamente passam a ver a landing (um clique a mais em "Sou professor").
- Nginx com lista explícita de prefixos precisa incluir `/teach`.
- Persistem identificadores internos `arena-dev`/`arenadev`, documentados em `docs/v0.12.1/RAMUVI_BRAND_AND_ROUTES.md`.

## Alternativas consideradas

### Redirecionar professor autenticado de `/` para `/teach`

Rejeitada por ora: exigiria consulta de sessão na landing (deixa de ser estática) e esconderia a página pública de quem tem sessão docente.

### Mover cada tela docente para um caminho próprio (`/teach/turmas/...`)

Rejeitada neste incremento: reescrita de navegação sem benefício funcional; `/teach` é um ponto de entrada e as sub-rotas continuam organizadas como hoje.

### Renomear pacote Java, banco e chaves de armazenamento

Rejeitada neste incremento: alto risco (perda de acesso lembrado do aluno, migração de volume) sem benefício funcional.
