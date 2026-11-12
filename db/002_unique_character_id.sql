-- SCM schema addition: character_id is unique across ALL owners, including literal owner 'template'.
-- Operator-applied after 001_scm.sql. The plugin NEVER runs this file.
-- Additive only: scm.schema_version stays 1, so the plugin and importer accept the database with or
-- without this index. Writers still check for cross-owner clashes themselves (template saves are
-- refused, not failed); this index is the guarantee behind those checks.
-- If duplicates already exist, CREATE UNIQUE INDEX fails and the transaction changes nothing.
-- List them first with:
--   SELECT character_id, array_agg(owner_id) FROM scm.characters GROUP BY character_id HAVING count(*) > 1;
BEGIN;
CREATE UNIQUE INDEX characters_character_id_key ON scm.characters (character_id);
COMMIT;
