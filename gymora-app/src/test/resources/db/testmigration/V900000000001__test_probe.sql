-- Test-only table for tenant isolation tests. Same shape every tenant-scoped table must follow.
create table test_probe (
    id         uuid         primary key,
    tenant_id  uuid         not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    name       varchar(100) not null,
    version    bigint       not null,
    created_at timestamptz  not null,
    created_by varchar(64),
    updated_at timestamptz  not null,
    updated_by varchar(64)
);

create index test_probe_tenant_idx on test_probe (tenant_id);
