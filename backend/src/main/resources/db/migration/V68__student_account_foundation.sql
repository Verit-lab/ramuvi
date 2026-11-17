-- v0.12 / etapa I0: identidade verificável, separada do Student e do device claim.
-- Nenhuma linha de students/enrollments é migrada automaticamente.
create table student_accounts (
    id uuid primary key,
    student_id uuid unique references students(id) on delete set null,
    status varchar(16) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    disabled_at timestamptz,
    constraint ck_student_accounts_status check (status in ('ACTIVE', 'DISABLED')),
    constraint ck_student_accounts_disabled check (
        (status = 'ACTIVE' and disabled_at is null)
        or (status = 'DISABLED' and disabled_at is not null)
    )
);

create table student_account_identities (
    id uuid primary key,
    account_id uuid not null references student_accounts(id) on delete cascade,
    method varchar(16) not null,
    issuer varchar(254) not null,
    subject varchar(254) not null,
    verified_at timestamptz not null,
    revoked_at timestamptz,
    constraint ck_student_account_identity_method check (method in ('OIDC', 'EMAIL_OTP')),
    constraint ck_student_account_identity_nonblank check (
        length(trim(issuer)) > 0 and length(trim(subject)) > 0
    ),
    constraint uk_student_account_external_identity unique (issuer, subject)
);
create index idx_student_account_identities_active
    on student_account_identities(account_id) where revoked_at is null;

-- Token aleatório de 256 bits; só o SHA-256 é armazenado.
-- Cada convite identifica UMA matrícula, não um nome/e-mail nem um código genérico de turma.
create table student_enrollment_invites (
    id uuid primary key,
    enrollment_id uuid not null references enrollments(id) on delete cascade,
    token_hash bytea not null unique,
    issued_at timestamptz not null default now(),
    expires_at timestamptz not null,
    claimed_at timestamptz,
    claimed_by_account_id uuid references student_accounts(id),
    revoked_at timestamptz,
    constraint ck_student_invite_hash check (octet_length(token_hash) = 32),
    constraint ck_student_invite_expiration check (expires_at > issued_at),
    constraint ck_student_invite_claim check (
        (claimed_at is null and claimed_by_account_id is null)
        or (claimed_at is not null and claimed_by_account_id is not null)
    )
);
create index idx_student_enrollment_invites_enrollment
    on student_enrollment_invites(enrollment_id, issued_at desc);
