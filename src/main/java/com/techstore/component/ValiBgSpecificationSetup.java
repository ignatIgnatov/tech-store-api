package com.techstore.component;

import com.techstore.dto.CategorySpecificationTemplateDTO;
import com.techstore.entity.Category;
import com.techstore.entity.CategorySpecificationTemplate;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategoryRepository;
import com.techstore.service.CategorySpecificationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.techstore.entity.CategorySpecificationTemplate.SpecificationType.BOOLEAN;
import static com.techstore.entity.CategorySpecificationTemplate.SpecificationType.DECIMAL;
import static com.techstore.entity.CategorySpecificationTemplate.SpecificationType.DROPDOWN;
import static com.techstore.entity.CategorySpecificationTemplate.SpecificationType.MULTI_SELECT;
import static com.techstore.entity.CategorySpecificationTemplate.SpecificationType.NUMBER;
import static com.techstore.entity.CategorySpecificationTemplate.SpecificationType.TEXT;

//@Component
@RequiredArgsConstructor
@Slf4j
public class ValiBgSpecificationSetup {

    private final CategorySpecificationService specService;
    private final CategoryRepository categoryRepository;

    /**
     * Setup ALL vali.bg categories with exact specifications
     * This creates the complete vali.bg product catalog structure
     */
    public void setupAllValiBgCategories() {
        log.info("🚀 Starting complete vali.bg categories setup...");

        // COMPUTER COMPONENTS
        setupMotherboardSpecifications();
        setupCPUSpecifications();
        setupGraphicsCardSpecifications();
        setupMemorySpecifications();
        setupStorageHDDSpecifications();
        setupStorageSSDSpecifications();
        setupPowerSupplySpecifications();
        setupComputerCaseSpecifications();
        setupCoolingSpecifications();
        setupSoundCardSpecifications();
        setupNetworkCardSpecifications();

        // COMPUTER SYSTEMS
        setupDesktopPCSpecifications();
        setupServerSpecifications();
        setupThinClientSpecifications();

        // LAPTOPS, TABLETS AND ACCESSORIES
        setupLaptopSpecifications();
        setupLaptopAccessorySpecifications();
//        setupTabletSpecifications();

        // MONITORS AND DISPLAYS
        setupMonitorSpecifications();
//        setupInteractiveDisplaySpecifications();

        // COMPUTER PERIPHERALS
//        setupKeyboardSpecifications();
//        setupMouseSpecifications();
//        setupSpeakerSpecifications();
//        setupHeadphoneSpecifications();
//        setupWebcamSpecifications();
//        setupMicrophoneSpecifications();
//        setupUSBHubSpecifications();
//        setupCardReaderSpecifications();

        // STORAGE DEVICES
//        setupExternalHDDSpecifications();
//        setupExternalSSDSpecifications();
//        setupUSBStickSpecifications();
//        setupOpticalDriveSpecifications();

        // POWER PROTECTION
//        setupUPSSpecifications();
//        setupSurgeProtectorSpecifications();

        // PRINTERS, SCANNERS AND CONSUMABLES
//        setupPrinterSpecifications();
//        setupScannerSpecifications();
//        setupPrinterConsumableSpecifications();

        // NETWORK EQUIPMENT
//        setupRouterSpecifications();
//        setupSwitchSpecifications();
//        setupAccessPointSpecifications();
//        setupNetworkAdapterSpecifications();

        // PROJECTORS AND INTERACTIVE BOARDS
//        setupProjectorSpecifications();
//        setupInteractiveBoardSpecifications();

        // ELECTRONICS
//        setupPortableSpeakerSpecifications();
//        setupMediaPlayerSpecifications();
//        setupRadioSpecifications();
//        setupEBookReaderSpecifications();

        // CABLES AND ADAPTERS
//        setupCableSpecifications();
//        setupAdapterSpecifications();

        // TV, VIDEO AND ACCESSORIES (already implemented)
//        setupTVSpecifications();
//        setupTVAccessorySpecifications();

        // PHOTO AND VIDEO ACCESSORIES
//        setupCameraSpecifications();
//        setupCamcorderSpecifications();
//        setupPhotoAccessorySpecifications();

        // MOBILE PHONES AND ACCESSORIES (already implemented)
//        setupSmartphoneSpecifications();
//        setupMobileAccessorySpecifications();

        // GAMING PERIPHERAL DEVICES
//        setupGamingKeyboardSpecifications();
//        setupGamingMouseSpecifications();
//        setupGamingHeadsetSpecifications();
//        setupGamingControllerSpecifications();
//        setupGamingChairSpecifications();

        // VR (VIRTUAL REALITY)
//        setupVRHeadsetSpecifications();
//        setupVRAccessorySpecifications();

        // NAVIGATION SYSTEMS
//        setupGPSNavigationSpecifications();
//        setupDashcamSpecifications();

        // OPTICS
//        setupBinocularSpecifications();
//        setupMicroscopeSpecifications();

        // OFFICE PRODUCTS
//        setupOfficeChairSpecifications();
//        setupDeskSpecifications();
//        setupLaminatorSpecifications();
//        setupShredderSpecifications();

        // SOFTWARE
//        setupSoftwareSpecifications();

        // BATTERIES AND CHARGERS
//        setupBatterySpecifications();
//        setupChargerSpecifications();

        // HOUSEHOLD PRODUCTS
//        setupKitchenApplianceSpecifications();
//        setupCleaningProductSpecifications();
//        setupSecuritySystemSpecifications();

        // BARCODE SCANNERS
//        setupBarcodeScannerSpecifications();

        // HOME APPLIANCES
//        setupHomeApplianceSpecifications();

        log.info("✅ Complete vali.bg categories setup finished!");
    }

    public void setupLaptopSpecifications() {
        Long categoryId = getCategoryId("laptops");

        // ===== BASIC CHARACTERISTICS =====
        createSpec(categoryId, "Laptop Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Ultrabook", "Gaming Laptop", "Business Laptop", "2-in-1 Convertible", "Chromebook", "Workstation", "Budget Laptop"));

        createSpec(categoryId, "Processor", null, "Basic Characteristics", true, true, TEXT, 2);
        createSpec(categoryId, "Processor Brand", null, "Basic Characteristics", false, true, DROPDOWN, 3,
                List.of("Intel", "AMD", "Apple"));

        createSpec(categoryId, "Processor Series", null, "Basic Characteristics", true, true, DROPDOWN, 4,
                List.of("Intel Core i3", "Intel Core i5", "Intel Core i7", "Intel Core i9",
                        "AMD Ryzen 3", "AMD Ryzen 5", "AMD Ryzen 7", "AMD Ryzen 9",
                        "Apple M1", "Apple M2", "Apple M3", "Intel Celeron", "Intel Pentium"));

        createSpec(categoryId, "Generation", null, "Basic Characteristics", false, true, DROPDOWN, 5,
                List.of("12th Gen", "13th Gen", "14th Gen", "15th Gen", "Zen 3", "Zen 4", "Zen 5"));

        createSpec(categoryId, "Cores", null, "Performance", false, true, DROPDOWN, 6,
                List.of("2", "4", "6", "8", "10", "12", "14", "16"));

        createSpec(categoryId, "Threads", null, "Performance", false, false, DROPDOWN, 7,
                List.of("2", "4", "6", "8", "12", "16", "20", "24"));

        createSpec(categoryId, "Base Clock", "GHz", "Performance", false, false, DECIMAL, 8);
        createSpec(categoryId, "Max Boost Clock", "GHz", "Performance", false, false, DECIMAL, 9);

        createSpec(categoryId, "Operating System", null, "Basic Characteristics", true, true, DROPDOWN, 10,
                List.of("Windows 11 Home", "Windows 11 Pro", "Windows 10", "macOS Monterey", "macOS Ventura", "macOS Sonoma",
                        "Linux Ubuntu", "Chrome OS", "FreeDOS", "No OS"));

        createSpec(categoryId, "RAM Memory", "GB", "Memory", true, true, DROPDOWN, 11,
                List.of("4", "8", "16", "32", "64", "128"));

        createSpec(categoryId, "RAM Type", null, "Memory", false, true, DROPDOWN, 12,
                List.of("DDR4", "DDR5", "LPDDR4", "LPDDR5", "Unified Memory"));

        createSpec(categoryId, "RAM Speed", "MHz", "Memory", false, false, DROPDOWN, 13,
                List.of("2133", "2400", "2666", "3200", "4800", "5600", "6400"));

        createSpec(categoryId, "Max RAM", "GB", "Memory", false, false, DROPDOWN, 14,
                List.of("8", "16", "32", "64", "128"));

        createSpec(categoryId, "RAM Slots", null, "Memory", false, false, DROPDOWN, 15,
                List.of("1", "2", "Soldered"));

        createSpec(categoryId, "Storage Capacity", "GB", "Storage", true, true, DROPDOWN, 16,
                List.of("128", "256", "512", "1000", "1024", "2000", "2048", "4096"));

        createSpec(categoryId, "Storage Type", null, "Storage", true, true, DROPDOWN, 17,
                List.of("SSD", "HDD", "SSD + HDD", "eMMC", "NVMe SSD", "PCIe SSD"));

        createSpec(categoryId, "Primary Storage", null, "Storage", false, false, TEXT, 18);
        createSpec(categoryId, "Secondary Storage", null, "Storage", false, false, TEXT, 19);

        createSpec(categoryId, "Storage Interface", null, "Storage", false, false, DROPDOWN, 20,
                List.of("SATA III", "PCIe 3.0", "PCIe 4.0", "NVMe"));

        // ===== DISPLAY =====
        createSpec(categoryId, "Screen Size", "inches", "Display", true, true, DROPDOWN, 21,
                List.of("11.6", "12", "12.5", "13", "13.3", "14", "15.6", "16", "17", "17.3", "18"));

        createSpec(categoryId, "Screen Resolution", null, "Display", true, true, DROPDOWN, 22,
                List.of("1366x768 (HD)", "1600x900 (HD+)", "1920x1080 (Full HD)", "2560x1440 (QHD)",
                        "2560x1600 (QHD+)", "2880x1800", "3840x2160 (4K UHD)", "3456x2234"));

        createSpec(categoryId, "Screen Type", null, "Display", false, true, DROPDOWN, 23,
                List.of("TN", "IPS", "VA", "OLED", "Mini LED", "Retina", "Liquid Retina"));

        createSpec(categoryId, "Touchscreen", null, "Display", false, true, BOOLEAN, 24);
        createSpec(categoryId, "Touch Type", null, "Display", false, false, DROPDOWN, 25,
                List.of("Capacitive", "Multi-touch", "Stylus Support"));

        createSpec(categoryId, "Refresh Rate", "Hz", "Display", false, true, DROPDOWN, 26,
                List.of("60", "90", "120", "144", "165", "240", "360"));

        createSpec(categoryId, "Brightness", "nits", "Display", false, false, NUMBER, 27);
        createSpec(categoryId, "Color Gamut", "%", "Display", false, false, TEXT, 28);
        createSpec(categoryId, "Contrast Ratio", null, "Display", false, false, TEXT, 29);

        createSpec(categoryId, "Anti-Glare", null, "Display", false, true, BOOLEAN, 30);
        createSpec(categoryId, "Blue Light Filter", null, "Display", false, false, BOOLEAN, 31);

        // ===== GRAPHICS =====
        createSpec(categoryId, "Graphics Type", null, "Graphics", true, true, DROPDOWN, 32,
                List.of("Integrated", "Dedicated", "Hybrid"));

        createSpec(categoryId, "Graphics Card", null, "Graphics", false, true, TEXT, 33);
        createSpec(categoryId, "GPU Brand", null, "Graphics", false, true, DROPDOWN, 34,
                List.of("Intel", "AMD", "NVIDIA", "Apple"));

        createSpec(categoryId, "GPU Series", null, "Graphics", false, true, DROPDOWN, 35,
                List.of("Intel Iris Xe", "Intel UHD", "AMD Radeon", "NVIDIA GeForce RTX", "NVIDIA GeForce GTX", "Apple GPU"));

        createSpec(categoryId, "Graphics Memory", "GB", "Graphics", false, true, DROPDOWN, 36,
                List.of("Shared", "2", "4", "6", "8", "12", "16"));

        createSpec(categoryId, "Ray Tracing", null, "Graphics", false, true, BOOLEAN, 37);
        createSpec(categoryId, "DLSS Support", null, "Graphics", false, false, BOOLEAN, 38);

        // ===== CONNECTIVITY =====
        createSpec(categoryId, "WiFi Standard", null, "Connectivity", false, true, DROPDOWN, 39,
                List.of("802.11n", "802.11ac", "WiFi 5", "WiFi 6", "WiFi 6E", "WiFi 7"));

        createSpec(categoryId, "Bluetooth", null, "Connectivity", false, true, DROPDOWN, 40,
                List.of("4.0", "4.2", "5.0", "5.1", "5.2", "5.3", "5.4"));

        createSpec(categoryId, "Ethernet Port", null, "Connectivity", false, true, BOOLEAN, 41);
        createSpec(categoryId, "Ethernet Speed", null, "Connectivity", false, false, DROPDOWN, 42,
                List.of("Fast Ethernet", "Gigabit"));

        createSpec(categoryId, "USB-A Ports", null, "Connectivity", false, true, DROPDOWN, 43,
                List.of("0", "1", "2", "3", "4"));

        createSpec(categoryId, "USB-C Ports", null, "Connectivity", false, true, DROPDOWN, 44,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "USB-C Features", null, "Connectivity", false, false, MULTI_SELECT, 45,
                List.of("Power Delivery", "DisplayPort Alt Mode", "Thunderbolt 3", "Thunderbolt 4"));

        createSpec(categoryId, "Thunderbolt", null, "Connectivity", false, true, DROPDOWN, 46,
                List.of("None", "Thunderbolt 3", "Thunderbolt 4", "Thunderbolt 5"));

        createSpec(categoryId, "HDMI Port", null, "Display Output", false, true, DROPDOWN, 47,
                List.of("None", "HDMI 1.4", "HDMI 2.0", "HDMI 2.1", "Mini HDMI", "Micro HDMI"));

        createSpec(categoryId, "DisplayPort", null, "Display Output", false, false, DROPDOWN, 48,
                List.of("None", "Mini DisplayPort", "USB-C DisplayPort"));

        createSpec(categoryId, "VGA Port", null, "Display Output", false, false, BOOLEAN, 49);

        createSpec(categoryId, "Audio Jack", null, "Audio", false, true, DROPDOWN, 50,
                List.of("3.5mm Combo", "3.5mm Headphone", "None"));

        createSpec(categoryId, "SD Card Slot", null, "Expansion", false, true, DROPDOWN, 51,
                List.of("None", "SD", "microSD", "SD + microSD"));

        // ===== PHYSICAL CHARACTERISTICS =====
        createSpec(categoryId, "Weight", "kg", "Physical", false, true, DECIMAL, 52);
        createSpec(categoryId, "Thickness", "mm", "Physical", false, true, DECIMAL, 53);
        createSpec(categoryId, "Width", "mm", "Physical", false, false, NUMBER, 54);
        createSpec(categoryId, "Depth", "mm", "Physical", false, false, NUMBER, 55);

        createSpec(categoryId, "Build Material", null, "Build Quality", false, true, DROPDOWN, 56,
                List.of("Plastic", "Aluminum", "Magnesium", "Carbon Fiber", "Glass", "Metal"));

        createSpec(categoryId, "Color", null, "Design", false, true, DROPDOWN, 57,
                List.of("Black", "Silver", "White", "Gray", "Gold", "Rose Gold", "Blue", "Red", "Space Gray"));

        createSpec(categoryId, "Convertible", null, "Design", false, true, BOOLEAN, 58);
        createSpec(categoryId, "2-in-1 Type", null, "Design", false, false, DROPDOWN, 59,
                List.of("Detachable", "360° Hinge", "Dual Screen"));

        // ===== BATTERY & POWER =====
        createSpec(categoryId, "Battery Capacity", "Wh", "Battery", false, true, NUMBER, 60);
        createSpec(categoryId, "Battery Life", "hours", "Battery", false, true, NUMBER, 61);
        createSpec(categoryId, "Battery Cells", null, "Battery", false, false, DROPDOWN, 62,
                List.of("3-cell", "4-cell", "6-cell", "8-cell"));

        createSpec(categoryId, "Fast Charging", null, "Battery", false, true, BOOLEAN, 63);
        createSpec(categoryId, "Charging Power", "W", "Battery", false, false, NUMBER, 64);
        createSpec(categoryId, "USB-C Charging", null, "Battery", false, true, BOOLEAN, 65);

        createSpec(categoryId, "Power Adapter", "W", "Power", false, false, NUMBER, 66);

        // ===== FEATURES =====
        createSpec(categoryId, "Backlit Keyboard", null, "Features", false, true, BOOLEAN, 67);
        createSpec(categoryId, "RGB Keyboard", null, "Features", false, true, BOOLEAN, 68);
        createSpec(categoryId, "Numeric Keypad", null, "Features", false, true, BOOLEAN, 69);

        createSpec(categoryId, "Fingerprint Reader", null, "Security", false, true, BOOLEAN, 70);
        createSpec(categoryId, "Face Recognition", null, "Security", false, true, BOOLEAN, 71);
        createSpec(categoryId, "TPM Chip", null, "Security", false, false, BOOLEAN, 72);
        createSpec(categoryId, "Kensington Lock", null, "Security", false, false, BOOLEAN, 73);

        createSpec(categoryId, "Webcam", null, "Camera", false, true, DROPDOWN, 74,
                List.of("None", "720p", "1080p", "1440p", "4K"));

        createSpec(categoryId, "Webcam Privacy", null, "Camera", false, true, BOOLEAN, 75);
        createSpec(categoryId, "IR Camera", null, "Camera", false, false, BOOLEAN, 76);

        createSpec(categoryId, "Microphone", null, "Audio", false, true, DROPDOWN, 77,
                List.of("Single", "Dual Array", "Quad Array", "None"));

        createSpec(categoryId, "Speakers", null, "Audio", false, true, TEXT, 78);
        createSpec(categoryId, "Audio Enhancement", null, "Audio", false, false, TEXT, 79);

        createSpec(categoryId, "Cooling System", null, "Thermal", false, true, DROPDOWN, 80,
                List.of("Passive", "Single Fan", "Dual Fan", "Liquid Cooling"));

        createSpec(categoryId, "Gaming Features", null, "Gaming", false, true, MULTI_SELECT, 81,
                List.of("High Refresh Display", "Advanced Cooling", "Gaming Mode", "RGB Lighting"));

        createSpec(categoryId, "Business Features", null, "Business", false, false, MULTI_SELECT, 82,
                List.of("vPro Support", "Enterprise Security", "Docking Station", "Remote Management"));

        // ===== MANUFACTURER INFO =====
        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 83,
                List.of("Apple", "ASUS", "Acer", "Dell", "HP", "Lenovo", "MSI", "Razer", "Samsung", "Microsoft", "LG", "Huawei"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 84);
        createSpec(categoryId, "Model Number", null, "Manufacturer", false, false, TEXT, 85);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, true, DROPDOWN, 86,
                List.of("1", "2", "3", "4", "5"));

        createSpec(categoryId, "Support Type", null, "Support", false, false, DROPDOWN, 87,
                List.of("Standard", "Premium", "On-site", "Next Business Day"));
    }

    public void setupLaptopAccessorySpecifications() {
        Long categoryId = getCategoryId("laptop-accessories");

        // ===== LAPTOP BAGS & CASES =====
        createSpec(categoryId, "Accessory Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Laptop Bag", "Laptop Backpack", "Laptop Sleeve", "Hard Case", "Rolling Case",
                        "Messenger Bag", "Briefcase", "Tablet Case", "Universal Case"));

        createSpec(categoryId, "Compatible Screen Size", "inches", "Compatibility", true, true, DROPDOWN, 2,
                List.of("11-12", "13-14", "15-16", "17-18", "Universal", "11.6", "13.3", "14", "15.6", "17.3"));

        createSpec(categoryId, "Material", null, "Build Quality", false, true, DROPDOWN, 3,
                List.of("Nylon", "Polyester", "Canvas", "Leather", "Faux Leather", "Neoprene", "Hard Plastic", "Aluminum"));

        createSpec(categoryId, "Water Resistance", null, "Protection", false, true, DROPDOWN, 4,
                List.of("None", "Water Resistant", "Water Repellent", "Waterproof", "IP Rating"));

        createSpec(categoryId, "Padding", null, "Protection", false, true, DROPDOWN, 5,
                List.of("None", "Light Padding", "Medium Padding", "Heavy Padding", "Memory Foam", "Shock Absorbing"));

        createSpec(categoryId, "Number of Compartments", null, "Organization", false, true, DROPDOWN, 6,
                List.of("1", "2", "3", "4", "5+"));

        createSpec(categoryId, "Additional Pockets", null, "Organization", false, false, DROPDOWN, 7,
                List.of("None", "1-2", "3-5", "5+"));

        createSpec(categoryId, "Carrying Options", null, "Portability", false, true, MULTI_SELECT, 8,
                List.of("Handle", "Shoulder Strap", "Backpack Straps", "Rolling Wheels", "Detachable Strap"));

        createSpec(categoryId, "Color", null, "Design", false, true, DROPDOWN, 9,
                List.of("Black", "Gray", "Brown", "Blue", "Red", "Pink", "White", "Green", "Multi-color"));

        // ===== POWER ADAPTERS & CHARGERS =====
        createSpec(categoryId, "Power Output", "W", "Power Specifications", false, true, DROPDOWN, 10,
                List.of("45", "65", "90", "120", "130", "150", "180", "200", "240"));

        createSpec(categoryId, "Connector Type", null, "Compatibility", false, true, DROPDOWN, 11,
                List.of("USB-C", "Barrel Jack", "MagSafe", "MagSafe 2", "MagSafe 3", "Proprietary", "Universal"));

        createSpec(categoryId, "Cable Length", "m", "Cable", false, true, DROPDOWN, 12,
                List.of("1", "1.5", "2", "2.5", "3", "4", "5"));

        createSpec(categoryId, "Input Voltage", "V", "Electrical", false, false, TEXT, 13);
        createSpec(categoryId, "Output Voltage", "V", "Electrical", false, false, TEXT, 14);

        createSpec(categoryId, "Brand Compatibility", null, "Compatibility", false, true, MULTI_SELECT, 15,
                List.of("Universal", "Apple", "Dell", "HP", "Lenovo", "ASUS", "Acer", "MSI", "Samsung"));

        createSpec(categoryId, "Safety Certifications", null, "Safety", false, false, MULTI_SELECT, 16,
                List.of("CE", "FCC", "UL", "RoHS", "Energy Star"));

        // ===== COOLING ACCESSORIES =====
        createSpec(categoryId, "Cooling Type", null, "Cooling", false, true, DROPDOWN, 17,
                List.of("Laptop Stand", "Cooling Pad", "External Fan", "Thermal Pad", "Laptop Riser"));

        createSpec(categoryId, "Fan Count", null, "Cooling", false, true, DROPDOWN, 18,
                List.of("0", "1", "2", "3", "4", "5", "6+"));

        createSpec(categoryId, "Fan Size", "mm", "Cooling", false, false, DROPDOWN, 19,
                List.of("60", "80", "120", "140"));

        createSpec(categoryId, "Fan Speed", "RPM", "Cooling", false, false, TEXT, 20);
        createSpec(categoryId, "Noise Level", "dB", "Cooling", false, false, DECIMAL, 21);

        createSpec(categoryId, "Height Adjustment", null, "Ergonomics", false, true, BOOLEAN, 22);
        createSpec(categoryId, "Angle Adjustment", null, "Ergonomics", false, true, BOOLEAN, 23);
        createSpec(categoryId, "Foldable", null, "Portability", false, true, BOOLEAN, 24);

        // ===== DOCKING STATIONS =====
        createSpec(categoryId, "Docking Connection", null, "Docking", false, true, DROPDOWN, 25,
                List.of("USB-C", "Thunderbolt 3", "Thunderbolt 4", "USB 3.0", "Proprietary"));

        createSpec(categoryId, "Video Outputs", null, "Display", false, true, DROPDOWN, 26,
                List.of("1", "2", "3", "4"));

        createSpec(categoryId, "HDMI Ports", null, "Display", false, true, DROPDOWN, 27,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "DisplayPort Outputs", null, "Display", false, false, DROPDOWN, 28,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "Max Resolution", null, "Display", false, true, DROPDOWN, 29,
                List.of("1920x1080", "2560x1440", "3840x2160", "5120x2880", "Dual 4K"));

        createSpec(categoryId, "USB-A Ports", null, "Connectivity", false, true, DROPDOWN, 30,
                List.of("0", "2", "4", "6", "8"));

        createSpec(categoryId, "USB-C Ports", null, "Connectivity", false, true, DROPDOWN, 31,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "Ethernet Port", null, "Networking", false, true, DROPDOWN, 32,
                List.of("None", "Gigabit", "2.5G"));

        createSpec(categoryId, "Audio Ports", null, "Audio", false, false, DROPDOWN, 33,
                List.of("None", "3.5mm", "Microphone + Headphone"));

        createSpec(categoryId, "Power Delivery", "W", "Power", false, true, DROPDOWN, 34,
                List.of("60", "85", "90", "96", "100"));

        // ===== SECURITY ACCESSORIES =====
        createSpec(categoryId, "Security Type", null, "Security", false, true, DROPDOWN, 35,
                List.of("Cable Lock", "Combination Lock", "Key Lock", "Biometric Lock", "Smart Lock"));

        createSpec(categoryId, "Lock Type", null, "Security", false, false, DROPDOWN, 36,
                List.of("Kensington", "Noble Wedge", "Cable Loop", "Adhesive Anchor"));

        createSpec(categoryId, "Cable Length", "m", "Security", false, false, DROPDOWN, 37,
                List.of("1", "1.5", "2", "2.5", "3"));

        createSpec(categoryId, "Security Level", null, "Security", false, true, DROPDOWN, 38,
                List.of("Basic", "Standard", "High", "Ultra High"));

        // ===== CLEANING & MAINTENANCE =====
        createSpec(categoryId, "Cleaning Type", null, "Maintenance", false, true, DROPDOWN, 39,
                List.of("Screen Cleaner", "Keyboard Cleaner", "Compressed Air", "Cleaning Kit", "Microfiber Cloth"));

        createSpec(categoryId, "Screen Protection", null, "Protection", false, true, DROPDOWN, 40,
                List.of("Screen Protector", "Privacy Filter", "Anti-Glare Filter", "Blue Light Filter"));

        createSpec(categoryId, "Keyboard Protection", null, "Protection", false, false, DROPDOWN, 41,
                List.of("Keyboard Cover", "Silicone Protector", "TPU Cover"));

        // ===== UNIVERSAL SPECIFICATIONS =====
        createSpec(categoryId, "Weight", "g", "Physical", false, false, NUMBER, 42);
        createSpec(categoryId, "Dimensions", "mm", "Physical", false, false, TEXT, 43);

        createSpec(categoryId, "Universal Compatibility", null, "Compatibility", false, true, BOOLEAN, 44);
        createSpec(categoryId, "Foldable/Portable", null, "Portability", false, true, BOOLEAN, 45);

        createSpec(categoryId, "LED Indicators", null, "Features", false, false, BOOLEAN, 46);
        createSpec(categoryId, "Button Controls", null, "Features", false, false, BOOLEAN, 47);

        createSpec(categoryId, "Operating Temperature", "°C", "Environmental", false, false, TEXT, 48);
        createSpec(categoryId, "Storage Temperature", "°C", "Environmental", false, false, TEXT, 49);

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 50,
                List.of("Targus", "Case Logic", "Thule", "Samsonite", "Dell", "HP", "Lenovo", "Belkin", "Kensington", "Cooler Master"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 51);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, false, DROPDOWN, 52,
                List.of("1", "2", "3", "5", "Lifetime"));

        createSpec(categoryId, "Price Range", null, "Pricing", false, true, DROPDOWN, 53,
                List.of("Budget", "Mid-range", "Premium", "Enterprise"));
    }

    public void setupDesktopPCSpecifications() {
        Long categoryId = getCategoryId("desktop-pcs");

        createSpec(categoryId, "PC Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Gaming PC", "Office PC", "Workstation", "Home PC", "Mini PC", "All-in-One"));

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("Tower", "Mini Tower", "Micro Tower", "Small Form Factor", "Ultra Small", "All-in-One"));

        createSpec(categoryId, "Processor", null, "Performance", true, true, TEXT, 3);
        createSpec(categoryId, "Processor Brand", null, "Performance", false, true, DROPDOWN, 4,
                List.of("Intel", "AMD"));

        createSpec(categoryId, "Processor Series", null, "Performance", false, true, DROPDOWN, 5,
                List.of("Core i3", "Core i5", "Core i7", "Core i9", "Ryzen 3", "Ryzen 5", "Ryzen 7", "Ryzen 9"));

        createSpec(categoryId, "Processor Cores", null, "Performance", false, true, DROPDOWN, 6,
                List.of("2", "4", "6", "8", "10", "12", "16", "20", "24"));

        createSpec(categoryId, "Base Clock", "GHz", "Performance", false, false, DECIMAL, 7);
        createSpec(categoryId, "Boost Clock", "GHz", "Performance", false, false, DECIMAL, 8);

        createSpec(categoryId, "RAM", "GB", "Memory", true, true, DROPDOWN, 9,
                List.of("4", "8", "16", "32", "64", "128"));

        createSpec(categoryId, "RAM Type", null, "Memory", false, true, DROPDOWN, 10,
                List.of("DDR4", "DDR5"));

        createSpec(categoryId, "RAM Speed", "MHz", "Memory", false, false, DROPDOWN, 11,
                List.of("2400", "2666", "3200", "3600", "4800", "5600"));

        createSpec(categoryId, "Max RAM", "GB", "Memory", false, false, DROPDOWN, 12,
                List.of("32", "64", "128", "256"));

        createSpec(categoryId, "Storage", "GB", "Storage", true, true, DROPDOWN, 13,
                List.of("256", "512", "1000", "1024", "2000", "2048", "4096"));

        createSpec(categoryId, "Storage Type", null, "Storage", true, true, DROPDOWN, 14,
                List.of("SSD", "HDD", "SSD + HDD", "NVMe SSD", "Hybrid"));

        createSpec(categoryId, "Primary Storage", null, "Storage", false, false, TEXT, 15);
        createSpec(categoryId, "Secondary Storage", null, "Storage", false, false, TEXT, 16);

        createSpec(categoryId, "Graphics", null, "Graphics", true, true, DROPDOWN, 17,
                List.of("Integrated", "Dedicated", "Workstation"));

        createSpec(categoryId, "Graphics Card", null, "Graphics", false, true, TEXT, 18);
        createSpec(categoryId, "Graphics Memory", "GB", "Graphics", false, true, DROPDOWN, 19,
                List.of("Shared", "2", "4", "6", "8", "12", "16", "24"));

        createSpec(categoryId, "Motherboard", null, "Components", false, false, TEXT, 20);
        createSpec(categoryId, "Chipset", null, "Components", false, false, TEXT, 21);

        createSpec(categoryId, "Power Supply", "W", "Power", false, true, DROPDOWN, 22,
                List.of("300", "400", "450", "500", "550", "600", "650", "700", "750", "800", "850", "1000"));

        createSpec(categoryId, "PSU Efficiency", null, "Power", false, false, DROPDOWN, 23,
                List.of("80 PLUS", "80 PLUS Bronze", "80 PLUS Gold", "80 PLUS Platinum"));

        createSpec(categoryId, "Case Type", null, "Case", false, false, TEXT, 24);
        createSpec(categoryId, "Case Material", null, "Case", false, false, DROPDOWN, 25,
                List.of("Steel", "Aluminum", "Plastic", "Tempered Glass"));

        createSpec(categoryId, "Optical Drive", null, "Optical", false, true, DROPDOWN, 26,
                List.of("None", "DVD-RW", "Blu-ray Reader", "Blu-ray Writer"));

        createSpec(categoryId, "Operating System", null, "Software", false, true, DROPDOWN, 27,
                List.of("Windows 11 Home", "Windows 11 Pro", "Windows 10", "No OS", "Linux", "FreeDOS"));

        createSpec(categoryId, "Pre-installed Software", null, "Software", false, false, BOOLEAN, 28);

        createSpec(categoryId, "WiFi", null, "Connectivity", false, true, DROPDOWN, 29,
                List.of("None", "WiFi 5", "WiFi 6", "WiFi 6E"));

        createSpec(categoryId, "Bluetooth", null, "Connectivity", false, true, DROPDOWN, 30,
                List.of("None", "4.2", "5.0", "5.1", "5.2", "5.3"));

        createSpec(categoryId, "Ethernet", null, "Connectivity", false, true, DROPDOWN, 31,
                List.of("Fast Ethernet", "Gigabit", "2.5G"));

        createSpec(categoryId, "USB 2.0 Ports", null, "I/O Ports", false, false, DROPDOWN, 32,
                List.of("0", "2", "4", "6"));

        createSpec(categoryId, "USB 3.0 Ports", null, "I/O Ports", false, true, DROPDOWN, 33,
                List.of("2", "4", "6", "8"));

        createSpec(categoryId, "USB-C Ports", null, "I/O Ports", false, true, DROPDOWN, 34,
                List.of("0", "1", "2", "4"));

        createSpec(categoryId, "Audio Ports", null, "I/O Ports", false, false, DROPDOWN, 35,
                List.of("Front + Rear", "Rear Only", "5.1 Audio", "7.1 Audio"));

        createSpec(categoryId, "Video Outputs", null, "Display", false, true, MULTI_SELECT, 36,
                List.of("HDMI", "DisplayPort", "DVI", "VGA", "USB-C"));

        createSpec(categoryId, "Max Displays", null, "Display", false, false, DROPDOWN, 37,
                List.of("1", "2", "3", "4", "6"));

        createSpec(categoryId, "4K Support", null, "Display", false, true, BOOLEAN, 38);

        createSpec(categoryId, "Keyboard Included", null, "Peripherals", false, true, BOOLEAN, 39);
        createSpec(categoryId, "Mouse Included", null, "Peripherals", false, true, BOOLEAN, 40);
        createSpec(categoryId, "Speakers Included", null, "Peripherals", false, false, BOOLEAN, 41);

        createSpec(categoryId, "Gaming Ready", null, "Gaming", false, true, BOOLEAN, 42);
        createSpec(categoryId, "VR Ready", null, "Gaming", false, false, BOOLEAN, 43);
        createSpec(categoryId, "Ray Tracing", null, "Gaming", false, true, BOOLEAN, 44);

        createSpec(categoryId, "Noise Level", "dB", "Acoustics", false, false, DECIMAL, 45);
        createSpec(categoryId, "Power Consumption", "W", "Power", false, false, NUMBER, 46);

        createSpec(categoryId, "Height", "mm", "Physical Dimensions", false, false, NUMBER, 47);
        createSpec(categoryId, "Width", "mm", "Physical Dimensions", false, false, NUMBER, 48);
        createSpec(categoryId, "Depth", "mm", "Physical Dimensions", false, false, NUMBER, 49);
        createSpec(categoryId, "Weight", "kg", "Physical Dimensions", false, false, DECIMAL, 50);

        createSpec(categoryId, "Energy Star", null, "Certifications", false, false, BOOLEAN, 51);
        createSpec(categoryId, "EPEAT Rating", null, "Certifications", false, false, DROPDOWN, 52,
                List.of("Bronze", "Silver", "Gold"));

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 53,
                List.of("HP", "Dell", "Lenovo", "ASUS", "Acer", "MSI", "Alienware", "Origin PC", "Falcon Northwest"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 54);

        createSpec(categoryId, "Color", null, "Design", false, true, DROPDOWN, 55,
                List.of("Black", "White", "Silver", "Gray", "RGB"));

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, true, DROPDOWN, 56,
                List.of("1", "2", "3", "4", "5"));

        createSpec(categoryId, "On-site Support", null, "Support", false, false, BOOLEAN, 57);
    }

    public void setupServerSpecifications() {
        Long categoryId = getCategoryId("servers");

        createSpec(categoryId, "Server Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Rack Server", "Tower Server", "Blade Server", "Micro Server", "Edge Server"));

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("1U", "2U", "3U", "4U", "5U", "Tower", "Blade", "Mini"));

        createSpec(categoryId, "Rack Units", "U", "Physical", false, true, DROPDOWN, 3,
                List.of("1", "2", "3", "4", "5", "6", "7", "8"));

        createSpec(categoryId, "Processor", null, "Performance", true, true, TEXT, 4);
        createSpec(categoryId, "Processor Family", null, "Performance", false, true, DROPDOWN, 5,
                List.of("Intel Xeon", "AMD EPYC", "Intel Core", "AMD Ryzen", "ARM"));

        createSpec(categoryId, "Socket Type", null, "Performance", false, false, DROPDOWN, 6,
                List.of("LGA3647", "LGA4189", "SP3", "SP5", "LGA1700", "AM4", "AM5"));

        createSpec(categoryId, "Processor Count", null, "Performance", false, true, DROPDOWN, 7,
                List.of("1", "2", "4", "8"));

        createSpec(categoryId, "Cores per CPU", null, "Performance", false, true, DROPDOWN, 8,
                List.of("4", "6", "8", "12", "16", "18", "20", "24", "28", "32", "48", "64"));

        createSpec(categoryId, "Total Cores", null, "Performance", false, true, DROPDOWN, 9,
                List.of("4", "8", "16", "24", "32", "48", "64", "96", "128", "256"));

        createSpec(categoryId, "Base Clock", "GHz", "Performance", false, false, DECIMAL, 10);
        createSpec(categoryId, "Max Turbo", "GHz", "Performance", false, false, DECIMAL, 11);

        createSpec(categoryId, "RAM", "GB", "Memory", true, true, DROPDOWN, 12,
                List.of("8", "16", "32", "64", "128", "256", "512", "1024", "2048", "4096"));

        createSpec(categoryId, "Memory Type", null, "Memory", false, true, DROPDOWN, 13,
                List.of("DDR4", "DDR5", "DDR4 ECC", "DDR5 ECC", "RDIMM", "LRDIMM"));

        createSpec(categoryId, "Memory Speed", "MHz", "Memory", false, false, DROPDOWN, 14,
                List.of("2133", "2400", "2666", "2933", "3200", "4800", "5600"));

        createSpec(categoryId, "Max Memory", "TB", "Memory", false, true, DROPDOWN, 15,
                List.of("1", "2", "4", "6", "8", "12", "16", "24", "32"));

        createSpec(categoryId, "Memory Slots", null, "Memory", false, false, DROPDOWN, 16,
                List.of("4", "8", "12", "16", "24", "32", "48"));

        createSpec(categoryId, "ECC Memory", null, "Memory", false, true, BOOLEAN, 17);
        createSpec(categoryId, "Registered Memory", null, "Memory", false, false, BOOLEAN, 18);

        createSpec(categoryId, "Storage", null, "Storage", false, true, TEXT, 19);
        createSpec(categoryId, "Storage Type", null, "Storage", false, true, DROPDOWN, 20,
                List.of("No Storage", "HDD", "SSD", "NVMe", "SAS", "Mixed"));

        createSpec(categoryId, "Drive Bays 3.5\"", null, "Storage", false, true, DROPDOWN, 21,
                List.of("0", "2", "4", "8", "12", "16", "24"));

        createSpec(categoryId, "Drive Bays 2.5\"", null, "Storage", false, true, DROPDOWN, 22,
                List.of("0", "4", "8", "12", "16", "24", "32"));

        createSpec(categoryId, "Hot-Swap Drives", null, "Storage", false, true, BOOLEAN, 23);
        createSpec(categoryId, "RAID Support", null, "Storage", false, true, BOOLEAN, 24);
        createSpec(categoryId, "RAID Levels", null, "Storage", false, false, MULTI_SELECT, 25,
                List.of("RAID 0", "RAID 1", "RAID 5", "RAID 6", "RAID 10", "RAID 50", "RAID 60"));

        createSpec(categoryId, "Graphics", null, "Graphics", false, false, DROPDOWN, 26,
                List.of("Integrated", "Discrete", "None"));

        createSpec(categoryId, "GPU", null, "Graphics", false, false, TEXT, 27);
        createSpec(categoryId, "GPU Count", null, "Graphics", false, false, DROPDOWN, 28,
                List.of("0", "1", "2", "4", "8"));

        createSpec(categoryId, "Expansion Slots", null, "Expansion", false, true, DROPDOWN, 29,
                List.of("0", "2", "4", "6", "8", "10", "12"));

        createSpec(categoryId, "PCIe Slots", null, "Expansion", false, false, TEXT, 30);
        createSpec(categoryId, "PCIe Gen", null, "Expansion", false, false, DROPDOWN, 31,
                List.of("3.0", "4.0", "5.0"));

        createSpec(categoryId, "Power Supply", "W", "Power", false, true, DROPDOWN, 32,
                List.of("300", "400", "500", "650", "750", "800", "1000", "1200", "1600", "2000", "2400"));

        createSpec(categoryId, "PSU Count", null, "Power", false, true, DROPDOWN, 33,
                List.of("1", "2"));

        createSpec(categoryId, "PSU Redundancy", null, "Power", false, true, BOOLEAN, 34);
        createSpec(categoryId, "Hot-Swap PSU", null, "Power", false, false, BOOLEAN, 35);

        createSpec(categoryId, "Power Efficiency", null, "Power", false, false, DROPDOWN, 36,
                List.of("80 PLUS", "80 PLUS Bronze", "80 PLUS Gold", "80 PLUS Platinum", "80 PLUS Titanium"));

        createSpec(categoryId, "Ethernet Ports", null, "Networking", false, true, DROPDOWN, 37,
                List.of("1", "2", "4", "8"));

        createSpec(categoryId, "Ethernet Speed", null, "Networking", false, true, DROPDOWN, 38,
                List.of("Gigabit", "2.5G", "10G", "25G", "40G", "100G"));

        createSpec(categoryId, "Network Interface", null, "Networking", false, false, DROPDOWN, 39,
                List.of("RJ45", "SFP+", "QSFP+", "SFP28"));

        createSpec(categoryId, "Remote Management", null, "Management", false, true, DROPDOWN, 40,
                List.of("iDRAC", "iLO", "IPMI", "BMC"));

        createSpec(categoryId, "KVM Support", null, "Management", false, false, BOOLEAN, 41);
        createSpec(categoryId, "Serial Console", null, "Management", false, false, BOOLEAN, 42);

        createSpec(categoryId, "USB Ports", null, "I/O", false, false, DROPDOWN, 43,
                List.of("2", "4", "6", "8"));

        createSpec(categoryId, "VGA Port", null, "I/O", false, false, BOOLEAN, 44);
        createSpec(categoryId, "Serial Port", null, "I/O", false, false, BOOLEAN, 45);

        createSpec(categoryId, "Operating System", null, "Software", false, true, DROPDOWN, 46,
                List.of("No OS", "Windows Server 2019", "Windows Server 2022", "Linux", "VMware ESXi", "Hyper-V"));

        createSpec(categoryId, "Virtualization Ready", null, "Virtualization", false, true, BOOLEAN, 47);
        createSpec(categoryId, "Hypervisor Support", null, "Virtualization", false, false, MULTI_SELECT, 48,
                List.of("VMware", "Hyper-V", "KVM", "Xen", "Citrix"));

        createSpec(categoryId, "TPM Support", null, "Security", false, true, BOOLEAN, 49);
        createSpec(categoryId, "Secure Boot", null, "Security", false, false, BOOLEAN, 50);
        createSpec(categoryId, "Hardware Encryption", null, "Security", false, false, BOOLEAN, 51);

        createSpec(categoryId, "Cooling", null, "Cooling", false, false, TEXT, 52);
        createSpec(categoryId, "Fan Count", null, "Cooling", false, false, DROPDOWN, 53,
                List.of("2", "4", "6", "8", "10"));

        createSpec(categoryId, "Hot-Swap Fans", null, "Cooling", false, false, BOOLEAN, 54);
        createSpec(categoryId, "Redundant Cooling", null, "Cooling", false, false, BOOLEAN, 55);

        createSpec(categoryId, "Noise Level", "dB", "Acoustics", false, false, DECIMAL, 56);
        createSpec(categoryId, "Operating Temperature", "°C", "Environmental", false, false, TEXT, 57);

        createSpec(categoryId, "Width", "mm", "Physical Dimensions", false, false, NUMBER, 58);
        createSpec(categoryId, "Depth", "mm", "Physical Dimensions", false, false, NUMBER, 59);
        createSpec(categoryId, "Height", "mm", "Physical Dimensions", false, false, NUMBER, 60);
        createSpec(categoryId, "Weight", "kg", "Physical Dimensions", false, false, DECIMAL, 61);

        createSpec(categoryId, "Energy Star", null, "Certifications", false, false, BOOLEAN, 62);
        createSpec(categoryId, "Compliance", null, "Certifications", false, false, MULTI_SELECT, 63,
                List.of("FCC", "CE", "UL", "CSA", "BSMI"));

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 64,
                List.of("Dell", "HP", "Lenovo", "Supermicro", "Cisco", "HPE", "IBM", "Fujitsu", "Huawei"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 65);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, true, DROPDOWN, 66,
                List.of("1", "3", "5"));

        createSpec(categoryId, "Support Level", null, "Support", false, false, DROPDOWN, 67,
                List.of("Basic", "ProSupport", "Mission Critical"));
    }

    public void setupThinClientSpecifications() {
        Long categoryId = getCategoryId("thin-clients");

        createSpec(categoryId, "Client Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Thin Client", "Zero Client", "Smart Client", "Hybrid Client"));

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("Desktop", "Ultra Small", "Micro", "Stick PC", "All-in-One", "Mobile"));

        createSpec(categoryId, "Processor", null, "Performance", true, true, TEXT, 3);
        createSpec(categoryId, "Processor Architecture", null, "Performance", false, true, DROPDOWN, 4,
                List.of("x86", "ARM", "x64"));

        createSpec(categoryId, "Processor Brand", null, "Performance", false, true, DROPDOWN, 5,
                List.of("Intel", "AMD", "ARM", "Qualcomm", "Rockchip"));

        createSpec(categoryId, "Cores", null, "Performance", false, true, DROPDOWN, 6,
                List.of("2", "4", "6", "8"));

        createSpec(categoryId, "Base Clock", "GHz", "Performance", false, false, DECIMAL, 7);
        createSpec(categoryId, "Max Clock", "GHz", "Performance", false, false, DECIMAL, 8);

        createSpec(categoryId, "RAM", "GB", "Memory", true, true, DROPDOWN, 9,
                List.of("1", "2", "4", "8", "16", "32"));

        createSpec(categoryId, "Memory Type", null, "Memory", false, false, DROPDOWN, 10,
                List.of("DDR3", "DDR4", "DDR5", "LPDDR4", "LPDDR5"));

        createSpec(categoryId, "Memory Expandable", null, "Memory", false, true, BOOLEAN, 11);
        createSpec(categoryId, "Max Memory", "GB", "Memory", false, false, DROPDOWN, 12,
                List.of("4", "8", "16", "32"));

        createSpec(categoryId, "Storage", "GB", "Storage", false, true, DROPDOWN, 13,
                List.of("0", "8", "16", "32", "64", "128", "256", "512"));

        createSpec(categoryId, "Storage Type", null, "Storage", false, true, DROPDOWN, 14,
                List.of("No Storage", "eMMC", "SSD", "Flash", "HDD"));

        createSpec(categoryId, "Network Boot", null, "Storage", false, true, BOOLEAN, 15);
        createSpec(categoryId, "Cloud Storage", null, "Storage", false, false, BOOLEAN, 16);

        createSpec(categoryId, "Graphics", null, "Graphics", false, true, DROPDOWN, 17,
                List.of("Integrated", "Shared", "Dedicated"));

        createSpec(categoryId, "GPU", null, "Graphics", false, false, TEXT, 18);
        createSpec(categoryId, "Video Memory", "MB", "Graphics", false, false, DROPDOWN, 19,
                List.of("128", "256", "512", "1024", "2048"));

        createSpec(categoryId, "Max Resolution", null, "Display Support", false, true, DROPDOWN, 20,
                List.of("1920x1080", "2560x1440", "3840x2160", "7680x4320"));

        createSpec(categoryId, "Display Outputs", null, "Display Support", false, true, DROPDOWN, 21,
                List.of("1", "2", "3", "4"));

        createSpec(categoryId, "HDMI Ports", null, "Display Connectivity", false, true, DROPDOWN, 22,
                List.of("0", "1", "2"));

        createSpec(categoryId, "DisplayPort", null, "Display Connectivity", false, true, DROPDOWN, 23,
                List.of("0", "1", "2"));

        createSpec(categoryId, "VGA Port", null, "Display Connectivity", false, false, DROPDOWN, 24,
                List.of("0", "1"));

        createSpec(categoryId, "DVI Port", null, "Display Connectivity", false, false, DROPDOWN, 25,
                List.of("0", "1"));

        createSpec(categoryId, "4K Support", null, "Display Support", false, true, BOOLEAN, 26);
        createSpec(categoryId, "Multi-Monitor", null, "Display Support", false, true, BOOLEAN, 27);

        createSpec(categoryId, "Ethernet", null, "Network Connectivity", true, true, DROPDOWN, 28,
                List.of("Fast Ethernet", "Gigabit"));

        createSpec(categoryId, "WiFi", null, "Network Connectivity", false, true, DROPDOWN, 29,
                List.of("None", "802.11n", "802.11ac", "WiFi 6"));

        createSpec(categoryId, "Bluetooth", null, "Network Connectivity", false, true, DROPDOWN, 30,
                List.of("None", "4.0", "4.2", "5.0", "5.1"));

        createSpec(categoryId, "Remote Protocols", null, "Remote Access", false, true, MULTI_SELECT, 31,
                List.of("RDP", "VDI", "Citrix", "VMware Horizon", "Amazon WorkSpaces", "VNC"));

        createSpec(categoryId, "Virtualization Support", null, "Remote Access", false, true, MULTI_SELECT, 32,
                List.of("Citrix XenDesktop", "VMware View", "Microsoft RDS", "Amazon WorkSpaces", "Parallels RAS"));

        createSpec(categoryId, "Operating System", null, "Software", false, true, DROPDOWN, 33,
                List.of("Windows 10 IoT", "Windows 11 IoT", "Linux", "Chrome OS", "Proprietary", "No OS"));

        createSpec(categoryId, "Management Software", null, "Software", false, true, BOOLEAN, 34);
        createSpec(categoryId, "Remote Management", null, "Software", false, false, BOOLEAN, 35);

        createSpec(categoryId, "USB 2.0 Ports", null, "I/O Connectivity", false, false, DROPDOWN, 36,
                List.of("0", "2", "4"));

        createSpec(categoryId, "USB 3.0 Ports", null, "I/O Connectivity", false, true, DROPDOWN, 37,
                List.of("0", "2", "4", "6"));

        createSpec(categoryId, "USB-C Ports", null, "I/O Connectivity", false, true, DROPDOWN, 38,
                List.of("0", "1", "2"));

        createSpec(categoryId, "Audio Jack", null, "I/O Connectivity", false, true, DROPDOWN, 39,
                List.of("None", "3.5mm", "Combo"));

        createSpec(categoryId, "Serial Port", null, "I/O Connectivity", false, false, BOOLEAN, 40);
        createSpec(categoryId, "Parallel Port", null, "I/O Connectivity", false, false, BOOLEAN, 41);

        createSpec(categoryId, "SD Card Slot", null, "Expansion", false, false, BOOLEAN, 42);
        createSpec(categoryId, "Smart Card Reader", null, "Security", false, false, BOOLEAN, 43);

        createSpec(categoryId, "Power Consumption", "W", "Power", false, true, DROPDOWN, 44,
                List.of("5", "10", "15", "20", "25", "30", "35", "40"));

        createSpec(categoryId, "Power Supply", null, "Power", false, false, DROPDOWN, 45,
                List.of("External Adapter", "PoE", "PoE+", "Internal"));

        createSpec(categoryId, "PoE Support", null, "Power", false, true, BOOLEAN, 46);
        createSpec(categoryId, "PoE+ Support", null, "Power", false, false, BOOLEAN, 47);

        createSpec(categoryId, "Fanless Design", null, "Cooling", false, true, BOOLEAN, 48);
        createSpec(categoryId, "Silent Operation", null, "Cooling", false, false, BOOLEAN, 49);

        createSpec(categoryId, "Operating Temperature", "°C", "Environmental", false, false, TEXT, 50);
        createSpec(categoryId, "Storage Temperature", "°C", "Environmental", false, false, TEXT, 51);
        createSpec(categoryId, "Humidity", "%", "Environmental", false, false, TEXT, 52);

        createSpec(categoryId, "VESA Mount", null, "Mounting", false, true, DROPDOWN, 53,
                List.of("75x75", "100x100"));

        createSpec(categoryId, "Wall Mount", null, "Mounting", false, false, BOOLEAN, 54);
        createSpec(categoryId, "Desk Mount", null, "Mounting", false, false, BOOLEAN, 55);

        createSpec(categoryId, "Security Features", null, "Security", false, true, MULTI_SELECT, 56,
                List.of("TPM", "Secure Boot", "Kensington Lock", "Chassis Intrusion"));

        createSpec(categoryId, "Encryption Support", null, "Security", false, false, BOOLEAN, 57);
        createSpec(categoryId, "Biometric Support", null, "Security", false, false, BOOLEAN, 58);

        createSpec(categoryId, "Width", "mm", "Physical Dimensions", false, false, NUMBER, 59);
        createSpec(categoryId, "Depth", "mm", "Physical Dimensions", false, false, NUMBER, 60);
        createSpec(categoryId, "Height", "mm", "Physical Dimensions", false, false, NUMBER, 61);
        createSpec(categoryId, "Weight", "g", "Physical Dimensions", false, false, NUMBER, 62);

        createSpec(categoryId, "Energy Star", null, "Certifications", false, false, BOOLEAN, 63);
        createSpec(categoryId, "RoHS Compliant", null, "Certifications", false, false, BOOLEAN, 64);

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 65,
                List.of("HP", "Dell", "Lenovo", "IGEL", "Stratodesk", "10ZiG", "Praim", "VXL", "NComputing"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 66);

        createSpec(categoryId, "Color", null, "Design", false, false, DROPDOWN, 67,
                List.of("Black", "White", "Silver", "Gray"));

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, true, DROPDOWN, 68,
                List.of("1", "2", "3", "5"));

        createSpec(categoryId, "Support Level", null, "Support", false, false, DROPDOWN, 69,
                List.of("Basic", "Extended", "Premium"));
    }

    public void setupPowerSupplySpecifications() {
        Long categoryId = getCategoryId("power-supplies");

        createSpec(categoryId, "Wattage", "W", "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("300", "400", "450", "500", "550", "600", "650", "700", "750", "800", "850", "1000", "1200", "1300", "1600", "2000"));

        createSpec(categoryId, "Efficiency Rating", null, "Efficiency", true, true, DROPDOWN, 2,
                List.of("80 PLUS", "80 PLUS Bronze", "80 PLUS Silver", "80 PLUS Gold", "80 PLUS Platinum", "80 PLUS Titanium"));

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 3,
                List.of("ATX", "SFX", "SFX-L", "TFX", "Flex ATX"));

        createSpec(categoryId, "Modular", null, "Cable Management", true, true, DROPDOWN, 4,
                List.of("Non-Modular", "Semi-Modular", "Fully Modular"));

        createSpec(categoryId, "Input Voltage", "V", "Electrical", false, false, DROPDOWN, 5,
                List.of("115V", "230V", "115-230V Auto"));

        createSpec(categoryId, "PFC", null, "Power Factor", false, false, DROPDOWN, 6,
                List.of("Active PFC", "Passive PFC"));

        createSpec(categoryId, "24-pin Connector", null, "Connectors", false, false, BOOLEAN, 7);
        createSpec(categoryId, "CPU 8-pin Connectors", null, "Connectors", false, true, DROPDOWN, 8,
                List.of("1", "2", "3"));

        createSpec(categoryId, "CPU 4-pin Connectors", null, "Connectors", false, false, DROPDOWN, 9,
                List.of("0", "1", "2"));

        createSpec(categoryId, "PCIe 8-pin Connectors", null, "GPU Connectors", false, true, DROPDOWN, 10,
                List.of("0", "2", "4", "6", "8", "10"));

        createSpec(categoryId, "PCIe 6-pin Connectors", null, "GPU Connectors", false, false, DROPDOWN, 11,
                List.of("0", "2", "4", "6"));

        createSpec(categoryId, "12VHPWR Connectors", null, "GPU Connectors", false, true, DROPDOWN, 12,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "SATA Connectors", null, "Storage Connectors", false, true, DROPDOWN, 13,
                List.of("4", "6", "8", "10", "12", "16"));

        createSpec(categoryId, "Molex Connectors", null, "Storage Connectors", false, false, DROPDOWN, 14,
                List.of("2", "4", "6", "8"));

        createSpec(categoryId, "+12V Rail", "A", "Power Rails", false, false, DECIMAL, 15);
        createSpec(categoryId, "+5V Rail", "A", "Power Rails", false, false, DECIMAL, 16);
        createSpec(categoryId, "+3.3V Rail", "A", "Power Rails", false, false, DECIMAL, 17);

        createSpec(categoryId, "Single Rail", null, "Rail Design", false, true, BOOLEAN, 18);
        createSpec(categoryId, "Multi Rail", null, "Rail Design", false, false, BOOLEAN, 19);

        createSpec(categoryId, "Fan Size", "mm", "Cooling", false, true, DROPDOWN, 20,
                List.of("80", "92", "120", "135", "140"));

        createSpec(categoryId, "Fan Type", null, "Cooling", false, true, DROPDOWN, 21,
                List.of("Sleeve Bearing", "Ball Bearing", "Fluid Dynamic Bearing", "Magnetic Levitation"));

        createSpec(categoryId, "Zero RPM Mode", null, "Cooling", false, true, BOOLEAN, 22);
        createSpec(categoryId, "Semi-Fanless", null, "Cooling", false, false, BOOLEAN, 23);

        createSpec(categoryId, "Noise Level", "dB", "Acoustics", false, true, DECIMAL, 24);

        createSpec(categoryId, "Length", "mm", "Physical Dimensions", false, true, NUMBER, 25);
        createSpec(categoryId, "Width", "mm", "Physical Dimensions", false, false, NUMBER, 26);
        createSpec(categoryId, "Height", "mm", "Physical Dimensions", false, false, NUMBER, 27);
        createSpec(categoryId, "Weight", "kg", "Physical Dimensions", false, false, DECIMAL, 28);

        createSpec(categoryId, "Protections", null, "Safety Features", false, true, MULTI_SELECT, 29,
                List.of("OVP", "UVP", "OCP", "OPP", "SCP", "OTP"));

        createSpec(categoryId, "MTBF", "hours", "Reliability", false, false, NUMBER, 30);

        createSpec(categoryId, "Operating Temperature", "°C", "Environmental", false, false, TEXT, 31);
        createSpec(categoryId, "Storage Temperature", "°C", "Environmental", false, false, TEXT, 32);
        createSpec(categoryId, "Humidity", "%", "Environmental", false, false, TEXT, 33);

        createSpec(categoryId, "Certifications", null, "Standards", false, false, MULTI_SELECT, 34,
                List.of("CE", "FCC", "UL", "TÜV", "CCC", "BSMI"));

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 35,
                List.of("Corsair", "EVGA", "Seasonic", "Cooler Master", "Thermaltake", "be quiet!", "Antec", "FSP", "Silverstone"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 36);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, true, DROPDOWN, 37,
                List.of("1", "2", "3", "5", "7", "10", "12"));
    }

    public void setupComputerCaseSpecifications() {
        Long categoryId = getCategoryId("computer-cases");

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Mini-ITX", "Micro-ATX", "Mid Tower", "Full Tower", "Super Tower", "HTPC", "Cube"));

        createSpec(categoryId, "Motherboard Support", null, "Compatibility", true, true, MULTI_SELECT, 2,
                List.of("Mini-ITX", "Micro-ATX", "ATX", "E-ATX", "XL-ATX"));

        createSpec(categoryId, "Material", null, "Build Quality", false, true, DROPDOWN, 3,
                List.of("Steel", "Aluminum", "Tempered Glass", "Acrylic", "Steel + Glass", "Aluminum + Glass"));

        createSpec(categoryId, "Side Panel", null, "Design", false, true, DROPDOWN, 4,
                List.of("Solid", "Mesh", "Tempered Glass", "Acrylic Window", "RGB Glass"));

        createSpec(categoryId, "Front Panel", null, "Design", false, true, DROPDOWN, 5,
                List.of("Solid", "Mesh", "Glass", "Perforated", "RGB"));

        createSpec(categoryId, "PSU Position", null, "Layout", false, true, DROPDOWN, 6,
                List.of("Bottom Mount", "Top Mount"));

        createSpec(categoryId, "PSU Shroud", null, "Layout", false, true, BOOLEAN, 7);

        createSpec(categoryId, "Max GPU Length", "mm", "GPU Compatibility", false, true, NUMBER, 8);
        createSpec(categoryId, "Max GPU Width", "mm", "GPU Compatibility", false, false, NUMBER, 9);
        createSpec(categoryId, "Vertical GPU Mount", null, "GPU Compatibility", false, true, BOOLEAN, 10);

        createSpec(categoryId, "Max CPU Cooler Height", "mm", "Cooling Compatibility", false, true, NUMBER, 11);
        createSpec(categoryId, "Max PSU Length", "mm", "PSU Compatibility", false, false, NUMBER, 12);

        createSpec(categoryId, "Drive Bays 3.5\"", null, "Storage", false, true, DROPDOWN, 13,
                List.of("0", "1", "2", "3", "4", "6", "8", "10"));

        createSpec(categoryId, "Drive Bays 2.5\"", null, "Storage", false, true, DROPDOWN, 14,
                List.of("0", "2", "3", "4", "6", "8", "10"));

        createSpec(categoryId, "External 5.25\" Bays", null, "External Drives", false, false, DROPDOWN, 15,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "Pre-installed Fans", null, "Cooling", false, true, TEXT, 16);
        createSpec(categoryId, "Front Fan Support", null, "Cooling", false, true, TEXT, 17);
        createSpec(categoryId, "Rear Fan Support", null, "Cooling", false, false, TEXT, 18);
        createSpec(categoryId, "Top Fan Support", null, "Cooling", false, true, TEXT, 19);
        createSpec(categoryId, "Bottom Fan Support", null, "Cooling", false, false, TEXT, 20);

        createSpec(categoryId, "Radiator Support Front", "mm", "Liquid Cooling", false, true, DROPDOWN, 21,
                List.of("120", "140", "240", "280", "360", "420"));

        createSpec(categoryId, "Radiator Support Top", "mm", "Liquid Cooling", false, true, DROPDOWN, 22,
                List.of("120", "140", "240", "280", "360"));

        createSpec(categoryId, "Radiator Support Rear", "mm", "Liquid Cooling", false, false, DROPDOWN, 23,
                List.of("120", "140"));

        createSpec(categoryId, "Cable Management", null, "Features", false, true, BOOLEAN, 24);
        createSpec(categoryId, "Cable Routing Holes", null, "Features", false, false, BOOLEAN, 25);
        createSpec(categoryId, "Cable Tie Points", null, "Features", false, false, BOOLEAN, 26);

        createSpec(categoryId, "Tool-free Installation", null, "Features", false, true, BOOLEAN, 27);
        createSpec(categoryId, "Thumb Screws", null, "Features", false, false, BOOLEAN, 28);

        createSpec(categoryId, "RGB Lighting", null, "Lighting", false, true, BOOLEAN, 29);
        createSpec(categoryId, "RGB Control", null, "Lighting", false, false, DROPDOWN, 30,
                List.of("Button", "Software", "Motherboard Sync"));

        createSpec(categoryId, "Front I/O USB 3.0", null, "Front I/O", false, true, DROPDOWN, 31,
                List.of("0", "1", "2", "4"));

        createSpec(categoryId, "Front I/O USB 2.0", null, "Front I/O", false, false, DROPDOWN, 32,
                List.of("0", "1", "2"));

        createSpec(categoryId, "Front I/O USB-C", null, "Front I/O", false, true, DROPDOWN, 33,
                List.of("0", "1", "2"));

        createSpec(categoryId, "Audio Jacks", null, "Front I/O", false, true, DROPDOWN, 34,
                List.of("0", "1", "2"));

        createSpec(categoryId, "Power Button", null, "Front I/O", false, false, BOOLEAN, 35);
        createSpec(categoryId, "Reset Button", null, "Front I/O", false, false, BOOLEAN, 36);

        createSpec(categoryId, "Dust Filters", null, "Maintenance", false, true, BOOLEAN, 37);
        createSpec(categoryId, "Removable Dust Filters", null, "Maintenance", false, false, BOOLEAN, 38);

        createSpec(categoryId, "Length", "mm", "Physical Dimensions", false, false, NUMBER, 39);
        createSpec(categoryId, "Width", "mm", "Physical Dimensions", false, false, NUMBER, 40);
        createSpec(categoryId, "Height", "mm", "Physical Dimensions", false, false, NUMBER, 41);
        createSpec(categoryId, "Weight", "kg", "Physical Dimensions", false, false, DECIMAL, 42);

        createSpec(categoryId, "Feet Type", null, "Base", false, false, DROPDOWN, 43,
                List.of("Rubber", "Plastic", "Adjustable"));

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 44,
                List.of("Corsair", "NZXT", "Fractal Design", "Cooler Master", "Thermaltake", "be quiet!", "Phanteks", "Silverstone", "Lian Li"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 45);

        createSpec(categoryId, "Color", null, "Design", false, true, DROPDOWN, 46,
                List.of("Black", "White", "Gray", "Silver", "RGB"));

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, false, DROPDOWN, 47,
                List.of("1", "2", "3", "5"));
    }

    public void setupCoolingSpecifications() {
        Long categoryId = getCategoryId("cooling-systems");

        createSpec(categoryId, "Cooler Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Air Cooler", "AIO Liquid Cooler", "Custom Loop", "Low Profile", "Passive Cooler"));

        createSpec(categoryId, "Socket Compatibility", null, "Compatibility", true, true, MULTI_SELECT, 2,
                List.of("LGA1700", "LGA1200", "LGA1151", "AM4", "AM5", "LGA2066", "sTRX4"));

        createSpec(categoryId, "TDP Rating", "W", "Performance", true, true, DROPDOWN, 3,
                List.of("65", "95", "105", "125", "150", "180", "200", "220", "250", "300"));

        createSpec(categoryId, "Fan Size", "mm", "Air Cooling", false, true, DROPDOWN, 4,
                List.of("80", "92", "120", "140"));

        createSpec(categoryId, "Fan Count", null, "Air Cooling", false, true, DROPDOWN, 5,
                List.of("1", "2", "3"));

        createSpec(categoryId, "Fan Speed", "RPM", "Air Cooling", false, false, TEXT, 6);
        createSpec(categoryId, "Airflow", "CFM", "Air Cooling", false, false, DECIMAL, 7);
        createSpec(categoryId, "Static Pressure", "mmH2O", "Air Cooling", false, false, DECIMAL, 8);

        createSpec(categoryId, "Noise Level", "dB", "Acoustics", false, true, DECIMAL, 9);

        createSpec(categoryId, "Radiator Size", "mm", "Liquid Cooling", false, true, DROPDOWN, 10,
                List.of("120", "140", "240", "280", "360", "420"));

        createSpec(categoryId, "Radiator Thickness", "mm", "Liquid Cooling", false, false, DROPDOWN, 11,
                List.of("25", "27", "30", "38", "45", "54"));

        createSpec(categoryId, "Pump Speed", "RPM", "Liquid Cooling", false, false, NUMBER, 12);
        createSpec(categoryId, "Pump Type", null, "Liquid Cooling", false, false, DROPDOWN, 13,
                List.of("Centrifugal", "Variable Speed", "Fixed Speed"));

        createSpec(categoryId, "Tubing Length", "mm", "Liquid Cooling", false, false, NUMBER, 14);
        createSpec(categoryId, "Tubing Material", null, "Liquid Cooling", false, false, DROPDOWN, 15,
                List.of("Rubber", "Sleeved Rubber", "Nylon Braided"));

        createSpec(categoryId, "Cooler Height", "mm", "Physical Dimensions", false, true, NUMBER, 16);
        createSpec(categoryId, "Base Diameter", "mm", "Physical Dimensions", false, false, NUMBER, 17);
        createSpec(categoryId, "Weight", "g", "Physical Dimensions", false, false, NUMBER, 18);

        createSpec(categoryId, "Heat Pipes", null, "Heat Transfer", false, true, DROPDOWN, 19,
                List.of("2", "3", "4", "5", "6", "7", "8"));

        createSpec(categoryId, "Heat Pipe Diameter", "mm", "Heat Transfer", false, false, DROPDOWN, 20,
                List.of("6", "8", "10"));

        createSpec(categoryId, "Base Material", null, "Heat Transfer", false, true, DROPDOWN, 21,
                List.of("Aluminum", "Copper", "Nickel-plated Copper"));

        createSpec(categoryId, "Fin Material", null, "Heat Transfer", false, false, DROPDOWN, 22,
                List.of("Aluminum", "Copper"));

        createSpec(categoryId, "Thermal Interface", null, "Installation", false, false, DROPDOWN, 23,
                List.of("Thermal Paste", "Thermal Pad", "Pre-applied"));

        createSpec(categoryId, "Mounting System", null, "Installation", false, true, DROPDOWN, 24,
                List.of("Spring Screws", "Clip-on", "Bracket Mount", "Tool-free"));

        createSpec(categoryId, "PWM Control", null, "Fan Control", false, true, BOOLEAN, 25);
        createSpec(categoryId, "Fan Connector", null, "Fan Control", false, false, DROPDOWN, 26,
                List.of("3-pin", "4-pin PWM"));

        createSpec(categoryId, "RGB Lighting", null, "Lighting", false, true, BOOLEAN, 27);
        createSpec(categoryId, "RGB Control", null, "Lighting", false, false, DROPDOWN, 28,
                List.of("Software", "Motherboard Sync", "Hardware Controller"));

        createSpec(categoryId, "Zero RPM Mode", null, "Features", false, true, BOOLEAN, 29);
        createSpec(categoryId, "Tool-free Installation", null, "Features", false, false, BOOLEAN, 30);

        createSpec(categoryId, "Operating Temperature", "°C", "Environmental", false, false, TEXT, 31);

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 32,
                List.of("Noctua", "be quiet!", "Cooler Master", "Corsair", "NZXT", "Arctic", "Deepcool", "Scythe", "Thermalright"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 33);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, false, DROPDOWN, 34,
                List.of("1", "2", "3", "5", "6"));
    }

    public void setupSoundCardSpecifications() {
        Long categoryId = getCategoryId("sound-cards");

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("PCIe x1", "PCIe x4", "PCIe x16", "USB External", "Thunderbolt"));

        createSpec(categoryId, "Audio Channels", null, "Audio Output", true, true, DROPDOWN, 2,
                List.of("2.0", "2.1", "4.1", "5.1", "7.1", "7.2"));

        createSpec(categoryId, "Sample Rate", "kHz", "Audio Quality", true, true, DROPDOWN, 3,
                List.of("44.1", "48", "88.2", "96", "176.4", "192", "384"));

        createSpec(categoryId, "Bit Depth", "bit", "Audio Quality", false, true, DROPDOWN, 4,
                List.of("16", "20", "24", "32"));

        createSpec(categoryId, "SNR", "dB", "Audio Quality", false, true, NUMBER, 5);
        createSpec(categoryId, "THD", "%", "Audio Quality", false, false, DECIMAL, 6);
        createSpec(categoryId, "Frequency Response", "Hz", "Audio Quality", false, false, TEXT, 7);

        createSpec(categoryId, "DAC Chip", null, "Components", false, false, TEXT, 8);
        createSpec(categoryId, "ADC Chip", null, "Components", false, false, TEXT, 9);
        createSpec(categoryId, "Op-Amp", null, "Components", false, false, TEXT, 10);

        createSpec(categoryId, "Analog Outputs", null, "Outputs", false, true, TEXT, 11);
        createSpec(categoryId, "Digital Outputs", null, "Outputs", false, true, MULTI_SELECT, 12,
                List.of("Optical", "Coaxial", "USB"));

        createSpec(categoryId, "Headphone Amp", null, "Features", false, true, BOOLEAN, 13);
        createSpec(categoryId, "Headphone Impedance", "Ohm", "Features", false, false, TEXT, 14);

        createSpec(categoryId, "Microphone Input", null, "Inputs", false, true, BOOLEAN, 15);
        createSpec(categoryId, "Line Input", null, "Inputs", false, false, BOOLEAN, 16);
        createSpec(categoryId, "Instrument Input", null, "Inputs", false, false, BOOLEAN, 17);

        createSpec(categoryId, "MIDI Support", null, "Features", false, false, BOOLEAN, 18);
        createSpec(categoryId, "ASIO Support", null, "Features", false, true, BOOLEAN, 19);
        createSpec(categoryId, "DirectSound Support", null, "Features", false, false, BOOLEAN, 20);

        createSpec(categoryId, "Hardware Acceleration", null, "Features", false, true, BOOLEAN, 21);
        createSpec(categoryId, "EAX Support", null, "Gaming", false, false, TEXT, 22);
        createSpec(categoryId, "3D Audio", null, "Gaming", false, true, BOOLEAN, 23);

        createSpec(categoryId, "Driver Support", null, "Software", false, true, MULTI_SELECT, 24,
                List.of("Windows 10", "Windows 11", "macOS", "Linux"));

        createSpec(categoryId, "Control Software", null, "Software", false, false, BOOLEAN, 25);

        createSpec(categoryId, "Power Consumption", "W", "Power", false, false, DECIMAL, 26);
        createSpec(categoryId, "Bus Power", null, "Power", false, false, BOOLEAN, 27);

        createSpec(categoryId, "Bracket Height", null, "Physical", false, false, DROPDOWN, 28,
                List.of("Full Height", "Low Profile"));

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 29,
                List.of("Creative", "ASUS", "Sound Blaster", "Focusrite", "PreSonus", "M-Audio", "Behringer"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 30);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, false, DROPDOWN, 31,
                List.of("1", "2", "3"));
    }

    public void setupNetworkCardSpecifications() {
        Long categoryId = getCategoryId("network-cards");

        createSpec(categoryId, "Network Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Ethernet", "WiFi", "Bluetooth", "WiFi + Bluetooth"));

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("PCIe x1", "PCIe x4", "PCIe x16", "M.2", "USB", "Mini PCIe"));

        createSpec(categoryId, "Ethernet Speed", null, "Wired Speed", false, true, DROPDOWN, 3,
                List.of("Fast Ethernet (100Mbps)", "Gigabit (1Gbps)", "2.5 Gigabit", "5 Gigabit", "10 Gigabit", "25 Gigabit"));

        createSpec(categoryId, "Port Count", null, "Wired Connectivity", false, true, DROPDOWN, 4,
                List.of("1", "2", "4"));

        createSpec(categoryId, "Port Type", null, "Wired Connectivity", false, false, DROPDOWN, 5,
                List.of("RJ45", "SFP+", "QSFP+"));

        createSpec(categoryId, "WiFi Standard", null, "Wireless", false, true, DROPDOWN, 6,
                List.of("802.11n (WiFi 4)", "802.11ac (WiFi 5)", "WiFi 6 (802.11ax)", "WiFi 6E", "WiFi 7"));

        createSpec(categoryId, "WiFi Speed", "Mbps", "Wireless", false, true, DROPDOWN, 7,
                List.of("150", "300", "433", "600", "867", "1200", "1733", "2400", "3000", "4800", "5800"));

        createSpec(categoryId, "Frequency Bands", null, "Wireless", false, true, DROPDOWN, 8,
                List.of("2.4GHz", "5GHz", "Dual Band", "Tri Band", "6GHz"));

        createSpec(categoryId, "Antenna Configuration", null, "Wireless", false, true, DROPDOWN, 9,
                List.of("1x1", "2x2", "3x3", "4x4"));

        createSpec(categoryId, "Antenna Type", null, "Wireless", false, false, DROPDOWN, 10,
                List.of("Internal", "External", "Detachable"));

        createSpec(categoryId, "MIMO Support", null, "Wireless", false, false, DROPDOWN, 11,
                List.of("MIMO", "MU-MIMO"));

        createSpec(categoryId, "Bluetooth Version", null, "Bluetooth", false, true, DROPDOWN, 12,
                List.of("4.0", "4.1", "4.2", "5.0", "5.1", "5.2", "5.3", "5.4"));

        createSpec(categoryId, "Security Protocols", null, "Security", false, true, MULTI_SELECT, 13,
                List.of("WEP", "WPA", "WPA2", "WPA3", "WPS"));

        createSpec(categoryId, "Wake on LAN", null, "Features", false, true, BOOLEAN, 14);
        createSpec(categoryId, "PXE Boot", null, "Features", false, false, BOOLEAN, 15);
        createSpec(categoryId, "VLAN Support", null, "Features", false, false, BOOLEAN, 16);
        createSpec(categoryId, "Jumbo Frames", null, "Features", false, false, BOOLEAN, 17);

        createSpec(categoryId, "Offload Features", null, "Performance", false, false, MULTI_SELECT, 18,
                List.of("TCP Checksum", "UDP Checksum", "IP Checksum", "Large Send Offload", "Receive Side Scaling"));

        createSpec(categoryId, "Operating System Support", null, "Compatibility", false, true, MULTI_SELECT, 19,
                List.of("Windows 10", "Windows 11", "Windows Server", "Linux", "macOS", "FreeBSD"));

        createSpec(categoryId, "Driver Support", null, "Software", false, false, BOOLEAN, 20);
        createSpec(categoryId, "Management Software", null, "Software", false, false, BOOLEAN, 21);

        createSpec(categoryId, "Power Consumption", "W", "Power", false, false, DECIMAL, 22);
        createSpec(categoryId, "Power over Ethernet", null, "Power", false, false, DROPDOWN, 23,
                List.of("None", "PoE", "PoE+", "PoE++"));

        createSpec(categoryId, "Temperature Range", "°C", "Environmental", false, false, TEXT, 24);
        createSpec(categoryId, "Humidity Range", "%", "Environmental", false, false, TEXT, 25);

        createSpec(categoryId, "Bracket Height", null, "Physical", false, false, DROPDOWN, 26,
                List.of("Full Height", "Low Profile"));

        createSpec(categoryId, "LED Indicators", null, "Status", false, false, MULTI_SELECT, 27,
                List.of("Link", "Activity", "Speed", "Power"));

        createSpec(categoryId, "Certifications", null, "Standards", false, false, MULTI_SELECT, 28,
                List.of("FCC", "CE", "IC", "WiFi Alliance"));

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 29,
                List.of("Intel", "Realtek", "Broadcom", "ASUS", "TP-Link", "Netgear", "Killer", "Aquantia"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, false, TEXT, 30);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, false, DROPDOWN, 31,
                List.of("1", "2", "3", "5"));
    }

    public void setupMotherboardSpecifications() {
        Long categoryId = getCategoryId("motherboards");

        createSpec(categoryId, "Socket Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("LGA1700", "LGA1200", "AM4", "AM5", "LGA2066", "sTRX4", "LGA1151"));

        createSpec(categoryId, "Chipset", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("Z790", "Z690", "B660", "H610", "X670E", "B650", "X570", "B550", "A520"));

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 3,
                List.of("ATX", "Micro-ATX", "Mini-ITX", "E-ATX", "XL-ATX"));

        createSpec(categoryId, "Memory Type", null, "Memory Support", true, true, DROPDOWN, 4,
                List.of("DDR4", "DDR5", "DDR4/DDR5"));

        createSpec(categoryId, "Max Memory", "GB", "Memory Support", false, true, DROPDOWN, 5,
                List.of("32", "64", "128", "256", "512"));

        createSpec(categoryId, "Memory Slots", null, "Memory Support", false, true, DROPDOWN, 6,
                List.of("2", "4", "8"));

        createSpec(categoryId, "Max Memory Speed", "MHz", "Memory Support", false, true, DROPDOWN, 7,
                List.of("3200", "3600", "4800", "5600", "6000", "6400"));

        createSpec(categoryId, "PCIe x16 Slots", null, "Expansion Slots", false, true, DROPDOWN, 8,
                List.of("1", "2", "3", "4"));

        createSpec(categoryId, "PCIe x1 Slots", null, "Expansion Slots", false, false, DROPDOWN, 9,
                List.of("0", "1", "2", "3", "4"));

        createSpec(categoryId, "M.2 Slots", null, "Storage", false, true, DROPDOWN, 10,
                List.of("1", "2", "3", "4", "5"));

        createSpec(categoryId, "SATA Ports", null, "Storage", false, true, DROPDOWN, 11,
                List.of("4", "6", "8", "10"));

        createSpec(categoryId, "WiFi Built-in", null, "Connectivity", false, true, BOOLEAN, 12);
        createSpec(categoryId, "WiFi Standard", null, "Connectivity", false, true, DROPDOWN, 13,
                List.of("WiFi 5", "WiFi 6", "WiFi 6E", "WiFi 7"));

        createSpec(categoryId, "Bluetooth", null, "Connectivity", false, true, BOOLEAN, 14);
        createSpec(categoryId, "Bluetooth Version", null, "Connectivity", false, false, DROPDOWN, 15,
                List.of("5.0", "5.1", "5.2", "5.3", "5.4"));

        createSpec(categoryId, "Ethernet", null, "Connectivity", false, true, DROPDOWN, 16,
                List.of("Gigabit", "2.5G", "5G", "10G"));

        createSpec(categoryId, "USB 2.0 Ports", null, "Connectivity", false, false, DROPDOWN, 17,
                List.of("0", "2", "4", "6", "8"));

        createSpec(categoryId, "USB 3.0/3.1 Ports", null, "Connectivity", false, true, DROPDOWN, 18,
                List.of("2", "4", "6", "8", "10"));

        createSpec(categoryId, "USB-C Ports", null, "Connectivity", false, true, DROPDOWN, 19,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "Audio Codec", null, "Audio", false, false, TEXT, 20);
        createSpec(categoryId, "Audio Channels", null, "Audio", false, true, DROPDOWN, 21,
                List.of("2.1", "5.1", "7.1"));

        createSpec(categoryId, "RGB Lighting", null, "Features", false, true, BOOLEAN, 22);
        createSpec(categoryId, "Overclocking Support", null, "Features", false, true, BOOLEAN, 23);
        createSpec(categoryId, "Multi-GPU Support", null, "Features", false, true, DROPDOWN, 24,
                List.of("None", "SLI", "CrossFire", "SLI/CrossFire"));

        createSpec(categoryId, "BIOS/UEFI", null, "Features", false, false, DROPDOWN, 25,
                List.of("BIOS", "UEFI", "Dual BIOS"));

        createSpec(categoryId, "CPU Power Connector", null, "Power", false, false, DROPDOWN, 26,
                List.of("4-pin", "8-pin", "4+4-pin", "8+4-pin"));

        createSpec(categoryId, "Main Power Connector", null, "Power", false, false, DROPDOWN, 27,
                List.of("20-pin", "24-pin"));
    }

    public void setupCPUSpecifications() {
        Long categoryId = getCategoryId("processors");

        createSpec(categoryId, "Brand", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("Intel", "AMD"));

        createSpec(categoryId, "Product Line", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("Core i3", "Core i5", "Core i7", "Core i9", "Ryzen 3", "Ryzen 5", "Ryzen 7", "Ryzen 9", "Ryzen Threadripper"));

        createSpec(categoryId, "Generation", null, "Basic Characteristics", true, true, DROPDOWN, 3,
                List.of("12th Gen", "13th Gen", "14th Gen", "15th Gen", "Zen 3", "Zen 4", "Zen 5"));

        createSpec(categoryId, "Model", null, "Basic Characteristics", true, true, TEXT, 4);

        createSpec(categoryId, "Socket", null, "Basic Characteristics", true, true, DROPDOWN, 5,
                List.of("LGA1700", "LGA1200", "AM4", "AM5", "LGA2066", "sTRX4"));

        createSpec(categoryId, "Cores", null, "Performance", true, true, DROPDOWN, 6,
                List.of("2", "4", "6", "8", "10", "12", "14", "16", "20", "24", "32", "64"));

        createSpec(categoryId, "Threads", null, "Performance", true, true, DROPDOWN, 7,
                List.of("2", "4", "6", "8", "12", "16", "20", "24", "28", "32", "40", "48", "64", "128"));

        createSpec(categoryId, "Base Clock", "GHz", "Performance", true, true, DECIMAL, 8);
        createSpec(categoryId, "Max Boost Clock", "GHz", "Performance", false, true, DECIMAL, 9);

        createSpec(categoryId, "Cache L1", "KB", "Cache", false, false, NUMBER, 10);
        createSpec(categoryId, "Cache L2", "MB", "Cache", false, false, DECIMAL, 11);
        createSpec(categoryId, "Cache L3", "MB", "Cache", false, true, DROPDOWN, 12,
                List.of("4", "6", "8", "12", "16", "20", "24", "32", "64", "96", "128"));

        createSpec(categoryId, "Manufacturing Process", "nm", "Technology", false, true, DROPDOWN, 13,
                List.of("4", "5", "6", "7", "10", "12", "14"));

        createSpec(categoryId, "TDP", "W", "Power", false, true, DROPDOWN, 14,
                List.of("15", "25", "35", "45", "65", "95", "105", "125", "150", "170", "280"));

        createSpec(categoryId, "Max Temperature", "°C", "Thermal", false, false, NUMBER, 15);

        createSpec(categoryId, "Integrated Graphics", null, "Graphics", false, true, BOOLEAN, 16);
        createSpec(categoryId, "Graphics Model", null, "Graphics", false, true, TEXT, 17);
        createSpec(categoryId, "Graphics Base Clock", "MHz", "Graphics", false, false, NUMBER, 18);
        createSpec(categoryId, "Graphics Max Clock", "MHz", "Graphics", false, false, NUMBER, 19);

        createSpec(categoryId, "Memory Support", null, "Memory", false, true, DROPDOWN, 20,
                List.of("DDR4-2133", "DDR4-2400", "DDR4-2666", "DDR4-3200", "DDR5-4800", "DDR5-5600", "DDR5-6400"));

        createSpec(categoryId, "Max Memory", "GB", "Memory", false, true, DROPDOWN, 21,
                List.of("32", "64", "128", "256", "512", "1024", "2048"));

        createSpec(categoryId, "Memory Channels", null, "Memory", false, false, DROPDOWN, 22,
                List.of("2", "4", "8"));

        createSpec(categoryId, "PCIe Lanes", null, "Connectivity", false, false, DROPDOWN, 23,
                List.of("16", "20", "24", "28", "32", "64", "128"));

        createSpec(categoryId, "PCIe Version", null, "Connectivity", false, true, DROPDOWN, 24,
                List.of("3.0", "4.0", "5.0"));

        createSpec(categoryId, "Unlocked Multiplier", null, "Overclocking", false, true, BOOLEAN, 25);
        createSpec(categoryId, "Overclocking Support", null, "Overclocking", false, true, BOOLEAN, 26);

        createSpec(categoryId, "Virtualization Support", null, "Features", false, false, BOOLEAN, 27);
        createSpec(categoryId, "Security Features", null, "Features", false, false, TEXT, 28);

        createSpec(categoryId, "Included Cooler", null, "Package", false, true, BOOLEAN, 29);
        createSpec(categoryId, "Cooler Type", null, "Package", false, false, TEXT, 30);
    }

    public void setupGraphicsCardSpecifications() {
        Long categoryId = getCategoryId("graphics-cards");

        createSpec(categoryId, "GPU Brand", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("NVIDIA", "AMD", "Intel"));

        createSpec(categoryId, "GPU Series", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("RTX 4090", "RTX 4080", "RTX 4070", "RTX 4060", "RTX 3090", "RTX 3080", "RTX 3070", "RTX 3060",
                        "RX 7900 XTX", "RX 7900 XT", "RX 7800 XT", "RX 7700 XT", "RX 7600", "RX 6950 XT", "RX 6900 XT", "RX 6800 XT", "RX 6700 XT"));

        createSpec(categoryId, "GPU Model", null, "Basic Characteristics", true, true, TEXT, 3);

        createSpec(categoryId, "Card Manufacturer", null, "Basic Characteristics", false, true, DROPDOWN, 4,
                List.of("ASUS", "MSI", "Gigabyte", "EVGA", "Zotac", "Palit", "Gainward", "Sapphire", "XFX", "PowerColor"));

        createSpec(categoryId, "Memory Size", "GB", "Memory", true, true, DROPDOWN, 5,
                List.of("2", "3", "4", "6", "8", "10", "12", "16", "20", "24", "48"));

        createSpec(categoryId, "Memory Type", null, "Memory", true, true, DROPDOWN, 6,
                List.of("GDDR5", "GDDR6", "GDDR6X", "HBM2", "HBM3"));

        createSpec(categoryId, "Memory Bus Width", "bit", "Memory", false, true, DROPDOWN, 7,
                List.of("64", "128", "192", "256", "320", "384", "512", "1024"));

        createSpec(categoryId, "Memory Clock", "MHz", "Memory", false, false, NUMBER, 8);
        createSpec(categoryId, "Memory Bandwidth", "GB/s", "Memory", false, false, DECIMAL, 9);

        createSpec(categoryId, "Base Clock", "MHz", "Performance", false, true, NUMBER, 10);
        createSpec(categoryId, "Boost Clock", "MHz", "Performance", false, true, NUMBER, 11);
        createSpec(categoryId, "Game Clock", "MHz", "Performance", false, false, NUMBER, 12);

        createSpec(categoryId, "CUDA Cores", null, "Performance", false, true, NUMBER, 13);
        createSpec(categoryId, "Stream Processors", null, "Performance", false, true, NUMBER, 14);
        createSpec(categoryId, "RT Cores", null, "Performance", false, false, NUMBER, 15);
        createSpec(categoryId, "Tensor Cores", null, "Performance", false, false, NUMBER, 16);

        createSpec(categoryId, "Manufacturing Process", "nm", "Technology", false, false, DROPDOWN, 17,
                List.of("4", "5", "6", "7", "8", "12", "14", "16"));

        createSpec(categoryId, "DirectX Support", null, "API Support", false, true, DROPDOWN, 18,
                List.of("11", "12", "12 Ultimate"));

        createSpec(categoryId, "OpenGL Support", null, "API Support", false, false, TEXT, 19);
        createSpec(categoryId, "Vulkan Support", null, "API Support", false, false, BOOLEAN, 20);

        createSpec(categoryId, "Ray Tracing", null, "Features", false, true, BOOLEAN, 21);
        createSpec(categoryId, "DLSS Support", null, "Features", false, true, BOOLEAN, 22);
        createSpec(categoryId, "FSR Support", null, "Features", false, true, BOOLEAN, 23);

        createSpec(categoryId, "Power Consumption", "W", "Power", false, true, DROPDOWN, 24,
                List.of("75", "100", "120", "150", "180", "200", "220", "250", "300", "350", "400", "450", "500"));

        createSpec(categoryId, "Recommended PSU", "W", "Power", false, true, DROPDOWN, 25,
                List.of("450", "500", "550", "600", "650", "700", "750", "800", "850", "1000", "1200"));

        createSpec(categoryId, "Power Connectors", null, "Power", false, true, DROPDOWN, 26,
                List.of("None", "6-pin", "8-pin", "6+6-pin", "6+8-pin", "8+8-pin", "8+8+8-pin", "12VHPWR", "16-pin"));

        createSpec(categoryId, "DisplayPort", null, "Display Outputs", false, true, DROPDOWN, 27,
                List.of("0", "1", "2", "3", "4"));

        createSpec(categoryId, "DisplayPort Version", null, "Display Outputs", false, false, DROPDOWN, 28,
                List.of("1.4", "1.4a", "2.0"));

        createSpec(categoryId, "HDMI", null, "Display Outputs", false, true, DROPDOWN, 29,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "HDMI Version", null, "Display Outputs", false, true, DROPDOWN, 30,
                List.of("2.0", "2.1"));

        createSpec(categoryId, "DVI", null, "Display Outputs", false, false, DROPDOWN, 31,
                List.of("0", "1", "2"));

        createSpec(categoryId, "USB-C", null, "Display Outputs", false, false, DROPDOWN, 32,
                List.of("0", "1", "2"));

        createSpec(categoryId, "Max Resolution", null, "Display Support", false, true, DROPDOWN, 33,
                List.of("1920x1080", "2560x1440", "3840x2160", "7680x4320"));

        createSpec(categoryId, "Max Displays", null, "Display Support", false, false, DROPDOWN, 34,
                List.of("2", "3", "4", "5", "6"));

        createSpec(categoryId, "Card Length", "mm", "Physical Dimensions", false, true, NUMBER, 35);
        createSpec(categoryId, "Card Width", "mm", "Physical Dimensions", false, false, NUMBER, 36);
        createSpec(categoryId, "Card Height", "mm", "Physical Dimensions", false, false, NUMBER, 37);

        createSpec(categoryId, "Slot Width", null, "Physical Dimensions", false, true, DROPDOWN, 38,
                List.of("Single Slot", "Dual Slot", "2.5 Slot", "Triple Slot"));

        createSpec(categoryId, "Weight", "kg", "Physical Dimensions", false, false, DECIMAL, 39);

        createSpec(categoryId, "Cooling Type", null, "Cooling", false, true, DROPDOWN, 40,
                List.of("Passive", "Single Fan", "Dual Fan", "Triple Fan", "Liquid Cooled", "Hybrid"));

        createSpec(categoryId, "Fan Count", null, "Cooling", false, true, DROPDOWN, 41,
                List.of("0", "1", "2", "3"));

        createSpec(categoryId, "Zero RPM Mode", null, "Cooling", false, true, BOOLEAN, 42);

        createSpec(categoryId, "RGB Lighting", null, "Aesthetics", false, true, BOOLEAN, 43);
        createSpec(categoryId, "Backplate", null, "Aesthetics", false, true, BOOLEAN, 44);

        createSpec(categoryId, "Overclocked", null, "Performance", false, true, BOOLEAN, 45);
        createSpec(categoryId, "Dual BIOS", null, "Features", false, false, BOOLEAN, 46);

        createSpec(categoryId, "VR Ready", null, "Features", false, true, BOOLEAN, 47);
        createSpec(categoryId, "4K Gaming", null, "Features", false, true, BOOLEAN, 48);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, false, DROPDOWN, 49,
                List.of("1", "2", "3", "4", "5"));
    }

    public void setupMemorySpecifications() {
        Long categoryId = getCategoryId("memory");

        createSpec(categoryId, "Memory Type", null, "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("DDR4", "DDR5", "DDR3", "DDR2", "SO-DIMM DDR4", "SO-DIMM DDR5", "SO-DIMM DDR3"));

        createSpec(categoryId, "Capacity", "GB", "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("2", "4", "8", "16", "32", "64", "128", "256"));

        createSpec(categoryId, "Kit Configuration", null, "Basic Characteristics", true, true, DROPDOWN, 3,
                List.of("1x2GB", "1x4GB", "1x8GB", "1x16GB", "1x32GB", "1x64GB", "1x128GB",
                        "2x2GB", "2x4GB", "2x8GB", "2x16GB", "2x32GB", "2x64GB",
                        "4x4GB", "4x8GB", "4x16GB", "4x32GB",
                        "8x8GB", "8x16GB", "8x32GB"));

        createSpec(categoryId, "Speed", "MHz", "Performance", true, true, DROPDOWN, 4,
                List.of("1333", "1600", "1866", "2133", "2400", "2666", "2933", "3000", "3200", "3466", "3600", "3733", "3866", "4000", "4266", "4400", "4800", "5200", "5600", "6000", "6400", "6800", "7200"));

        createSpec(categoryId, "CAS Latency", null, "Timings", false, true, DROPDOWN, 5,
                List.of("14", "15", "16", "17", "18", "19", "20", "22", "30", "32", "34", "36", "38", "40"));

        createSpec(categoryId, "Timings", null, "Timings", false, false, TEXT, 6);

        createSpec(categoryId, "Voltage", "V", "Electrical", false, true, DROPDOWN, 7,
                List.of("1.2", "1.35", "1.5", "1.65"));

        createSpec(categoryId, "ECC Support", null, "Features", false, true, BOOLEAN, 8);
        createSpec(categoryId, "Registered/Buffered", null, "Features", false, true, BOOLEAN, 9);

        createSpec(categoryId, "Heat Spreader", null, "Cooling", false, true, BOOLEAN, 10);
        createSpec(categoryId, "Heat Spreader Material", null, "Cooling", false, false, DROPDOWN, 11,
                List.of("Aluminum", "Copper", "Steel"));

        createSpec(categoryId, "RGB Lighting", null, "Aesthetics", false, true, BOOLEAN, 12);
        createSpec(categoryId, "Lighting Type", null, "Aesthetics", false, false, DROPDOWN, 13,
                List.of("Single Color", "RGB", "ARGB"));

        createSpec(categoryId, "XMP Profile", null, "Overclocking", false, true, BOOLEAN, 14);
        createSpec(categoryId, "XMP Version", null, "Overclocking", false, false, DROPDOWN, 15,
                List.of("2.0", "3.0"));

        createSpec(categoryId, "EXPO Profile", null, "Overclocking", false, true, BOOLEAN, 16);
        createSpec(categoryId, "Intel Certified", null, "Compatibility", false, false, BOOLEAN, 17);
        createSpec(categoryId, "AMD Certified", null, "Compatibility", false, false, BOOLEAN, 18);

        createSpec(categoryId, "Form Factor", null, "Physical", false, true, DROPDOWN, 19,
                List.of("DIMM", "SO-DIMM", "Mini-DIMM"));

        createSpec(categoryId, "Pin Count", null, "Physical", false, false, DROPDOWN, 20,
                List.of("240", "260", "288"));

        createSpec(categoryId, "Operating Temperature", "°C", "Environmental", false, false, TEXT, 21);

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 22,
                List.of("Corsair", "G.Skill", "Kingston", "Crucial", "Team Group", "ADATA", "Patriot", "HyperX", "Mushkin"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, true, TEXT, 23);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, false, DROPDOWN, 24,
                List.of("Lifetime", "1", "2", "3", "5", "10"));
    }

    public void setupStorageHDDSpecifications() {
        Long categoryId = getCategoryId("hdd-storage");

        createSpec(categoryId, "Capacity", "TB", "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("0.5", "1", "2", "3", "4", "6", "8", "10", "12", "14", "16", "18", "20", "22"));

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("3.5\"", "2.5\""));

        createSpec(categoryId, "Interface", null, "Connectivity", true, true, DROPDOWN, 3,
                List.of("SATA III", "SATA II", "SAS", "IDE/PATA"));

        createSpec(categoryId, "RPM", null, "Performance", true, true, DROPDOWN, 4,
                List.of("5400", "5900", "7200", "10000", "15000"));

        createSpec(categoryId, "Cache", "MB", "Performance", false, true, DROPDOWN, 5,
                List.of("8", "16", "32", "64", "128", "256", "512"));

        createSpec(categoryId, "Transfer Rate", "MB/s", "Performance", false, false, NUMBER, 6);

        createSpec(categoryId, "Seek Time", "ms", "Performance", false, false, DECIMAL, 7);

        createSpec(categoryId, "Usage Type", null, "Application", false, true, DROPDOWN, 8,
                List.of("Desktop", "Enterprise", "NAS", "Surveillance", "Gaming"));

        createSpec(categoryId, "Workload Rate", "TB/year", "Reliability", false, false, NUMBER, 9);

        createSpec(categoryId, "MTBF", "hours", "Reliability", false, false, NUMBER, 10);

        createSpec(categoryId, "Power Consumption Active", "W", "Power", false, false, DECIMAL, 11);
        createSpec(categoryId, "Power Consumption Idle", "W", "Power", false, false, DECIMAL, 12);

        createSpec(categoryId, "Operating Temperature", "°C", "Environmental", false, false, TEXT, 13);
        createSpec(categoryId, "Storage Temperature", "°C", "Environmental", false, false, TEXT, 14);

        createSpec(categoryId, "Shock Resistance Operating", "G", "Environmental", false, false, NUMBER, 15);
        createSpec(categoryId, "Shock Resistance Non-Operating", "G", "Environmental", false, false, NUMBER, 16);

        createSpec(categoryId, "Noise Level Idle", "dB", "Acoustics", false, false, DECIMAL, 17);
        createSpec(categoryId, "Noise Level Seek", "dB", "Acoustics", false, false, DECIMAL, 18);

        createSpec(categoryId, "Height", "mm", "Physical Dimensions", false, false, DECIMAL, 19);
        createSpec(categoryId, "Width", "mm", "Physical Dimensions", false, false, DECIMAL, 20);
        createSpec(categoryId, "Depth", "mm", "Physical Dimensions", false, false, DECIMAL, 21);
        createSpec(categoryId, "Weight", "g", "Physical Dimensions", false, false, NUMBER, 22);

        createSpec(categoryId, "Technology", null, "Technology", false, true, DROPDOWN, 23,
                List.of("CMR", "SMR", "Helium-filled"));

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 24,
                List.of("Western Digital", "Seagate", "Toshiba", "HGST", "Maxtor"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, true, DROPDOWN, 25,
                List.of("WD Blue", "WD Black", "WD Red", "WD Purple", "WD Gold", "Seagate Barracuda", "Seagate IronWolf", "Seagate Exos"));

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, true, DROPDOWN, 26,
                List.of("1", "2", "3", "5"));
    }

    public void setupStorageSSDSpecifications() {
        Long categoryId = getCategoryId("ssd-storage");

        createSpec(categoryId, "Capacity", "GB", "Basic Characteristics", true, true, DROPDOWN, 1,
                List.of("120", "128", "240", "250", "256", "480", "500", "512", "960", "1000", "1024", "2000", "2048", "4000", "4096", "8000", "8192", "15360", "30720"));

        createSpec(categoryId, "Form Factor", null, "Basic Characteristics", true, true, DROPDOWN, 2,
                List.of("2.5\"", "M.2 2280", "M.2 2242", "M.2 2260", "mSATA", "U.2", "M.2 22110"));

        createSpec(categoryId, "Interface", null, "Connectivity", true, true, DROPDOWN, 3,
                List.of("SATA III", "PCIe 3.0 x4", "PCIe 4.0 x4", "PCIe 5.0 x4", "U.2", "mSATA"));

        createSpec(categoryId, "Protocol", null, "Connectivity", false, true, DROPDOWN, 4,
                List.of("AHCI", "NVMe", "NVMe 1.3", "NVMe 1.4", "NVMe 2.0"));

        createSpec(categoryId, "Sequential Read", "MB/s", "Performance", true, true, NUMBER, 5);
        createSpec(categoryId, "Sequential Write", "MB/s", "Performance", true, true, NUMBER, 6);

        createSpec(categoryId, "Random Read IOPS", "IOPS", "Performance", false, true, NUMBER, 7);
        createSpec(categoryId, "Random Write IOPS", "IOPS", "Performance", false, true, NUMBER, 8);

        createSpec(categoryId, "Memory Type", null, "Technology", false, true, DROPDOWN, 9,
                List.of("3D NAND", "TLC", "QLC", "MLC", "SLC", "V-NAND"));

        createSpec(categoryId, "Controller", null, "Technology", false, false, TEXT, 10);

        createSpec(categoryId, "DRAM Cache", null, "Performance", false, true, BOOLEAN, 11);
        createSpec(categoryId, "Cache Size", "MB", "Performance", false, false, NUMBER, 12);

        createSpec(categoryId, "SLC Cache", null, "Performance", false, false, BOOLEAN, 13);

        createSpec(categoryId, "TBW", "TB", "Endurance", false, true, NUMBER, 14);
        createSpec(categoryId, "DWPD", null, "Endurance", false, false, DECIMAL, 15);

        createSpec(categoryId, "MTBF", "hours", "Reliability", false, false, NUMBER, 16);

        createSpec(categoryId, "Power Consumption Active", "W", "Power", false, false, DECIMAL, 17);
        createSpec(categoryId, "Power Consumption Idle", "W", "Power", false, false, DECIMAL, 18);

        createSpec(categoryId, "Operating Temperature", "°C", "Environmental", false, false, TEXT, 19);
        createSpec(categoryId, "Storage Temperature", "°C", "Environmental", false, false, TEXT, 20);

        createSpec(categoryId, "Shock Resistance", "G", "Environmental", false, false, NUMBER, 21);
        createSpec(categoryId, "Vibration Resistance", "G", "Environmental", false, false, DECIMAL, 22);

        createSpec(categoryId, "Height", "mm", "Physical Dimensions", false, false, DECIMAL, 23);
        createSpec(categoryId, "Width", "mm", "Physical Dimensions", false, false, DECIMAL, 24);
        createSpec(categoryId, "Depth", "mm", "Physical Dimensions", false, false, DECIMAL, 25);
        createSpec(categoryId, "Weight", "g", "Physical Dimensions", false, false, NUMBER, 26);

        createSpec(categoryId, "Heat Spreader", null, "Cooling", false, true, BOOLEAN, 27);

        createSpec(categoryId, "Encryption", null, "Security", false, true, DROPDOWN, 28,
                List.of("None", "AES 256-bit", "TCG Opal 2.0", "eDrive"));

        createSpec(categoryId, "Self-Encrypting Drive", null, "Security", false, false, BOOLEAN, 29);

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 30,
                List.of("Samsung", "Western Digital", "Crucial", "Kingston", "ADATA", "SanDisk", "Intel", "Corsair", "Seagate"));

        createSpec(categoryId, "Product Line", null, "Manufacturer", false, true, TEXT, 31);

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, true, DROPDOWN, 32,
                List.of("1", "2", "3", "5", "10"));
    }

    // Continue with more categories...
    // Due to length constraints, I'll show the pattern for a few more major categories

    public void setupMonitorSpecifications() {
        Long categoryId = getCategoryId("monitors");

        createSpec(categoryId, "Screen Size", "inches", "Display", true, true, DROPDOWN, 1,
                List.of("19", "21.5", "22", "23", "24", "25", "27", "28", "29", "30", "32", "34", "35", "37", "38", "43", "49", "55"));

        createSpec(categoryId, "Resolution", null, "Display", true, true, DROPDOWN, 2,
                List.of("1366x768 (HD)", "1600x900 (HD+)", "1920x1080 (Full HD)", "2560x1080 (UW FHD)",
                        "2560x1440 (QHD)", "3440x1440 (UW QHD)", "3840x2160 (4K UHD)", "5120x1440 (UW 5K)", "7680x4320 (8K)"));

        createSpec(categoryId, "Aspect Ratio", null, "Display", true, true, DROPDOWN, 3,
                List.of("16:9", "16:10", "21:9", "32:9", "4:3", "5:4"));

        createSpec(categoryId, "Panel Type", null, "Display Technology", true, true, DROPDOWN, 4,
                List.of("TN", "IPS", "VA", "OLED", "Mini LED", "Quantum Dot"));

        createSpec(categoryId, "Refresh Rate", "Hz", "Performance", true, true, DROPDOWN, 5,
                List.of("60", "75", "100", "120", "144", "165", "180", "240", "280", "360", "500"));

        createSpec(categoryId, "Response Time", "ms", "Performance", false, true, DROPDOWN, 6,
                List.of("0.5", "1", "2", "4", "5", "6", "8", "10", "12", "16"));

        createSpec(categoryId, "Brightness", "cd/m²", "Display Quality", false, true, NUMBER, 7);
        createSpec(categoryId, "Contrast Ratio", null, "Display Quality", false, true, TEXT, 8);
        createSpec(categoryId, "Color Gamut sRGB", "%", "Display Quality", false, false, NUMBER, 9);
        createSpec(categoryId, "Color Gamut DCI-P3", "%", "Display Quality", false, false, NUMBER, 10);

        createSpec(categoryId, "HDR Support", null, "Display Quality", false, true, DROPDOWN, 11,
                List.of("None", "HDR10", "HDR400", "HDR600", "HDR1000", "HDR1400", "Dolby Vision"));

        createSpec(categoryId, "Curve", null, "Design", false, true, DROPDOWN, 12,
                List.of("Flat", "1500R", "1800R", "1000R", "3800R"));

        createSpec(categoryId, "Adaptive Sync", null, "Gaming Features", false, true, DROPDOWN, 13,
                List.of("None", "FreeSync", "FreeSync Premium", "FreeSync Premium Pro", "G-Sync", "G-Sync Compatible", "G-Sync Ultimate"));

        createSpec(categoryId, "Low Input Lag", null, "Gaming Features", false, true, BOOLEAN, 14);
        createSpec(categoryId, "Game Mode", null, "Gaming Features", false, true, BOOLEAN, 15);

        createSpec(categoryId, "HDMI Ports", null, "Connectivity", false, true, DROPDOWN, 16,
                List.of("0", "1", "2", "3", "4"));

        createSpec(categoryId, "HDMI Version", null, "Connectivity", false, true, DROPDOWN, 17,
                List.of("1.4", "2.0", "2.1"));

        createSpec(categoryId, "DisplayPort", null, "Connectivity", false, true, DROPDOWN, 18,
                List.of("0", "1", "2"));

        createSpec(categoryId, "DisplayPort Version", null, "Connectivity", false, false, DROPDOWN, 19,
                List.of("1.2", "1.4", "2.0"));

        createSpec(categoryId, "USB-C", null, "Connectivity", false, true, BOOLEAN, 20);
        createSpec(categoryId, "USB-C Power Delivery", "W", "Connectivity", false, false, NUMBER, 21);

        createSpec(categoryId, "USB Hub", null, "Connectivity", false, true, BOOLEAN, 22);
        createSpec(categoryId, "USB Ports", null, "Connectivity", false, false, DROPDOWN, 23,
                List.of("0", "2", "4", "6"));

        createSpec(categoryId, "Built-in Speakers", null, "Audio", false, true, BOOLEAN, 24);
        createSpec(categoryId, "Speaker Power", "W", "Audio", false, false, NUMBER, 25);
        createSpec(categoryId, "Audio Out", null, "Audio", false, false, BOOLEAN, 26);

        createSpec(categoryId, "VESA Mount", null, "Mounting", false, true, DROPDOWN, 27,
                List.of("75x75", "100x100", "200x200", "400x400", "600x400"));

        createSpec(categoryId, "Stand Adjustments", null, "Ergonomics", false, true, MULTI_SELECT, 28,
                List.of("Height", "Tilt", "Swivel", "Pivot", "Rotate"));

        createSpec(categoryId, "Blue Light Filter", null, "Eye Care", false, true, BOOLEAN, 29);
        createSpec(categoryId, "Flicker Free", null, "Eye Care", false, true, BOOLEAN, 30);

        createSpec(categoryId, "Power Consumption", "W", "Power", false, false, NUMBER, 31);
        createSpec(categoryId, "Standby Power", "W", "Power", false, false, DECIMAL, 32);

        createSpec(categoryId, "Touchscreen", null, "Features", false, true, BOOLEAN, 33);
        createSpec(categoryId, "Webcam", null, "Features", false, true, BOOLEAN, 34);
        createSpec(categoryId, "Picture-in-Picture", null, "Features", false, false, BOOLEAN, 35);

        createSpec(categoryId, "Brand", null, "Manufacturer", false, true, DROPDOWN, 36,
                List.of("ASUS", "LG", "Samsung", "Dell", "HP", "Acer", "MSI", "BenQ", "AOC", "ViewSonic"));

        createSpec(categoryId, "Warranty Period", "years", "Warranty", false, false, DROPDOWN, 37,
                List.of("1", "2", "3", "5"));
    }

    // ===== HELPER METHODS =====

    private void createSpec(Long categoryId, String name, String unit, String group,
                            boolean required, boolean filterable,
                            CategorySpecificationTemplate.SpecificationType type, int sortOrder) {
        createSpec(categoryId, name, unit, group, required, filterable, type, sortOrder, null);
    }

    private void createSpec(Long categoryId, String name, String unit, String group,
                            boolean required, boolean filterable,
                            CategorySpecificationTemplate.SpecificationType type, int sortOrder,
                            List<String> allowedValues) {

        CategorySpecificationTemplateDTO template = CategorySpecificationTemplateDTO.builder()
                .categoryId(categoryId)
                .specName(name)
                .specUnit(unit)
                .specGroup(group)
                .required(required)
                .filterable(filterable)
                .searchable(true)
                .type(type)
                .sortOrder(sortOrder)
                .showInListing(required || (filterable && List.of("Brand", "Model", "Capacity", "Size", "Memory", "Storage").contains(name)))
                .showInComparison(true)
                .allowedValues(allowedValues)
                .build();

        try {
            specService.createTemplate(template);
            log.info("✅ Created spec: {} for category {}", name, categoryId);
        } catch (Exception e) {
            log.error("❌ Failed to create spec: {} - {}", name, e.getMessage());
        }
    }

    private Long getCategoryId(String slug) {
        return categoryRepository.findBySlug(slug)
                .map(Category::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));
    }

    // ===== USAGE METHODS =====

    @PostConstruct
    public void initializeAllCategories() {
        // Add property: app.initialize.all-categories=true
        if (shouldInitializeAllCategories()) {
            log.info("🚀 Setting up ALL vali.bg categories...");
            setupAllValiBgCategories();
            log.info("✅ ALL vali.bg categories setup complete!");
        }
    }

    private boolean shouldInitializeAllCategories() {
        // Check property or environment variable
        return false; // Set to true when ready to initialize all categories
    }
}