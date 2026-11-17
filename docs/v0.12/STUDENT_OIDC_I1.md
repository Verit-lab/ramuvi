# Ramuvi v0.12 — I1: Login verificado do aluno (OIDC)

**Status:** implementado em branch de revisão; habilitação operacional NÃO autorizada.

**Base:** `feat/v0.12-student-account-foundation@097f652` (PR #136)

**Branch:** `feat/v0.12-student-oidc-i1` (PR dependente da #136).

**Relacionado:** issue #119, ADR-0068 e ADR-0069.

## Entregue

- Spring Security OAuth2 Client para login do aluno com **Authorization Code + PKCE S256 + state + nonce**;
- registros configuráveis para Google e Microsoft com **emissores fixados**, Microsoft exigindo tenant UUID;
- sessão do Spring HttpOnly, SameSite=Lax, Secure para HTTPS; sem token bearer em localStorage;
- persistência de identidade `issuer + subject` verificada em `StudentAccount` sem associar matrícula por email;
- papel `ROLE_STUDENT` restrito, sem alterar conta docente única; consulta de status no DB para revogação;
- `GET /api/student-account/providers` público, `GET /api/student-account/session` e `POST /api/student-account/logout` protegidos;
- tela funcional `/learn/login` como superfície de acesso auxiliar, sem substituir o redesign Figma;
- defaults **OFF** para todos os ambientes e `/join` mantido.

## Configurar provedores (somente após review e em homologação)

Utilize variáveis de ambiente ou Secrets da infraestrutura, nunca no Git:

```dotenv
APP_STUDENT_OIDC_ENABLED=true
APP_STUDENT_OIDC_PUBLIC_BASE_URL=https://SEU_DOMINIO_PUBLICO
APP_SESSION_COOKIE_SECURE=true

# Habilite um ou ambos os provedores, cada qual com seu par:
APP_STUDENT_OIDC_GOOGLE_CLIENT_ID=...
APP_STUDENT_OIDC_GOOGLE_CLIENT_SECRET=...
APP_STUDENT_OIDC_MICROSOFT_CLIENT_ID=...
APP_STUDENT_OIDC_MICROSOFT_CLIENT_SECRET=...
APP_STUDENT_OIDC_MICROSOFT_TENANT_ID=uuid-do-tenant
```

Configurar no console dos provedores URIs **exatas**:
- `https://SEU_DOMINIO_PUBLICO/api/student-oidc/callback/google`
- `https://SEU_DOMINIO_PUBLICO/api/student-oidc/callback/microsoft`

**Não reutilize** credenciais das integrações Google Classroom/Microsoft Teams. `APP_STUDENT_OIDC_ENABLED=true` com nenhum provedor configurado provoca falha de inicialização, intencionalmente. Se o domínio for HTTPS, `APP_SESSION_COOKIE_SECURE=true` é obrigatório. O backend continua atrás do proxy Next.js.

## Interfaces

| Método e rota | Público? | Resultado |
| --- | --- | --- |
| `GET /api/student-account/providers` | Sim, com feature ligada | Provedores habilitados e caminhos de início fixos |
| `GET /api/student-oidc/authorization/{google,microsoft}` | Sim, com feature ligada | Redirect ao provedor, com state, nonce e PKCE |
| `GET /api/student-oidc/callback/{provider}` | Callback assinado/validado | Spring realiza troca de código, valida ID Token e abre sessão |
| `GET /api/student-account/session` | ROLE_STUDENT | Account ID, vínculo opcional a Student, sem PII |
| `POST /api/student-account/logout` | ROLE_STUDENT + CSRF | Invalida sessão |
| `/learn/login` | Público | Login, situação da conta e saída |

## Aceite e testes

- [ ] CI oficial da PR no último SHA (Maven, frontend e E2E Chromium/WebKit);
- [x] Regressão local: Maven verify 178 testes; frontend 425 testes, lint/typecheck/build; E2E focal 6/6 em Chromium e WebKit móvel;
- [x] Contrato v0.12 I1, Compose local/sala e versionamento dev.2 válidos;
- [ ] revisão de código e autorização, sobretudo `issuer+sub`, cookie Secure, PKCE e redirecionamento;
- [ ] configurar credenciais reais em homologação com HTTPS;
- [ ] completar Google e Microsoft pelo navegador com 2 contas distintas e 2 dispositivos;
- [ ] testar logout, revogação e retomada após expiração, Safari/iOS;
- [ ] manter professor, sessões por código e convidado funcionando;
- [ ] aceitar ou alterar ADR-0069 antes do merge.

**Não entregue:** email OTP (sem servidor de envio e contrato de recuperação), associação do aluno à turma, painel de turmas, aprovação docente e publicação da v0.12. Todos pertencem aos incrementos I2–I4.

**Não realizar merge nem deploy nesta etapa sem autorização explícita.**
