# Ramuvi v0.12 — I3: Jornada persistente entre dispositivos

**Situação:** desenvolvimento concluído em branch, aguardando gates finais da PR; **não publicado**.
**Base:** `feat/v0.12-enrollment-claim-i2@9e7b796`, PRs #136 → #137 → #138.
**Branch:** `feat/v0.12-verified-journey-i3`.
**Versão da implementação original:** `0.12.0-dev.4`; empacotamento estável preparado para `0.12.0`, Flyway permanece em V68. Publicação e implantação dependem de gates próprios.
**Arquitetura:** [ADR-0071](../adr/ADR-0071-verified-student-journey.md).

## Objetivo e escopo entregue

O aluno cuja conta OIDC já está vinculada a um `Student` por convite I2 pode acessar Jornadas e retomar a conclusão dos conteúdos em outro navegador usando somente a sessão autenticada. O progresso acadêmico continua vinculado a `Enrollment`, inclusive para usos anteriores com `X-Study-Token`, sem duplicação.

- Nova prova de identidade tipada `StudyPrincipal.Proof.VERIFIED_ACCOUNT`; prova `DEVICE_CLAIM` legada preservada.
- `VerifiedStudentStudyService` resolve `issuer + sub` de um principal OIDC ativo para o `Student` canônico, a cada operação.
- Catálogo multi-turma, Jornada, material de conteúdo, início e conclusão reutilizam `JourneyStudyService` e `JourneyStageProgress` existentes.
- Verificações por operação: aluno/conta ativos, matrícula ativa na turma, turma ativa, Jornada publicada e liberação de estudo pelo professor. As condições do acesso verificado ficam centralizadas em `StudyAccessPolicy.canAccessVerifiedAccountStudy`. Regras de capítulos bloqueados continuam válidas.
- No frontend, o login autenticado utiliza automaticamente API de conta; uma negativa de Jornada não é contornada com claims de dispositivo. Após uma sessão OIDC previamente observada ser revogada ou expirar, a interface não muda silenciosamente para o dispositivo: exige novo login ou escolha explícita do modo legado. Sem conta anterior ou com I3 desligado, permanece o fluxo por código. A marca local não contém dados pessoais nem concede autorização.
- `/learn/login` agora oferece acesso às Jornadas; navegação de estudo oferece entrada em Minha conta.
- Todas as mudanças protegidas por **flag OFF** na configuração de backend e Compose; não modifica schema, XP, registro de matrícula ou login docente.

## Contratos HTTP do I3

| Método | Rota | Resposta |
| --- | --- | --- |
| GET | `/api/student-account/study/journeys` | Lista somente Jornadas publicadas liberadas nas matrículas ativas, ou lista vazia |
| GET | `/api/student-account/study/journeys/{id}` | Progresso, módulos, conteúdos e estados da Jornada |
| GET | `/api/student-account/study/journeys/{id}/contents/{contentId}` | Material autorizado, conforme bloqueio da Jornada |
| POST | `/api/student-account/study/journeys/{id}/contents/{contentId}/start` | Registra início na matrícula, CSRF obrigatório |
| POST | `/api/student-account/study/journeys/{id}/contents/{contentId}/complete` | Registra conclusão e avalia conquistas para o Student canônico, CSRF obrigatório |

Todos exigem `ROLE_STUDENT`, sessão OIDC válida e controle `Cache-Control: no-store`. O principal do backend é a única origem de identidade. Ausência de sessão: 401; role incompatível: 403; Jornada não autorizada, não publicada ou matrícula inativa: 404; conteúdo bloqueado: 409. A API **não recebe** studentId ou enrollmentId no request.

## Configuração da feature

```dotenv
APP_STUDENT_OIDC_ENABLED=false
APP_STUDENT_IDENTITY_ENROLLMENT_CLAIM_ENABLED=false
APP_STUDENT_IDENTITY_STUDY_SESSION_ENABLED=false
```

Para habilitar em homologação: configurar OIDC de I1 com credenciais reais e cookie Secure no domínio HTTPS, validar I2, e só depois habilitar as três flags. Habilitar o I3 sem I1/I2 não cria endpoint. Não ativar na VPS por esta PR.

## Testes e critérios de aceite

| Gate | Verificação | Resultado |
| --- | --- | --- |
| I3-A1 | Anônimo e docente bloqueados do endpoint autenticado | Testes HTTP direcionados |
| I3-A2 | Mesmo progresso lido/gravado por conta e fluxo legado | PostgreSQL/JourneyCoreIT |
| I3-A3 | Outra conta não acessa ou grava Jornada de terceiro | PostgreSQL/JourneyCoreIT |
| I3-A4 | Matrícula suspensa, turma sem liberação, turma desativada, Student inativo, Jornada arquivada ou identidade revogada bloqueiam leitura e escrita sem apagar progresso | PostgreSQL/JourneyCoreIT |
| I3-A5 | Catálogo e leitor funcionam no Chromium e WebKit móvel; conclusão recuperada em dois navegadores | E2E com API simulada |
| I3-A6 | Frontend lint/typecheck/test/build, contrato e Compose | Verificação local e CI |
| I3-A7 | CI oficial 3/3 no SHA final e revisão da ADR-0071 | Pendente até verificação da PR |
| I3-A8 | Google/Microsoft reais, Safari físico, backup/restauração e GO institucional | Pendente / fora do escopo de desenvolvimento |
| I3-A9 | Primeiros logins OIDC simultâneos não duplicam conta; duas conclusões concorrentes usam um único progresso por matrícula | Regressão PostgreSQL introduzida na remediação pré-merge |
| I3-A10 | Sessão revogada não recorre ao dispositivo; reentrada legada somente por decisão explícita; I1/I2/I3a em WebKit | Novos testes Chromium e WebKit; CI ampliada (aguardando novo SHA) |

**Validação local final (17/11):** `mvn verify` com PostgreSQL 17.11 aprovou **214 testes iniciais + 193 de integração (407/407)** após os patches de concorrência. Frontend com **425/425 testes**, lint sem erros (97 avisos preexistentes), formatação, TypeScript e build de produção aprovados. OIDC/Jornada I1–I3a: **24/24 E2E** em Chromium/WebKit; correção adicional do histórico docente: **16/16 execuções repetidas no build de produção**, oito Chromium + oito WebKit, sem retries. **CI do SHA 26b9441:** backend e frontend PASS; Compose construiu e iniciou a pilha, porém o E2E completo detectou uma instabilidade em `teacher-history.spec.ts` (1 flaky, 112 PASS). O patch de navegação subsequente ainda requer CI oficial no novo SHA; não confundir essa evidência local com gate GitHub aprovado.

O teste E2E com dois navegadores utiliza backend simulado para conferir a UX; a prova de persistência real no PostgreSQL é coberta separadamente pelo teste de integração Java. A homologação com IdP real ainda não foi feita.

## Remediações da auditoria pré-merge (17/11/2026)

- **R12-01:** marcado apenas o histórico de uma sessão OIDC válida (sem credenciais ou identidade pessoal no armazenamento local). A perda posterior dessa sessão e o logout voluntário passam a oferecer login renovado ou retorno explícito por código, sem fallback automático no catálogo de Jornadas.
- **R12-02:** a CI WebKit deixa de filtrar apenas casos da v0.11 e passa a incluir I1/I2/I3a, sem remover a cobertura da v0.11.
- **R12-03:** provisionamento inicial da identidade externa serializado por advisory lock transacional PostgreSQL, preservando a constraint por (issuer,subject) e evitando erro em callbacks concorrentes.
- **R12-04:** criação inicial de JourneyStageProgress com ON CONFLICT DO NOTHING e atualização sob lock pessimista, usando a matrícula canônica, sem nova migration.
- **Navegação docente:** após falha de `teacher-history.spec.ts` na CI, a restauração URL → React foi separada de navegação intencional para que `popstate` e reload não criem entradas extras. E2E reforçado com verificação da URL intermediária e da estabilidade do tamanho do histórico; 16/16 repetições no build de produção Chromium/WebKit.
- **R12-09 (ensaio isolado):** Flyway V68 aplicado em PostgreSQL 17 temporário; `pg_dump -Fc` com SHA-256; `scripts/release/verify-backup.sh` aprovou o restore em outro contêiner; segunda restauração comprovou 68 migrations e integridade de cadastro sintético `Student → Enrollment → StudentAccount → OIDC identity` e convite SHA-256 (32 bytes). Contêineres e dados sintéticos removidos; **não foi testado um backup real de produção**.
- **Riscos residuais:** rate limiting por réplica (exige definição de topologia), trilha de auditoria de negócio, retenção de respostas livres sob LGPD e ADRs 0068–0071 ainda propostas. Não confundir dados sintéticos nem automação de navegador com homologação institucional.
- **Não homologado por este patch:** OIDC real Google/Microsoft, restore de backup representativo da instalação, Safari/iOS físico, política LGPD/retenção e formalização das ADRs. Nenhuma flag foi ligada em produção; esta PR continua em draft e não autoriza merge ou deploy sem nova verificação.

## Sequência posterior (sem inclusão automática no I3)

1. **I3b:** auditoria da autorização de Atividades, quizzes, Conquistas, Arena e Perfil, evitando liberar dados por role genérica ou duplicar progressos.
2. **I3c:** revisão da jornada UX no Figma e piloto docente/aluno com fluxos de aparelhos compartilhados e conta sem matrícula.
3. **I4:** ADRs assinadas, credenciais OAuth reais, ensaio de backup/restore V68, segurança, acessibilidade física, observabilidade, GO/NO-GO, tag/release/deploy explícitos.

## Restrições de integração

- PR empilhada sobre a #138; não integrar na `main` antes da #136/#137/#138.
- Não confundir testes automatizados com identidade externa homologada.
- Se houver revisão de requisitos que amplie o escopo para tarefas/submissões e Quiz, criar gates e PR específicos, não acoplar silenciosamente ao leitor de conteúdos.
