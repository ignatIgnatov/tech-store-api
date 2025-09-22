package com.techstore.dto.tekra;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class TekraParameter {

    @JacksonXmlProperty(localName = "id")
    private Long id;

    @JacksonXmlProperty(localName = "name")
    private String name;

    @JacksonXmlProperty(localName = "type")
    private String type; // text, select, multiselect, boolean, number

    @JacksonXmlProperty(localName = "value")
    private String value;

    @JacksonXmlProperty(localName = "unit")
    private String unit;

    @JacksonXmlProperty(localName = "isRequired")
    private Boolean isRequired;

    @JacksonXmlProperty(localName = "sortOrder")
    private Integer sortOrder;

    @JacksonXmlElementWrapper(localName = "options")
    @JacksonXmlProperty(localName = "option")
    private List<TekraParameterOption> options;
}