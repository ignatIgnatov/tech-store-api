package com.techstore.dto.tekra;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@JacksonXmlRootElement(localName = "feed")
public class TekraProductFeed {

    @JacksonXmlElementWrapper(localName = "products")
    @JacksonXmlProperty(localName = "product")
    private List<TekraProduct> products;

    @JacksonXmlProperty(localName = "categories")
    private List<TekraCategory> categories;

    @JacksonXmlProperty(localName = "totalProducts")
    private Integer totalProducts;

    @JacksonXmlProperty(localName = "currentPage")
    private Integer currentPage;

    @JacksonXmlProperty(localName = "totalPages")
    private Integer totalPages;
}