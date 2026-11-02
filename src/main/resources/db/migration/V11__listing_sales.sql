-- The owner records the sale price and an optional note when marking a listing sold; sold listings feed the public
-- sold vehicles page. An admin can hide a sale whose price looks made up (the listing stays SOLD).
-- Listings marked sold before this have no sale price: they go back to PASSIVE and can be marked sold again.
UPDATE listings SET status = 'PASSIVE' WHERE status = 'SOLD';

ALTER TABLE listings ADD COLUMN sale_price  numeric(15, 2);
ALTER TABLE listings ADD COLUMN sale_note   varchar(1000);
ALTER TABLE listings ADD COLUMN sold_at     timestamp(6) with time zone;
ALTER TABLE listings ADD COLUMN sale_hidden boolean NOT NULL DEFAULT false;

-- Sale fields exist exactly while SOLD; relisting clears them
ALTER TABLE listings ADD CONSTRAINT ck_listings_sale
    CHECK ((status = 'SOLD') = (sale_price IS NOT NULL)
        AND (status = 'SOLD') = (sold_at IS NOT NULL)
        AND (status = 'SOLD' OR (sale_note IS NULL AND NOT sale_hidden)));

CREATE INDEX idx_listings_sold_at ON listings (sold_at) WHERE status = 'SOLD';
