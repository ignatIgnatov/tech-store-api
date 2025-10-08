-- V4__add_speedy_fields_to_orders.sql

-- ============================================================
-- ADD SPEEDY FIELDS TO ORDERS TABLE
-- ============================================================

-- Add shipping method field - using VARCHAR to match Hibernate enum mapping
ALTER TABLE orders
ADD COLUMN shipping_method VARCHAR(30);

-- Add Speedy specific fields
ALTER TABLE orders
ADD COLUMN shipping_speedy_site_id BIGINT;

ALTER TABLE orders
ADD COLUMN shipping_speedy_office_id BIGINT;

ALTER TABLE orders
ADD COLUMN shipping_speedy_site_name VARCHAR(255);

ALTER TABLE orders
ADD COLUMN shipping_speedy_office_name VARCHAR(255);

-- Add comments for new fields
COMMENT ON COLUMN orders.shipping_method IS 'Shipping method: SPEEDY, FREE, STANDARD, EXPRESS';
COMMENT ON COLUMN orders.shipping_speedy_site_id IS 'Speedy site ID (city ID) for delivery';
COMMENT ON COLUMN orders.shipping_speedy_office_id IS 'Speedy office ID for office delivery';
COMMENT ON COLUMN orders.shipping_speedy_site_name IS 'Speedy site name (city name)';
COMMENT ON COLUMN orders.shipping_speedy_office_name IS 'Speedy office name';

-- Create indexes for better performance
CREATE INDEX idx_orders_shipping_method ON orders(shipping_method);
CREATE INDEX idx_orders_speedy_site_id ON orders(shipping_speedy_site_id);
CREATE INDEX idx_orders_speedy_office_id ON orders(shipping_speedy_office_id);

-- ============================================================
-- UPDATE EXISTING ORDERS WITH DEFAULT VALUES
-- ============================================================

-- Set default shipping method for existing orders
UPDATE orders
SET shipping_method = 'STANDARD'
WHERE shipping_method IS NULL;

-- ============================================================
-- VALIDATION QUERIES
-- ============================================================

DO $$
DECLARE
    total_orders INTEGER;
    orders_with_speedy_fields INTEGER;
BEGIN
    -- Count total orders
    SELECT COUNT(*) INTO total_orders FROM orders;

    -- Count orders that have the new fields populated
    SELECT COUNT(*) INTO orders_with_speedy_fields
    FROM orders
    WHERE shipping_method IS NOT NULL;

    RAISE NOTICE 'Speedy fields migration completed:';
    RAISE NOTICE '  Total orders: %', total_orders;
    RAISE NOTICE '  Orders with shipping method: %', orders_with_speedy_fields;
    RAISE NOTICE '  Migration successful!';
END $$;