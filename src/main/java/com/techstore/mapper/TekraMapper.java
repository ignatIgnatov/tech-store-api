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

    private static final String WILDLIFE_SURVEILLANCE_CATEGORY_NAME_EN = "Wildlife Surveillance";
    private static final String WILDLIFE_SURVEILLANCE_CATEGORY_NAME_BG = "Наблюдение на дивата природа";

    /**
     * Maps Tekra categories to internal category format
     */
    public List<CategoryRequestFromExternalDto> mapCategories(List<TekraCategory> tekraCategories) {
        List<CategoryRequestFromExternalDto> categories = new ArrayList<>();

        // First, create the main Wildlife Surveillance category
        CategoryRequestFromExternalDto wildlifeCategory = createWildlifeSurveillanceCategory();
        categories.add(wildlifeCategory);

        // Map other relevant categories as subcategories
        for (TekraCategory tekraCategory : tekraCategories) {
            if (isRelevantCategory(tekraCategory)) {
                CategoryRequestFromExternalDto category = new CategoryRequestFromExternalDto();

                // Use unique external ID (add 10000 to avoid conflicts)
                category.setId(tekraCategory.getId() + 10000L);
                category.setParent(1000L); // Parent is Wildlife Surveillance
                category.setShow(tekraCategory.getIsActive() != null ? tekraCategory.getIsActive() : true);
                category.setOrder(tekraCategory.getSortOrder() != null ? tekraCategory.getSortOrder() : 0);
                category.setSlug(generateSlug(tekraCategory.getName()));

                // Set multilingual names
                List<NameDto> names = new ArrayList<>();
                names.add(createNameDto("en", tekraCategory.getName()));
                names.add(createNameDto("bg", tekraCategory.getName())); // Assuming same name for now
                category.setName(names);

                categories.add(category);
            }
        }

        return categories;
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
     * Maps Tekra parameters to internal format
     */
    public List<ParameterRequestDto> mapParameters(List<TekraParameter> tekraParameters) {
        return tekraParameters.stream()
                .map(this::mapParameter)
                .collect(Collectors.toList());
    }

    /**
     * Maps Tekra products to internal format
     */
    public List<ProductRequestDto> mapProducts(List<TekraProduct> tekraProducts) {
        return tekraProducts.stream()
                .map(this::mapProduct)
                .collect(Collectors.toList());
    }

    private CategoryRequestFromExternalDto createWildlifeSurveillanceCategory() {
        CategoryRequestFromExternalDto category = new CategoryRequestFromExternalDto();
        category.setId(1000L); // Fixed ID for Wildlife Surveillance
        category.setParent(null); // Top-level category
        category.setShow(true);
        category.setOrder(100);
        category.setSlug("wildlife-surveillance");

        List<NameDto> names = new ArrayList<>();
        names.add(createNameDto("en", WILDLIFE_SURVEILLANCE_CATEGORY_NAME_EN));
        names.add(createNameDto("bg", WILDLIFE_SURVEILLANCE_CATEGORY_NAME_BG));
        category.setName(names);

        return category;
    }

    private ManufacturerRequestDto mapManufacturer(TekraManufacturer tekraManufacturer) {
        ManufacturerRequestDto manufacturer = new ManufacturerRequestDto();

        // Use unique external ID (add 20000 to avoid conflicts)
        manufacturer.setId(tekraManufacturer.getId() + 20000L);
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

    private ParameterRequestDto mapParameter(TekraParameter tekraParameter) {
        ParameterRequestDto parameter = new ParameterRequestDto();

        // Use unique external ID (add 30000 to avoid conflicts)
        parameter.setId(tekraParameter.getId() + 30000L);
        parameter.setCategoryId(1000L); // Wildlife Surveillance category
        parameter.setOrder(tekraParameter.getSortOrder() != null ? tekraParameter.getSortOrder() : 0);

        // Set multilingual names
        List<NameDto> names = new ArrayList<>();
        names.add(createNameDto("en", tekraParameter.getName()));
        names.add(createNameDto("bg", translateParameterName(tekraParameter.getName())));
        parameter.setName(names);

        // Map parameter options if available
        if (tekraParameter.getOptions() != null && !tekraParameter.getOptions().isEmpty()) {
            List<ParameterOptionRequestDto> options = tekraParameter.getOptions().stream()
                    .map(this::mapParameterOption)
                    .collect(Collectors.toList());
            parameter.setOptions(options);
        } else if (StringUtils.hasText(tekraParameter.getValue())) {
            // Create single option from value
            ParameterOptionRequestDto option = new ParameterOptionRequestDto();
            option.setId(tekraParameter.getId() + 40000L);
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

        // Use unique external ID (add 40000 to avoid conflicts)
        option.setId(tekraOption.getId() + 40000L);
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

        // Use unique external ID (add 50000 to avoid conflicts)
        product.setId(tekraProduct.getId() + 50000L);
        product.setReferenceNumber(generateReferenceNumber(tekraProduct));
        product.setModel(tekraProduct.getModel());
        product.setBarcode(tekraProduct.getBarcode());
        product.setManufacturerId(tekraProduct.getManufacturer() != null ?
                tekraProduct.getManufacturer().getId() + 20000L : null);

        // Set status based on availability
        product.setStatus(mapProductStatus(tekraProduct));

        // Set prices
        product.setPriceClient(tekraProduct.getPrice());
        product.setPricePromo(tekraProduct.getPricePromo());
        product.setShow(tekraProduct.getIsActive() != null ? tekraProduct.getIsActive() : true);
        product.setWarranty(tekraProduct.getWarranty());
        product.setWeight(tekraProduct.getWeight());

        // Set category - always Wildlife Surveillance for filtered products
        CategoryIdDto categoryId = new CategoryIdDto();
        categoryId.setId(1000L);
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

        paramValue.setParameterId(tekraParameter.getId() + 30000L);

        // Set parameter names
        List<NameDto> paramNames = new ArrayList<>();
        paramNames.add(createNameDto("en", tekraParameter.getName()));
        paramNames.add(createNameDto("bg", translateParameterName(tekraParameter.getName())));
        paramValue.setParameterName(paramNames);

        // Set option based on value or first available option
        if (tekraParameter.getOptions() != null && !tekraParameter.getOptions().isEmpty()) {
            TekraParameterOption firstOption = tekraParameter.getOptions().get(0);
            paramValue.setOptionId(firstOption.getId() + 40000L);

            List<NameDto> optionNames = new ArrayList<>();
            String optionValue = StringUtils.hasText(firstOption.getLabel()) ?
                    firstOption.getLabel() : firstOption.getValue();
            optionNames.add(createNameDto("en", optionValue));
            optionNames.add(createNameDto("bg", optionValue));
            paramValue.setOptionName(optionNames);
        } else if (StringUtils.hasText(tekraParameter.getValue())) {
            paramValue.setOptionId(tekraParameter.getId() + 40000L);

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

    private boolean isRelevantCategory(TekraCategory category) {
        if (category.getName() == null) {
            return false;
        }

        String name = category.getName().toLowerCase();
        return name.contains("камер") || name.contains("camera") ||
                name.contains("surveillance") || name.contains("наблюдение") ||
                name.contains("ловн") || name.contains("trail");
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