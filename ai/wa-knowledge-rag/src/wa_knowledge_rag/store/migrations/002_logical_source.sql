-- Upgrade the packaged schema without changing an already applied migration.
ALTER TABLE kb_source ADD COLUMN source_id UUID;
-- Legacy revisions were grouped by title. Preserve that identity on upgrade.
UPDATE kb_source s SET source_id = (
    SELECT first.id FROM kb_source first
    WHERE first.title = s.title ORDER BY first.version, first.id LIMIT 1
);
ALTER TABLE kb_source ALTER COLUMN source_id SET NOT NULL;
ALTER TABLE kb_source DROP CONSTRAINT kb_source_title_version_key;
ALTER TABLE kb_source ADD UNIQUE (source_id, version);
UPDATE kb_source s SET status = 'retired'
WHERE s.status = 'approved' AND EXISTS (
    SELECT 1 FROM kb_source newer WHERE newer.source_id = s.source_id
    AND newer.status = 'approved' AND newer.version > s.version
);
CREATE UNIQUE INDEX kb_source_current ON kb_source(source_id) WHERE status = 'approved';
