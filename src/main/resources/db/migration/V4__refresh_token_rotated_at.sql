-- Set when a refresh token is rotated; a replay shortly afterwards is another tab, not theft
ALTER TABLE refresh_tokens ADD COLUMN rotated_at timestamp(6) with time zone;
