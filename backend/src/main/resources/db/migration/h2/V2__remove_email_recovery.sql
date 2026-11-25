-- Preserve the original migration checksum while removing the obsolete recovery table.
DROP TABLE IF EXISTS password_resets;
