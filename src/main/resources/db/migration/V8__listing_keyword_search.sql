-- Keyword search: case and Turkish-accent insensitive ("IŞIK" = "ışık" = "isik").
-- No stemmer on purpose: Turkish stemming mangles vehicle names; queries use prefix matching instead.
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TEXT SEARCH CONFIGURATION satmis_search (COPY = simple);
ALTER TEXT SEARCH CONFIGURATION satmis_search
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, simple;

-- Written by the application (brand, model, series, title, description) because the names live in other tables
ALTER TABLE listings ADD COLUMN search_text text;

UPDATE listings l
SET search_text = concat_ws(' ', b.name, m.name, s.name, l.title, l.description)
FROM vehicle_details vd
         JOIN vehicle_series s ON s.id = vd.series_id
         JOIN vehicle_models m ON m.id = s.model_id
         JOIN brands b ON b.id = m.brand_id
WHERE vd.listing_id = l.id;

ALTER TABLE listings ALTER COLUMN search_text SET NOT NULL;

-- The expression must match the one rendered by ListingSearchFunctions, or the index is not used
CREATE INDEX idx_listings_search ON listings USING gin (to_tsvector('satmis_search'::regconfig, search_text));
