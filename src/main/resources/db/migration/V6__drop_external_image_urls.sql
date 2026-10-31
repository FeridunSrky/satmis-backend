-- Listings now only use images uploaded to object storage. Images given as external https URLs were test
-- data; they are removed, so those listings show no image until their owner uploads new ones.

DELETE FROM listing_images WHERE url IS NOT NULL;

ALTER TABLE listing_images DROP CONSTRAINT ck_listing_images_source;
ALTER TABLE listing_images DROP CONSTRAINT ck_listing_images_uploaded_file;
ALTER TABLE listing_images DROP COLUMN url;

ALTER TABLE listing_images ALTER COLUMN object_key SET NOT NULL;
ALTER TABLE listing_images ALTER COLUMN image_type SET NOT NULL;
ALTER TABLE listing_images ALTER COLUMN size_bytes SET NOT NULL;
