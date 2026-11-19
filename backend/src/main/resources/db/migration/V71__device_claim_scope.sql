-- v0.12.1 P0 (ADR-0075): separa o "lembrar este dispositivo" da aula ao vivo da
-- credencial de estudo X-Study-Token. Migration ADITIVA: nenhuma linha é apagada.
--
-- LEGACY     claims emitidos antes desta versão (bootstrap de Jornada por nome/
--            matrícula/e-mail ou /api/join). Continuam válidos para estudo apenas
--            enquanto APP_STUDY_LEGACY_DEVICE_CLAIMS_STUDY_ENABLED=true e até a
--            expiração atual; deixam de ser renovados no uso.
-- LIVE       novo padrão de /api/join: só reentra em aulas ao vivo; não é aceito
--            em /api/learn/** (Jornadas, perfil, conquistas).
-- LIVE_STUDY opt-in explícito do operador (APP_STUDY_LIVE_DEVICE_CLAIM_STUDY_ENABLED);
--            validade curta.
alter table enrollment_device_claims
    add column scope varchar(16) not null default 'LEGACY';
alter table enrollment_device_claims
    add constraint ck_enrollment_device_claims_scope
        check (scope in ('LEGACY', 'LIVE', 'LIVE_STUDY'));
-- Qualquer insert futuro que esqueça o escopo falha fechado (sem acesso a estudos).
alter table enrollment_device_claims alter column scope set default 'LIVE';
