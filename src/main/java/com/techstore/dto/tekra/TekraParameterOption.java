package com.techstore.dto.tekra;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class TekraParameterOption {

    @JacksonXmlProperty(localName = "id")
    private Long id;

    @JacksonXmlProperty(localName = "value")
    private String value;

    @JacksonXmlProperty(localName = "label")
    private String label;

    @JacksonXmlProperty(localName = "sortOrder")
    private Integer sortOrder;

    @JacksonXmlProperty(localName = "isDefault")
    private Boolean isDefault;
}