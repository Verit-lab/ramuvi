-- v0.12.1 P1 (ADR-0074): link de ingresso dedicado da turma + fila de revisão docente.
-- Migration ADITIVA. A V70 (classrooms.self_enrollment_enabled) é preservada, mas
-- deixa de autorizar qualquer ingresso: o código de negócio classrooms.code nunca
-- é prova de ingresso.

-- Token aleatório de 160 bits mostrado UMA vez ao professor; só o SHA-256 é guardado.
create table classroom_join_links (
    id uuid primary key,
    classroom_id uuid not null references classrooms(id) on delete cascade,
    token_hash bytea not null unique,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    requires_approval boolean not null default false,
    accepted_count integer not null default 0,
    last_accepted_at timestamptz,
    constraint ck_classroom_join_link_hash check (octet_length(token_hash) = 32),
    constraint ck_classroom_join_link_expiration check (expires_at > created_at),
    constraint ck_classroom_join_link_count check (accepted_count >= 0)
);
-- No máximo um link não revogado por turma: gerar um novo revoga o anterior.
create unique index uk_classroom_join_links_open
    on classroom_join_links(classroom_id) where revoked_at is null;

-- Pedido de ingresso que exige decisão do professor (aprovação ligada, possível
-- duplicata ou e-mail verificado que não pode ser vinculado automaticamente).
-- Nunca cria aluno nem vincula conta até a decisão docente.
create table classroom_join_requests (
    id uuid primary key,
    classroom_id uuid not null references classrooms(id) on delete cascade,
    account_id uuid not null references student_accounts(id) on delete cascade,
    link_id uuid references classroom_join_links(id) on delete set null,
    reason varchar(24) not null,
    status varchar(16) not null default 'PENDING',
    display_name varchar(140) not null,
    -- Somente e-mail com email_verified=true do Google; apagado ao decidir.
    verified_email varchar(254),
    created_at timestamptz not null default now(),
    decided_at timestamptz,
    resolved_student_id uuid references students(id) on delete set null,
    constraint ck_classroom_join_request_reason
        check (reason in ('APPROVAL_REQUIRED', 'POSSIBLE_DUPLICATE', 'EMAIL_REVIEW')),
    constraint ck_classroom_join_request_status
        check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    constraint ck_classroom_join_request_decision check (
        (status = 'PENDING' and decided_at is null)
        or (status <> 'PENDING' and decided_at is not null)
    )
);
create unique index uk_classroom_join_requests_pending
    on classroom_join_requests(classroom_id, account_id) where status = 'PENDING';
create index idx_classroom_join_requests_classroom
    on classroom_join_requests(classroom_id, status, created_at);
create index idx_classroom_join_requests_account
    on classroom_join_requests(account_id, created_at desc);
