package com.techstore.dto.tekra;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = {"id", "slug"})
public class TekraCategory {

    @JacksonXmlProperty(localName = "id")
    private Long id;

    @JacksonXmlProperty(localName = "name")
    private String name;

    @JacksonXmlProperty(localName = "slug")
    private String slug;

    @JacksonXmlProperty(localName = "parentId")
    private Long parentId;

    @JacksonXmlProperty(localName = "description")
    private String description;

    @JacksonXmlProperty(localName = "isActive")
    private Boolean isActive;

    @JacksonXmlProperty(localName = "sortOrder")
    private Integer sortOrder;
}