package com.techstore.service;

import com.techstore.exception.BusinessLogicException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${app.upload.max-file-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${app.upload.allowed-extensions:jpg,jpeg,png,gif,webp}")
    private String[] allowedExtensions;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String uploadProductImage(MultipartFile file, String subfolder) {
        validateFile(file);

        try {
            String fileName = generateFileName(file.getOriginalFilename());
            String key = subfolder + "/" + fileName;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String imageUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);

            log.info("Successfully uploaded image to S3: {}", imageUrl);
            return imageUrl;

        } catch (IOException e) {
            log.error("Error uploading file to S3: {}", e.getMessage());
            throw new BusinessLogicException("Failed to upload file: " + e.getMessage());
        }
    }

    public void deleteImage(String imageUrl) {
        try {
            String key = extractKeyFromUrl(imageUrl);

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted image from S3: {}", imageUrl);

        } catch (Exception e) {
            log.error("Error deleting file from S3: {}", e.getMessage());
            // Don't throw exception here to avoid breaking the main operation
        }
    }

    public void deleteImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        for (String imageUrl : imageUrls) {
            deleteImage(imageUrl);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessLogicException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new BusinessLogicException("File size exceeds maximum allowed size of " + maxFileSize + " bytes");
        }

        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        List<String> allowedExtList = Arrays.asList(allowedExtensions);

        if (!allowedExtList.contains(extension)) {
            throw new BusinessLogicException("File extension not allowed. Allowed extensions: " + String.join(", ", allowedExtensions));
        }
    }

    private String generateFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        return UUID.randomUUID().toString() + "." + extension;
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private String extractKeyFromUrl(String imageUrl) {
        // Extract key from URL like: https://bucket.s3.region.amazonaws.com/key
        try {
            return imageUrl.substring(imageUrl.indexOf(".amazonaws.com/") + 14);
        } catch (Exception e) {
            log.warn("Could not extract key from URL: {}", imageUrl);
            return "";
        }
    }
}