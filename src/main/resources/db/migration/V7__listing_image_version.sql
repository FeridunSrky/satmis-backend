-- Optimistic locking: the cleanup job marks abandoned images DELETED in a bulk update that bumps the version,
-- so a listing save that read the image just before fails instead of attaching an image whose file is removed.
ALTER TABLE listing_images ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE listing_images ALTER COLUMN version DROP DEFAULT;
