# ADR-0076 — Turma permanente, Sessão temporal, Jornada estruturada e Biblioteca compartilhável

- **Status:** Aceito como direção arquitetural da v0.12.2; **implementação e publicação ainda dependem dos gates e PRs específicos**.
- **Data:** 20/11/2026.
- **Responsável pela decisão:** direção de produto da Ramuvi.
- **Escopo:** produto, domínio acadêmico, identidade/autorização, dados, UX e evolução da v0.12.
- **Origem:** avaliação crítica dos fluxos de Turma/Sessão/Jornada/Biblioteca e `Ramuvi — Plano técnico de implementação v0.12.2`, de 20/11/2026.
- **Baseline de código para análise:** candidata v0.12.1 na PR [#154](https://github.com/Verit-lab/ramuvi/pull/154), a reconciliar com `main` e o SHA implantado na VPS antes de implementar.
- **Abrangência da aprovação:** aprova **o modelo e seus invariantes**, não a criação imediata de tabelas, alterações na VPS, merge, tag ou liberação ao público.

## 1. Contexto e problema

A Ramuvi já dispõe de `Classroom`, `ClassSession`, `Journey`, `LearningResource`, matrículas, códigos de acesso, progresso, eventos e pontuação. O problema observado é sobretudo de **orquestração de uso**: o professor mantém por muitos encontros uma única Sessão que foi desenhada para uma aula individual, enquanto os alunos enfrentam ingressos repetidos ou bloqueios de acesso quando o Google OIDC não está operacional.

O código vigente apresenta invariantes que não podem ser quebrados por uma troca de nomenclatura:

- `ClassSession` pertence a `Classroom`, possui início/fim e status `ACTIVE/FINISHED`; hoje só é permitida uma sessão ativa por turma.
- `SessionJoinCode` é um código temporário, com validade de aproximadamente 12 horas e revogação ao encerrar a sessão. Não é identificador permanente de turma.
- `SessionParticipant`, eventos da Arena, resultados, presença e parte da pontuação são específicos de sessão; unir várias aulas num único histórico destrói a granularidade pedagógica.
- `Journey` é um percurso sequencial, ligado atualmente à turma, com capítulos, etapas, regras de liberação, atividades e progresso.
- `LearningResource` é recurso editorial reutilizável, possivelmente sem turma, com formatos `MARKDOWN`, `PLAIN_TEXT` ou `LINK` e número de revisão; a revisão atual não garante, sozinha, recuperação de todas as versões anteriores.
- O fluxo legado que emitia credencial de estudo a partir de código compartilhado e nome/e-mail/matrícula foi removido por risco de personificação (ADR-0075). Reintroduzi-lo para facilitar compartilhamento é expressamente vedado.
- A conta verificada e a matrícula têm contratos próprios. Hoje existem progressos por matrícula e progressos ligados ao resgate anterior de Jornada, sem equivalência automática entre eles.

O objetivo é **reduzir ações e códigos desnecessários na experiência**, preservando as fronteiras, os dados e a segurança, em vez de trocar um problema de UX por uma reescrita de domínio.

## 2. Decisão arquitetural

### D1 — Preservar quatro responsabilidades, não fundir entidades

| Conceito | Responsabilidade normativa | Ciclo de vida |
| --- | --- | --- |
| **Turma — Classroom** | Ambiente acadêmico contínuo: alunos, matrículas, Jornadas, atividades, políticas, histórico e acompanhamento | Dura enquanto a organização pedagógica o exigir |
| **Sessão/Aula — ClassSession** | Encontro delimitado: presença, participação, dinâmicas, quiz, sorteios, eventos, resultados, projetor e código/QR de participação | Iniciar, retomar e encerrar de forma explícita |
| **Jornada — Journey** | Percurso de aprendizagem: conteúdos ordenados, capítulos, desbloqueios, avaliações e avanço | Publicada/arquivada, sem depender automaticamente de uma sessão ativa |
| **Biblioteca — LearningResource** | Acervo de materiais editoriais reutilizáveis, versionáveis e compartilháveis, inclusive fora de turma | Revisões, publicação, concessões e arquivamento próprios |

**Minha Biblioteca** é uma **visão/coleção pessoal do aluno sobre recursos autorizados**, não uma quinta cópia do conteúdo editorial e não uma nova matrícula. **Arena** continua sendo o contexto de condução da sessão e **Atividades** mantêm seus próprios contratos.

Não transformar `ClassSession` em `Classroom`; não transformar `Journey` em simples arquivo; não criar `Disciplina`/`Curso` nesta release.

### D2 — Turma é o ponto de entrada durável de Teach

Na interface docente, `/teach` → Turmas → turma selecionada torna-se o fluxo principal. A central da turma reúne **Visão geral, Jornadas, Aulas, Atividades e Alunos**, com contexto de sessão ativa e histórico. A Biblioteca continua acessível globalmente.

A ação primária contextual é uma de: **Iniciar aula**, **Retomar aula** ou **Encerrar aula**. Iniciar sugere data/título, sem recriar turma, aluno, matrícula ou material. A navegação para Arena/Projetor reutiliza a sessão criada; não cria uma nova Arena por encontro.

O fluxo deve permitir iniciar uma aula em **até duas ações principais** a partir da turma em estado normal. Esse é critério de UX, não permissão para pular confirmação em ações destrutivas.

### D3 — Sessões são temporais, auditáveis e fáceis de recuperar

- Cada encontro deve ter `ClassSession.id` próprio. Estado da Arena, quiz, presença, eventos, pontuação contextual e resultados permanecem associados à sessão correta.
- Garantir **no máximo uma sessão ativa por turma**, inclusive sob concorrência de requisições. Preferir operações idempotentes por chave ou conflito semântico que devolva a sessão existente. Validar a constraint no PostgreSQL antes de adicionar migration.
- Se houver sessão ativa ao voltar à turma, oferecer **Retomar aula** ou **Encerrar e iniciar outra**. A segunda opção exige confirmação e duas transições auditáveis; nunca sobrescrever ou excluir o encontro anterior.
- Sessão aparentemente esquecida gera **alerta/sugestão**, nunca encerramento silencioso por idade, falta de conexão, perda de rede ou temporizador.
- Encerrar fecha os mecanismos ao vivo, tokens de ingresso, timer, enquetes e publicações temporárias. **Não encerra a turma, não arquiva Jornadas, não apaga progresso, presença, XP nem recursos pessoais.**
- O histórico encerrado é consultável sem reativar runtime; correções de presença e resultados requerem contratos próprios, autorização e auditoria.
- Estudantes matriculados após o início de uma aula precisam de caminho idempotente e autorizado para compor `SessionParticipant`. Matrícula tardia **não é presença automática**; registrar presença ao efetivo ingresso ou ação docente autorizada.

### D4 — Aluno matriculado entra pela conta e vínculo, não por código a cada aula

- Com OIDC válido, `StudentAccount`, `Student` e `Enrollment` confirmados, o aluno vê **Minhas turmas**, **Minhas Jornadas**, **Ao vivo** e **Minha Biblioteca** em `/learn`.
- A aula ativa é descoberta **no backend**, a partir da identidade verificada e de matrículas ativas. A entrada verifica conta, estudante, matrícula, turma, sessão e estado em cada requisição.
- Membro autenticado ingressa na sessão ativa **sem digitar novamente o código**; continua sendo `SessionParticipant` da sessão específica. Seus dados acadêmicos não são criados novamente.
- O vínculo inicial com a turma continua usando link de ingresso de alta entropia/aprovação ou convite individual (ADRs 0070/0074). `classrooms.code` é administrativo e **nunca prova matrícula ou identidade**.
- Novos dispositivos recuperam a identidade e matrícula verificadas, sem associação automática por nome ou e-mail isolado. Matrículas bloqueadas, turmas inativas e contas revogadas falham de forma explícita.
- O login Google do aluno é um **gate operacional independente** do OAuth do Google Drive para backups. Enquanto OIDC estiver OFF, não anunciar progresso sincronizado nem oferecer botão de provedor inoperante.

### D5 — Código/QR de sessão continua existindo, mas com escopo transitório

Preservar `/join` e o código temporário da sessão para participantes ocasionais, demonstrações e entrada rápida. Esse código **não se torna código permanente da turma** nem credencial de conta, matrícula, Jornada, perfil, Biblioteca ou XP histórico.

Acesso visitante, se entregue, deve ser explicitamente **não verificado** e não pode atrelar nome digitado a cadastro acadêmico alheio. Caso a segregação segura de participantes temporários exija mudanças adicionais, manter o ingresso existente restrito e **não anunciar presença acadêmica autenticada para visitantes** antes de implementá-la. Não criar uma identidade persistente falsa para simplificar o fluxo.

### D6 — Jornada é percurso da turma e não exige uma aula ao vivo aberta

Na v0.12.2, a Jornada continua **prioritariamente vinculada à Turma**, com autorização por matrícula, publicação e liberação de capítulos. Encerrar uma sessão não revoga automaticamente a Jornada; condições de acesso acadêmico continuam regidas por turma/matrícula/publicação.

Materiais apresentados apenas no palco da aula seguem o ciclo da sessão, distinguindo-se de material atribuído como Jornada.

Não implementar neste ciclo Jornada totalmente independente com progresso formal próprio. Não converter Jornada para Markdown da Biblioteca alegando preservação de estrutura, quizzes, pré-requisitos, acompanhamento ou progresso. Uma Jornada pode **consumir** recursos e snapshots da Biblioteca.

Preservar significados dos códigos legados `J-...` e dados relacionados. **Não ressuscitar** `POST /api/learn/journeys/redeem/bootstrap` (resposta `410 Gone`) nem usar código + nome/matrícula como prova de identidade.

### D7 — Biblioteca do professor distribui material independentemente de turma

- `LearningResource` permanece entidade editorial com autoria, metadados, revisão, proveniência, uso em Jornadas e apresentação ao vivo.
- Oferecer ação **Compartilhar** distinta de **Usar na Jornada** e **Apresentar na aula**.
- Compartilhamentos autorizados terão modalidades **Somente visualizar** e **Permitir salvar na Minha Biblioteca**, com possibilidade de prazo, revogação e visibilidade controlada. Não confundir `library_visible` com publicação pública irrestrita.
- Links/QR devem usar token aleatório de **pelo menos 128 bits de entropia**, persistido somente como hash. A emissão mostra o token uma vez; o token jamais representa matrícula, identidade ou acesso a resultados.
- Preferir fragmento de URL e envio do segredo no corpo de requisição; remover da barra de endereço, impedir logs/Referer e exigir rate limit, sanitização, resposta `no-store` e tratamento de expiração.
- Visualização anônima é permitida **apenas para recursos expressamente autorizados como visualizáveis por link**, sem gravação de progresso acadêmico ou inferência de identidade. Para recurso sensível, exigir conta.
- Revogar o link interrompe **novas visualizações por ele e novos resgates**. Não remove automaticamente concessões pessoais previamente válidas, salvo excepcionalidade jurídica ou de segurança auditada.

Não utilizar código curto como segredo suficiente para dar acesso a dados privados.

### D8 — Minha Biblioteca pertence à conta autenticada, mesmo sem matrícula

- O aluno pode **salvar** material na sua coleção após autenticação verificada, mesmo que ainda não exista `Student`/`Enrollment` associado à conta.
- O vínculo é `StudentAccount` → recurso/versão concedida; **não** criar matrícula artificial, `Student` fictício, propriedade editorial duplicada ou concessão por nome.
- Salvar repetidamente é **idempotente**: uma entrada por conta e recurso, com versão de referência estável.
- A coleção pessoal reúne materiais de diferentes turmas/professores e permanece acessível após término da aula ou arquivamento da turma, respeitadas políticas de direitos, privacidade e revogação excepcional.
- A conta pode visualizar, organizar e remover seus itens; a remoção da coleção não exclui o recurso editorial do professor.
- Visitante sem conta pode **visualizar** quando permitido, mas não tem coleção sincronizada nem histórico acadêmico persistente garantido. Favorito local, se existir, é somente cache/preferência não canônica.
- Compartilhar ou salvar conteúdo não concede ao professor, por si só, identidade dos leitores, telemetria pessoal, nota ou progresso individual.

### D9 — Versionamento entregue e integridade referencial são obrigatórios

A revisão atual de `LearningResource` não basta para recuperar a versão específica entregue. Para compartilhamento persistente, criar snapshot **imutável** da representação autorizada, com revisão, formato, conteúdo canônico ou referência externa, origem e checksum quando apropriado.

Um link aponta para uma revisão definida; um item pessoal aponta para a revisão concedida. Edição editorial posterior não altera silenciosamente o item salvo; atualizações de materiais concedidos exigem política/ação explícita.

Links externos representam **referência**, não garantia de cópia local ou de conteúdo remoto estável.

Não permitir que `ClassroomService.hardDelete()` elimine itens da coleção pessoal ou cause FK inconsistente. Antes de implementar, projetar migração de propriedade/retenção e tratamento de dependências para recurso com origem na turma. Exceções por LGPD, direitos autorais e segurança devem ter auditoria; não assumir direito perpétuo de distribuição de conteúdo de terceiros.

### D10 — Evidências acadêmicas permanecem separadas

- Presença e eventos: **por Sessão**, não um contador da turma inteira.
- Matrícula: vínculo acadêmico do aluno com a Turma.
- Progresso formal da Jornada: preservar os stores existentes (`JourneyStageProgress` por matrícula e, onde compatível, `StudentJourneyStageProgress` por concessão legada), sem fusão ou transferência automática.
- Avaliações, entregas e XP: manter identificadores, autorizações e regras vigentes; não converter automaticamente leitura de Biblioteca em nota, presença, conclusão de Jornada ou XP.
- Relatórios devem exibir denominadores distintos de presença, respostas, progresso e resultados. **XP não é evidência suficiente de aprendizagem.**
- Vínculo de conta com histórico anterior depende de prova legítima de titularidade e auditoria, nunca de correlação automática por nome, e-mail digitado ou código compartilhado.
- Conta OIDC não herda silenciosamente permissões legadas de `X-Study-Token` (ADR-0071).

### D11 — Offline, privacidade e segurança mantêm contratos específicos

Preservar as ADRs 0039, 0069–0075 onde aplicáveis. Jornadas offline seguem permissões, pacote, credencial, sincronização e revogação próprios (ADR-0073). **Biblioteca pessoal offline não entra automaticamente na v0.12.2**.

Toda API de leitura/escrita precisa validar ator e contexto no servidor; proibir IDOR entre professor/aluno/turma; exigir CSRF nas mutações de sessão autenticada, token de finalidade única, revogação, tentativas limitadas e monitoramento. Recusar por padrão.

Proteger recursos contra XSS/Markdown inseguro e enumeração; limitar metadados públicos; não expor PII a quem apenas compartilhou um link. Usar `Referrer-Policy` restritiva e evitar indexação de material privado. Restrição de cópia da interface não constitui DRM.

## 3. Matriz normativa de acesso e disponibilidade

| Operação | Autorização | Duração | Registro permitido |
| --- | --- | --- | --- |
| Abrir central da turma | Professor autorizado | Enquanto possuir permissão | Configuração e auditoria docente |
| Entrar na turma como aluno | Conta verificada + matrícula, ou ingresso inicial aprovado | Enquanto vínculo/política válidos | Matrícula e resultados formais |
| Aula ao vivo de membro | Conta/matrícula + sessão ativa | Até fim da aula | `SessionParticipant`, eventos e atividades da sessão |
| Aula eventual por código | Token transitório próprio da sessão | Sessão ativa, prazo e política | Participação limitada; sem atribuição acadêmica automática |
| Jornada da turma | Identidade elegível + matrícula + Jornada publicada/liberação | Independente da sessão ativa, se política autorizar | Progresso formal vinculado ao contexto |
| Material somente projetado | Autorização/política da sessão | Enquanto publicação da sessão estiver ativa | Interações da aula |
| Visualizar recurso compartilhado | Token `LIBRARY_SHARE` válido com `VIEW` | Até expiração/revogação | Mínimas métricas agregadas, sem perfil acadêmico |
| Salvar recurso pessoal | Conta OIDC válida + concessão `SAVE` | Persistente conforme política | Item da conta e snapshot, não presença/XP |
| Consultar Minha Biblioteca | A própria conta autenticada | Enquanto item/concessão válidos | Metadados e material do titular |

**Regra de encerramento:** `FINISHED` revoga acesso ao contexto ao vivo, não reinterpreta matrículas, histórico, Jornada publicada nem Biblioteca pessoal. **Regra de arquivamento:** políticas formais podem bloquear novos acessos a turma/Jornadas, sem apagar registros auditáveis e concessões pessoais legítimas.

## 4. Orientações para contrato e persistência (desenho, não schema final)

### 4.1 I1 — Read model e ciclo da sessão

Reutilizar `ClassroomService`, `SessionService`, `SessionJoinService`, `ClassSession`, `SessionParticipant`, `SessionEvent`, Arena/Projetor e controladores existentes. Criar **read model da central** com sessão ativa e histórico resumido; não criar novo agregado de “aula” apenas para refletir a interface.

Testar concorrência com PostgreSQL. Se necessário, adicionar **índice parcial de unicidade** para sessão ativa por turma após auditoria/saneamento de dados. Detectar conflito e devolver resposta semântica com referência à sessão já existente. Ingresso tardio idempotente, com presença apenas por ação real.

### 4.2 I2 — Identidade e descoberta de aula

Reutilizar `StudentAccount`, `Student`, `Enrollment`, `ClassroomJoinLinkService`, OIDC, `StudyAccessPolicy` e mecanismos de sessão. O backend constrói a lista de aulas disponíveis a partir do titular verificado, sem entregar segredos de outra turma ou lista de estudantes.

Autorizar `join` autenticado por matrícula e sessão; não aceitar IDs de aluno/conta do cliente como prova. Manter API de visitante isolada e limitada. Não transformar o código administrativo da turma em token de ingresso.

### 4.3 I3 — Modelagem mínima sugerida

Os nomes seguintes são **propostos**, sujeitos à inspeção do Flyway em `main` e na VPS antes da migration:

| Tabela proposta | Campos/constraints essenciais | Finalidade |
| --- | --- | --- |
| `learning_resource_snapshots` | `id`, `resource_id`, `revision`, formato/payload ou referência, checksum, `created_at`; `UNIQUE(resource_id, revision)` | Versão editorial imutável |
| `resource_share_links` | `id`, `snapshot_id`, `token_hash UNIQUE`, `expires_at`, `revoked_at`, `permission` (`VIEW`/`SAVE`), autor, contadores mínimos | Concessão temporária de acesso/novo resgate |
| `student_library_items` | `id`, `student_account_id`, `resource_id`, `snapshot_id`, `saved_at`, origem e status; `UNIQUE(student_account_id, resource_id)` | Coleção persistente sem matrícula |

Verificar primeiro o ciclo de vida real de `StudentAccount` (inclusive conta sem `student_id`), anonimização, FK, snapshots e deleção de `Classroom`. Garantir que não haja cascata que remova cópias legítimas. Material de link externo deve registrar limitação de estabilidade.

### 4.4 APIs conceituais, a harmonizar com contratos existentes

| Operação proposta | Segurança e efeito |
| --- | --- |
| Central da turma com sessão ativa/histórico | Professor; consulta sem expor tokens de sessão de outros atores |
| Iniciar/retomar/encerrar sessão | Professor; CSRF, idempotência/conflito, auditabilidade |
| Descoberta e ingresso da aula ativa pelo membro | Aluno OIDC; matrícula/autorização por requisição |
| `POST /api/library/resources/{id}/shares` | Professor; gera token seguro exibido somente na emissão |
| `GET /api/library/resources/{id}/shares` | Professor; lista estado, **sem retornar o segredo** |
| `POST /api/library/shares/preview` | Token no corpo, limitação de tentativas, prévia anônima autorizada |
| `POST /api/student-account/library/items` | Conta OIDC + CSRF; resgate `SAVE` idempotente |
| `GET /api/student-account/library/items` | Titular OIDC; somente a própria coleção |
| `DELETE /api/student-account/library/items/{id}` | Titular OIDC + CSRF; retira da coleção, não exclui recurso |

Rotas, nomes de tabelas, DTOs, flags e status HTTP finais serão congelados no **I0/P0** após confronto com implementação e testes de caracterização. O presente ADR fixa a **semântica**, não aprova códigos exemplificativos como APIs já existentes.

## 5. Implementação e enquadramento na release v0.12.2

A v0.12.2 é o limite de release proposto para entregar a reorganização **sem constituir novo ciclo v0.13**. Dividir em incrementos e PRs verificáveis, não em reescrita única:

| Incremento | Responsabilidade | Dependência/gate |
| --- | --- | --- |
| **I0 — Contratos e baseline** | Esta ADR, matriz de autorização, estados e baseline de uso/desempenho; reconciliar a candidata `#154` com `main`/VPS | Sem mudança de schema. Não desenvolver contra SHA diferente do implantado sem registrar a divergência |
| **I1 — Turma como central** | UX da turma; iniciar/retomar/encerrar; sessão antiga, concorrência e matrícula tardia | Testes backend e E2E de professor; preservar dados, sem encerramento automático |
| **I2 — Ingresso contínuo** | Descoberta de aulas para aluno autenticado; entrar sem código repetido; caminhos distintos para visitante | **OIDC Google homologado** e vínculo canônico; testes cross-user, cross-device e de revogação |
| **I3a — Compartilhar Biblioteca** | Tokens, snapshots, preview VIEW/SAVE, auditoria e expiração | Migration aditiva, segurança e autorização de compartilhamento |
| **I3b — Minha Biblioteca** | Item por `StudentAccount`, salvamento idempotente e leitura cross-device | OIDC, FK e política de ciclo de vida da conta; testes sem matrícula |
| **I4 — Integração e rollout** | Jornadas + Arena + Biblioteca + evidências acadêmicas; navegação, acessibilidade, segurança, release | Regressão, staging PostgreSQL real, desempenho observado e aceite operacional |

I1 e preparação editorial de I3 podem avançar em paralelo apenas depois de congelar os contratos no I0. Cada fatia exige PR revisável e testes específicos.

### Restrições de release

1. **Antes da v0.12.2:** reconciliar a PR #154, `origin/main`, imagem/SHA instalado e migrações V69–V72; resolver PRs sobrepostas sem merge cego. A candidata já publicada tecnicamente não equivale a release integrada e homologada.
2. **Google OIDC:** autenticação de aluno é pré-condição para continuidade pessoal; OAuth do Drive de backup é um serviço **distinto**. Não restaurar bootstrap legado como workaround.
3. **Flyway:** migrations somente aditivas depois da última versão realmente aplicada; testar base representativa isolada e leitura/rollback de binário antigo, evitando promessa de downgrade automático.
4. **Flags independentes:** central da turma, membro entrando em sessão, compartilhamento da Biblioteca e Minha Biblioteca (nomes finais no I0); **OFF** em produção até homologação. Flag não substitui autorização de backend.
5. **Compatibilidade:** preservar `/join`, códigos `J-...`, matrículas, sessões, IDs, XP, progresso e estado legado; deprecar apenas com telemetria, nunca reinterpretar token/código existente silenciosamente.
6. **Operação:** CI no SHA final, E2E Chrome/WebKit e aparelhos reais, segurança/IDOR/CSRF, acessibilidade móvel, observabilidade e backup/restauração isolada. Publicar por coortes: professor piloto → alunos piloto → Biblioteca → geral.
7. **RPO/RTO/performance:** medir baseline e fixar limites antes de afirmar sucesso; não inventar benchmarks. Monitorar taxa de falha de ingresso, 401/403/409, 5xx, latência e disponibilidade.
8. **Aceite:** nenhuma regressão crítica P0/P1; tag/release apenas com evidências e decisão de GO formal. Merge, implantação e homologação são gates distintos.

### Escopo obrigatório para declarar v0.12.2 concluída

- Central da Turma funcional, sessão de cada encontro e reentrada contextual.
- Aluno autenticado entra sem código repetido e continua seus registros sem duplicações.
- Jornada da turma preserva seus controles e progresso formal.
- Professor compartilha `MARKDOWN`, `PLAIN_TEXT` e `LINK` sem exigir matrícula.
- Link com VIEW funciona somente na política autorizada; token não dá acesso a dados acadêmicos.
- Aluno com conta, **sem matrícula**, salva e recupera Minha Biblioteca em outro dispositivo.
- Snapshot imutável, revogação de novos resgates e preservação dos itens adquiridos dentro da política.
- Segurança, dados preexistentes, migrações, rollback e testes funcionais/operacionais aprovados.

**Gate de corte:** se I3b ou snapshots não estiverem homologados, é possível publicar fatias menores sob flags, porém **não anunciar a v0.12.2 como integralmente concluída**. Não substituir a entrega persistente por preview anônimo e chamá-la “Minha Biblioteca pronta”.

### Fora do escopo explícito

Entidade nova Curso/Disciplina; marketplace; autoria colaborativa; mesclagem automática de cadastros; calendário genérico de disponibilização; Jornada individual completa; transformação perfeita de Jornada em material; DRM; Biblioteca offline automática; nova gamificação; BI amplo; refatoração integral de serviços; alteração do produto para exigir matrícula antes de visualizar um recurso liberado por link.

## 6. Alternativas avaliadas

| Alternativa | Decisão | Motivo |
| --- | --- | --- |
| Converter Sessão em Turma e usar seu código como ingresso permanente | **Rejeitada** | Quebra temporalidade, presença por aula, tokens expirados e runtime da Arena |
| Manter uma sessão ativa durante todo o curso | **Rejeitada** | Mistura encontros, impede novas sessões, torna auditoria e presença pouco confiáveis |
| Fundir Jornada com Biblioteca ou converter tudo em Markdown | **Rejeitada** | Perde estrutura, pré-requisitos, avaliação e progresso |
| Criar nova entidade Curso/Disciplina e reescrever o domínio | **Adiada** | Complexidade desnecessária sem evidência de insuficiência do modelo atual |
| Permitir bootstrap com código + nome/matrícula para facilitar resgate | **Rejeitada** | Reabre personificação documentada na ADR-0075 |
| Guardar Biblioteca pessoal dentro da matrícula | **Rejeitada** | Impede coleção independente e pode perdê-la ao apagar turma |
| Copiar material editorial integral em toda concessão | **Rejeitada como padrão** | Duplicação; snapshots de revisão bastam se preservados sob política de retenção |
| Compartilhamento independente + central de Turma, preservando entidades | **Aceita** | Menor mudança compatível com experiência, segurança e dados existentes |

## 7. Consequências, riscos e pontos deliberadamente abertos

**Benefícios esperados (hipóteses a validar):** professor conduz encontros sem sessão indefinida; alunos vinculados não repetem códigos; materiais circulam sem matrícula artificial; histórico por aula permanece auditável; recursos versionados são reutilizados.

**Custos reais:** novos contratos de autorização, autenticação OIDC operacional, migrações de snapshots e coleção, compatibilidade de dados, alterações de UX e segurança, testes de aparelhos reais e disciplina de release. Não presumir redução de complexidade interna apenas pela simplificação da tela.

**Riscos:** conflitos de início de sessão, aluno matriculado tardiamente, duplicidade de identidade, tokens vazados, histórico acadêmico indevidamente associado, snapshots órfãos após exclusão de turma, direitos/anonimização, divergência de progresso legado e rollback Flyway.

**Decisões de detalhe ainda abertas para I0 (não inventar resolução):**

1. Modelo exato de participação visitante sem matrícula e limite de telemetria permitido; se não couber, não liberar participante não verificado como acadêmico.
2. TTL máximo/política de emissão e revogação da Biblioteca (somente a entropia mínima e separação de permissões ficam fixadas aqui).
3. Política de atualização de item salvo, retenção de snapshots, exclusão/anonimização e direitos autorais de links externos.
4. Requisitos quantitativos de performance e limites de regressão após medir baseline.
5. Detalhes dos endpoints, flags, constraints, migrações e desenho de telas aprovadas no Figma.

Nenhum desses pontos permite contornar os invariantes de autenticação, matrícula, autorização e histórico.

## 8. Critérios verificáveis de aceite

- [ ] Professor inicia aula a partir da turma em fluxo curto, sem recriar alunos nem código.
- [ ] Sessão antiga oferece retomada ou fechamento explícito; nenhuma sessão fecha por rede ausente.
- [ ] Concorrência nunca deixa duas sessões ativas para a mesma turma.
- [ ] Aluno matriculado posteriormente ingressa com registro apropriado e sem presença fictícia.
- [ ] Aluno vinculado entra em duas sessões diferentes da mesma turma sem código novo.
- [ ] Conta de aluno desvinculada, turma inativa e membro de outra turma são negados.
- [ ] Ingresso por código temporário não cria identidade acadêmica persistente por nome.
- [ ] Sessão encerrada conserva presença, dinâmicas, XP e histórico; mantém Jornada da turma segundo política.
- [ ] Professor cria compartilhamento da Biblioteca fora de turma, com VIEW/SAVE, expiração e revogação.
- [ ] Visitante autorizado visualiza recurso sem conseguir acessar perfil, matrícula, XP ou notas.
- [ ] Conta autenticada sem turma salva recurso, reabre noutro dispositivo e não duplica item.
- [ ] Alteração do recurso editorial não modifica silenciosamente versão entregue.
- [ ] Encerramento/exclusão da turma não remove item pessoal válido; LGPD e exceções tratadas.
- [ ] OIDC não herda `X-Study-Token`; bootstrap legado continua `410`.
- [ ] Jornada mantém publicação, liberação de capítulos, progresso formal e offline existente.
- [ ] Sem IDOR/CSRF/XSS/regressão de permissão; Chrome/WebKit/móvel/acessibilidade homologados.
- [ ] Migração PostgreSQL real, rollback compatível e backup/restauração testados antes do deploy.
- [ ] CI verde no SHA final, métricas observadas, flags e aceite de produção registrados.

## 9. Relações e fontes

**ADRs preservadas e complementadas:** [ADR-0020](ADR-0020-mecanicas-e-atividades-autoritativas-no-backend.md) (Arena autoritativa no backend), [ADR-0039](ADR-0039-pwa-cache-de-shell-nao-e-verdade-do-dominio.md) (PWA não é fonte do domínio), ADR-0068 (identidade persistente), [ADR-0069](ADR-0069-student-oidc-authentication.md), [ADR-0070](ADR-0070-student-enrollment-claim-authorization.md), [ADR-0071](ADR-0071-verified-student-journey.md), ADR-0073 (offline da Jornada), ADR-0074 (ingresso da turma), ADR-0075 (bloqueio de personificação). As ADRs 0073–0075 estão na candidata PR #154 e precisam ser integradas/reconciliadas; os links relativos tornam-se válidos após a base convergir.

**Não substitui:** contratos de segurança das ADRs 0071, 0074 e 0075. Se a implementação exigir relaxar uma dessas garantias, abrir revisão explícita de decisão antes de codificar.

**Fontes:**
- [PR #154 — release candidata v0.12.1](https://github.com/Verit-lab/ramuvi/pull/154).
- [Arquitetura vigente — Turma](../../backend/src/main/java/br/com/arenadev/classroom/Classroom.java), [Sessão](../../backend/src/main/java/br/com/arenadev/session/SessionService.java), [Jornada](../../backend/src/main/java/br/com/arenadev/journey/Journey.java), [Biblioteca](../../backend/src/main/java/br/com/arenadev/content/LearningResource.java).
- Plano técnico Ramuvi v0.12.2 (20/11/2026), documento de escopo e execução; a ADR é o **registro normativo** e o plano é o **roteiro de implementação**, não documentos concorrentes.
- [Notion — Ramuvi, plataforma de aprendizagem](https://app.notion.com/p/3bff3eb198c3818fa281fd0abb23b6ce).
