package com.techstore.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NameDto {
    @JsonProperty("language_code")
    private String languageCode;
    private String text;
}
