# Ramuvi v0.11.1 — notas candidatas

**Estado:** candidato em desenvolvimento, **não publicado**. A tag `v0.11.1` só pode ser criada após CI verde, homologação e GO explícito.

## Objetivo

Consolidar as correções e funcionalidades incrementais aplicadas depois da tag v0.11.0, eliminando a divergência entre schema real, contratos de teste, perfil de execução e ferramenta de release. Esta **não** é a release do login verificado dos alunos.

## Código pós-v0.11.0 incorporado nesta candidata

- `42ca693`: aviso de conteúdo de Atividade atualizado e preservação de tentativas já iniciadas.
- `cfee841`, `0c0f49f`, `163afe3`, `1d21567`: edição completa do Markdown da Jornada, organizada no menu de ações, prévia e reconstrução fiel do conteúdo.
- `bdd2525`: sobrescrita controlada da Jornada com snapshot de progresso, migração aditiva V67.
- `20f1205`: simplificação do editor de Markdown por substituição integral.

## Incrementos da v0.11.1

- Contrato `SecurityAndMigrationIT` atualizado para V1–V67, incluindo `journey_overwrite_snapshots`.
- Versionamento de backend, frontend, package-lock e testes de release alinhados para `0.11.1`.
- Compose básico em loopback; perfil classroom deliberadamente publica somente frontend na LAN; produção permanece atrás de HTTPS e origens configuradas.
- Imagem backend/frontend identificada por SHA OCI e endpoint de saúde do backend informa `buildSha`; imagem compilada pelo script guarda o SHA da origem.
- `scripts/arena.sh upgrade TAG` obriga tag explícita em vez de `git pull` da `main`, com backup prévio e instrução de rollback controlado.
- Hook de proteção local contra push acidental na `main`; não substitui regra server-side.
- README e status corrente corrigidos; histórico anterior preservado separadamente.

## Não entregues

Contas de aluno, login OIDC/social/e-mail, aprovação de ingresso em turmas, múltiplos professores independentes, reestruturação Figma, correção objetiva, IA e refatoração arquitetural ampla.

## Gates para publicar

- [ ] PR na `main` aprovada e CI backend/frontend/E2E 3/3 no SHA final.
- [ ] Regressão real de edição da Jornada com snapshots e tentativas existentes.
- [ ] Testes Safari/iOS físico, desktop e projetor com rede instável.
- [ ] Backup de dados e restauração ensaiados em ambiente isolado; compatibilidade de rollback da aplicação frente à V67 validada.
- [ ] Segurança operacional e segredos verificados para instalação de destino.
- [ ] Autorização humana do GO, tag e Release conferidas por SHA.

## Testes locais realizados até o momento

- Contratos `check-metadata` e `verify-v0.11-contract`: PASS com versão 0.11.1 / V67.
- `mvn verify` completo após as mudanças de código: **167 testes de integração**, 0 falhas; Maven `BUILD SUCCESS`.
- Frontend `lint`, `format:check`, `npm test` (**424 testes**, zero falhas), `typecheck` e `build`: todos PASS.
- `docker compose config` local, sala e produção: sem erros; mapeamento verificado (local 127.0.0.1; sala 0.0.0.0 apenas frontend).
- Hook de main: push de teste para `refs/heads/main` recusado; push para outra branch permitido.
- `scripts/arena.sh upgrade` sem tag: falha de forma segura antes de tocar em serviços.
- Ensaios sintéticos isolados de upgrade **Flyway V56→V67**, retorno à V56 e restauração do backup V67: **PASS**, com integridade SHA-256 e verificação da estrutura dos snapshots. Não valida um backup real da VPS.
- Gate de readiness agora verifica o repositório `Verit-lab/ramuvi` e rejeita aceite de versão anterior, mesmo quando houver referências de evidência; 12 testes de scripts operacionais passaram.
- A execução oficial do GitHub Actions voltou a iniciar após o repositório ficar público. A versão final depende de CI verde **no SHA atual**.
