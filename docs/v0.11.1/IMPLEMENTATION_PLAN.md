# Ramuvi v0.11.1 — plano de estabilização e homologação

**Estado:** EM DESENVOLVIMENTO, sem GO de produção. **Responsável pelo aceite:** mantenedor do produto. **Base:** `main@20f1205b4c6cf8ebfeb50b9f22a8d489ff5d74f8` (16/11/2026). **Branch:** `fix/v0.11.1-stabilization`.

## Problema e resultado esperado

A v0.11.0 foi publicada no commit `1f23df6`. Sete commits funcionais entraram depois da tag sem PR e adicionaram o editor Markdown integral da Jornada, preservação de snapshots e Flyway V67. Na `main@20f1205`, a CI falha no teste `SecurityAndMigrationIT` porque ainda exige exclusivamente V1–V66, impedindo o E2E. A versão operacional e a documentação também estão divergentes.

**Objetivo:** transformar a revisão pós-v0.11.0 numa versão pequena, rastreável, verificável, com rollback documentado e sem modificação no banco de produção neste ciclo de desenvolvimento.

## Escopo aprovado para implementação em branch

1. Corrigir contrato de migrações V67 sem afrouxar a validação da estrutura e executar testes de integração.
2. Congelar e documentar a linha de base, os sete commits e a propriedade dos snapshots existentes. Não recriar ou modificar V67.
3. Restringir publicação de serviços do compose básico ao loopback, preservando conscientemente o frontend na LAN no perfil `classroom` e o proxy HTTPS no perfil `prod`.
4. Tornar verificáveis versão e SHA na rotina operacional e orientar atualização por **tag explicitamente selecionada**, sem seguir automaticamente a `main`; rollback reprodutível.
5. Corrigir referências e status operacionais vigentes no README e em `docs/STATUS_ATUAL.md`; preservar documentos históricos.
6. Revisar metadados de versão e gates existentes sem declarar homologação de aparelhos ou restauração que não ocorreram.
7. Publicar branch e PR após validação estática, unitária e integração possível.

## Fora de escopo

- Identidade verificada, OAuth/OIDC, múltiplos professores, permissões multiusuário, associação de matrículas e migrações dessas contas: incremento funcional seguinte.
- Redesign Figma, arquitetura ampla, mudanças em dinâmicas, Mastery, IA e novas funcionalidades.
- Merge na `main`, tag final, deploy na VPS ou alteração de dados reais sem gates de release e aceite explícito.

## Etapas e critérios de saída

| Gate | Trabalho | Evidência exigida |
| --- | --- | --- |
| G0 | Branch limpa e SHA remoto conferido; mapa do desvio pós-tag | `git status`, `git log`, CI atual e inventário de migrações |
| G1 | Patch do contrato V67 e segurança do compose | Testes JUnit de integração, `docker compose config` em perfis `dev/classroom/prod`, sem credencial padrão publicada |
| G2 | Versionamento, atualização segura e documentação operacional | Metadados consistentes, testes de shell, `--help` e fluxos de erro, rollback por SHA/tag documentado |
| G3 | Regressão | `mvn verify`, frontend `lint`, `test`, `typecheck`, `build` e E2E Chromium/WebKit na CI 3/3 |
| G4 | Homologação | Testes em Safari físico, fluxo professor/aluno/projetor, backup/restore isolados, validação de segurança, aprovação humana |
| G5 | Publicação | PR aprovado e mesclado, SHA final CI verde, tag `v0.11.1`, Release, evidências e instruções de rollback |

## Riscos e controles

- **Migração V67:** é aditiva e contém snapshots de jornada. Exige backup validado antes de implantação; rollback de aplicação não desfaz migration ou dados. Não alterar migration já aplicada.
- **Sala de aula:** acesso externo só no perfil classroom e apenas ao frontend; perfil básico não expõe a LAN.
- **Segredos:** nunca imprimir, commitar nem recolher `.env`. Valor de demonstração recusado nos scripts de instalação.
- **Atualização:** pin por tag de release imutável (com commit conhecido), verificação de worktree e fast-forward restrita ao script; backup antes da mudança e rota de recuperação testada fora de produção.
- **Revisão dos 7 commits:** integração no pacote não substitui testes de regressão do editor e da preservação do progresso.
- **Isolamento docente:** mantém-se a restrição de um operador por implantação (ADR-0053).

## Fontes

- Auditoria de 16/11: https://app.notion.com/p/41af3eb198c3819f8306e5b247d1f355
- Base anterior: https://github.com/Verit-lab/ramuvi/releases/tag/v0.11.0
- CI vermelha: https://github.com/Verit-lab/ramuvi/actions/runs/42093112826
- Repositório: https://github.com/Verit-lab/ramuvi
- Futuro da identidade discente: https://github.com/Verit-lab/ramuvi/issues/119

## Diário de execução

- 16/11: estado `main@20f1205` e sincronismo com remoto verificados; CI de 05/11 falha; branch criada.
- 16/11: G1 local: 67 migrações aplicadas em PostgreSQL de teste, `mvn verify` PASS (167 testes de integração, zero falhas). Compose local em loopback, sala apenas frontend na LAN; produção validada por `config --quiet`.
- 16/11: G2 local: versões alinhadas para `0.11.1`, contratos de release PASS; atualização exige tag publicada, rejeita chamada sem argumento e downgrade; hook local de bloqueio à `main` validado.
- 16/11: G3 parcial: frontend lint, formatação, 424 testes, typecheck e build PASS; falta CI 3/3 em PR, ensaio de rollback e testes físicos para G4/G5.
