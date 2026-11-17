-- Gyms are the tenants of GYMORA. A gym's id is the tenant_id carried by its users' tokens
-- and stored on every row of gym data. This table itself is platform data: not tenant-scoped.
create table tenant_gym (
    id                uuid         primary key,
    name              varchar(150) not null,
    city              varchar(100),
    country_code      varchar(2)   not null,
    phone             varchar(30),
    email             varchar(255),
    currency          varchar(3)   not null,
    time_zone         varchar(50)  not null,
    status            varchar(20)  not null check (status in ('ACTIVE', 'SUSPENDED')),
    suspended_reason  varchar(500),
    suspended_at      timestamptz,
    owner_user_id     varchar(64),
    owner_email       varchar(255) not null,
    owner_first_name  varchar(100) not null,
    owner_last_name   varchar(100) not null,
    version           bigint       not null,
    created_at        timestamptz  not null,
    created_by        varchar(64),
    updated_at        timestamptz  not null,
    updated_by        varchar(64)
);

create index tenant_gym_status_idx on tenant_gym (status);
create index tenant_gym_name_idx on tenant_gym (lower(name));
