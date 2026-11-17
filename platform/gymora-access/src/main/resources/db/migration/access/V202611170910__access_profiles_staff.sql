-- Profiles: named sets of capabilities, defined by each gym.
create table access_profile (
    id          uuid         primary key,
    tenant_id   uuid         not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    name        varchar(80)  not null,
    description varchar(300),
    default_key varchar(20)  check (default_key in ('MANAGER', 'RECEPTIONIST', 'TRAINER')),
    version     bigint       not null,
    created_at  timestamptz  not null,
    created_by  varchar(64),
    updated_at  timestamptz  not null,
    updated_by  varchar(64)
);
create unique index access_profile_name_uq on access_profile (tenant_id, lower(name));

create table access_profile_capability (
    profile_id uuid         not null references access_profile (id) on delete cascade,
    capability varchar(120) not null,
    primary key (profile_id, capability)
);

-- Staff: gym users without a Keycloak role. Their access comes only from their profiles.
create table access_staff_member (
    id         uuid         primary key,
    tenant_id  uuid         not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    user_id    varchar(64)  not null unique,
    email      varchar(255) not null,
    first_name varchar(100) not null,
    last_name  varchar(100) not null,
    phone      varchar(30),
    status     varchar(20)  not null check (status in ('ACTIVE', 'DISABLED')),
    version    bigint       not null,
    created_at timestamptz  not null,
    created_by varchar(64),
    updated_at timestamptz  not null,
    updated_by varchar(64)
);
create unique index access_staff_email_uq on access_staff_member (tenant_id, lower(email));
create index access_staff_tenant_idx on access_staff_member (tenant_id);

create table access_staff_profile (
    staff_id   uuid not null references access_staff_member (id) on delete cascade,
    profile_id uuid not null references access_profile (id),
    primary key (staff_id, profile_id)
);
create index access_staff_profile_profile_idx on access_staff_profile (profile_id);
