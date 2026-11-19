# Ramuvi — correções de segurança S2: QR e Next.js

Base main a75e398. Sem migrações, alterações de banco ou deploy.

## QR Code: origem estrita
A geração pública de QR aceitava qualquer URL HTTP(S) como base; uma imagem servida pela Ramuvi podia apontar para site de terceiro. Agora QrCodeService aceita apenas a origem exata APP_FRONTEND_URL e entradas explícitas sem curingas de APP_ALLOWED_ORIGIN_PATTERNS. Esquema, host e porta precisam coincidir. Porta padrão e capitalização de hostname são normalizadas.

Sites externos, nomes de host parecidos, URL com usuário/senha, caminho adicional, consulta, fragmento e troca de protocolo resultam em HTTP 400. Rede local continua viável quando IP:porta exatos são configurados pelo operador; curingas CORS não autorizam destinos de QR.

Os dois endpoints de QR utilizam o mesmo serviço. Testes: QrCodeServiceTest (aceite e rejeição) e ProjectorAccessIT (QR decodificado e cenários negativos).

## Next.js
Atualizado next de 16.4.2 para 16.4.6, mesmo minor, com lockfile. npm audit --omit=dev --audit-level=moderate apresentou zero vulnerabilidades de produção no momento da verificação.

## Evidências e limites
Backend Maven verify: 193 testes, PostgreSQL 17 isolado, aprovado. Frontend TypeScript, lint (avisos preexistentes), 437 testes e build aprovados.
A CI oficial deve ser reexecutada no SHA publicado; não assumir gates verdes quando os jobs nem iniciaram. Antes da implantação, conferir APP_FRONTEND_URL e entradas de APP_ALLOWED_ORIGIN_PATTERNS na VPS, sem registrar segredos em logs.
PR independente das PRs 140, 144 e 146. Publicação requer revisão e aceite.
