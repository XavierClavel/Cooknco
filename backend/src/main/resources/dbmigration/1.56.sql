-- apply alter tables
alter table meal_plan_entries add column if not exists cooked boolean default true not null;
