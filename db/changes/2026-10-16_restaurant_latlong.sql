-- 2026-10-16 · Restaurant location as latitude/longitude (R-ORD-01)
-- Author: Yana Vassilyev
--
-- Why: R-ORD-01 says each restaurant has a lat/long position, but "Restaurant".location
-- was a single text column holding a street address.
--
-- How to apply: Supabase dashboard -> SQL Editor -> New query -> paste this file -> Run.
-- Flyway is disabled, so nothing runs this automatically. Run it once.
--
-- Stage 1 of 2. The old "location" column is kept for now because DemoProfileSeeder
-- (Ashton) still writes to it. Stage 2, which drops it, goes in a separate file once the
-- seeder is updated.

alter table public."Restaurant"
  add column latitude  double precision,
  add column longitude double precision;

alter table public."Restaurant"
  add constraint restaurant_lat_range check (latitude  between -90  and 90),
  add constraint restaurant_lng_range check (longitude between -180 and 180);

-- Demo restaurant seeded by DemoProfileSeeder (downtown Montreal).
update public."Restaurant"
set latitude = 45.5017, longitude = -73.5673
where id = '34300000-0000-4000-8000-000000000002';
