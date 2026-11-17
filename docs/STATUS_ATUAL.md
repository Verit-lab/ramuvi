# Ramuvi — estado técnico atual

**Atualizado em 16/11/2026.** Esta página contém o estado operacional vigente. O registro anterior, que incluía estados antigos de RCs e SHAs, foi preservado em [histórico anterior](archive/STATUS_ATUAL_2026-10-25.md).

| Linha | Estado confirmado | Evidência |
| --- | --- | --- |
| `v0.11.0` | Última release estável publicada em 25/10; SHA `1f23df6` | [GitHub Release](https://github.com/Verit-lab/ramuvi/releases/tag/v0.11.0) |
| `main` | Commit `20f1205` (05/11), com 7 commits funcionais após a tag, incluindo V67 | [GitHub](https://github.com/Verit-lab/ramuvi/commit/20f1205) |
| CI `main` | **FALHOU** nos dois últimos workflows; migração V67 fora do contrato `SecurityAndMigrationIT`; o E2E ficou impedido | [Última execução de 05/11](https://github.com/Verit-lab/ramuvi/actions/runs/42093112826) |
| `v0.11.1` | **CANDIDATA EM HOMOLOGAÇÃO** na [PR #135](https://github.com/Verit-lab/ramuvi/pull/135); testes locais e rollback sintético aprovados, CI e aceite final pendentes; sem tag e sem deploy | [Plano de implementação](v0.11.1/IMPLEMENTATION_PLAN.md) |
| `v0.12` | Próxima evolução funcional de identidade discente, sujeita a decisão de escopo e à baseline estável | [Issue #119](https://github.com/Verit-lab/ramuvi/issues/119) |

## Entregas previstas na v0.11.1

1. Corrigir testes e contrato de versão para Flyway V67, preservar snapshots e validar regressão.
2. Tornar explícita a separação dos perfis local, LAN/classroom e prod.
3. Exibir SHA real na imagem/backend; atualizar apenas por tag aprovada.
4. Corrigir documentação corrente e completar testes em CI e homologação de campo.

## Limitações e critérios obrigatórios

- A edição tem **um operador docente por instalação** (ADR-0053); não há autenticação verificada multi-dispositivo de aluno. O modo convidado usa códigos e credenciais limitados. Não declarar capacidade multiusuário.
- O plano e testes locais **não equivalem** a release publicada. GO exige PR integrado, CI 3/3 no SHA final, testes físico e de segurança, backup e rollback ensaiado, tag e GitHub Release.
- Nada deve ser aplicado na VPS ou em banco real sem confirmar compatibilidade de esquema e backup restaurável.

**Fontes canônicas:** [repositório](https://github.com/Verit-lab/ramuvi) para código/CI/ADRs; [Notion](https://app.notion.com/p/3bff3eb198c3818fa281fd0abb23b6ce) para decisões de produto; [Figma](https://www.figma.com/design/gWMkarPwmeFY15jr0Tr6DN) para design aprovado.
