# ADR-0069 — Autenticação discente via OIDC, sessão de navegador e isolamento de papéis

- **Status:** Proposto, sujeito a revisão de segurança e homologação real dos provedores.
- **Data:** 2026-11-17
- **Escopo:** I1 da v0.12, issue #119; complementa ADR-0028, ADR-0053 e ADR-0068.

## Problema

A associação de um aluno por código, nome, email ou claim de dispositivo não constitui autenticação verificada. Precisamos de login entre dispositivos sem criar indevidamente um segundo professor/tenant, sem assumir posse de email cadastrado na turma e sem remover a entrada rápida como convidado.

## Decisão implementada para revisão

1. **OIDC Authorization Code, PKCE S256, state e nonce**: usar suporte do Spring Security 7 / Boot 4. O Spring valida assinatura JWT, issuer, audience, nonce e troca do código. A Ramuvi **não valida tokens enviados em JSON pelo navegador**. Cliente confidencial com PKCE adicional.
2. **Provedores explícitos**: Google com emissor `https://accounts.google.com`; Microsoft somente tenant UUID (sem `common`/multi-tenant). URLs de login/token/JWK fixadas no backend; callback público com origem exata definida por implantação. Nenhuma credencial OAuth usada na integração Classroom/Teams é reutilizada.
3. **Desligado por padrão**: `APP_STUDENT_OIDC_ENABLED=false`; ativar somente com pelo menos um par client ID/secret configurado. Origem HTTPS exige cookie de sessão `Secure`; HTTP permitido só em loopback.
4. **Identidade imutável**: `(issuer,sub)` do ID Token já verificado identifica `StudentAccount`. Email e nome não associam automaticamente `Student` ou `Enrollment`. ODBC/OTP continuam separados de device claims. Associação com matrícula será resolvida no I2 por convite individual.
5. **Papéis isolados**: login OIDC obtém somente `ROLE_STUDENT`, nunca `ROLE_TEACHER` mesmo com claims externos. `/api/student-account/**` é protegido; endpoints administrativos existentes continuam `ROLE_TEACHER`.
6. **Sessão revogável**: cookie HTTP-only e SameSite=Lax, HTTPS Secure obrigatório em produção. `GET /api/student-account/session` consulta a identidade ativa no banco em cada solicitação; `POST /api/student-account/logout` invalida a sessão sob CSRF. Nenhum bearer token em localStorage ou no JavaScript.
7. **Navegação**: `/learn/login` apenas oferece provedores conhecidos, mostra estado de conta verificada/desvinculada e saída. Isso **não substitui** os pilotos Figma nem libera conteúdo de turmas.
8. **Fallback**: `/join` com código permanece funcionando para aulas. Alternativa email-OTP não está operacional: requer infraestrutura de envio, prevenção de abuso, expiração, recuperação e testes E2E próprios.

## Limites e riscos

- Validações automatizadas cobrem construção segura do redirect, PKCE/state/nonce, configuração, privacidade e isolamento HTTP. **Não comprovam login real no Google/Microsoft**, que exige configurar apps OAuth e percorrer o callback HTTPS com ID Token legítimo; esse é gate explícito de homologação.
- Sessão revogada pode continuar contendo ID Token até expirar; o backend revalida o vínculo em cada endpoint da nova área. As futuras rotas de Jornada devem usar o mesmo guard, sem delegar autorização apenas ao papel `STUDENT`.
- Sem migrações além da V68; alterações na autenticação continuam isoladas de login docente e das integrações delegadas.
- O fluxo de conexão no navegador deve ser validado em Safari/iOS, Android, desktop e aparelhos compartilhados antes do GO.

## Referências

- [Spring Security OAuth2 / OIDC](https://docs.spring.io/spring-security/reference/servlet/oauth2/)
- [Configuração avançada OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html)
