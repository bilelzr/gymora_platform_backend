-- Gyms created before this module existed: give their starting profiles the same subscription
-- capabilities a new gym gets (see SubscriptionCapabilities.grants()). Profiles a gym created
-- itself are left alone. Safe to run on an empty database.
insert into access_profile_capability (profile_id, capability)
select p.id, g.capability
from access_profile p
join (values
        ('MANAGER', 'subscription.plan.read'),
        ('MANAGER', 'subscription.plan.manage'),
        ('MANAGER', 'subscription.subscription.read'),
        ('MANAGER', 'subscription.subscription.sell'),
        ('MANAGER', 'subscription.subscription.freeze'),
        ('MANAGER', 'subscription.subscription.cancel'),
        ('MANAGER', 'subscription.payment.read'),
        ('MANAGER', 'subscription.payment.record'),
        ('MANAGER', 'subscription.payment.void'),
        ('RECEPTIONIST', 'subscription.plan.read'),
        ('RECEPTIONIST', 'subscription.subscription.read'),
        ('RECEPTIONIST', 'subscription.subscription.sell'),
        ('RECEPTIONIST', 'subscription.subscription.freeze'),
        ('RECEPTIONIST', 'subscription.payment.read'),
        ('RECEPTIONIST', 'subscription.payment.record'),
        ('TRAINER', 'subscription.subscription.read')
     ) as g (default_key, capability)
  on g.default_key = p.default_key
on conflict do nothing;
