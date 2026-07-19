-- ============================================================================
--  EVANTA · Storage bucket policies — FREE PATH (Firebase third-party auth)
-- ============================================================================
--  Storage RLS lives on the table `storage.objects`. Run in SQL Editor AFTER
--  the same pre-checks as rls_policies.sql (Firebase provider registered,
--  USE_FIREBASE_AUTH = true verified). Same caller identity:
--     auth.jwt() ->> 'sub'  =  Firebase UID,  role stays `anon`  →  TO public.
--
--  Buckets in this app:
--    avatars        <uid>.jpg                      — user's own profile photo
--    event-covers   <title>_<uuid>.jpg             — admins upload/delete
--    certificates   <eventId>_<userUid>_<uuid>.ext — admins upload/delete
--
--  READS: keep public (all three buckets are served via public URLs and the
--  app loads them with Glide). We only gate writes.
--
--  ROLLBACK: drop any policy below, or make the bucket public again in the
--  Storage UI. Reads never break from these — they only restrict writes.
-- ============================================================================

alter table storage.objects enable row level security;  -- usually already on

-- ---- AVATARS ---------------------------------------------------------------
-- Public read.
drop policy if exists avatars_read on storage.objects;
create policy avatars_read on storage.objects
  for select to public
  using (bucket_id = 'avatars');

-- A user may write/overwrite ONLY the object named "<their uid>.jpg".
-- name = '<uid>.jpg'  →  strip the extension and compare to the JWT sub.
drop policy if exists avatars_write_self on storage.objects;
create policy avatars_write_self on storage.objects
  for insert to public
  with check (
    bucket_id = 'avatars'
    and split_part(name, '.', 1) = auth.jwt() ->> 'sub'
  );

drop policy if exists avatars_update_self on storage.objects;
create policy avatars_update_self on storage.objects
  for update to public
  using (
    bucket_id = 'avatars'
    and split_part(name, '.', 1) = auth.jwt() ->> 'sub'
  )
  with check (
    bucket_id = 'avatars'
    and split_part(name, '.', 1) = auth.jwt() ->> 'sub'
  );


-- ---- EVENT-COVERS ----------------------------------------------------------
-- Public read.
drop policy if exists covers_read on storage.objects;
create policy covers_read on storage.objects
  for select to public
  using (bucket_id = 'event-covers');

-- Admins upload / overwrite / delete covers.
drop policy if exists covers_insert_admin on storage.objects;
create policy covers_insert_admin on storage.objects
  for insert to public
  with check (bucket_id = 'event-covers' and public.is_admin());

drop policy if exists covers_update_admin on storage.objects;
create policy covers_update_admin on storage.objects
  for update to public
  using (bucket_id = 'event-covers' and public.is_admin())
  with check (bucket_id = 'event-covers' and public.is_admin());

drop policy if exists covers_delete_admin on storage.objects;
create policy covers_delete_admin on storage.objects
  for delete to public
  using (bucket_id = 'event-covers' and public.is_admin());


-- ---- CERTIFICATES ----------------------------------------------------------
-- Public read (certificate public URLs are stored on the registration row).
drop policy if exists certs_read on storage.objects;
create policy certs_read on storage.objects
  for select to public
  using (bucket_id = 'certificates');

-- Admins upload / overwrite / delete certificates.
drop policy if exists certs_insert_admin on storage.objects;
create policy certs_insert_admin on storage.objects
  for insert to public
  with check (bucket_id = 'certificates' and public.is_admin());

drop policy if exists certs_update_admin on storage.objects;
create policy certs_update_admin on storage.objects
  for update to public
  using (bucket_id = 'certificates' and public.is_admin())
  with check (bucket_id = 'certificates' and public.is_admin());

drop policy if exists certs_delete_admin on storage.objects;
create policy certs_delete_admin on storage.objects
  for delete to public
  using (bucket_id = 'certificates' and public.is_admin());

-- ============================================================================
--  END. `public.is_admin()` is defined in rls_policies.sql — run that first.
-- ============================================================================
