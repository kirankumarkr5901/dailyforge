-- Reward stock becomes a recurring allowance instead of a dwindling total (owner request).
--
-- The tiers already said what they meant — MICRO is "a small daily treat", WEEKLY and
-- MONTHLY are progressively bigger splurges — but stock did not follow: it was a single
-- pool that counted down to zero once and stayed there. A daily treat you could take
-- three times ever is not a daily treat.
--
-- Stock now means "how many times per period", and the period comes from the tier:
-- MICRO refreshes every day, WEEKLY every Monday, MONTHLY on the 1st.
--
-- Nothing resets anything. There is no job, no scheduled sweep and no stored counter,
-- because the remaining count is derived on read: the allowance minus the non-refunded
-- redemptions that fall inside the current period. That is the same shape as every
-- other derived figure in this app (PRs, the heatmap, goal progress, badges), and it is
-- specifically what keeps this correct on a host that sleeps — a reset job would simply
-- miss the window, exactly the failure the daily rollover needed a watermark to avoid.
--
-- One consequence worth stating: because remaining is derived, a refund restores the
-- allowance for free. The redemption stops counting the moment it is refunded, with no
-- second write to keep in step.

-- Existing rows hold what is *left* of a one-off pool, not the allowance it started as.
-- The allowance is recoverable exactly: every non-refunded redemption took one from it,
-- and every refund already gave one back, so the original is the current value plus the
-- redemptions still standing against it.
UPDATE reward r
SET stock = r.stock + (
    SELECT COUNT(*)
    FROM reward_redemption rr
    WHERE rr.reward_id = r.id
      AND rr.refunded_at IS NULL
)
WHERE r.stock IS NOT NULL;
