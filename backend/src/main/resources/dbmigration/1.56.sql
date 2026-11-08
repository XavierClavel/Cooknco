-- apply alter tables
alter table meal_plan_entries add column if not exists has_cooked boolean default true not null;
