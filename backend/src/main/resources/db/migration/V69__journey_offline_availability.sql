-- Existing Journeys require explicit teacher opt-in. New Journeys allow optional downloads.
ALTER TABLE journeys ADD COLUMN offline_allowed boolean NOT NULL DEFAULT false;
ALTER TABLE journeys ALTER COLUMN offline_allowed SET DEFAULT true;
