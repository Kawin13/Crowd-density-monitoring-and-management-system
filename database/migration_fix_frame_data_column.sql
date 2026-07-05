-- =============================================================================
-- Migration: fix_frame_data_column_size.sql
--
-- WHY THIS IS NEEDED:
-- The CrowdData JPA entity originally declared `@Lob private byte[] frameData`
-- with no explicit columnDefinition. Hibernate's default mapping for that
-- combination on MySQL is BLOB (64 KB max), not LONGBLOB (4 GB max).
-- Because spring.jpa.hibernate.ddl-auto=update is enabled in
-- application.properties, Hibernate re-validated and ALTERed the column to
-- its own default (BLOB) on every backend startup — even though schema.sql
-- originally created it as LONGBLOB. Any database that has already been
-- started with the old entity code may currently have frame_data/heatmap_data
-- as BLOB instead of LONGBLOB.
--
-- The entity has now been fixed to explicitly pin columnDefinition = "LONGBLOB",
-- so future ddl-auto=update runs will keep it correct. Run this script once
-- against your EXISTING database to immediately restore the correct column
-- size without needing to drop and recreate the table.
--
-- Safe to run multiple times — MODIFY COLUMN is idempotent.
-- =============================================================================

USE crowd_monitoring;

ALTER TABLE crowd_data
    MODIFY COLUMN frame_data   LONGBLOB,
    MODIFY COLUMN heatmap_data LONGBLOB;

-- Verify the fix:
-- SHOW COLUMNS FROM crowd_data WHERE Field IN ('frame_data', 'heatmap_data');
-- Expected: Type = longblob for both rows
