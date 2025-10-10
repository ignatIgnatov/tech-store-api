-- V4__allow_guest_orders.sql

-- Премахни NOT NULL constraint от user_id (само ако съществува)
DO $$
BEGIN
    -- Проверяваме дали колоната е NOT NULL
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'orders'
        AND column_name = 'user_id'
        AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE orders ALTER COLUMN user_id DROP NOT NULL;
    END IF;
END $$;

-- Премахни съществуващия foreign key constraint
ALTER TABLE orders
DROP CONSTRAINT IF EXISTS fk_order_user;

-- Създай отново constraint-а (позволява NULL стойности)
ALTER TABLE orders
ADD CONSTRAINT fk_order_user
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

-- Добави коментар
COMMENT ON COLUMN orders.user_id IS 'User ID (nullable for guest orders)';