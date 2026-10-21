-- Non-sensitive display hint (public prefix + last four characters) so the UI can help
-- owners identify an issued key without persisting the plaintext. Rows created before this
-- migration keep NULL: their plaintext keys are not recoverable and must be regenerated
-- before a hint can be shown.
ALTER TABLE applications ADD COLUMN api_key_hint VARCHAR(32) NULL;
