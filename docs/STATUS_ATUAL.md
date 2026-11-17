# Ramuvi — estado técnico atual

**Atualizado em 17/11/2026.** O código I0–I3a está integrado à main. A publicação estável é uma etapa distinta e ainda não ocorreu.

| Linha | Estado verificado | Evidência |
| --- | --- | --- |
| v0.11.0 | Última tag/release estável publicada | [GitHub Release](https://github.com/Verit-lab/ramuvi/releases/tag/v0.11.0) |
| v0.11.1 | Integrada à main pela PR #135; sem tag/release própria | [PR #135](https://github.com/Verit-lab/ramuvi/pull/135) |
| main | Commit 09819c5 com I0/I1/I2/I3a e Flyway V68, sem deploy | [Commit](https://github.com/Verit-lab/ramuvi/commit/09819c5b5c5c35701441991a51fbbe146133e78a) |
| CI main | 3/3 PASS; 113 Chromium e 19 WebKit | [Execução 44171449577](https://github.com/Verit-lab/ramuvi/actions/runs/44171449577) |
| v0.12.0 | Empacotamento de release em preparação, sem tag e sem publicação | [Notas candidatas](RELEASE_0_12.md) |
| VPS ramuvi.io | HTTPS válido, frontend anterior ainda ativo; /learn/login retornou 404 | [Runbook](v0.12/PRODUCTION_RELEASE.md) |

## Bloqueios concretos para a publicação

1. A branch de release precisa ser integrada à main e passar CI no SHA final, com metadados estáveis 0.12.0.
2. O backup real/representativo da VPS precisa de checksum e restauração comprovada. O ensaio sintético V68 pré-merge passou, mas não é esse backup.
3. O gate exige atestação privada de v0.12.0 com responsável, datas e referências para os 11 critérios; a declaração de aceite não comprova automaticamente testes físicos ou callbacks OIDC.
4. A sessão SSH da VPS está bloqueada pela chave privada criptografada, que não foi desbloqueada no notebook conectado.

## HTTPS e identidade

- Origem canônica https://ramuvi.io; certificado Let's Encrypt válido até 07/02/2027, redirecionamentos HTTP e www funcionando.
- Conferir na VPS APP_FRONTEND_URL, APP_ALLOWED_ORIGIN_PATTERNS, APP_STUDENT_OIDC_PUBLIC_BASE_URL e cookies Secure, antes de ligar o login externo.
- Novas flags de aluno iniciam OFF por padrão; deploy do código não habilita automaticamente OIDC, convites ou resgate.
- Nenhuma tag, release ou atualização de produção foi realizada nesta preparação.

**Fontes canônicas:** [GitHub](https://github.com/Verit-lab/ramuvi) para código e CI; [Notion](https://app.notion.com/p/3bff3eb198c3818fa281fd0abb23b6ce) para decisões; [Figma](https://www.figma.com/design/gWMkarPwmeFY15jr0Tr6DN) para design aprovado.

**Histórico anterior:** [snapshot de 25/10](archive/STATUS_ATUAL_2026-10-25.md), planos e notas de v0.11.1 e [registro ativo de v0.12](v0.12/PRODUCTION_RELEASE.md).
