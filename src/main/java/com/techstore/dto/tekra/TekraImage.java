package com.techstore.dto.tekra;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class TekraImage {

    @JacksonXmlProperty(localName = "url")
    private String url;

    @JacksonXmlProperty(localName = "alt")
    private String alt;

    @JacksonXmlProperty(localName = "title")
    private String title;

    @JacksonXmlProperty(localName = "isPrimary")
    private Boolean isPrimary;

    @JacksonXmlProperty(localName = "sortOrder")
    private Integer sortOrder;

    @JacksonXmlProperty(localName = "width")
    private Integer width;

    @JacksonXmlProperty(localName = "height")
    private Integer height;
}