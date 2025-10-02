-- V1__initial_schema.sql

-- ============================================================
-- USERS TABLE
-- ============================================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(200) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    active BOOLEAN NOT NULL DEFAULT true,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    last_login_at TIMESTAMP,
    is_correct BOOLEAN NOT NULL DEFAULT true,
    description TEXT,
    preferred_language VARCHAR(10) DEFAULT 'bg',
    phone VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system'
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);

-- ============================================================
-- CATEGORIES TABLE
-- ============================================================
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    tekra_id VARCHAR(255),
    tekra_slug VARCHAR(255),
    external_id BIGINT UNIQUE,
    name_en VARCHAR(255),
    name_bg VARCHAR(255),
    slug VARCHAR(200),
    show_flag BOOLEAN NOT NULL DEFAULT true,
    sort_order INTEGER NOT NULL DEFAULT 0,
    parent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
);

CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_tekra_slug ON categories(tekra_slug);
CREATE INDEX idx_categories_external_id ON categories(external_id);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_show_flag ON categories(show_flag);

-- ============================================================
-- MANUFACTURERS TABLE
-- ============================================================
CREATE TABLE manufacturers (
    id BIGSERIAL PRIMARY KEY,
    external_id BIGINT UNIQUE,
    name VARCHAR(255) NOT NULL,
    information_name VARCHAR(255),
    information_email VARCHAR(255),
    information_address TEXT,
    eu_representative_name VARCHAR(255),
    eu_representative_email VARCHAR(255),
    eu_representative_address TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system'
);

CREATE INDEX idx_manufacturers_name ON manufacturers(name);
CREATE INDEX idx_manufacturers_external_id ON manufacturers(external_id);

-- ============================================================
-- PRODUCTS TABLE
-- ============================================================
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    tekra_id VARCHAR(255),
    sku VARCHAR(255),
    name_bg TEXT,
    name_en TEXT,
    description_bg TEXT,
    description_en TEXT,
    external_id BIGINT UNIQUE,
    workflow_id BIGINT,
    reference_number VARCHAR(255) UNIQUE,
    model VARCHAR(255),
    barcode VARCHAR(255),
    manufacturer_id BIGINT,
    status VARCHAR(50),
    price_client DECIMAL(10, 2),
    price_partner DECIMAL(10, 2),
    price_promo DECIMAL(10, 2),
    price_client_promo DECIMAL(10, 2),
    markup_percentage DECIMAL(5, 2) DEFAULT 20.0,
    final_price DECIMAL(10, 2),
    show_flag BOOLEAN DEFAULT true,
    warranty INTEGER,
    discount DECIMAL(10, 2) DEFAULT 0,
    active BOOLEAN DEFAULT true,
    featured BOOLEAN DEFAULT false,
    image_url VARCHAR(1000),
    weight DECIMAL(8, 2),
    category_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',
    CONSTRAINT fk_product_manufacturer FOREIGN KEY (manufacturer_id) REFERENCES manufacturers(id) ON DELETE SET NULL,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_external_id ON products(external_id);
CREATE INDEX idx_products_reference_number ON products(reference_number);
CREATE INDEX idx_products_manufacturer_id ON products(manufacturer_id);
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_show_flag ON products(show_flag);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_products_featured ON products(featured);

-- ============================================================
-- ADDITIONAL IMAGES TABLE (ElementCollection)
-- ============================================================
CREATE TABLE additional_images (
    product_id BIGINT NOT NULL,
    additional_urls VARCHAR(1000),
    CONSTRAINT fk_additional_images_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX idx_additional_images_product_id ON additional_images(product_id);

-- ============================================================
-- PARAMETERS TABLE
-- ============================================================
CREATE TABLE parameters (
    id BIGSERIAL PRIMARY KEY,
    external_id BIGINT,
    category_id BIGINT,
    tekra_key VARCHAR(255),
    name_bg VARCHAR(255),
    name_en VARCHAR(255),
    sort_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',
    CONSTRAINT fk_parameter_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,
    CONSTRAINT uq_parameter_category_external UNIQUE (category_id, external_id)
);

CREATE INDEX idx_parameters_category_id ON parameters(category_id);
CREATE INDEX idx_parameters_external_id ON parameters(external_id);
CREATE INDEX idx_parameters_tekra_key ON parameters(tekra_key);

-- ============================================================
-- PARAMETER OPTIONS TABLE
-- ============================================================
CREATE TABLE parameter_options (
    id BIGSERIAL PRIMARY KEY,
    external_id BIGINT,
    parameter_id BIGINT NOT NULL,
    name_bg VARCHAR(255),
    name_en VARCHAR(255),
    sort_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',
    CONSTRAINT fk_parameter_option_parameter FOREIGN KEY (parameter_id) REFERENCES parameters(id) ON DELETE CASCADE
);

CREATE INDEX idx_parameter_options_parameter_id ON parameter_options(parameter_id);
CREATE INDEX idx_parameter_options_external_id ON parameter_options(external_id);

-- ============================================================
-- PRODUCT PARAMETERS TABLE (Many-to-Many with values)
-- ============================================================
CREATE TABLE product_parameters (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    parameter_id BIGINT NOT NULL,
    parameter_option_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',
    CONSTRAINT fk_product_parameter_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_parameter_parameter FOREIGN KEY (parameter_id) REFERENCES parameters(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_parameter_option FOREIGN KEY (parameter_option_id) REFERENCES parameter_options(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_parameters_product_id ON product_parameters(product_id);
CREATE INDEX idx_product_parameters_parameter_id ON product_parameters(parameter_id);
CREATE INDEX idx_product_parameters_option_id ON product_parameters(parameter_option_id);

-- ============================================================
-- PRODUCT FLAGS TABLE
-- ============================================================
CREATE TABLE product_flags (
    id BIGSERIAL PRIMARY KEY,
    external_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    image_url TEXT,
    name_bg VARCHAR(255),
    name_en VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',
    CONSTRAINT fk_product_flag_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_flags_product_id ON product_flags(product_id);
CREATE INDEX idx_product_flags_external_id ON product_flags(external_id);

-- ============================================================
-- CART ITEMS TABLE
-- ============================================================
CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',
    CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT uq_cart_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_cart_items_user_id ON cart_items(user_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);

-- ============================================================
-- USER FAVORITES TABLE
-- ============================================================
CREATE TABLE user_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system',
    CONSTRAINT fk_user_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_favorite_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_favorite UNIQUE (user_id, product_id)
);

CREATE INDEX idx_user_favorites_user_id ON user_favorites(user_id);
CREATE INDEX idx_user_favorites_product_id ON user_favorites(product_id);

-- ============================================================
-- SUBSCRIPTIONS TABLE
-- ============================================================
CREATE TABLE subscription (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE
);

CREATE INDEX idx_subscription_email ON subscription(email);

-- ============================================================
-- SYNC LOGS TABLE
-- ============================================================
CREATE TABLE sync_logs (
    id BIGSERIAL PRIMARY KEY,
    sync_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    records_processed BIGINT,
    records_created BIGINT,
    records_updated BIGINT,
    error_message TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    last_modified_by VARCHAR(100) DEFAULT 'system'
);

CREATE INDEX idx_sync_logs_sync_type ON sync_logs(sync_type);
CREATE INDEX idx_sync_logs_status ON sync_logs(status);
CREATE INDEX idx_sync_logs_created_at ON sync_logs(created_at);

-- ============================================================
-- FULL TEXT SEARCH INDEXES (PostgreSQL specific)
-- NOTE: Advanced search indexes (trigrams, filtered) are created
-- by SearchIndexManager.java at application startup
-- ============================================================

-- Basic full text search index for products
CREATE INDEX IF NOT EXISTS idx_products_search_basic ON products USING gin(
    to_tsvector('simple',
        COALESCE(name_bg, '') || ' ' ||
        COALESCE(model, '') || ' ' ||
        COALESCE(reference_number, '')
    )
);

-- Basic search for manufacturers
CREATE INDEX IF NOT EXISTS idx_manufacturers_name_basic ON manufacturers(name);

-- ============================================================
-- COMMENTS
-- ============================================================

COMMENT ON TABLE users IS 'User accounts with authentication';
COMMENT ON TABLE categories IS 'Product categories with hierarchical structure';
COMMENT ON TABLE manufacturers IS 'Product manufacturers/brands';
COMMENT ON TABLE products IS 'Main products table';
COMMENT ON TABLE parameters IS 'Product parameter definitions (per category)';
COMMENT ON TABLE parameter_options IS 'Available values for each parameter';
COMMENT ON TABLE product_parameters IS 'Product-specific parameter values';
COMMENT ON TABLE sync_logs IS 'Logs for external API synchronization';