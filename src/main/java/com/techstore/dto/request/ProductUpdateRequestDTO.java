package com.techstore.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductUpdateRequestDTO extends ProductCreateRequestDTO {

    private List<ProductImageUpdateDTO> images;
    private List<String> imagesToDelete;
}