-- 2026-10-16 · Drop "Restaurant".location (stage 2 of 2, R-ORD-01)
-- Author: Yana Vassilyev
--
-- Why: latitude/longitude (added in 2026-10-16_restaurant_latlong.sql) replace the old
-- text address. DemoProfileSeeder and RestaurantOwner no longer read or write "location".
--
-- Run ONLY after the seeder change is merged into main; teammates seeding from an older
-- main would otherwise fail on the missing column.
--
-- Before running, save the old addresses if you want them back later:
--   select id, location from public."Restaurant";
--
-- How to apply: Supabase dashboard -> SQL Editor -> New query -> paste -> Run. Run once.
-- Rollback: 2026-10-16_restaurant_drop_location_rollback.sql

begin;

-- Every restaurant must have coordinates before the address goes away.
do $$
begin
  if exists (select 1 from public."Restaurant" where latitude is null or longitude is null) then
    raise exception 'Some restaurants have no latitude/longitude; fill them in before dropping location.';
  end if;
end $$;

alter table public."Restaurant" drop column location;

commit;
