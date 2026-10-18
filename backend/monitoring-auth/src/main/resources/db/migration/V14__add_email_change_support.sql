-- Email-change support for the identity module (control plane).
-- Schema is owned by Flyway; Hibernate only validates the mapping.

-- A pending email change is carried by a single-use EMAIL_CHANGE token: the target address
-- is stored on the token itself so the confirmation link alone can apply it, without a
-- separate pending-email column on users.
ALTER TABLE auth_tokens ADD COLUMN new_email VARCHAR(255) NULL;
