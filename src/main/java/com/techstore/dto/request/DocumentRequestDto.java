package com.techstore.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class DocumentRequestDto {
    private Long id;
    private Long productId;
    private String documentUrl;
    private List<CommentDto> comment;

    @Data
    public static class CommentDto {
        private String languageCode;
        private String text;
    }
}