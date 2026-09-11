-- The mail service no longer keeps a copy of who the users are and who follows whom:
-- the address and everything else a mail needs now travels on the event that asks for it.
-- Followers first — it is the side holding the foreign keys.
drop table if exists followers;
drop table if exists users;
