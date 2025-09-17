package com.techstore.controller;

import com.techstore.service.ProductService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Hidden
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageProxyController {

    private final ProductService productService;

    @GetMapping("/product/{productId}/primary")
    public void getPrimaryImage(@PathVariable Long productId, HttpServletResponse response) {
        try {
            String originalImageUrl = productService.getOriginalImageUrl(productId, true, 0);
            if (originalImageUrl != null) {
                proxyImage(originalImageUrl, response);
            } else {
                response.setStatus(HttpStatus.NOT_FOUND.value());
            }
        } catch (Exception e) {
            log.error("Error serving primary image for product {}: {}", productId, e.getMessage());
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @GetMapping("/product/{productId}/additional/{index}")
    public void getAdditionalImage(@PathVariable Long productId, @PathVariable int index,
                                   HttpServletResponse response) {
        try {
            String originalImageUrl = productService.getOriginalImageUrl(productId, false, index);
            if (originalImageUrl != null) {
                proxyImage(originalImageUrl, response);
            } else {
                response.setStatus(HttpStatus.NOT_FOUND.value());
            }
        } catch (Exception e) {
            log.error("Error serving additional image {} for product {}: {}", index, productId, e.getMessage());
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private void proxyImage(String imageUrl, HttpServletResponse response) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            // Set appropriate content type
            String contentType = connection.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                response.setContentType(contentType);
            } else {
                // Fallback content type based on URL extension
                response.setContentType(getContentTypeFromUrl(imageUrl));
            }

            // Set cache headers (1 hour cache)
            response.setHeader("Cache-Control", "public, max-age=3600");

            // Copy image data
            try (InputStream inputStream = connection.getInputStream();
                 OutputStream outputStream = response.getOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

        } catch (Exception e) {
            log.error("Error proxying image from {}: {}", imageUrl, e.getMessage());
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String getContentTypeFromUrl(String imageUrl) {
        if (imageUrl.toLowerCase().endsWith(".png")) return "image/png";
        if (imageUrl.toLowerCase().endsWith(".jpg") || imageUrl.toLowerCase().endsWith(".jpeg")) return "image/jpeg";
        if (imageUrl.toLowerCase().endsWith(".gif")) return "image/gif";
        if (imageUrl.toLowerCase().endsWith(".webp")) return "image/webp";
        if (imageUrl.toLowerCase().endsWith(".svg")) return "image/svg+xml";
        return "image/jpeg"; // default fallback
    }
}