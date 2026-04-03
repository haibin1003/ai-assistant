-- Add Basic Auth columns to t_session table
ALTER TABLE t_session
    ADD COLUMN auth_username VARCHAR(128) COMMENT 'Basic Auth username',
    ADD COLUMN auth_password TEXT COMMENT 'Basic Auth password (encrypted)';
