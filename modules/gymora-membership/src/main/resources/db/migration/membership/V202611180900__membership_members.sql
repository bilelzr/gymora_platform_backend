-- Members of a gym. Never deleted: archived members keep their history.
create table membership_member (
    id                      uuid         primary key,
    tenant_id               uuid         not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    member_number           varchar(20)  not null,
    first_name              varchar(100) not null,
    last_name               varchar(100) not null,
    -- Normalized: digits with an optional leading +. Required: it is how gyms identify members.
    phone                   varchar(30)  not null,
    email                   varchar(255),
    gender                  varchar(10)  check (gender in ('FEMALE', 'MALE')),
    birth_date              date,
    address                 varchar(300),
    emergency_contact_name  varchar(150),
    emergency_contact_phone varchar(30),
    status                  varchar(20)  not null check (status in ('ACTIVE', 'ARCHIVED')),
    archived_at             timestamptz,
    version                 bigint       not null,
    created_at              timestamptz  not null,
    created_by              varchar(64),
    updated_at              timestamptz  not null,
    updated_by              varchar(64)
);
create unique index membership_member_number_uq on membership_member (tenant_id, member_number);
create unique index membership_member_phone_uq on membership_member (tenant_id, phone);
create index membership_member_status_idx on membership_member (tenant_id, status);
create index membership_member_name_idx on membership_member (tenant_id, lower(last_name), lower(first_name));

-- Staff notes about a member. The author's name is kept as written, so it survives staff changes.
create table membership_member_note (
    id          uuid          primary key,
    tenant_id   uuid          not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    member_id   uuid          not null references membership_member (id),
    body        varchar(2000) not null,
    author_name varchar(150)  not null,
    version     bigint        not null,
    created_at  timestamptz   not null,
    created_by  varchar(64),
    updated_at  timestamptz   not null,
    updated_by  varchar(64)
);
create index membership_member_note_member_idx on membership_member_note (member_id, created_at desc);

-- Per-gym counter for member numbers (M-000001, M-000002, ...). Incremented atomically.
create table membership_member_counter (
    tenant_id  uuid   primary key check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    last_value bigint not null
);
