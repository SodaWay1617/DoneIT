-- Manual seed for the initial single-user MVP account.
-- Replace login, display name, and password hash before any real use.

INSERT INTO users (login, password_hash, display_name, created_at, updated_at)
SELECT
    'doneit',
    '$2a$10$9vodoPiyjy6ExZjs402LEenBewNDSbndh3.oMeYlnzUIPnHVvoJ6u',
    'DoneIt Admin',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE login = 'doneit'
);
