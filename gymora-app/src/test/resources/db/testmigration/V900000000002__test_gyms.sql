-- Test gyms: A and B are active, C is suspended. Versions sort after every real migration.
insert into tenant_gym (id, name, city, country_code, currency, time_zone, status, suspended_reason, suspended_at,
                        owner_user_id, owner_email, owner_first_name, owner_last_name,
                        version, created_at, created_by, updated_at, updated_by)
values ('a1a1a1a1-0000-4000-8000-00000000000a', 'Test Gym A', 'Tunis', 'TN', 'TND', 'Africa/Tunis', 'ACTIVE', null, null,
        'owner-a1a1a1a1-0000-4000-8000-00000000000a', 'owner.a@test.local', 'Owner', 'A', 0, now(), 'test', now(), 'test'),
       ('b2b2b2b2-0000-4000-8000-00000000000b', 'Test Gym B', 'Sousse', 'TN', 'TND', 'Africa/Tunis', 'ACTIVE', null, null,
        'owner-b2b2b2b2-0000-4000-8000-00000000000b', 'owner.b@test.local', 'Owner', 'B', 0, now(), 'test', now(), 'test'),
       ('c3c3c3c3-0000-4000-8000-00000000000c', 'Test Gym C', 'Sfax', 'TN', 'TND', 'Africa/Tunis', 'SUSPENDED', 'Unpaid', now(),
        'owner-c3c3c3c3-0000-4000-8000-00000000000c', 'owner.c@test.local', 'Owner', 'C', 0, now(), 'test', now(), 'test');
