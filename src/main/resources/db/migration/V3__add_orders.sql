-- V3__add_orders.sql

-- ============================================================
-- ORDERS TABLE
-- ============================================================
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,

    -- Status fields
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(30),

    -- Price fields
    subtotal DECIMAL(10, 2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    shipping_cost DECIMAL(10, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(10, 2) DEFAULT 0,
    total DECIMAL(10, 2) NOT NULL DEFAULT 0,

    -- Customer information
    customer_first_name VARCHAR(100) NOT NULL,
    customer_last_name VARCHAR(100) NOT NULL,
    customer_email VARCHAR(200) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    customer_company VARCHAR(200),
    customer_vat_number VARCHAR(50),
    customer_vat_registered BOOLEAN DEFAULT false,

    -- Shipping address
    shipping_address TEXT NOT NULL,
    shipping_city VARCHAR(100) NOT NULL,
    shipping_postal_code VARCHAR(20),
    shipping_country VARCHAR(100) NOT NULL DEFAULT 'Bulgaria',

    -- Billing address
    billing_address TEXT,
    billing_city VARCHAR(100),
    billing_postal_code VARCHAR(20),
    billing_country VARCHAR(100) DEFAULT 'Bulgaria',

    -- Notes
    customer_notes TEXT,
    admin_notes TEXT,

    -- Delivery tracking
    tracking_number VARCHAR(100),
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,

    -- Invoice/Fiscal data
    invoice_number VARCHAR(50),
    invoice_date TIMESTAMP,
    fiscal_receipt_number VARCHAR(100),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',

    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- Indexes for orders table
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_customer_email ON orders(customer_email);

-- ============================================================
-- ORDER ITEMS TABLE
-- ============================================================
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    -- Product snapshot (for historical records)
    product_name VARCHAR(500) NOT NULL,
    product_sku VARCHAR(100),
    product_model VARCHAR(255),

    -- Pricing
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    tax_rate DECIMAL(5, 2) NOT NULL DEFAULT 20.00,
    line_total DECIMAL(10, 2) NOT NULL,
    line_tax DECIMAL(10, 2) NOT NULL,
    discount_amount DECIMAL(10, 2) DEFAULT 0,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',

    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

-- Indexes for order_items table
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- ============================================================
-- COMMENTS
-- ============================================================
COMMENT ON TABLE orders IS 'Customer orders with billing and shipping information';
COMMENT ON TABLE order_items IS 'Individual items within orders with historical product data';

COMMENT ON COLUMN orders.order_number IS 'Unique order number (e.g., ORD-2025-00001)';
COMMENT ON COLUMN orders.subtotal IS 'Total without VAT';
COMMENT ON COLUMN orders.tax_amount IS 'VAT amount (20%)';
COMMENT ON COLUMN orders.total IS 'Final total including VAT and shipping';
COMMENT ON COLUMN orders.customer_vat_registered IS 'Whether customer is VAT registered';

COMMENT ON COLUMN order_items.product_name IS 'Product name snapshot at time of order';
COMMENT ON COLUMN order_items.unit_price IS 'Price per unit without VAT';
COMMENT ON COLUMN order_items.line_total IS 'Total for this line (quantity * unitPrice - discount)';
COMMENT ON COLUMN order_items.line_tax IS 'VAT for this line item';