-- Removes duplicated revision rows.
--
-- Concurrent saves of the same resource could allocate the same revision number before the
-- revision history was serialized with a database lock. The newest row of every duplicated
-- revision number is kept, the older ones are removed.
DELETE FROM config_revisions
WHERE id NOT IN (
    SELECT keep_id FROM (
        SELECT MAX(id) AS keep_id
        FROM config_revisions
        GROUP BY organization_id, resource_type, resource_id, revision_number
    ) AS duplicated_revisions
);
