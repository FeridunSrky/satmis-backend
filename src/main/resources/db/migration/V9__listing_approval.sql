-- Admin approval: new listings and edited content wait in PENDING_APPROVAL; an admin approves (ACTIVE) or
-- rejects (REJECTED, with a reason the owner sees). Existing listings keep their status.
ALTER TABLE listings DROP CONSTRAINT listings_status_check;
ALTER TABLE listings ADD CONSTRAINT listings_status_check
    CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'PASSIVE', 'SOLD', 'REJECTED'));

ALTER TABLE listings ADD COLUMN rejection_reason varchar(500);
ALTER TABLE listings ADD CONSTRAINT ck_listings_rejection_reason
    CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL));
