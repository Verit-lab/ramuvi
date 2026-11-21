# ADR-0073 — Download seletivo de Jornadas e política de cópia

- **Status:** Código I1-I4 em branch para homologação; não aprovado como release.
- **Data:** 17/11/2026.
- **Relacionados:** ADR-0039, ADR-0058, ADR-0071, V61 e V69.

## Contexto

A PWA da Ramuvi permanece online-first. A v0.12 implementa Jornada por conta verificada, mas não disponibiliza materiais sem rede. As ADRs 0039/0058 proíbem cache genérico de APIs acadêmicas, corretamente. A política da Jornada para copiar conteúdo já existe (copy_protected), combinada com a política de cada material da Biblioteca.

## Decisão

1. **Duas permissões independentes na Jornada:** offline_allowed e copy_protected. Na interface aparecem como *Permitir acesso offline* e *Permitir copiar e colar conteúdo*.
2. **Novas Jornadas:** offline_allowed = true, copy_protected = false (ambas permissões ligadas). Isso **não** provoca download automático.
3. **Jornadas existentes:** V69 mantém offline_allowed = false até decisão expressa do professor. Preservar copy_protected e progresso.
4. **Compatibilidade da cópia:** reutilizar V61; copy_protected no material prevalece sobre a permissão geral. A interface desencoraja cópia; não é DRM.
5. **Edição:** permitir alterar as permissões em Jornadas publicadas sem reconstruir etapas; campos opcionais ausentes conservam valor. Etapas de rascunho também devem permanecer quando stages é omitido.
6. **Offline futuro:** somente leitura de pacotes explicitamente baixados e autorizados por conta verificada, com IndexedDB e shell offline independente de APIs autenticadas. Não duplicar progresso.
7. **Revisão obrigatória:** revogação enquanto offline, validade de pacotes, privacidade em aparelhos compartilhados, quotas e iOS físico. A PWA não pode garantir remoção instantânea sem rede.

## Consequências

A V69 é aditiva; desligar a futura feature flag impedirá novos downloads mas não apagará materiais já baixados em dispositivos desconectados. Ficam fora do I1 manifesto, download, IndexedDB, leitor offline, sincronização, conclusão de capítulos e implantação.

## Gates

- PostgreSQL real V1–V69 e testes de preservação de permissões e etapas.
- Typecheck, testes, lint e build Next.
- PR isolada; nenhuma liberação automática de download, merge ou deploy.

## Extensão I2–I4 (18/11/2026)

- A API de pacote exige OIDC de aluno, matrícula ativa, Jornada publicada, permissão offline do professor e conteúdo desbloqueado. Respostas com Cache-Control no-store. Não usa token de dispositivo.
- Apenas textos no pacote: limite de 80 conteúdos e 2 milhões de caracteres, sem baixar mídia, links externos ou anexos. A leitura offline do Markdown é textual e simplificada.
- Validade máxima de sete dias. Não existe revogação instantânea sem rede nem proteção DRM; o navegador pode ter seu relógio alterado.
- Leitor PWA estático pré-cacheado, com armazenamento AES-256-GCM no IndexedDB, chave derivada com PBKDF2-SHA256 250 mil iterações a partir de senha offline de pelo menos 12 caracteres. Não guardar senha nem chave em localStorage.
- Particionamento do pacote por conta e Jornada, com identificador externo opaco e dados pedagógicos criptografados. Aparelhos compartilhados exigem cuidado especial.
- Progresso local pendente só é submetido mediante ação explícita, sessão OIDC e CSRF, após revalidação de acesso e estado de desbloqueio. A autoridade sobre progresso e pontos permanece no backend.
- Se a sincronização confirmar revogação (HTTP 403/404), remover pacote do dispositivo. Falha transitória não é motivo para eliminar progresso. A mesma matrícula continua única.
- Permissão docente ON por padrão para Jornadas novas não ativa automaticamente o recurso. O rollout exige a flag APP_STUDENT_IDENTITY_OFFLINE_DOWNLOAD_ENABLED=true, OFF por padrão.
- No WebKit Playwright headless, o Service Worker controla a página e seus três arquivos estão em Cache API. O teste de reinício offline com setOffline(true) resulta em erro interno de navegação, portanto a abertura fria no Safari/iPhone real continua como bloqueio de aceite.
- Flyway V69 é aditiva, mas uma imagem anterior que conhece somente V68 pode falhar em validação Flyway. Fazer ensaio de rollback em staging com V69; desligar a flag é o fallback operacional principal.
