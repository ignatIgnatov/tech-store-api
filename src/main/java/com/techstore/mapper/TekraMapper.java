package com.techstore.mapper;

import com.techstore.dto.external.CategoryIdDto;
import com.techstore.dto.external.NameDto;
import com.techstore.dto.request.*;
import com.techstore.dto.tekra.*;
import com.techstore.enums.ProductStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TekraMapper {

    private static final String VIDEO_SURVEILLANCE_MAIN_CATEGORY_NAME_EN = "Video Surveillance";
    private static final String VIDEO_SURVEILLANCE_MAIN_CATEGORY_NAME_BG = "Видеонаблюдение";
    private static final Long MAIN_VIDEO_SURVEILLANCE_CATEGORY_ID = 2000L; // Changed from 1000L to avoid conflicts

    /**
     * Maps Tekra categories to internal category format - now supports ALL video surveillance categories
     */
    public List<CategoryRequestFromExternalDto> mapCategories(List<TekraCategory> tekraCategories) {
        List<CategoryRequestFromExternalDto> categories = new ArrayList<>();

        // First, create the main Video Surveillance category
        CategoryRequestFromExternalDto mainVideoCategory = createMainVideoSurveillanceCategory();
        categories.add(mainVideoCategory);

        // Map ALL Tekra categories as subcategories (remove filtering)
        for (TekraCategory tekraCategory : tekraCategories) {
            CategoryRequestFromExternalDto category = new CategoryRequestFromExternalDto();

            // Use unique external ID (add 20000 to avoid conflicts with existing categories)
            category.setId(tekraCategory.getId() + 20000L);
            category.setParent(MAIN_VIDEO_SURVEILLANCE_CATEGORY_ID); // Parent is main Video Surveillance
            category.setShow(tekraCategory.getIsActive() != null ? tekraCategory.getIsActive() : true);
            category.setOrder(tekraCategory.getSortOrder() != null ? tekraCategory.getSortOrder() : 0);
            category.setSlug(generateSlug(tekraCategory.getName()));

            // Set multilingual names
            List<NameDto> names = new ArrayList<>();
            names.add(createNameDto("en", tekraCategory.getName()));
            names.add(createNameDto("bg", translateCategoryName(tekraCategory.getName())));
            category.setName(names);

            categories.add(category);
        }

        // Handle parent-child relationships for Tekra categories
        handleTekraCategoryHierarchy(tekraCategories, categories);

        return categories;
    }

    /**
     * Handle parent-child relationships for Tekra categories
     */
    private void handleTekraCategoryHierarchy(List<TekraCategory> tekraCategories,
                                              List<CategoryRequestFromExternalDto> mappedCategories) {
        for (TekraCategory tekraCategory : tekraCategories) {
            if (tekraCategory.getParentId() != null && tekraCategory.getParentId() > 0) {
                // Find the mapped category
                CategoryRequestFromExternalDto mappedCategory = mappedCategories.stream()
                        .filter(cat -> cat.getId().equals(tekraCategory.getId() + 20000L))
                        .findFirst()
                        .orElse(null);

                if (mappedCategory != null) {
                    // Set parent to the mapped Tekra parent (not main video surveillance)
                    mappedCategory.setParent(tekraCategory.getParentId() + 20000L);
                }
            }
        }
    }

    /**
     * Maps Tekra manufacturers to internal format
     */
    public List<ManufacturerRequestDto> mapManufacturers(List<TekraProduct> products) {
        return products.stream()
                .filter(product -> product.getManufacturer() != null)
                .map(TekraProduct::getManufacturer)
                .distinct()
                .map(this::mapManufacturer)
                .collect(Collectors.toList());
    }

    /**
     * Maps Tekra parameters to internal format - now maps to actual categories
     */
    public List<ParameterRequestDto> mapParameters(List<TekraParameter> tekraParameters,
                                                   List<TekraCategory> categories) {
        List<ParameterRequestDto> mappedParameters = new ArrayList<>();

        // Group parameters by category if possible, otherwise assign to main category
        for (TekraParameter tekraParameter : tekraParameters) {
            ParameterRequestDto parameter = mapSingleParameter(tekraParameter, categories);
            mappedParameters.add(parameter);
        }

        return mappedParameters;
    }

    /**
     * Maps Tekra products to internal format - now properly categorizes all products
     */
    public List<ProductRequestDto> mapProducts(List<TekraProduct> tekraProducts) {
        return tekraProducts.stream()
                .map(this::mapProduct)
                .collect(Collectors.toList());
    }

    private CategoryRequestFromExternalDto createMainVideoSurveillanceCategory() {
        CategoryRequestFromExternalDto category = new CategoryRequestFromExternalDto();
        category.setId(MAIN_VIDEO_SURVEILLANCE_CATEGORY_ID);
        category.setParent(null); // Top-level category
        category.setShow(true);
        category.setOrder(100);
        category.setSlug("video-surveillance");

        List<NameDto> names = new ArrayList<>();
        names.add(createNameDto("en", VIDEO_SURVEILLANCE_MAIN_CATEGORY_NAME_EN));
        names.add(createNameDto("bg", VIDEO_SURVEILLANCE_MAIN_CATEGORY_NAME_BG));
        category.setName(names);

        return category;
    }

    private ManufacturerRequestDto mapManufacturer(TekraManufacturer tekraManufacturer) {
        ManufacturerRequestDto manufacturer = new ManufacturerRequestDto();

        // Use unique external ID (add 30000 to avoid conflicts)
        manufacturer.setId(tekraManufacturer.getId() + 30000L);
        manufacturer.setName(tekraManufacturer.getName());

        // Set manufacturer information
        if (StringUtils.hasText(tekraManufacturer.getWebsite()) ||
                StringUtils.hasText(tekraManufacturer.getCountry())) {

            ManufacturerInformationDto info = new ManufacturerInformationDto();
            info.setName(tekraManufacturer.getName());
            info.setAddress(tekraManufacturer.getCountry());
            manufacturer.setInformation(info);
        }

        return manufacturer;
    }

    private ParameterRequestDto mapSingleParameter(TekraParameter tekraParameter,
                                                   List<TekraCategory> categories) {
        ParameterRequestDto parameter = new ParameterRequestDto();

        // Use unique external ID (add 40000 to avoid conflicts)
        parameter.setId(tekraParameter.getId() + 40000L);

        // Try to determine category, default to main video surveillance category
        parameter.setCategoryId(MAIN_VIDEO_SURVEILLANCE_CATEGORY_ID);
        parameter.setOrder(tekraParameter.getSortOrder() != null ? tekraParameter.getSortOrder() : 0);

        // Set multilingual names
        List<NameDto> names = new ArrayList<>();
        names.add(createNameDto("en", tekraParameter.getName()));
        names.add(createNameDto("bg", translateParameterName(tekraParameter.getName())));
        parameter.setName(names);

        // Map parameter options
        if (tekraParameter.getOptions() != null && !tekraParameter.getOptions().isEmpty()) {
            List<ParameterOptionRequestDto> options = tekraParameter.getOptions().stream()
                    .map(this::mapParameterOption)
                    .collect(Collectors.toList());
            parameter.setOptions(options);
        } else if (StringUtils.hasText(tekraParameter.getValue())) {
            // Create single option from value
            ParameterOptionRequestDto option = new ParameterOptionRequestDto();
            option.setId(tekraParameter.getId() + 50000L);
            option.setOrder(0);

            List<NameDto> optionNames = new ArrayList<>();
            String optionValue = tekraParameter.getValue();
            if (StringUtils.hasText(tekraParameter.getUnit())) {
                optionValue += " " + tekraParameter.getUnit();
            }
            optionNames.add(createNameDto("en", optionValue));
            optionNames.add(createNameDto("bg", optionValue));
            option.setName(optionNames);

            parameter.setOptions(List.of(option));
        }

        return parameter;
    }

    private ParameterOptionRequestDto mapParameterOption(TekraParameterOption tekraOption) {
        ParameterOptionRequestDto option = new ParameterOptionRequestDto();

        // Use unique external ID (add 50000 to avoid conflicts)
        option.setId(tekraOption.getId() + 50000L);
        option.setOrder(tekraOption.getSortOrder() != null ? tekraOption.getSortOrder() : 0);

        // Set multilingual names
        List<NameDto> names = new ArrayList<>();
        String optionValue = StringUtils.hasText(tekraOption.getLabel()) ?
                tekraOption.getLabel() : tekraOption.getValue();
        names.add(createNameDto("en", optionValue));
        names.add(createNameDto("bg", optionValue));
        option.setName(names);

        return option;
    }

    private ProductRequestDto mapProduct(TekraProduct tekraProduct) {
        ProductRequestDto product = new ProductRequestDto();

        // Use unique external ID (add 60000 to avoid conflicts)
        product.setId(tekraProduct.getId() + 60000L);
        product.setReferenceNumber(generateReferenceNumber(tekraProduct));
        product.setModel(tekraProduct.getModel());
        product.setBarcode(tekraProduct.getBarcode());
        product.setManufacturerId(tekraProduct.getManufacturer() != null ?
                tekraProduct.getManufacturer().getId() + 30000L : null);

        // Set status based on availability
        product.setStatus(mapProductStatus(tekraProduct));

        // Set prices
        product.setPriceClient(tekraProduct.getPrice());
        product.setPricePromo(tekraProduct.getPricePromo());
        product.setShow(tekraProduct.getIsActive() != null ? tekraProduct.getIsActive() : true);
        product.setWarranty(tekraProduct.getWarranty());
        product.setWeight(tekraProduct.getWeight());

        // Set category - map to actual Tekra category or main video surveillance
        CategoryIdDto categoryId = new CategoryIdDto();
        if (tekraProduct.getCategory() != null) {
            categoryId.setId(tekraProduct.getCategory().getId() + 20000L);
        } else {
            categoryId.setId(MAIN_VIDEO_SURVEILLANCE_CATEGORY_ID);
        }
        product.setCategories(List.of(categoryId));

        // Set multilingual names
        List<NameDto> names = new ArrayList<>();
        names.add(createNameDto("en", tekraProduct.getName()));
        names.add(createNameDto("bg", tekraProduct.getName()));
        product.setName(names);

        // Set descriptions
        if (StringUtils.hasText(tekraProduct.getDescription()) ||
                StringUtils.hasText(tekraProduct.getShortDescription())) {

            List<com.techstore.dto.external.DescriptionDto> descriptions = new ArrayList<>();

            String description = StringUtils.hasText(tekraProduct.getDescription()) ?
                    tekraProduct.getDescription() : tekraProduct.getShortDescription();

            com.techstore.dto.external.DescriptionDto descEn = new com.techstore.dto.external.DescriptionDto();
            descEn.setLanguageCode("en");
            descEn.setText(description);
            descriptions.add(descEn);

            com.techstore.dto.external.DescriptionDto descBg = new com.techstore.dto.external.DescriptionDto();
            descBg.setLanguageCode("bg");
            descBg.setText(description);
            descriptions.add(descBg);

            product.setDescription(descriptions);
        }

        // Set images
        if (tekraProduct.getImages() != null && !tekraProduct.getImages().isEmpty()) {
            List<com.techstore.dto.external.ImageDto> images = tekraProduct.getImages().stream()
                    .map(this::mapImage)
                    .collect(Collectors.toList());
            product.setImages(images);
        }

        // Set parameters
        if (tekraProduct.getParameters() != null && !tekraProduct.getParameters().isEmpty()) {
            List<ParameterValueRequestDto> parameterValues = tekraProduct.getParameters().stream()
                    .map(this::mapParameterValue)
                    .collect(Collectors.toList());
            product.setParameters(parameterValues);
        }

        return product;
    }

    private com.techstore.dto.external.ImageDto mapImage(TekraImage tekraImage) {
        com.techstore.dto.external.ImageDto image = new com.techstore.dto.external.ImageDto();
        image.setHref(tekraImage.getUrl());
        return image;
    }

    private ParameterValueRequestDto mapParameterValue(TekraParameter tekraParameter) {
        ParameterValueRequestDto paramValue = new ParameterValueRequestDto();

        paramValue.setParameterId(tekraParameter.getId() + 40000L);

        // Set parameter names
        List<NameDto> paramNames = new ArrayList<>();
        paramNames.add(createNameDto("en", tekraParameter.getName()));
        paramNames.add(createNameDto("bg", translateParameterName(tekraParameter.getName())));
        paramValue.setParameterName(paramNames);

        // Set option based on value or first available option
        if (tekraParameter.getOptions() != null && !tekraParameter.getOptions().isEmpty()) {
            TekraParameterOption firstOption = tekraParameter.getOptions().get(0);
            paramValue.setOptionId(firstOption.getId() + 50000L);

            List<NameDto> optionNames = new ArrayList<>();
            String optionValue = StringUtils.hasText(firstOption.getLabel()) ?
                    firstOption.getLabel() : firstOption.getValue();
            optionNames.add(createNameDto("en", optionValue));
            optionNames.add(createNameDto("bg", optionValue));
            paramValue.setOptionName(optionNames);
        } else if (StringUtils.hasText(tekraParameter.getValue())) {
            paramValue.setOptionId(tekraParameter.getId() + 50000L);

            List<NameDto> optionNames = new ArrayList<>();
            String optionValue = tekraParameter.getValue();
            if (StringUtils.hasText(tekraParameter.getUnit())) {
                optionValue += " " + tekraParameter.getUnit();
            }
            optionNames.add(createNameDto("en", optionValue));
            optionNames.add(createNameDto("bg", optionValue));
            paramValue.setOptionName(optionNames);
        }

        return paramValue;
    }

    private Integer mapProductStatus(TekraProduct tekraProduct) {
        if (tekraProduct.getInStock() != null && tekraProduct.getInStock()) {
            return ProductStatus.AVAILABLE.getCode();
        } else if ("available".equalsIgnoreCase(tekraProduct.getAvailability())) {
            return ProductStatus.AVAILABLE.getCode();
        } else if ("limited".equalsIgnoreCase(tekraProduct.getAvailability())) {
            return ProductStatus.LIMITED_QUANTITY.getCode();
        } else if ("preorder".equalsIgnoreCase(tekraProduct.getAvailability()) ||
                "on_demand".equalsIgnoreCase(tekraProduct.getAvailability())) {
            return ProductStatus.ON_DEMAND.getCode();
        } else {
            return ProductStatus.NOT_AVAILABLE.getCode();
        }
    }

    private String generateReferenceNumber(TekraProduct product) {
        if (StringUtils.hasText(product.getSku())) {
            return "TK-" + product.getSku();
        } else {
            return "TK-" + product.getId();
        }
    }

    private String translateCategoryName(String englishName) {
        if (englishName == null) return null;

        // Add more comprehensive translations for video surveillance categories
        return switch (englishName.toLowerCase()) {
            case "ip cameras" -> "IP камери";
            case "analog cameras" -> "Аналогови камери";
            case "wireless cameras" -> "Безжични камери";
            case "dome cameras" -> "Куполни камери";
            case "bullet cameras" -> "Цилиндрични камери";
            case "ptz cameras" -> "PTZ камери";
            case "thermal cameras" -> "Топлинни камери";
            case "wildlife cameras", "trail cameras" -> "Ловни камери";
            case "hidden cameras" -> "Скрити камери";
            case "nvr systems" -> "NVR системи";
            case "dvr systems" -> "DVR системи";
            case "monitors" -> "Монитори";
            case "accessories" -> "Аксесоари";
            case "cables" -> "Кабели";
            case "power supplies" -> "Захранвания";
            case "lenses" -> "Обективи";
            default -> englishName; // Keep original if no translation
        };
    }

    private String translateParameterName(String englishName) {
        // Basic translations for common parameter names
        if (englishName == null) return null;

        return switch (englishName.toLowerCase()) {
            case "resolution" -> "Резолюция";
            case "battery life" -> "Живот на батерията";
            case "memory" -> "Памет";
            case "storage" -> "Съхранение";
            case "weight" -> "Тегло";
            case "dimensions" -> "Размери";
            case "color" -> "Цвят";
            case "brand" -> "Марка";
            case "model" -> "Модел";
            case "warranty" -> "Гаранция";
            case "night vision" -> "Нощно виждане";
            case "infrared" -> "Инфрачервено";
            case "motion detection" -> "Детекция на движение";
            case "viewing angle" -> "Ъгъл на виждане";
            case "zoom" -> "Увеличение";
            case "connectivity" -> "Свързаност";
            case "power consumption" -> "Консумация на енергия";
            case "operating temperature" -> "Работна температура";
            case "waterproof" -> "Водоустойчив";
            default -> englishName; // Keep original if no translation
        };
    }

    private String generateSlug(String name) {
        if (name == null) return null;

        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private NameDto createNameDto(String languageCode, String text) {
        NameDto nameDto = new NameDto();
        nameDto.setLanguageCode(languageCode);
        nameDto.setText(text);
        return nameDto;
    }
}