-- Existing classrooms remain invitation-only until the teacher explicitly opts in.
-- Independent from published Journey visibility.
alter table classrooms add column self_enrollment_enabled boolean not null default false;
