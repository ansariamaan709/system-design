-- Migration script to update short_code column from VARCHAR(10) to VARCHAR(20)
-- Run this script on your existing database

-- Update urls table
ALTER TABLE urls ALTER COLUMN short_code TYPE VARCHAR(20);

-- Update click_events table
ALTER TABLE click_events ALTER COLUMN short_code TYPE VARCHAR(20);

-- Verify the changes
SELECT 
    table_name, 
    column_name, 
    data_type, 
    character_maximum_length
FROM information_schema.columns
WHERE table_name IN ('urls', 'click_events') 
AND column_name = 'short_code';
