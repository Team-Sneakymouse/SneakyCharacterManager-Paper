-- Breaking: identity columns become uuid; template owner and named template ids are removed.
-- Empty last_played_character becomes NULL. skin_uuid stays text (MineSkin opaque ids).
-- Non-UUID owner_id / character_id values fail the cast and roll this script back.

DELETE FROM scm.characters WHERE owner_id = 'template';
DELETE FROM scm.owners WHERE owner_id = 'template';

ALTER TABLE scm.characters DROP CONSTRAINT characters_owner_id_fkey;
DROP INDEX scm.characters_character_id_key;

ALTER TABLE scm.owners
    ALTER COLUMN last_played_character DROP DEFAULT,
    ALTER COLUMN last_played_character TYPE uuid USING NULLIF(last_played_character, '')::uuid,
    ALTER COLUMN owner_id TYPE uuid USING owner_id::uuid;

ALTER TABLE scm.characters
    ALTER COLUMN owner_id TYPE uuid USING owner_id::uuid,
    ALTER COLUMN character_id TYPE uuid USING character_id::uuid;

ALTER TABLE scm.characters
    ADD CONSTRAINT characters_owner_id_fkey
    FOREIGN KEY (owner_id) REFERENCES scm.owners (owner_id);

CREATE UNIQUE INDEX characters_character_id_key ON scm.characters (character_id);

DROP TABLE scm.schema_version;
