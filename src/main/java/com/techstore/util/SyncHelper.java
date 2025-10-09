package com.techstore.util;

import com.techstore.entity.Category;
import com.techstore.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncHelper {

    private final CategoryRepository categoryRepository;

    // ===========================================
    // SLUG GENERATION
    // ===========================================

    public String createSlugFromName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return transliterateCyrillic(name)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public String normalizeCategoryForPath(String categoryName) {
        if (categoryName == null) return null;

        String transliterated = transliterateCyrillic(categoryName.trim());

        return transliterated.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    // ===========================================
    // SLUG EXISTENCE CHECKS
    // ===========================================

    public boolean slugExistsInMap(String slug, Map<String, Category> existingCategories) {
        return existingCategories.containsKey(slug);
    }

    public boolean slugExistsInDatabase(String slug, Category parentCategory) {
        List<Category> existing = categoryRepository.findAll().stream()
                .filter(cat -> slug.equals(cat.getSlug()))
                .toList();

        if (existing.isEmpty()) {
            return false;
        }

        if (parentCategory != null) {
            for (Category cat : existing) {
                Category catParent = cat.getParent();

                if (catParent == null && parentCategory != null) {
                    return true;
                }
                if (catParent != null && parentCategory == null) {
                    return true;
                }
                if (catParent != null && !catParent.getId().equals(parentCategory.getId())) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    // ===========================================
    // CATEGORY LOOKUP BY PATH
    // ===========================================

    /**
     * Find category by hierarchical path (e.g., "zahranvaniya-i-baterii/za-postoyanno-naprezhenie")
     *
     * @param path The category path with slugs separated by "/"
     * @return Optional containing the category if found, empty otherwise
     */
    public Optional<Category> findCategoryByPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            log.debug("Empty path provided");
            return Optional.empty();
        }

        String[] slugs = path.split("/");

        if (slugs.length == 0) {
            log.debug("No slugs found in path: {}", path);
            return Optional.empty();
        }

        // Start with root level categories
        Category currentCategory = null;

        for (int i = 0; i < slugs.length; i++) {
            String slug = slugs[i].trim();

            if (slug.isEmpty()) {
                continue;
            }

            // Find category with this slug that has the current category as parent
            Category finalCurrentCategory = currentCategory;
            Optional<Category> foundCategory = categoryRepository.findAll().stream()
                    .filter(cat -> slug.equals(cat.getSlug()))
                    .filter(cat -> {
                        if (finalCurrentCategory == null) {
                            // Looking for root level category (no parent)
                            return cat.getParent() == null;
                        } else {
                            // Looking for subcategory
                            return cat.getParent() != null &&
                                    cat.getParent().getId().equals(finalCurrentCategory.getId());
                        }
                    })
                    .findFirst();

            if (foundCategory.isEmpty()) {
                log.warn("Category not found at level {} for slug '{}' in path '{}' (parent: {})",
                        i + 1, slug, path,
                        finalCurrentCategory != null ? finalCurrentCategory.getSlug() : "root");
                return Optional.empty();
            }

            currentCategory = foundCategory.get();
        }

        if (currentCategory != null) {
            log.debug("Successfully found category '{}' (ID: {}) for path '{}'",
                    currentCategory.getNameBg(), currentCategory.getId(), path);
        }

        return Optional.ofNullable(currentCategory);
    }

    /**
     * Build category path from multiple category names
     */
    public String buildCategoryPath(String category1, String category2, String category3) {
        List<String> parts = new ArrayList<>();

        if (category1 != null) {
            parts.add(normalizeCategoryForPath(category1));
        }
        if (category2 != null) {
            parts.add(normalizeCategoryForPath(category2));
        }
        if (category3 != null) {
            parts.add(normalizeCategoryForPath(category3));
        }

        return parts.isEmpty() ? null : String.join("/", parts);
    }

    // ===========================================
    // DISCRIMINATOR EXTRACTION
    // ===========================================

    public String extractDiscriminator(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) {
            return null;
        }

        String lowerName = categoryName.toLowerCase();

        Map<String, String> keywords = Map.ofEntries(
                Map.entry("ip", "ip"),
                Map.entry("аналогов", "analog"),
                Map.entry("hd", "hd"),
                Map.entry("wifi", "wifi"),
                Map.entry("безжичн", "wireless"),
                Map.entry("куполн", "dome"),
                Map.entry("булет", "bullet"),
                Map.entry("вътрешн", "indoor"),
                Map.entry("външн", "outdoor"),
                Map.entry("nvr", "nvr"),
                Map.entry("dvr", "dvr")
        );

        for (Map.Entry<String, String> entry : keywords.entrySet()) {
            if (lowerName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        String[] words = lowerName.split("\\s+");
        if (words.length > 0 && words[0].length() > 2) {
            String transliterated = transliterateCyrillic(words[0]);
            return transliterated
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]", "")
                    .substring(0, Math.min(4, transliterated.length()));
        }

        return null;
    }

    // ===========================================
    // TRANSLITERATION
    // ===========================================

    public String transliterateCyrillic(String text) {
        if (text == null) return "";

        Map<Character, String> transliterationMap = Map.ofEntries(
                Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"),
                Map.entry('г', "g"), Map.entry('д', "d"), Map.entry('е', "e"),
                Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
                Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"),
                Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
                Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"),
                Map.entry('т', "t"), Map.entry('у', "u"), Map.entry('ф', "f"),
                Map.entry('х', "h"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
                Map.entry('ш', "sh"), Map.entry('щ', "sht"), Map.entry('ъ', "a"),
                Map.entry('ь', "y"), Map.entry('ю', "yu"), Map.entry('я', "ya"),
                Map.entry('А', "A"), Map.entry('Б', "B"), Map.entry('В', "V"),
                Map.entry('Г', "G"), Map.entry('Д', "D"), Map.entry('Е', "E"),
                Map.entry('Ж', "Zh"), Map.entry('З', "Z"), Map.entry('И', "I"),
                Map.entry('Й', "Y"), Map.entry('К', "K"), Map.entry('Л', "L"),
                Map.entry('М', "M"), Map.entry('Н', "N"), Map.entry('О', "O"),
                Map.entry('П', "P"), Map.entry('Р', "R"), Map.entry('С', "S"),
                Map.entry('Т', "T"), Map.entry('У', "U"), Map.entry('Ф', "F"),
                Map.entry('Х', "H"), Map.entry('Ц', "Ts"), Map.entry('Ч', "Ch"),
                Map.entry('Ш', "Sh"), Map.entry('Щ', "Sht"), Map.entry('Ъ', "A"),
                Map.entry('Ь', "Y"), Map.entry('Ю', "Yu"), Map.entry('Я', "Ya")
        );

        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(transliterationMap.getOrDefault(c, String.valueOf(c)));
        }
        return result.toString();
    }
}