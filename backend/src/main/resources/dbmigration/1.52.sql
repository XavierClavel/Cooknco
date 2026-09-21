-- Premium, as a subscription will eventually set it and as an operator sets it today.
-- Two columns rather than one: a grant with no end and a grant that runs out are different
-- things, and one nullable date would need a sentinel expiry that every query, every
-- listing and every operator reading the row would have to know to ignore.
--
-- Everyone starts without one, which is what they have had all along -- the only feature
-- behind the gate is the PDF export, and that was open to nobody but the admins until now.

-- apply alter tables
alter table users add column if not exists is_premium_forever boolean default false not null;
alter table users add column if not exists premium_until timestamp;
