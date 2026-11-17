# v0.12.1 — Registro de implementação

**17/11/2026 — incremento I1: domínio e controles docentes (em validação).**

## Entregue em código na branch (não na main)
- V69: offline_allowed falso nas Jornadas anteriores e default verdadeiro nas próximas.
- JPA/DTO de Journey: GET/listagem/POST/PUT recebem e retornam offlineAllowed.
- Criação: offline permitido e cópia permitida por padrão; não inicia downloads.
- Editor: duas permissões independentes na criação e edição publicada.
- Reutilização da V61 para cópia; materiais da Biblioteca com proteção própria continuam protegidos.
- Correção: edição por PUT sem stages não remove etapas de rascunho.
- Testes backend das permissões e integridade de etapas; contrato V69 atualizado; teste frontend de UI.

## Ainda não entregue
- Download/manifesto autenticado (incremento I2).
- Armazenamento IndexedDB, quotas, atualização e remoção (incremento I3).
- Shell/leitor offline e E2E em modo avião, iOS/Android físicos (incremento I4).
- Homologação OIDC real v0.12 e rollout/deploy (gate operacional).

**Não chamar o recurso de Jornada offline funcional até concluir I2–I4.**

## Evidências
- Preencher com SHA final e comandos/testes aprovados após execução.
- Nenhum dado de produção pode ser migrado antes do aceite e backup.
