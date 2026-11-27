ALTER TABLE kb_release_item
    ADD COLUMN parameter_versions jsonb NOT NULL DEFAULT '{}',
    ADD COLUMN approval_kind text CHECK (approval_kind IN ('real', 'prueba')),
    ADD COLUMN approved_at timestamptz;
