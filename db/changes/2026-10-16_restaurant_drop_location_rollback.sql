-- Rollback for 2026-10-16_restaurant_drop_location.sql
--
-- Restores the column only. The old address values are gone; restore them from the
-- "select id, location" output saved before the drop, e.g.
--   update public."Restaurant" set location = '456 Demo Street, Montreal'
--   where id = '34300000-0000-4000-8000-000000000002';
--
-- The seeder no longer writes this column, so a rollback also needs the Java change reverted.

alter table public."Restaurant" add column location text;
