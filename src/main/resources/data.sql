-- ========================================
-- REALISTIC TECH STORE DATA
-- Fixed version with proper escaping and table creation
-- ========================================

-- Create tables first (if they don't exist)
-- Note: Add your actual table creation statements here if needed

-- Insert default users WITH audit fields (fixed password escaping)
INSERT INTO users (username, email, password, first_name, last_name, role, active, email_verified, created_at, updated_at, created_by, last_modified_by) VALUES
('admin', 'admin@techstore.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'Super', 'Admin', 'SUPER_ADMIN', true, true, NOW(), NOW(), 'system', 'system'),
('manager', 'manager@techstore.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'Store', 'Manager', 'ADMIN', true, true, NOW(), NOW(), 'system', 'system');

-- Insert comprehensive tech store categories
INSERT INTO categories (name, slug, description, active, sort_order, created_at, updated_at, created_by, last_modified_by) VALUES
-- Main Categories
('Laptops & Notebooks', 'laptops-notebooks', 'Gaming, business, and personal laptops', true, 1, NOW(), NOW(), 'system', 'system'),
('Desktop Computers', 'desktop-computers', 'Pre-built and custom desktop PCs', true, 2, NOW(), NOW(), 'system', 'system'),
('Computer Components', 'computer-components', 'Motherboards, CPUs, GPUs, RAM, and more', true, 3, NOW(), NOW(), 'system', 'system'),
('Computer Peripherals', 'computer-peripherals', 'Keyboards, mice, speakers, webcams', true, 4, NOW(), NOW(), 'system', 'system'),
('Monitors & Displays', 'monitors-displays', 'Gaming, professional, and standard monitors', true, 5, NOW(), NOW(), 'system', 'system'),
('Storage Solutions', 'storage-solutions', 'Hard drives, SSDs, external storage', true, 6, NOW(), NOW(), 'system', 'system'),
('Networking Equipment', 'networking-equipment', 'Routers, switches, WiFi, cables', true, 7, NOW(), NOW(), 'system', 'system'),
('Printers & Scanners', 'printers-scanners', 'Inkjet, laser, multifunction devices', true, 8, NOW(), NOW(), 'system', 'system'),
('Software & Licenses', 'software-licenses', 'Operating systems, productivity software', true, 9, NOW(), NOW(), 'system', 'system'),
('Gaming', 'gaming', 'Gaming accessories and equipment', true, 10, NOW(), NOW(), 'system', 'system'),
('Mobile & Tablets', 'mobile-tablets', 'Smartphones, tablets, accessories', true, 11, NOW(), NOW(), 'system', 'system'),
('Audio & Video', 'audio-video', 'Headphones, speakers, webcams, microphones', true, 12, NOW(), NOW(), 'system', 'system'),
('Cables & Adapters', 'cables-adapters', 'USB, HDMI, power cables, adapters', true, 13, NOW(), NOW(), 'system', 'system'),
('Server & Enterprise', 'server-enterprise', 'Server hardware, enterprise solutions', true, 14, NOW(), NOW(), 'system', 'system'),
('Accessories', 'accessories', 'Computer bags, cleaning, tools', true, 15, NOW(), NOW(), 'system', 'system');

-- Insert Laptop Subcategories (parent_id will be 1)
INSERT INTO categories (name, slug, description, parent_id, active, sort_order, created_at, updated_at, created_by, last_modified_by) VALUES
('Gaming Laptops', 'gaming-laptops', 'High-performance gaming notebooks', 1, true, 1, NOW(), NOW(), 'system', 'system'),
('Business Laptops', 'business-laptops', 'Professional and enterprise laptops', 1, true, 2, NOW(), NOW(), 'system', 'system'),
('Ultrabooks', 'ultrabooks', 'Thin, light, premium laptops', 1, true, 3, NOW(), NOW(), 'system', 'system'),
('Budget Laptops', 'budget-laptops', 'Affordable everyday laptops', 1, true, 4, NOW(), NOW(), 'system', 'system'),
('Workstations', 'workstations', 'Professional mobile workstations', 1, true, 5, NOW(), NOW(), 'system', 'system'),
('Chromebooks', 'chromebooks', 'Chrome OS laptops', 1, true, 6, NOW(), NOW(), 'system', 'system'),
('MacBooks', 'macbooks', 'Apple MacBook laptops', 1, true, 7, NOW(), NOW(), 'system', 'system');

-- Insert Desktop Subcategories (parent_id will be 2)
INSERT INTO categories (name, slug, description, parent_id, active, sort_order, created_at, updated_at, created_by, last_modified_by) VALUES
('Gaming PCs', 'gaming-pcs', 'Pre-built gaming desktop computers', 2, true, 1, NOW(), NOW(), 'system', 'system'),
('Office PCs', 'office-pcs', 'Business and home office computers', 2, true, 2, NOW(), NOW(), 'system', 'system'),
('All-in-One PCs', 'all-in-one-pcs', 'Integrated monitor and PC systems', 2, true, 3, NOW(), NOW(), 'system', 'system'),
('Mini PCs', 'mini-pcs', 'Compact desktop computers', 2, true, 4, NOW(), NOW(), 'system', 'system'),
('Barebone Systems', 'barebone-systems', 'Basic system frameworks for custom builds', 2, true, 5, NOW(), NOW(), 'system', 'system');

-- Insert Component Subcategories (parent_id will be 3)
INSERT INTO categories (name, slug, description, parent_id, active, sort_order, created_at, updated_at, created_by, last_modified_by) VALUES
('Processors (CPUs)', 'processors-cpus', 'Intel and AMD processors', 3, true, 1, NOW(), NOW(), 'system', 'system'),
('Graphics Cards (GPUs)', 'graphics-cards-gpus', 'NVIDIA and AMD graphics cards', 3, true, 2, NOW(), NOW(), 'system', 'system'),
('Motherboards', 'motherboards', 'ATX, micro-ATX, mini-ITX motherboards', 3, true, 3, NOW(), NOW(), 'system', 'system'),
('Memory (RAM)', 'memory-ram', 'DDR4, DDR5 system memory', 3, true, 4, NOW(), NOW(), 'system', 'system'),
('Power Supplies', 'power-supplies', 'Modular and non-modular PSUs', 3, true, 5, NOW(), NOW(), 'system', 'system'),
('Computer Cases', 'computer-cases', 'Full tower, mid tower, mini-ITX cases', 3, true, 6, NOW(), NOW(), 'system', 'system'),
('CPU Coolers', 'cpu-coolers', 'Air and liquid cooling solutions', 3, true, 7, NOW(), NOW(), 'system', 'system'),
('Case Fans', 'case-fans', 'System cooling fans', 3, true, 8, NOW(), NOW(), 'system', 'system'),
('Sound Cards', 'sound-cards', 'Dedicated audio cards', 3, true, 9, NOW(), NOW(), 'system', 'system'),
('Network Cards', 'network-cards', 'Ethernet and WiFi cards', 3, true, 10, NOW(), NOW(), 'system', 'system');

-- Insert Peripheral Subcategories (parent_id will be 4)
INSERT INTO categories (name, slug, description, parent_id, active, sort_order, created_at, updated_at, created_by, last_modified_by) VALUES
('Keyboards', 'keyboards', 'Mechanical, membrane, wireless keyboards', 4, true, 1, NOW(), NOW(), 'system', 'system'),
('Mice', 'mice', 'Gaming, office, wireless mice', 4, true, 2, NOW(), NOW(), 'system', 'system'),
('Webcams', 'webcams', 'HD and 4K webcams', 4, true, 3, NOW(), NOW(), 'system', 'system'),
('Speakers', 'speakers', 'Computer speakers and sound systems', 4, true, 4, NOW(), NOW(), 'system', 'system'),
('Microphones', 'microphones', 'USB and professional microphones', 4, true, 5, NOW(), NOW(), 'system', 'system'),
('Headsets', 'headsets', 'Gaming and professional headsets', 4, true, 6, NOW(), NOW(), 'system', 'system'),
('Mousepads', 'mousepads', 'Gaming and office mousepads', 4, true, 7, NOW(), NOW(), 'system', 'system'),
('Drawing Tablets', 'drawing-tablets', 'Graphics tablets and stylus devices', 4, true, 8, NOW(), NOW(), 'system', 'system');

-- Insert comprehensive tech brands (fixed special characters)
INSERT INTO brands (name, slug, description, country, active, featured, sort_order, created_at, updated_at, created_by, last_modified_by) VALUES
('ASUS', 'asus', 'Leading manufacturer of motherboards, laptops, and components', 'Taiwan', true, true, 1, NOW(), NOW(), 'system', 'system'),
('MSI', 'msi', 'Gaming hardware and laptop specialist', 'Taiwan', true, true, 2, NOW(), NOW(), 'system', 'system'),
('Gigabyte', 'gigabyte', 'Motherboards, graphics cards, and laptops', 'Taiwan', true, true, 3, NOW(), NOW(), 'system', 'system'),
('ASRock', 'asrock', 'Motherboard and mini PC manufacturer', 'Taiwan', true, false, 4, NOW(), NOW(), 'system', 'system'),
('Dell', 'dell', 'Enterprise and consumer computers', 'USA', true, true, 5, NOW(), NOW(), 'system', 'system'),
('HP', 'hp', 'Computers, printers, and enterprise solutions', 'USA', true, true, 6, NOW(), NOW(), 'system', 'system'),
('Lenovo', 'lenovo', 'ThinkPad and consumer laptops', 'China', true, true, 7, NOW(), NOW(), 'system', 'system'),
('Acer', 'acer', 'Consumer laptops and monitors', 'Taiwan', true, false, 8, NOW(), NOW(), 'system', 'system'),
('Intel', 'intel', 'Leading processor and chipset manufacturer', 'USA', true, true, 9, NOW(), NOW(), 'system', 'system'),
('AMD', 'amd', 'Processors and graphics solutions', 'USA', true, true, 10, NOW(), NOW(), 'system', 'system'),
('NVIDIA', 'nvidia', 'Graphics processing units and AI chips', 'USA', true, true, 11, NOW(), NOW(), 'system', 'system'),
('EVGA', 'evga', 'High-performance graphics cards and power supplies', 'USA', true, true, 12, NOW(), NOW(), 'system', 'system'),
('Zotac', 'zotac', 'Compact graphics cards and mini PCs', 'Hong Kong', true, false, 13, NOW(), NOW(), 'system', 'system'),
('Sapphire', 'sapphire', 'AMD graphics card specialist', 'Hong Kong', true, false, 14, NOW(), NOW(), 'system', 'system'),
('Samsung', 'samsung', 'Memory, SSDs, and displays', 'South Korea', true, true, 15, NOW(), NOW(), 'system', 'system'),
('Kingston', 'kingston', 'Memory and storage solutions', 'USA', true, true, 16, NOW(), NOW(), 'system', 'system'),
('Corsair', 'corsair', 'Gaming memory, cooling, and peripherals', 'USA', true, true, 17, NOW(), NOW(), 'system', 'system'),
('G.Skill', 'g-skill', 'High-performance memory modules', 'Taiwan', true, false, 18, NOW(), NOW(), 'system', 'system'),
('Crucial', 'crucial', 'Memory and SSD manufacturer', 'USA', true, false, 19, NOW(), NOW(), 'system', 'system'),
('Western Digital', 'western-digital', 'Hard drives and storage solutions', 'USA', true, true, 20, NOW(), NOW(), 'system', 'system'),
('Seagate', 'seagate', 'Hard drive and storage specialist', 'USA', true, false, 21, NOW(), NOW(), 'system', 'system'),
('Logitech', 'logitech', 'Computer peripherals and accessories', 'Switzerland', true, true, 22, NOW(), NOW(), 'system', 'system'),
('Razer', 'razer', 'Gaming peripherals and laptops', 'Singapore', true, true, 23, NOW(), NOW(), 'system', 'system'),
('SteelSeries', 'steelseries', 'Gaming keyboards, mice, and headsets', 'Denmark', true, true, 24, NOW(), NOW(), 'system', 'system'),
('HyperX', 'hyperx', 'Gaming memory and peripherals', 'USA', true, false, 25, NOW(), NOW(), 'system', 'system'),
('Roccat', 'roccat', 'Gaming peripherals and accessories', 'Germany', true, false, 26, NOW(), NOW(), 'system', 'system'),
('BenQ', 'benq', 'Professional and gaming monitors', 'Taiwan', true, true, 27, NOW(), NOW(), 'system', 'system'),
('LG', 'lg', 'Displays, monitors, and electronics', 'South Korea', true, true, 28, NOW(), NOW(), 'system', 'system'),
('ViewSonic', 'viewsonic', 'Professional displays and projectors', 'USA', true, false, 29, NOW(), NOW(), 'system', 'system'),
('AOC', 'aoc', 'Affordable monitors and displays', 'Taiwan', true, false, 30, NOW(), NOW(), 'system', 'system'),
('Seasonic', 'seasonic', 'High-quality power supplies', 'Taiwan', true, false, 31, NOW(), NOW(), 'system', 'system'),
('Thermaltake', 'thermaltake', 'Cases, cooling, and power supplies', 'Taiwan', true, false, 32, NOW(), NOW(), 'system', 'system'),
('Cooler Master', 'cooler-master', 'Cases, cooling solutions, and peripherals', 'Taiwan', true, false, 33, NOW(), NOW(), 'system', 'system'),
('be quiet!', 'be-quiet', 'Silent cooling and power solutions', 'Germany', true, false, 34, NOW(), NOW(), 'system', 'system'),
('Noctua', 'noctua', 'Premium CPU coolers and fans', 'Austria', true, false, 35, NOW(), NOW(), 'system', 'system'),
('TP-Link', 'tp-link', 'Networking equipment and smart home', 'China', true, false, 36, NOW(), NOW(), 'system', 'system'),
('ASUS Networking', 'asus-networking', 'Professional networking solutions', 'Taiwan', true, false, 37, NOW(), NOW(), 'system', 'system'),
('Netgear', 'netgear', 'Home and business networking', 'USA', true, false, 38, NOW(), NOW(), 'system', 'system'),
('Ubiquiti', 'ubiquiti', 'Enterprise networking equipment', 'USA', true, false, 39, NOW(), NOW(), 'system', 'system');

-- Sample realistic products with detailed specifications (fixed special characters)
INSERT INTO products (name, sku, description, price, discount, discounted_price, stock_quantity, active, featured, warranty, weight, dimensions, category_id, brand_id, created_at, updated_at, created_by, last_modified_by) VALUES
('ASUS ROG Strix G16 (2024) Gaming Laptop', 'LAPTOP-ASUS-G16-2024', '16" QHD 165Hz, Intel Core i7-13650HX, RTX 4060 8GB, 16GB DDR5, 512GB SSD', 1699.99, -200.00, 1499.99, 12, true, true, '2 years', 2.5, '35.4 x 25.2 x 2.34 cm', 16, 1, NOW(), NOW(), 'system', 'system'),
('MSI Katana 17 B13V Gaming Laptop', 'LAPTOP-MSI-KATANA17', '17.3" FHD 144Hz, Intel Core i7-13620H, RTX 4050 6GB, 16GB DDR5, 1TB SSD', 1299.99, -100.00, 1199.99, 8, true, true, '2 years', 2.6, '39.8 x 27.5 x 2.49 cm', 16, 2, NOW(), NOW(), 'system', 'system'),
('Dell Latitude 5540 Business Laptop', 'LAPTOP-DELL-LAT5540', '15.6" FHD, Intel Core i5-1345U, 16GB DDR4, 512GB SSD, Windows 11 Pro', 1099.99, 0, 1099.99, 25, true, false, '3 years', 1.61, '35.7 x 23.5 x 1.99 cm', 17, 5, NOW(), NOW(), 'system', 'system'),
('Lenovo ThinkPad E16 Gen 5', 'LAPTOP-LENOVO-E16G5', '16" WUXGA, AMD Ryzen 5 7530U, 16GB DDR4, 512GB SSD, Windows 11 Pro', 899.99, -50.00, 849.99, 18, true, false, '1 year', 1.97, '36.2 x 25.4 x 1.99 cm', 17, 7, NOW(), NOW(), 'system', 'system'),
('Intel Core i7-13700K Desktop Processor', 'CPU-INTEL-I7-13700K', '16 cores (8P+8E), up to 5.4GHz, LGA1700, 125W TDP', 409.99, -30.00, 379.99, 35, true, true, '3 years', 0.1, '3.75 x 3.75 x 0.76 cm', 24, 9, NOW(), NOW(), 'system', 'system'),
('AMD Ryzen 7 7700X Desktop Processor', 'CPU-AMD-R7-7700X', '8 cores, 16 threads, up to 5.4GHz, AM5, 105W TDP', 349.99, 0, 349.99, 42, true, true, '3 years', 0.1, '4.0 x 4.0 x 0.8 cm', 24, 10, NOW(), NOW(), 'system', 'system'),
('MSI GeForce RTX 4070 GAMING X TRIO 12G', 'GPU-MSI-RTX4070-GXT', 'NVIDIA RTX 4070, 12GB GDDR6X, Triple Fan Cooling, RGB Lighting', 649.99, -50.00, 599.99, 15, true, true, '3 years', 1.67, '33.6 x 14.0 x 5.5 cm', 25, 2, NOW(), NOW(), 'system', 'system'),
('ASUS TUF Gaming GeForce RTX 4060 Ti', 'GPU-ASUS-RTX4060TI-TUF', 'NVIDIA RTX 4060 Ti, 16GB GDDR6, Dual Fan, Military-grade Components', 549.99, 0, 549.99, 22, true, true, '3 years', 1.42, '30.0 x 12.9 x 5.1 cm', 25, 1, NOW(), NOW(), 'system', 'system'),
('Corsair Vengeance LPX 32GB (2x16GB) DDR4-3200', 'RAM-CORSAIR-32GB-3200', 'High-performance desktop memory, C16 latency, Black heatspreader', 99.99, -10.00, 89.99, 67, true, false, 'Lifetime', 0.32, '13.4 x 0.8 x 3.1 cm', 27, 17, NOW(), NOW(), 'system', 'system'),
('G.SKILL Trident Z5 RGB 32GB (2x16GB) DDR5-6000', 'RAM-GSKILL-32GB-6000', 'DDR5-6000 CL36, RGB lighting, Intel XMP 3.0 ready', 189.99, -20.00, 169.99, 28, true, true, 'Lifetime', 0.28, '14.4 x 0.8 x 4.4 cm', 27, 18, NOW(), NOW(), 'system', 'system'),
('Samsung 980 PRO 2TB NVMe SSD', 'SSD-SAMSUNG-980PRO-2TB', 'PCIe 4.0 M.2 2280, up to 7,000 MB/s read, V-NAND technology', 179.99, -30.00, 149.99, 45, true, true, '5 years', 0.008, '8.0 x 2.2 x 0.15 cm', 6, 15, NOW(), NOW(), 'system', 'system'),
('Western Digital Blue 4TB Internal Hard Drive', 'HDD-WD-BLUE-4TB', '3.5" SATA 6Gb/s, 5400 RPM, 256MB cache, Desktop storage', 79.99, 0, 79.99, 89, true, false, '2 years', 0.45, '10.2 x 14.7 x 2.6 cm', 6, 20, NOW(), NOW(), 'system', 'system'),
('Logitech MX Master 3S Wireless Mouse', 'MOUSE-LOGITECH-MX3S', 'Advanced wireless mouse, 4000 DPI, USB-C, Multi-device', 99.99, -15.00, 84.99, 156, true, true, '1 year', 0.141, '12.4 x 8.4 x 5.1 cm', 34, 22, NOW(), NOW(), 'system', 'system'),
('Razer BlackWidow V4 Pro Mechanical Keyboard', 'KB-RAZER-BWV4PRO', 'Green mechanical switches, RGB backlighting, macro keys, wrist rest', 229.99, -30.00, 199.99, 34, true, true, '2 years', 1.52, '46.1 x 16.8 x 4.2 cm', 33, 23, NOW(), NOW(), 'system', 'system'),
('BenQ ZOWIE XL2566K 24.5" Gaming Monitor', 'MON-BENQ-XL2566K', '24.5" FHD, 360Hz, 0.5ms response, DyAc+ technology, Tournament-ready', 599.99, -50.00, 549.99, 12, true, true, '3 years', 7.0, '56.2 x 21.8 x 5.5 cm', 5, 27, NOW(), NOW(), 'system', 'system'),
('LG 27GN950-B UltraGear Gaming Monitor', 'MON-LG-27GN950B', '27" 4K UHD, 144Hz, Nano IPS, G-SYNC Compatible, HDR600', 699.99, -100.00, 599.99, 8, true, true, '3 years', 8.9, '61.3 x 18.3 x 5.6 cm', 5, 28, NOW(), NOW(), 'system', 'system');

-- Comprehensive Product Specifications
-- Gaming Laptop Specifications (ASUS ROG Strix G16)
INSERT INTO product_specifications (spec_name, spec_value, spec_unit, spec_group, sort_order, product_id, created_at, updated_at, created_by, last_modified_by) VALUES
('Processor', 'Intel Core i7-13650HX', '', 'Performance', 1, 1, NOW(), NOW(), 'system', 'system'),
('Graphics Card', 'NVIDIA GeForce RTX 4060', '8GB GDDR6', 'Performance', 2, 1, NOW(), NOW(), 'system', 'system'),
('Memory', '16', 'GB DDR5-4800', 'Performance', 3, 1, NOW(), NOW(), 'system', 'system'),
('Storage', '512', 'GB PCIe 4.0 NVMe SSD', 'Storage', 1, 1, NOW(), NOW(), 'system', 'system'),
('Display Size', '16', 'inches', 'Display', 1, 1, NOW(), NOW(), 'system', 'system'),
('Resolution', '2560x1600', 'pixels', 'Display', 2, 1, NOW(), NOW(), 'system', 'system'),
('Refresh Rate', '165', 'Hz', 'Display', 3, 1, NOW(), NOW(), 'system', 'system'),
('Panel Type', 'IPS', '', 'Display', 4, 1, NOW(), NOW(), 'system', 'system'),
('Weight', '2.5', 'kg', 'Physical', 1, 1, NOW(), NOW(), 'system', 'system'),
('Battery', '90', 'Wh', 'Power', 1, 1, NOW(), NOW(), 'system', 'system'),
('Ports', 'USB-C, USB-A, HDMI 2.1, Audio Jack', '', 'Connectivity', 1, 1, NOW(), NOW(), 'system', 'system'),
('WiFi', '802.11ax (WiFi 6E)', '', 'Connectivity', 2, 1, NOW(), NOW(), 'system', 'system'),
('Bluetooth', '5.3', '', 'Connectivity', 3, 1, NOW(), NOW(), 'system', 'system'),
('Operating System', 'Windows 11 Home', '', 'Software', 1, 1, NOW(), NOW(), 'system', 'system'),
('Keyboard', 'RGB Backlit', '', 'Features', 1, 1, NOW(), NOW(), 'system', 'system');

-- CPU Specifications (Intel i7-13700K)
INSERT INTO product_specifications (spec_name, spec_value, spec_unit, spec_group, sort_order, product_id, created_at, updated_at, created_by, last_modified_by) VALUES
('Architecture', 'Raptor Lake', '', 'Core', 1, 5, NOW(), NOW(), 'system', 'system'),
('Total Cores', '16', '', 'Core', 2, 5, NOW(), NOW(), 'system', 'system'),
('Performance Cores', '8', '', 'Core', 3, 5, NOW(), NOW(), 'system', 'system'),
('Efficiency Cores', '8', '', 'Core', 4, 5, NOW(), NOW(), 'system', 'system'),
('Total Threads', '24', '', 'Core', 5, 5, NOW(), NOW(), 'system', 'system'),
('Base Clock P-Core', '3.4', 'GHz', 'Performance', 1, 5, NOW(), NOW(), 'system', 'system'),
('Max Boost Clock', '5.4', 'GHz', 'Performance', 2, 5, NOW(), NOW(), 'system', 'system'),
('Cache L3', '30', 'MB', 'Cache', 1, 5, NOW(), NOW(), 'system', 'system'),
('Socket', 'LGA1700', '', 'Compatibility', 1, 5, NOW(), NOW(), 'system', 'system'),
('TDP', '125', 'W', 'Power', 1, 5, NOW(), NOW(), 'system', 'system'),
('Max Memory', '128', 'GB', 'Memory Support', 1, 5, NOW(), NOW(), 'system', 'system'),
('Memory Types', 'DDR4-3200, DDR5-5600', '', 'Memory Support', 2, 5, NOW(), NOW(), 'system', 'system'),
('Process Node', '10', 'nm', 'Manufacturing', 1, 5, NOW(), NOW(), 'system', 'system'),
('Integrated Graphics', 'Intel UHD Graphics 770', '', 'Graphics', 1, 5, NOW(), NOW(), 'system', 'system'),
('PCI Express', '5.0 x20, 4.0 x4', '', 'Features', 1, 5, NOW(), NOW(), 'system', 'system');

-- Graphics Card Specifications (MSI RTX 4070)
INSERT INTO product_specifications (spec_name, spec_value, spec_unit, spec_group, sort_order, product_id, created_at, updated_at, created_by, last_modified_by) VALUES
('GPU Chip', 'NVIDIA GeForce RTX 4070', '', 'Core', 1, 7, NOW(), NOW(), 'system', 'system'),
('Architecture', 'Ada Lovelace', '', 'Core', 2, 7, NOW(), NOW(), 'system', 'system'),
('CUDA Cores', '5888', '', 'Core', 3, 7, NOW(), NOW(), 'system', 'system'),
('RT Cores', '46', '3rd Gen', 'Core', 4, 7, NOW(), NOW(), 'system', 'system'),
('Tensor Cores', '184', '4th Gen', 'Core', 5, 7, NOW(), NOW(), 'system', 'system'),
('Base Clock', '1920', 'MHz', 'Performance', 1, 7, NOW(), NOW(), 'system', 'system'),
('Boost Clock', '2475', 'MHz', 'Performance', 2, 7, NOW(), NOW(), 'system', 'system'),
('Memory Size', '12', 'GB GDDR6X', 'Memory', 1, 7, NOW(), NOW(), 'system', 'system'),
('Memory Interface', '192', 'bit', 'Memory', 2, 7, NOW(), NOW(), 'system', 'system'),
('Memory Bandwidth', '504.2', 'GB/s', 'Memory', 3, 7, NOW(), NOW(), 'system', 'system'),
('TGP', '200', 'W', 'Power', 1, 7, NOW(), NOW(), 'system', 'system'),
('Recommended PSU', '650', 'W', 'Power', 2, 7, NOW(), NOW(), 'system', 'system'),
('Power Connectors', '1x 16-pin (12VHPWR)', '', 'Power', 3, 7, NOW(), NOW(), 'system', 'system'),
('DirectX Support', '12 Ultimate', '', 'Features', 1, 7, NOW(), NOW(), 'system', 'system'),
('OpenGL Support', '4.6', '', 'Features', 2, 7, NOW(), NOW(), 'system', 'system'),
('Vulkan Support', '1.3', '', 'Features', 3, 7, NOW(), NOW(), 'system', 'system'),
('Ray Tracing', 'Yes', '', 'Features', 4, 7, NOW(), NOW(), 'system', 'system'),
('DLSS', '3.0', '', 'Features', 5, 7, NOW(), NOW(), 'system', 'system'),
('Display Outputs', '3x DisplayPort 1.4a, 1x HDMI 2.1a', '', 'Connectivity', 1, 7, NOW(), NOW(), 'system', 'system'),
('Cooling', 'Triple Fan TORX 4.0', '', 'Cooling', 1, 7, NOW(), NOW(), 'system', 'system');