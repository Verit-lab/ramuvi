-- Backup transacional da jornada antes de sobrescrever sua estrutura.
-- Mantém os registros originais de conteúdo e progresso fora da trilha ativa.
create table journey_overwrite_snapshots (
    id uuid primary key,
    journey_id uuid not null references journeys(id) on delete cascade,
    created_at timestamptz not null default now(),
    journey_title varchar(180) not null,
    journey_status varchar(20) not null,
    journey_updated_at timestamptz not null,
    previous_stage_count integer not null,
    enrollment_progress_count integer not null,
    redeemed_progress_count integer not null,
    snapshot jsonb not null
);
create index idx_journey_overwrite_snapshots_history
    on journey_overwrite_snapshots(journey_id, created_at desc);
