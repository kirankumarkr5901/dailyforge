-- Referral requests get a clock of their own (owner request).
--
-- JobSource has had REFERRAL_REQUESTED since M7, but it was only a label: an
-- application marked that way sat in the list next to forty others, and the one
-- question a referral actually raises — "how long have I been waiting on my referrer,
-- and should I just apply myself?" — had nowhere to be answered from.
--
-- Two columns answer it. referral_requested_on is the day the ask was made, which is
-- deliberately not applied_on: you ask for a referral before you apply, and the whole
-- point of tracking it is the gap between the two. referral_id is whatever handle the
-- referrer or the company's portal gave you, which you have to paste into the
-- application form later and which is otherwise lost in a chat thread.
--
-- The waiting state (fresh / follow up / apply anyway) is derived from
-- referral_requested_on in the user's own time zone, never stored — a stored state
-- would be wrong the moment a day passed with nobody writing to the row.
ALTER TABLE job_application ADD COLUMN referral_id VARCHAR(80);
ALTER TABLE job_application ADD COLUMN referral_requested_on DATE;

-- The referral list is read by user and ordered by how long each has been waiting.
CREATE INDEX idx_job_application_referral ON job_application (user_id, referral_requested_on);
