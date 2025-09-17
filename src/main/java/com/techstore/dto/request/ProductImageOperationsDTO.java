package com.techstore.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageOperationsDTO {
    private List<String> imagesToDelete;           // URLs to delete
    private List<ProductImageUpdateDTO> reorderImages;  // Reorder existing images
    private Boolean replacePrimaryImage;           // Whether to replace primary with newPrimaryImage
}
