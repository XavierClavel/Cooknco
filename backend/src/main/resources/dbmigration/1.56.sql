-- apply alter tables
alter table meal_plan_entries add column if not exists not_cooked boolean default false not null;
