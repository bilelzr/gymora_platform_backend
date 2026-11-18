-- What a gym sells. Changing a plan never changes subscriptions already sold (they keep a copy).
create table subscription_plan (
    id              uuid         primary key,
    tenant_id       uuid         not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    name            varchar(100) not null,
    description     varchar(500),
    duration_value  integer      not null check (duration_value between 1 and 1000),
    duration_unit   varchar(10)  not null check (duration_unit in ('DAY', 'MONTH')),
    -- Money is stored in millimes (1 TND = 1000 millimes), so amounts are exact.
    price_millimes  bigint       not null check (price_millimes >= 0),
    -- For session cards (e.g. 10 sessions). Counting sessions comes with check-ins.
    session_count   integer      check (session_count > 0),
    -- Total freeze days a subscription to this plan may use. 0 = no freezing.
    max_freeze_days integer      not null check (max_freeze_days >= 0),
    status          varchar(20)  not null check (status in ('ACTIVE', 'ARCHIVED')),
    archived_at     timestamptz,
    version         bigint       not null,
    created_at      timestamptz  not null,
    created_by      varchar(64),
    updated_at      timestamptz  not null,
    updated_by      varchar(64)
);
create unique index subscription_plan_name_uq on subscription_plan (tenant_id, lower(name));

-- A plan sold to a member for a period. Status (upcoming, active, frozen, expired, cancelled) is
-- worked out from the dates, so nothing has to run at midnight.
create table subscription_subscription (
    id                  uuid         primary key,
    tenant_id           uuid         not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    member_id           uuid         not null references membership_member (id),
    plan_id             uuid         not null references subscription_plan (id),
    -- Copy of the plan at the time of sale.
    plan_name           varchar(100) not null,
    duration_value      integer      not null,
    duration_unit       varchar(10)  not null check (duration_unit in ('DAY', 'MONTH')),
    session_count       integer,
    max_freeze_days     integer      not null,
    price_millimes      bigint       not null,
    discount_millimes   bigint       not null,
    amount_due_millimes bigint       not null,
    -- Sum of the payments that were not voided. Kept up to date with each payment.
    paid_millimes       bigint       not null,
    start_date          date         not null,
    -- End date without freezes; end_date = base_end_date + frozen_days.
    base_end_date       date         not null,
    end_date            date         not null,
    frozen_days         integer      not null check (frozen_days >= 0),
    cancelled_at        timestamptz,
    cancelled_on        date,
    cancel_reason       varchar(500),
    cancelled_by_name   varchar(150),
    version             bigint       not null,
    created_at          timestamptz  not null,
    created_by          varchar(64),
    updated_at          timestamptz  not null,
    updated_by          varchar(64),
    check (discount_millimes between 0 and price_millimes),
    check (amount_due_millimes = price_millimes - discount_millimes),
    check (paid_millimes between 0 and amount_due_millimes),
    check (end_date >= start_date)
);
create index subscription_member_idx on subscription_subscription (tenant_id, member_id, start_date desc);
create index subscription_end_idx on subscription_subscription (tenant_id, end_date);
create index subscription_plan_idx on subscription_subscription (tenant_id, plan_id);

-- A freeze pauses a subscription between two dates; the subscription's end moves by as many days.
create table subscription_freeze (
    id              uuid         primary key,
    tenant_id       uuid         not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    subscription_id uuid         not null references subscription_subscription (id),
    start_date      date         not null,
    end_date        date         not null,
    days            integer      not null check (days > 0),
    reason          varchar(300) not null,
    -- True when the member came back before the planned end date.
    ended_early     boolean      not null,
    version         bigint       not null,
    created_at      timestamptz  not null,
    created_by      varchar(64),
    updated_at      timestamptz  not null,
    updated_by      varchar(64),
    check (end_date >= start_date)
);
create index subscription_freeze_sub_idx on subscription_freeze (subscription_id, start_date);

-- Money received. Never deleted: a mistake is voided with a reason.
create table subscription_payment (
    id               uuid         primary key,
    tenant_id        uuid         not null check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    subscription_id  uuid         not null references subscription_subscription (id),
    member_id        uuid         not null references membership_member (id),
    receipt_number   varchar(20)  not null,
    amount_millimes  bigint       not null check (amount_millimes > 0),
    -- Only CASH for now; online payment comes in a later release.
    method           varchar(20)  not null check (method in ('CASH')),
    paid_at          timestamptz  not null,
    -- The gym's calendar day of paid_at, for the day's cash.
    paid_on          date         not null,
    received_by_name varchar(150) not null,
    note             varchar(300),
    voided_at        timestamptz,
    voided_by_name   varchar(150),
    void_reason      varchar(300),
    version          bigint       not null,
    created_at       timestamptz  not null,
    created_by       varchar(64),
    updated_at       timestamptz  not null,
    updated_by       varchar(64)
);
create unique index subscription_payment_receipt_uq on subscription_payment (tenant_id, receipt_number);
create index subscription_payment_day_idx on subscription_payment (tenant_id, paid_on);
create index subscription_payment_sub_idx on subscription_payment (subscription_id);
create index subscription_payment_member_idx on subscription_payment (tenant_id, member_id, paid_at desc);

-- Per-gym counter for receipt numbers (R-000001, ...). Incremented atomically.
create table subscription_receipt_counter (
    tenant_id  uuid   primary key check (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    last_value bigint not null
);
