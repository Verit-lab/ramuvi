# Ramuvi v0.12.0 — publicação na VPS e contrato HTTPS

**Status:** procedimento de publicação. Nada neste arquivo confirma que um deploy foi executado.

## Ambiente conhecido e verificações públicas (17/11/2026)

- Origem canônica: https://ramuvi.io; www.ramuvi.io redireciona para ela.
- Certificado Let's Encrypt com expiração em 07/02/2027. HTTP redireciona para HTTPS.
- Antes do deploy: / e /api/health/live e /api/health/ready retornaram 200; /learn/login retornou 404 (versão antiga).
- VPS Ubuntu: /opt/ramuvi/app, com Docker Compose e PostgreSQL persistente; verificar estes caminhos novamente na máquina antes de usar.
- Foram observados serviços anteriores frontend em 127.0.0.1:3100 e backend em 127.0.0.1:8180; nunca presumir que essas portas permanecem as mesmas.
- A chave SSH existente exige desbloqueio local e não autenticou nesta revisão. Não há autorização para improvisar credenciais ou acessar outra VPS.

## 1. Pré-voo obrigatório (sem escrita)

- Confirmar host e usuário SSH, identidade de máquina, diretórios, branch/tag atual, alterações locais e configuração dos arquivos Compose efetivamente usados na VPS.
- Verificar espaço livre, Docker daemon, saúde do PostgreSQL e qual banco/volume a instalação realmente utiliza.
- Registrar SHA atual da aplicação e imagem, tag anterior, logs relevantes, versões Flyway presentes e políticas de backup.
- Verificar DNS A/AAAA, certificados de ramuvi.io e www, Nginx de 80/443 e origem pública efetiva.
- Não executar compose up, git checkout ou nginx reload antes de concluir a verificação de backup e rollback.

## 2. Backup e teste de recuperação

- Produzir um novo backup consistente com scripts/release/backup-postgres.sh, garantindo que COMPOSE_FILE e COMPOSE_PROJECT_NAME apontem para a instalação real.
- Criar/verificar checksum SHA-256. Preservar um exemplar com acesso restrito fora da pasta de deploy e manter referência do volume original.
- Testar scripts/release/verify-backup.sh em contêiner isolado com o backup real ou representativo; verificar Flyway V67 no estado antigo e V68 após a evolução.
- Confirmar preservação de matrícula, progresso, participação e XP. Documentar tempo de indisponibilidade e procedimento de recuperação.
- A migração V68 é aditiva, mas rollback de imagens só é permitido após verificar compatibilidade do código anterior com o banco migrado; nunca executar restauração de banco automaticamente.

## 3. Gate do repositório e tag

- Release estável exige backend/pom.xml, frontend/package.json e package-lock com versão 0.12.0.
- scripts/release/release-gate.sh final exige main limpa/sincronizada, tag pretendida, CI 3/3 no SHA exato, backup restaurável e atestação privada com GO de 11 gates.
- Template inicial: docs/v0.12/RELEASE_ACCEPTANCE_TEMPLATE.json (NO-GO por padrão). Não marcar PASS sem responsável, data e evidência.
- Executar o gate antes de criar tag; então criar tag v0.12.0 no mesmo SHA e GitHub Release com notas verificadas.

## 4. Contrato público HTTPS

| Componente | Valor esperado |
| --- | --- |
| Origem canônica e frontend | https://ramuvi.io |
| APP_FRONTEND_URL | https://ramuvi.io |
| APP_ALLOWED_ORIGIN_PATTERNS | https://ramuvi.io (sem wildcard) |
| NEXT_PUBLIC_API_URL | https://ramuvi.io (mesma origem) |
| APP_STUDENT_OIDC_PUBLIC_BASE_URL | https://ramuvi.io |
| APP_SESSION_COOKIE_SECURE | true |
| SPRING_PROFILES_ACTIVE | prod |
| Google redirect URI | https://ramuvi.io/api/student-oidc/callback/google |
| Microsoft redirect URI | https://ramuvi.io/api/student-oidc/callback/microsoft |

- Manter APP_STUDENT_OIDC_ENABLED=false até confirmar client ID/secret e callback real. Manter também as flags de convite, resgate e estudo OFF até aceite funcional.
- Nginx deve preservar Host, usar X-Forwarded-Proto=https e X-Forwarded-For confiável, suportar /api/* e /ws/* com Upgrade e encaminhar rotas /learn/* ao Next.js.
- Não confiar em X-Forwarded-* injetados por clientes, não publicar PostgreSQL nem backend na interface externa.
- Certificado precisa cobrir ambos os hosts antes de manter redirecionamento de www. Testar nginx -t antes de recarregar; evitar reinicialização desnecessária.
- Verificar se HSTS está configurado; introduzi-lo gradualmente somente após demonstrar HTTPS estável para todos os subdomínios aplicáveis.
- Cookie de sessão: Secure, HttpOnly e SameSite=Lax no perfil de produção. SameSite=Strict impede o cookie JSESSIONID no redirecionamento de retorno Google/Microsoft e perde OAuth state/nonce; manter CSRF para POST. Inspecionar cabeçalhos sem registrar valores.

## 5. Atualização e verificação pós-deploy

- Manter os Compose overrides existentes da VPS, sem sobrescrever .env nem recriar o volume de banco.
- Fixar a tag imutável v0.12.0/SHA auditado, reconstruir as imagens em ambiente isolado ou via procedimento de deploy controlado.
- Confirmar Flyway V68 e saúde dos três contêineres. Validar /api/health/live e /api/health/ready pelo domínio, sem expor logs sensíveis.
- Conferir GET /learn/login = 200 no novo frontend, /learn/journeys e entrada por código sem regressão; antes, /learn/login estava 404.
- Verificar HTTPS, certificado, redirecionamentos, cookies e ausência de mixed content. Validar acesso professor e aluno legado.
- Se OIDC permanecer OFF, não declarar login com Google/Microsoft funcional. Registrar flags ativas, imagens, SHA, saúde e evidência de aceite.
- Em falha de saúde, interromper o avanço. Reverter somente após analisar a compatibilidade V68 e preservar o backup original.

## Restrições e dependências

- Este procedimento não autoriza publicação de funcionalidades I3b nem refatoração A/E não implementadas.
- O aceite do responsável para publicar não substitui prova de backup restaurado, testes físicos ou callback real.
- A operação exige credencial SSH desbloqueada no notebook autorizado; não registrar senhas ou chaves privadas no repositório, chat ou logs.
