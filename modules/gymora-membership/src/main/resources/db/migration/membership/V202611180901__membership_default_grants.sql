-- Gyms created before this module existed: give their starting profiles the same membership
-- capabilities a new gym gets (see MembershipCapabilities.grants()). Profiles a gym created
-- itself are left alone. Safe to run on an empty database.
insert into access_profile_capability (profile_id, capability)
select p.id, g.capability
from access_profile p
join (values
        ('MANAGER', 'membership.member.read'),
        ('MANAGER', 'membership.member.create'),
        ('MANAGER', 'membership.member.update'),
        ('MANAGER', 'membership.member.archive'),
        ('MANAGER', 'membership.note.read'),
        ('MANAGER', 'membership.note.create'),
        ('RECEPTIONIST', 'membership.member.read'),
        ('RECEPTIONIST', 'membership.member.create'),
        ('RECEPTIONIST', 'membership.member.update'),
        ('RECEPTIONIST', 'membership.note.read'),
        ('RECEPTIONIST', 'membership.note.create'),
        ('TRAINER', 'membership.member.read'),
        ('TRAINER', 'membership.note.read')
     ) as g (default_key, capability)
  on g.default_key = p.default_key
on conflict do nothing;
