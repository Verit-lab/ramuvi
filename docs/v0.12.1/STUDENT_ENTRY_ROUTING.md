# Entrada unificada do aluno: /learn e /join

**Escopo:** alteração incremental de navegação na v0.12.1. Não altera tabelas, matrículas, permissões, contratos das APIs nem o runtime ao vivo.

## Contrato público

- `/learn`: uma única entrada para conta do aluno. Sem sessão OIDC válida, apresenta provedores permitidos e opção de ingresso ao vivo. Com sessão OIDC e matrícula, apresenta o painel de estudo. A conta é gerenciada na mesma rota com `?account=1`; após o retorno do provedor, `?connected=1` permite visualizar o vínculo e informar convite individual. Conta válida ainda sem matrícula abre o formulário de vínculo.
- `/join`: ingresso em experiências ao vivo por código/QR, sem exigir conta permanente. O fluxo de participação existente permanece preservado.
- `/learn/login`: **apenas redireciona** para `/learn?account=1`, preservando os indicadores permitidos de retorno OIDC (`connected=1` e `error=oidc`). Não há mais uma segunda tela de autenticação.
- `/learn/journeys`, `/learn/live` e demais URLs internas: preservadas para compatibilidade, conteúdo e links profundos. O aluno do fluxo legado por código recebe link direto para `/learn/journeys`, evitando passar novamente pela entrada de conta.

## Segurança, compatibilidade e validação

- A autorização permanece no backend. Um estado visual autenticado nunca é prova de matrícula; o servidor verifica a identidade OIDC e a matrícula ativa nas APIs correspondentes.
- Uma sessão OIDC observada que expire/revogue **não** faz fallback silencioso para o token lembrado de outro aluno. A troca para o fluxo legado exige uma ação explícita.
- OIDC mantém os mesmos endpoints `/api/student-oidc/authorization/*` e `/api/student-oidc/callback/*`; somente o destino UI de sucesso/erro muda para `/learn`.
- O fluxo por código continua reversível: APIs `/api/join/**` e `/api/learn/**` não foram alteradas.
- Testar: primeiro acesso sem sessão; conta verificada; conta sem matrícula; resgate de convite; sessão revogada; logout; OIDC desligado; link antigo; ingresso por código; zoom/mobile e providers não confiáveis.

**Implantação:** não há autorização de deploy neste incremento. Validar localmente e via CI na PR; integrar à base depois dos gates e de coordenação com o trabalho da Jornada offline da v0.12.1.
