-- Gives an applicant a way back into their own application, and stops the id being the credential.
--
-- TWO PROBLEMS, ONE MECHANISM.
--
-- 1. There was no return path at all. The only retrieval was GET /applications/{uuid}, the wizard
--    never persisted that uuid, and the reference number shown to the applicant was accepted by no
--    endpoint. Closing the tab orphaned the draft — and because assertNoOpenApplication refuses a
--    second application unless the first is DENIED or MATRICULATED, the applicant was then locked
--    out of that programme and term entirely, by their own lost draft, with no way to recover it.
--
-- 2. The id WAS the credential. GET/PATCH/submit/documents were all reachable by anyone holding it,
--    and a uuid in a URL path is treated as non-secret by the whole stack: browser history, Referer
--    headers, access logs, CDN logs, and anything the applicant pastes into a support ticket.
--
-- A capability token fixes both: the applicant keeps something they can come back with, and it
-- travels in a header instead of a URL.
--
-- ONLY THE HASH IS STORED. A stolen database backup then yields no usable tokens. SHA-256 rather
-- than bcrypt is deliberate and is the right choice here: slow hashing exists to frustrate offline
-- brute force against LOW-entropy secrets. These tokens are 256 bits from a CSPRNG, so guessing is
-- already infeasible, and a fast digest keeps verification cheap on every request.

ALTER TABLE applications
    ADD COLUMN access_token_hash       VARCHAR(64),
    ADD COLUMN access_token_expires_at TIMESTAMPTZ,
    ADD COLUMN last_accessed_at        TIMESTAMPTZ;

-- Verification looks the token up directly; without this every request would scan the table, which
-- turns an authentication check into a table scan an anonymous caller can trigger at will.
CREATE UNIQUE INDEX uk_applications_access_token_hash
    ON applications (access_token_hash)
    WHERE access_token_hash IS NOT NULL;

-- Nullable, because rows that predate this migration have no token. They are reachable by staff
-- through the admissions queue, which is the correct recovery path for them; backfilling tokens
-- nobody was ever given would create credentials with no owner.
COMMENT ON COLUMN applications.access_token_hash IS
    'SHA-256 of the applicant capability token. Never the token itself.';
COMMENT ON COLUMN applications.access_token_expires_at IS
    'Rotated and extended whenever the applicant resumes; expiry bounds a leaked link.';
