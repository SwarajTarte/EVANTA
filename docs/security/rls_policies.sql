-- ============================================================================
--  EVANTA · Row-Level Security (RLS) — FREE PATH (Firebase third-party auth)
-- ============================================================================
--  Run this in Supabase → SQL Editor.
--
--  WHAT THIS DOES
--    Locks down INSERT / UPDATE / DELETE on every table so a request must
--    carry a valid Firebase ID token whose UID matches the row it touches
--    (or belongs to an admin). SELECT (reads) stay open so the public browse
--    page and name-resolution keep working.
--
--  HOW CALLER IDENTITY WORKS ON THE FREE PATH
--    The app sends the Firebase ID token as `Authorization: Bearer`.
--    Supabase (with Firebase registered as a Third-Party Auth provider)
--    verifies it and exposes:  auth.jwt() ->> 'sub'  =  the Firebase UID.
--    The DB role stays `anon` (we did NOT add the paid custom-claim step),
--    so EVERY policy below is granted `TO public` and gates on the JWT sub.
--
--  ⚠ DO NOT RUN THIS until you have:
--     1. Registered Firebase as a Third-Party Auth provider in Supabase.
--     2. Flipped USE_FIREBASE_AUTH = true in SupabaseConfig.java, rebuilt,
--        signed in, and confirmed the PRE-RLS CHECK below returns your UID.
--    Running it before that will lock the app out of all writes.
--
--  ROLLBACK (paste any one line if something breaks — instantly reopens):
--     alter table public.users          disable row level security;
--     alter table public.events         disable row level security;
--     alter table public.registrations  disable row level security;
--     alter table public.notifications  disable row level security;
--     alter table public.colleges       disable row level security;
-- ============================================================================


-- ---------------------------------------------------------------------------
--  PRE-RLS CHECK — run this ALONE first, while signed in through the app
--  with USE_FIREBASE_AUTH = true. It must return your Firebase UID, NOT null.
--  (You run it from the app, not here — see the guide. Shown for reference.)
--
--     select auth.jwt() ->> 'sub' as firebase_uid;
--
--  If that is null when the app calls it, STOP. RLS would lock you out.
-- ---------------------------------------------------------------------------


-- Helper: is the current caller an admin?  (SECURITY DEFINER so it can read
-- the users table regardless of that table's own RLS.)
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.users u
    where u.uid::text = auth.jwt() ->> 'sub'
      and u.role = 'admin'
  );
$$;

-- Helper: the caller's college_id (for scoping admin writes to their college).
create or replace function public.my_college_id()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select u.college_id::text from public.users u
  where u.uid::text = auth.jwt() ->> 'sub'
  limit 1;
$$;


-- ============================ USERS =========================================
alter table public.users enable row level security;

-- Read: open (needed to resolve registrant names, profiles).
drop policy if exists users_select_all on public.users;
create policy users_select_all on public.users
  for select to public using (true);

-- Insert: only your own row (login upsert writes uid = your Firebase UID).
drop policy if exists users_insert_self on public.users;
create policy users_insert_self on public.users
  for insert to public
  with check (uid = auth.jwt() ->> 'sub');

-- Update: only your own row.
drop policy if exists users_update_self on public.users;
create policy users_update_self on public.users
  for update to public
  using (uid = auth.jwt() ->> 'sub')
  with check (uid = auth.jwt() ->> 'sub');

-- (No delete policy = nobody can delete user rows via the API.)


-- ============================ EVENTS ========================================
alter table public.events enable row level security;

-- Read: open (public browse page).
drop policy if exists events_select_all on public.events;
create policy events_select_all on public.events
  for select to public using (true);

-- Insert: admins only, and the new event must belong to the admin's college.
drop policy if exists events_insert_admin on public.events;
create policy events_insert_admin on public.events
  for insert to public
  with check (public.is_admin() and college_id = public.my_college_id());

-- Update: admins, only events in their own college.
drop policy if exists events_update_admin on public.events;
create policy events_update_admin on public.events
  for update to public
  using (public.is_admin() and college_id = public.my_college_id())
  with check (public.is_admin() and college_id = public.my_college_id());

-- Delete: admins, only events in their own college.
drop policy if exists events_delete_admin on public.events;
create policy events_delete_admin on public.events
  for delete to public
  using (public.is_admin() and college_id = public.my_college_id());


-- ========================= REGISTRATIONS ====================================
alter table public.registrations enable row level security;

-- Read: open (admin sees registrants for their events; student sees own).
drop policy if exists regs_select_all on public.registrations;
create policy regs_select_all on public.registrations
  for select to public using (true);

-- Insert: a student may only register THEMSELVES.
drop policy if exists regs_insert_self on public.registrations;
create policy regs_insert_self on public.registrations
  for insert to public
  with check (user_uid = auth.jwt() ->> 'sub');

-- Update: the owning student (e.g. cancel) OR any admin (approve/reject,
-- attach certificate_url).
drop policy if exists regs_update_self_or_admin on public.registrations;
create policy regs_update_self_or_admin on public.registrations
  for update to public
  using (user_uid = auth.jwt() ->> 'sub' or public.is_admin())
  with check (user_uid = auth.jwt() ->> 'sub' or public.is_admin());

-- Delete: the owning student OR any admin.
drop policy if exists regs_delete_self_or_admin on public.registrations;
create policy regs_delete_self_or_admin on public.registrations
  for delete to public
  using (user_uid = auth.jwt() ->> 'sub' or public.is_admin());


-- ========================= NOTIFICATIONS ====================================
alter table public.notifications enable row level security;

-- Read: only your own notifications.
drop policy if exists notif_select_own on public.notifications;
create policy notif_select_own on public.notifications
  for select to public
  using (user_uid = auth.jwt() ->> 'sub');

-- Insert: any signed-in user. (Admins insert notifications addressed to
-- students on approve/reject; students may create their own.) The gate is
-- simply "a valid Firebase token is present."
drop policy if exists notif_insert_signed_in on public.notifications;
create policy notif_insert_signed_in on public.notifications
  for insert to public
  with check ((auth.jwt() ->> 'sub') is not null);

-- Update: only your own (mark as read).
drop policy if exists notif_update_own on public.notifications;
create policy notif_update_own on public.notifications
  for update to public
  using (user_uid = auth.jwt() ->> 'sub')
  with check (user_uid = auth.jwt() ->> 'sub');

-- Delete: only your own.
drop policy if exists notif_delete_own on public.notifications;
create policy notif_delete_own on public.notifications
  for delete to public
  using (user_uid = auth.jwt() ->> 'sub');


-- ============================ COLLEGES ======================================
alter table public.colleges enable row level security;

-- Read: open (needed at signup / college pickers).
drop policy if exists colleges_select_all on public.colleges;
create policy colleges_select_all on public.colleges
  for select to public using (true);

-- Insert / Update / Delete: admins only.
drop policy if exists colleges_insert_admin on public.colleges;
create policy colleges_insert_admin on public.colleges
  for insert to public with check (public.is_admin());

drop policy if exists colleges_update_admin on public.colleges;
create policy colleges_update_admin on public.colleges
  for update to public
  using (public.is_admin()) with check (public.is_admin());

drop policy if exists colleges_delete_admin on public.colleges;
create policy colleges_delete_admin on public.colleges
  for delete to public using (public.is_admin());

-- ============================================================================
--  END. Storage bucket policies (avatars, event-covers, certificates) are
--  managed separately in Supabase → Storage → Policies. See the guide.
-- ============================================================================
