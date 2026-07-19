# EVANTA — Security Fix (Free Path) · Step-by-Step Guide

**Goal:** stop anyone who extracts the anon key from the APK from writing or
deleting your data. We do this with **Row-Level Security (RLS)**: the database
itself refuses writes unless the request carries a valid **Firebase ID token**
whose UID matches the row (or belongs to an admin).

This is the **free path** — no Firebase Blaze plan, no Cloud Functions. Because
we skipped the paid custom-claim step, the database role stays `anon`, and the
policies identify you by `auth.jwt() ->> 'sub'` (your Firebase UID) instead.

> **The anon key is still visible in the APK — and that's fine.** Hiding it in a
> mobile app is impossible. RLS is what actually protects you: the key alone can
> now only *read* public data, not write or delete.

---

## What I already changed in the app code

All reversible, all currently **inert** (flag defaults off):

- `SupabaseConfig.java` — added `USE_FIREBASE_AUTH = false`.
- `AuthTokens.java` (new) — picks the Bearer token: Firebase ID token when
  signed in and the flag is on, else the anon key. Caches the token (~1h).
- `RetrofitClient.java` + all storage uploads/deletes (avatars, event-covers,
  certificates) — now send `Authorization: Bearer <AuthTokens.bearer()>`.
  The `apikey` header is unchanged (always the anon key).

**With the flag off, the app behaves exactly as before.** Nothing breaks until
you both flip the flag *and* apply the SQL.

---

## The order that keeps you from getting locked out

Do these **in order**. Do not run the SQL first.

### Step 1 — Register Firebase as a Third-Party Auth provider (Supabase)
1. Supabase dashboard → **Authentication** → **Sign In / Providers** (or
   **Third-Party Auth**, depending on dashboard version).
2. Add provider → **Firebase**.
3. Enter your Firebase **Project ID**: `evanta-...` (from Firebase console →
   Project settings → General → Project ID).
4. Save. This tells Supabase to trust ID tokens issued by your Firebase project
   and to populate `auth.jwt()` from them.

### Step 2 — Turn on the flag and verify the token is seen (BEFORE any RLS)
1. In `SupabaseConfig.java` set `USE_FIREBASE_AUTH = true`. Rebuild, install.
2. Sign in to the app (student or admin — a real Firebase login).
3. Confirm the app still works normally. **RLS is not on yet**, so every write
   should still succeed exactly as before. If the app breaks here, the token
   wiring or provider registration is wrong — fix that before going further.
4. **Verify Supabase actually sees your identity.** Easiest way: temporarily add
   this call somewhere you can trigger (or use the app's normal traffic) and
   check that authenticated requests carry the token. The definitive test is the
   SQL check — but that only returns your UID when *the app* calls it, because
   the token comes from the app. Practical proof for now: everything works with
   the flag on. The real gate is Step 3's dry-run.

### Step 3 — Apply RLS to ONE table first (dry run)
Don't enable everything at once. Prove the mechanism on the least-risky table.

1. Supabase → **SQL Editor**. Run **only** the two helper functions and the
   **NOTIFICATIONS** block from `rls_policies.sql` (copy just those lines).
2. In the app, open notifications, mark one read, or trigger an approve (which
   inserts a notification). 
   - **If it works:** the token is being verified and `sub` matching works. 
   - **If writes now fail with 401/permission denied:** the provider isn't
     verifying tokens. **Roll back** immediately:
     ```sql
     alter table public.notifications disable row level security;
     ```
     Then revisit Step 1.

### Step 4 — Apply the rest
Once the dry run passes, run the **whole** `rls_policies.sql`, then
`storage_policies.sql`. Test each surface:

- [ ] Student: browse events (read) ✓
- [ ] Student: register for an event ✓
- [ ] Student: edit own profile + upload avatar ✓
- [ ] Student: mark notification read ✓
- [ ] Admin: create an event + upload cover ✓
- [ ] Admin: edit / delete own-college event ✓
- [ ] Admin: approve/reject a registration (updates reg + sends notification) ✓
- [ ] Admin: upload a certificate ✓
- [ ] Admin: upload/change own avatar ✓

Any failure → roll back that table (lines at top of `rls_policies.sql`), tell me
which surface failed, and I'll adjust the policy.

---

## If everything is locked and you panic

Paste these five lines in the SQL Editor — instantly reopens all writes
(reverts to today's behaviour). The app keeps working because the flag path
falls back gracefully.

```sql
alter table public.users          disable row level security;
alter table public.events         disable row level security;
alter table public.registrations  disable row level security;
alter table public.notifications  disable row level security;
alter table public.colleges       disable row level security;
```

For storage, in Supabase → Storage → each bucket → Policies, delete the write
policies (or set the bucket public again).

---

## What this does and does NOT protect

**Protects (after Step 4):**
- A stranger with the extracted anon key can no longer insert/update/delete
  rows or upload/delete files. Writes require a real Firebase login, and the
  server checks that the caller owns the row (or is an admin).

**Still open by design:**
- **Reads.** Public event listings, and name/profile resolution, stay readable.
  That's intentional for a browse app. If you later want to hide student PII,
  we tighten the `*_select_all` policies — tell me and I'll write those.

**Not covered by the free path:**
- The paid "authenticated role" upgrade (Blaze + Cloud Function custom claim).
  You declined it; the `sub`-matching policies above achieve the same access
  control without it.

---

## Files in this bundle
- `rls_policies.sql` — table RLS (run first).
- `storage_policies.sql` — bucket RLS (run second; needs `is_admin()` from the
  first file).
- `SECURITY_GUIDE.md` — this file.
