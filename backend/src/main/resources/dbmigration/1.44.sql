-- apply alter tables
alter table users alter column locale drop default;
alter table users alter column locale drop not null;

-- Every row holds 0 (FR) because that was the column default and nothing has ever written
-- it: `users.locale` was added with a default and no writer, which is why every mail this
-- product has sent went out in French whoever read it. Those zeroes are not choices, so
-- they become the null the readers now fall back from -- and they fall back to FR, so no
-- account's mail changes language until one of its clients actually reports one.
update users set locale = null;
