CREATE TABLE users (
    id UUID PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    uniqueness_field VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX ux_users_external_id ON users(external_id);
CREATE UNIQUE INDEX ux_users_uniqueness_field ON users(uniqueness_field);
CREATE TABLE audit_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(32) NOT NULL,
    result VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32),
    new_status VARCHAR(32),
    changed_fields VARCHAR(1024),
    actor VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
