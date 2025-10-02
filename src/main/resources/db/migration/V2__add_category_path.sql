-- V2__add_category_path.sql

ALTER TABLE categories
ADD COLUMN category_path VARCHAR(500);

COMMENT ON COLUMN categories.category_path IS 'Full hierarchical path (e.g., videonablyudenie/ip-sistemi/kameri)';

CREATE INDEX idx_categories_path ON categories(category_path);

CREATE OR REPLACE FUNCTION generate_category_path(cat_id BIGINT)
RETURNS TEXT AS $$
DECLARE
    path_parts TEXT[] := '{}';
    current_id BIGINT;
    current_slug TEXT;
    current_parent_id BIGINT;
    iteration_count INTEGER := 0;
    max_iterations INTEGER := 10;
BEGIN
    current_id := cat_id;

    WHILE current_id IS NOT NULL AND iteration_count < max_iterations LOOP
        SELECT
            COALESCE(tekra_slug, slug, CONCAT('cat-', id)),
            parent_id
        INTO current_slug, current_parent_id
        FROM categories
        WHERE id = current_id;

        IF NOT FOUND THEN
            EXIT;
        END IF;

        IF current_slug IS NOT NULL THEN
            path_parts := array_prepend(current_slug, path_parts);
        END IF;

        current_id := current_parent_id;
        iteration_count := iteration_count + 1;
    END LOOP;

    IF array_length(path_parts, 1) > 0 THEN
        RETURN array_to_string(path_parts, '/');
    ELSE
        RETURN NULL;
    END IF;
END;
$$ LANGUAGE plpgsql;

UPDATE categories
SET category_path = generate_category_path(id)
WHERE category_path IS NULL;

DO $$
DECLARE
    total_count INTEGER;
    with_path_count INTEGER;
    without_path_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_count FROM categories;
    SELECT COUNT(*) INTO with_path_count FROM categories WHERE category_path IS NOT NULL;
    SELECT COUNT(*) INTO without_path_count FROM categories WHERE category_path IS NULL;

    RAISE NOTICE 'Category path migration completed:';
    RAISE NOTICE '  Total categories: %', total_count;
    RAISE NOTICE '  With path: %', with_path_count;
    RAISE NOTICE '  Without path: %', without_path_count;
END $$;

DO $$
DECLARE
    duplicate_rec RECORD;
BEGIN
    RAISE NOTICE 'Categories with duplicate names (expected for hierarchical structure):';

    FOR duplicate_rec IN
        SELECT
            name_bg,
            COUNT(*) as count,
            STRING_AGG(category_path, ' | ' ORDER BY category_path) as paths
        FROM categories
        WHERE category_path IS NOT NULL
        GROUP BY name_bg
        HAVING COUNT(*) > 1
        ORDER BY COUNT(*) DESC
        LIMIT 10
    LOOP
        RAISE NOTICE '  Name: "%" (% times) - Paths: %',
            duplicate_rec.name_bg,
            duplicate_rec.count,
            duplicate_rec.paths;
    END LOOP;
END $$;

COMMENT ON FUNCTION generate_category_path(BIGINT) IS
    'Generates full hierarchical path for a category. Used by Java code and migrations.';