ALTER TABLE study_groups ADD COLUMN created_by BIGINT REFERENCES users(id);

UPDATE study_groups sg
SET created_by = (
    SELECT gm.user_id FROM group_members gm WHERE gm.group_id = sg.id ORDER BY gm.user_id LIMIT 1
)
WHERE created_by IS NULL;

-- Fallback for any pre-existing group left with zero members (possible before the
-- last-member removal guard existed): attribute it to the earliest registered user
-- rather than fail the NOT NULL migration below.
UPDATE study_groups
SET created_by = (SELECT id FROM users ORDER BY id LIMIT 1)
WHERE created_by IS NULL;

ALTER TABLE study_groups ALTER COLUMN created_by SET NOT NULL;
