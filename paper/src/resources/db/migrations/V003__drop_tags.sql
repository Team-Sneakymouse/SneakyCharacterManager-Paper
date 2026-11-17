-- requires-if-present: sneakyvariables >= 2
-- Character tags are gone from SneakyCharacterManager. The two in use (paladin, badge_number) are SneakyVariables
-- character variables now; SneakyVariables' V002__scm_character_tags.sql copies them, and the requires line above makes
-- that run first. Breaking: SneakyCharacterManager jars older than this one write the column.

ALTER TABLE scm.characters DROP COLUMN tags;
