-- v0.12.2 I3 (ADR-0076 D7–D9): Biblioteca compartilhável e Minha Biblioteca.
-- Migration ADITIVA: só cria tabelas novas; nenhuma linha existente é alterada.
-- Nenhuma tabela nova referencia classrooms, students ou enrollments: excluir uma turma
-- (ClassroomService.hardDelete, que apaga learning_resources por cascata da V29) nunca
-- remove snapshots nem itens pessoais legítimos.

-- Representação editorial IMUTÁVEL efetivamente entregue. resource_id vira NULL se o
-- recurso for excluído; source_resource_id preserva a origem sem FK.
create table learning_resource_snapshots (
    id uuid primary key,
    resource_id uuid references learning_resources(id) on delete set null,
    source_resource_id uuid not null,
    revision integer not null,
    format varchar(30) not null,
    title varchar(180) not null,
    summary text,
    body text,
    -- LINK: referência externa, não cópia; o destino pode mudar ou sair do ar.
    source_url text,
    source_label varchar(255),
    copy_protected boolean not null default false,
    content_sha256 varchar(64) not null,
    created_at timestamptz not null default now(),
    constraint ck_lr_snapshot_format check (format in ('MARKDOWN', 'PLAIN_TEXT', 'LINK')),
    constraint ck_lr_snapshot_revision check (revision >= 1),
    constraint ck_lr_snapshot_sha check (content_sha256 ~ '^[0-9a-f]{64}$'),
    -- A revisão do recurso só avança com mudança de conteúdo; o título não a altera,
    -- por isso o checksum completo também faz parte da identidade do snapshot.
    constraint uk_lr_snapshot unique (source_resource_id, revision, content_sha256)
);
create index idx_lr_snapshots_resource on learning_resource_snapshots(resource_id);

-- Link de compartilhamento: token aleatório de 160 bits exibido UMA vez; só o SHA-256 é guardado.
-- Contadores são agregados: nenhum registro de quem leu ou salvou.
create table library_share_links (
    id uuid primary key,
    resource_id uuid references learning_resources(id) on delete set null,
    source_resource_id uuid not null,
    snapshot_id uuid not null references learning_resource_snapshots(id),
    token_hash bytea not null unique,
    permission varchar(8) not null,
    label varchar(120),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    view_count integer not null default 0,
    save_count integer not null default 0,
    last_used_at timestamptz,
    constraint ck_library_share_hash check (octet_length(token_hash) = 32),
    constraint ck_library_share_permission check (permission in ('VIEW', 'SAVE')),
    constraint ck_library_share_expiration check (expires_at > created_at),
    constraint ck_library_share_counts check (view_count >= 0 and save_count >= 0)
);
create index idx_library_share_links_resource
    on library_share_links(source_resource_id, created_at desc);

-- Coleção pessoal da CONTA verificada, mesmo sem Student/Enrollment. Uma entrada por conta e
-- recurso, apontando para a versão concedida. Apagar a conta apaga a coleção (titularidade);
-- revogar o link NÃO apaga itens já salvos.
create table student_library_items (
    id uuid primary key,
    account_id uuid not null references student_accounts(id) on delete cascade,
    source_resource_id uuid not null,
    snapshot_id uuid not null references learning_resource_snapshots(id),
    share_link_id uuid references library_share_links(id) on delete set null,
    saved_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_student_library_item unique (account_id, source_resource_id)
);
create index idx_student_library_items_account
    on student_library_items(account_id, saved_at desc);
create index idx_student_library_items_snapshot on student_library_items(snapshot_id);
