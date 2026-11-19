# ADR-0073 — Download seletivo de Jornadas e política de cópia

- **Status:** Proposto, incremento I1 da v0.12.1; não aprovado como release.
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
