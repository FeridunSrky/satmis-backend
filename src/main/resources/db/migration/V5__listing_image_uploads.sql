-- Images are uploaded to object storage before the listing is saved, so a row now lives on its own:
-- PENDING (upload URL issued) -> UPLOADED (file checked) -> ATTACHED (part of a listing) -> DELETED (file to remove).
-- External https URLs stay readable until the next migration replaces them with uploaded files.

ALTER TABLE listing_images ADD COLUMN owner_id bigint CONSTRAINT fk_listing_images_owner REFERENCES users (id);
UPDATE listing_images image SET owner_id = listing.user_id FROM listings listing WHERE listing.id = image.listing_id;
ALTER TABLE listing_images ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE listing_images ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ATTACHED'
    CHECK (status IN ('PENDING', 'UPLOADED', 'ATTACHED', 'DELETED'));
ALTER TABLE listing_images ALTER COLUMN status DROP DEFAULT;

ALTER TABLE listing_images ADD COLUMN object_key varchar(200) CONSTRAINT uq_listing_images_object_key UNIQUE;
ALTER TABLE listing_images ADD COLUMN image_type varchar(10) CHECK (image_type IN ('JPEG', 'PNG', 'WEBP'));
ALTER TABLE listing_images ADD COLUMN size_bytes bigint;

ALTER TABLE listing_images ADD COLUMN created_at timestamp(6) with time zone NOT NULL DEFAULT now();
ALTER TABLE listing_images ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE listing_images ADD COLUMN updated_at timestamp(6) with time zone;

ALTER TABLE listing_images ALTER COLUMN listing_id DROP NOT NULL;
ALTER TABLE listing_images ALTER COLUMN display_order DROP NOT NULL;
ALTER TABLE listing_images ALTER COLUMN url DROP NOT NULL;

ALTER TABLE listing_images ADD CONSTRAINT ck_listing_images_attached
    CHECK ((status = 'ATTACHED') = (listing_id IS NOT NULL AND display_order IS NOT NULL));
ALTER TABLE listing_images ADD CONSTRAINT ck_listing_images_source
    CHECK ((url IS NULL) <> (object_key IS NULL));
ALTER TABLE listing_images ADD CONSTRAINT ck_listing_images_uploaded_file
    CHECK (object_key IS NULL OR (image_type IS NOT NULL AND size_bytes IS NOT NULL));

-- Per-user limit on images not yet in a listing, and the cleanup of abandoned or deleted ones
CREATE INDEX idx_listing_images_owner_status ON listing_images (owner_id, status);
CREATE INDEX idx_listing_images_status_created_at ON listing_images (status, created_at);
