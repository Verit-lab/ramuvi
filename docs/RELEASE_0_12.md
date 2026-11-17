# Ramuvi v0.12.0 — identidade verificada e Jornada persistente

**Estado deste documento:** notas da release candidata. Não publicar como estável, criar tag ou implantar antes do gate final e do backup representativo aprovado.

## Escopo efetivamente integrado

- I0: fundação de StudentAccount e identidades verificadas; convites individuais com hash e V68 aditiva.
- I1: login de aluno OIDC Google/Microsoft usando Authorization Code, PKCE S256 e CSRF, com vínculo por issuer/subject, sem associação automática por e-mail.
- I2: emissão, revogação e resgate autenticado de convites para matrícula/turma ativa.
- I3a: consulta de Jornadas publicadas e progresso no Enrollment em dispositivos distintos; isolamento por identidade e matrícula.
- Correções pré-release: provisionamento concorrente, primeira conclusão de progresso, fronteira entre conta e claim legado, logout sem fallback silencioso e navegação Voltar/Recarregar do professor.

## Compatibilidade e segurança

- Dados e fluxo legados preservados; código de acesso do aluno continua disponível.
- Flyway V68 acrescenta as tabelas de identidade sem apagar matrículas nem progresso existentes.
- OIDC, emissão de convites, resgate e sessão verificada permanecem OFF por padrão. A simples instalação não habilita login social.
- Deploy deve usar origem canônica https://ramuvi.io e cookies Secure/HttpOnly, com HTTPS no proxy reverso e backend restrito à rede interna.
- Ativação real dos provedores depende de clientes OAuth próprios, callback HTTPS registrado e homologação separada.

## Não incluído

- I3b: Atividades/quiz, Conquistas, Perfil e Arena pela conta verificada.
- Redesign Figma A0–A4, refatorações E0–E4 e triagem das 133 propostas, sem autorização para declarar concluídas.
- Email-OTP, aprovação de professores multi-tenant e integrações externas adicionais.

## Evidências do código integrado

- Main consolidada: https://github.com/Verit-lab/ramuvi/commit/09819c5b5c5c35701441991a51fbbe146133e78a
- PRs #136 → #137 → #138 → #139 mescladas em ordem.
- CI da main: https://github.com/Verit-lab/ramuvi/actions/runs/44171449577, 3/3 PASS; 113 Chromium e 19 WebKit, sem flaky.
- Maven local pré-merge: 214 testes iniciais + 193 de integração (407/407). Frontend: 425/425.
- Ensaio isolado Flyway V68, backup e restore sintético: PASS; NÃO comprova restauração de dados reais da VPS.
- A versão estável só poderá ser associada ao SHA final de release, que exigirá CI própria e aceite operacional da mesma revisão.

## Requisitos antes de publicar

1. Revisar status da VPS e Compose de produção; confirmar origem, Secure cookies, DNS, proxy e callbacks OIDC.
2. Gerar backup do banco real, verificar checksum e executar restore isolado do mesmo backup.
3. Executar gate da v0.12 na main limpa, com CI 3/3 no SHA final e atestação privada preenchida com evidências legítimas.
4. Publicar tag imutável v0.12.0 e GitHub Release apontando para o mesmo SHA, apenas após aprovação do gate.
5. Atualizar a VPS de maneira controlada, preservar o backup e validar saúde, rotas, TLS, integridade e retorno.

**Runbook:** docs/v0.12/PRODUCTION_RELEASE.md
