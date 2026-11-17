# Ramuvi v0.12 — I3: Jornada persistente entre dispositivos

**Situação:** desenvolvimento concluído em branch, aguardando gates finais da PR; **não publicado**.
**Base:** `feat/v0.12-enrollment-claim-i2@9e7b796`, PRs #136 → #137 → #138.
**Branch:** `feat/v0.12-verified-journey-i3`.
**Versão:** `0.12.0-dev.4`, Flyway permanece em V68.
**Arquitetura:** [ADR-0071](../adr/ADR-0071-verified-student-journey.md).

## Objetivo e escopo entregue

O aluno cuja conta OIDC já está vinculada a um `Student` por convite I2 pode acessar Jornadas e retomar a conclusão dos conteúdos em outro navegador usando somente a sessão autenticada. O progresso acadêmico continua vinculado a `Enrollment`, inclusive para usos anteriores com `X-Study-Token`, sem duplicação.

- Nova prova de identidade tipada `StudyPrincipal.Proof.VERIFIED_ACCOUNT`; prova `DEVICE_CLAIM` legada preservada.
- `VerifiedStudentStudyService` resolve `issuer + sub` de um principal OIDC ativo para o `Student` canônico, a cada operação.
- Catálogo multi-turma, Jornada, material de conteúdo, início e conclusão reutilizam `JourneyStudyService` e `JourneyStageProgress` existentes.
- Verificações por operação: aluno/conta ativos, matrícula ativa na turma, turma ativa, Jornada publicada e liberação de estudo pelo professor. As condições do acesso verificado ficam centralizadas em `StudyAccessPolicy.canAccessVerifiedAccountStudy`. Regras de capítulos bloqueados continuam válidas.
- No frontend, o login autenticado utiliza automaticamente API de conta; uma negativa de Jornada não é contornada com claims salvos em localStorage. Sem conta ou com I3 desligado, permanece o fluxo antigo.
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

**Validação local em 17/11:** o primeiro `mvn verify` da branch aprovou 214 testes iniciais + 190 de integração; 425 testes de frontend, lint, format, typecheck e build aprovados; oito E2E específicos Chromium/WebKit aprovados; novos testes de revogação passaram em execução dirigida 30/30. A regressão completa do SHA final e CI oficial ainda precisam ser confirmadas.

O teste E2E com dois navegadores utiliza backend simulado para conferir a UX; a prova de persistência real no PostgreSQL é coberta separadamente pelo teste de integração Java. A homologação com IdP real ainda não foi feita.

## Sequência posterior (sem inclusão automática no I3)

1. **I3b:** auditoria da autorização de Atividades, quizzes, Conquistas, Arena e Perfil, evitando liberar dados por role genérica ou duplicar progressos.
2. **I3c:** revisão da jornada UX no Figma e piloto docente/aluno com fluxos de aparelhos compartilhados e conta sem matrícula.
3. **I4:** ADRs assinadas, credenciais OAuth reais, ensaio de backup/restore V68, segurança, acessibilidade física, observabilidade, GO/NO-GO, tag/release/deploy explícitos.

## Restrições de integração

- PR empilhada sobre a #138; não integrar na `main` antes da #136/#137/#138.
- Não confundir testes automatizados com identidade externa homologada.
- Se houver revisão de requisitos que amplie o escopo para tarefas/submissões e Quiz, criar gates e PR específicos, não acoplar silenciosamente ao leitor de conteúdos.
