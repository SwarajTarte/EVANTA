-- ============================================================================
--  EVANTA · migration — reapply-after-reject support
--  Run once in Supabase → SQL Editor.
-- ============================================================================
--  Adds an attempt counter to registrations. attempts = total submissions:
--    1  = original enrollment
--    2,3,4 = successive reapplies after a rejection
--  The app allows reapply while attempts < 4  (i.e. up to 3 reapplies), then
--  locks the student out of that event.
-- ============================================================================

alter table public.registrations
  add column if not exists attempts integer not null default 1;

-- Backfill any existing rows to a sane value (they were their 1st attempt).
update public.registrations set attempts = 1 where attempts is null;
