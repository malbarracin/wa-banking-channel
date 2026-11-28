DROP INDEX ux_users_uniqueness_field;
ALTER TABLE users ALTER COLUMN uniqueness_field DROP NOT NULL;
CREATE TABLE user_creation_lock (lock_id INTEGER PRIMARY KEY);
INSERT INTO user_creation_lock (lock_id) VALUES (1);
