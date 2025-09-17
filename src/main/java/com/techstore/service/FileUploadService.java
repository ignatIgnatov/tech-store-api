package com.techstore.service;

import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.ValidationException;
import com.techstore.util.ExceptionHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FileUploadService {

    // Configuration
    @Value("${app.upload.directory:./uploads}")
    private String uploadDirectory;

    @Value("${app.upload.max-file-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${app.upload.allowed-extensions:jpg,jpeg,png,gif,webp}")
    private String[] allowedExtensions;

    // Constants
    private static final long MAX_TOTAL_STORAGE = 5L * 1024 * 1024 * 1024; // 5GB
    private static final int MAX_FILES_PER_FOLDER = 10000;
    private static final Set<String> ALLOWED_SUBFOLDERS = Set.of("products", "categories", "brands", "documents");

    // Validation patterns
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern SAFE_SUBFOLDER_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    // ============ PUBLIC FILE OPERATIONS ============

    public String uploadFile(MultipartFile file, String subfolder) {
        log.info("Uploading file: {} to subfolder: {}", file.getOriginalFilename(), subfolder);

        String context = ExceptionHelper.createErrorContext(
                "uploadFile", "File", null,
                String.format("filename: %s, subfolder: %s", file.getOriginalFilename(), subfolder));

        return ExceptionHelper.wrapBusinessOperation(() -> {
            // Comprehensive validation
            validateUploadRequest(file, subfolder);

            // Validate storage limits
            validateStorageLimits(subfolder);

            try {
                // Create upload directory structure
                Path uploadPath = createUploadDirectory(subfolder);

                // Generate safe filename
                String safeFilename = generateSafeFilename(file.getOriginalFilename());

                // Perform upload
                Path filePath = performFileUpload(file, uploadPath, safeFilename);

                String relativePath = "/" + subfolder + "/" + safeFilename;

                log.info("File uploaded successfully: {}", filePath);
                return relativePath;

            } catch (IOException e) {
                log.error("IO error during file upload: {}", e.getMessage());
                throw new BusinessLogicException("File upload failed due to storage error: " + e.getMessage());
            } catch (SecurityException e) {
                log.error("Security error during file upload: {}", e.getMessage());
                throw new BusinessLogicException("File upload failed due to security restrictions: " + e.getMessage());
            }
        }, context);
    }

    public void deleteFile(String filePath) {
        log.info("Deleting file: {}", filePath);

        String context = ExceptionHelper.createErrorContext("deleteFile", "File", null, "path: " + filePath);

        ExceptionHelper.wrapBusinessOperation(() -> {
            // Validate delete request
            validateDeleteRequest(filePath);

            try {
                Path absolutePath = resolveSecurePath(filePath);

                if (!Files.exists(absolutePath)) {
                    log.warn("Attempt to delete non-existent file: {}", filePath);
                    return null; // Don't throw error for non-existent files
                }

                // Validate file is within allowed directory
                validateFileWithinUploadDirectory(absolutePath);

                Files.deleteIfExists(absolutePath);
                log.info("File deleted successfully: {}", absolutePath);

            } catch (IOException e) {
                log.error("Error deleting file {}: {}", filePath, e.getMessage());
                throw new BusinessLogicException("Failed to delete file: " + e.getMessage());
            } catch (SecurityException e) {
                log.error("Security error deleting file {}: {}", filePath, e.getMessage());
                throw new BusinessLogicException("Access denied when deleting file: " + e.getMessage());
            }

            return null;
        }, context);
    }

    public void deleteFiles(List<String> filePaths) {
        log.info("Bulk deleting {} files", filePaths != null ? filePaths.size() : 0);

        String context = ExceptionHelper.createErrorContext(
                "deleteFiles", "File", null,
                "count: " + (filePaths != null ? filePaths.size() : 0));

        ExceptionHelper.wrapBusinessOperation(() -> {
            validateBulkDeleteRequest(filePaths);

            int successCount = 0;
            int errorCount = 0;

            for (String filePath : filePaths) {
                try {
                    deleteFile(filePath);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to delete file {} in bulk operation: {}", filePath, e.getMessage());
                }
            }

            log.info("Bulk delete completed - Success: {}, Errors: {}", successCount, errorCount);

            if (errorCount > 0 && successCount == 0) {
                throw new BusinessLogicException(
                        String.format("Bulk delete failed - %d errors occurred", errorCount));
            }

            return null;
        }, context);
    }

    // ============ UTILITY METHODS ============

    public boolean fileExists(String filePath) {
        log.debug("Checking file existence: {}", filePath);

        String context = ExceptionHelper.createErrorContext("fileExists", "File", null, "path: " + filePath);

        return ExceptionHelper.wrapBusinessOperation(() -> {
            validateFilePath(filePath);

            try {
                Path absolutePath = resolveSecurePath(filePath);
                validateFileWithinUploadDirectory(absolutePath);
                return Files.exists(absolutePath);
            } catch (Exception e) {
                log.debug("Error checking file existence for {}: {}", filePath, e.getMessage());
                return false;
            }
        }, context);
    }

    public long getFileSize(String filePath) {
        log.debug("Getting file size: {}", filePath);

        String context = ExceptionHelper.createErrorContext("getFileSize", "File", null, "path: " + filePath);

        return ExceptionHelper.wrapBusinessOperation(() -> {
            validateFilePath(filePath);

            try {
                Path absolutePath = resolveSecurePath(filePath);
                validateFileWithinUploadDirectory(absolutePath);

                if (!Files.exists(absolutePath)) {
                    throw new ValidationException("File does not exist: " + filePath);
                }

                return Files.size(absolutePath);
            } catch (IOException e) {
                log.error("Error getting file size for {}: {}", filePath, e.getMessage());
                throw new BusinessLogicException("Failed to get file size: " + e.getMessage());
            }
        }, context);
    }

    public String getFileContentType(String filePath) {
        log.debug("Getting content type for file: {}", filePath);

        String context = ExceptionHelper.createErrorContext("getFileContentType", "File", null, "path: " + filePath);

        return ExceptionHelper.wrapBusinessOperation(() -> {
            validateFilePath(filePath);

            try {
                Path absolutePath = resolveSecurePath(filePath);
                validateFileWithinUploadDirectory(absolutePath);

                if (!Files.exists(absolutePath)) {
                    throw new ValidationException("File does not exist: " + filePath);
                }

                String contentType = Files.probeContentType(absolutePath);
                return contentType != null ? contentType : getContentTypeFromExtension(filePath);
            } catch (IOException e) {
                log.error("Error getting content type for {}: {}", filePath, e.getMessage());
                return getContentTypeFromExtension(filePath);
            }
        }, context);
    }

    // ============ VALIDATION METHODS ============

    private void validateUploadRequest(MultipartFile file, String subfolder) {
        validateFile(file);
        validateSubfolder(subfolder);
    }

    private void validateFile(MultipartFile file) {
        if (file == null) {
            throw new ValidationException("File cannot be null");
        }

        if (file.isEmpty()) {
            throw new ValidationException("File cannot be empty");
        }

        validateFileSize(file);
        validateFileExtension(file);
        validateFileName(file);
        validateFileContent(file);
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() <= 0) {
            throw new ValidationException("File size must be greater than zero");
        }

        if (file.getSize() > maxFileSize) {
            throw new ValidationException(
                    String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes)",
                            file.getSize(), maxFileSize));
        }
    }

    private void validateFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new ValidationException("File must have a valid filename");
        }

        String extension = FilenameUtils.getExtension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            throw new ValidationException("File must have a valid extension");
        }

        extension = extension.toLowerCase();
        List<String> allowedExtList = Arrays.asList(allowedExtensions);

        if (!allowedExtList.contains(extension)) {
            throw new ValidationException(
                    String.format("File extension '%s' not allowed. Allowed extensions: %s",
                            extension, String.join(", ", allowedExtensions)));
        }
    }

    private void validateFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new ValidationException("File must have a valid filename");
        }

        if (originalFilename.length() > 255) {
            throw new ValidationException("Filename cannot exceed 255 characters");
        }

        // Check for dangerous characters
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new ValidationException("Filename contains invalid characters");
        }

        // Check for reserved names
        String baseName = FilenameUtils.getBaseName(originalFilename).toLowerCase();
        List<String> reservedNames = Arrays.asList("con", "prn", "aux", "nul", "com1", "com2", "lpt1", "lpt2");
        if (reservedNames.contains(baseName)) {
            throw new ValidationException("Filename uses reserved name: " + baseName);
        }
    }

    private void validateFileContent(MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            log.warn("File has no content type, relying on extension validation");
            return;
        }

        // Basic content type validation for images
        if (contentType.startsWith("image/")) {
            List<String> allowedImageTypes = Arrays.asList(
                    "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
            );
            if (!allowedImageTypes.contains(contentType.toLowerCase())) {
                throw new ValidationException("Image content type not allowed: " + contentType);
            }
        }
    }

    private void validateSubfolder(String subfolder) {
        if (!StringUtils.hasText(subfolder)) {
            throw new ValidationException("Subfolder is required");
        }

        String trimmedSubfolder = subfolder.trim();

        if (trimmedSubfolder.length() > 50) {
            throw new ValidationException("Subfolder name cannot exceed 50 characters");
        }

        if (!SAFE_SUBFOLDER_PATTERN.matcher(trimmedSubfolder).matches()) {
            throw new ValidationException("Subfolder name contains invalid characters. Only letters, numbers, underscores and hyphens are allowed");
        }

        if (!ALLOWED_SUBFOLDERS.contains(trimmedSubfolder)) {
            throw new ValidationException(
                    String.format("Subfolder '%s' not allowed. Allowed subfolders: %s",
                            trimmedSubfolder, ALLOWED_SUBFOLDERS));
        }
    }

    private void validateDeleteRequest(String filePath) {
        validateFilePath(filePath);
    }

    private void validateBulkDeleteRequest(List<String> filePaths) {
        if (filePaths == null) {
            throw new ValidationException("File paths list cannot be null");
        }

        if (filePaths.isEmpty()) {
            throw new ValidationException("File paths list cannot be empty");
        }

        if (filePaths.size() > 100) {
            throw new ValidationException("Cannot delete more than 100 files in a single operation");
        }

        for (String filePath : filePaths) {
            validateFilePath(filePath);
        }
    }

    private void validateFilePath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new ValidationException("File path cannot be empty");
        }

        if (filePath.length() > 500) {
            throw new ValidationException("File path cannot exceed 500 characters");
        }

        if (filePath.contains("..") || filePath.contains("~")) {
            throw new ValidationException("File path contains invalid characters");
        }
    }

    private void validateStorageLimits(String subfolder) {
        try {
            Path uploadPath = Paths.get(uploadDirectory, subfolder);

            if (Files.exists(uploadPath)) {
                // Check total storage usage
                long totalSize = Files.walk(uploadPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(path -> {
                            try {
                                return Files.size(path);
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .sum();

                if (totalSize > MAX_TOTAL_STORAGE) {
                    throw new BusinessLogicException(
                            String.format("Storage limit exceeded. Current usage: %d bytes, Limit: %d bytes",
                                    totalSize, MAX_TOTAL_STORAGE));
                }

                // Check file count
                long fileCount = Files.list(uploadPath).count();
                if (fileCount >= MAX_FILES_PER_FOLDER) {
                    throw new BusinessLogicException(
                            String.format("File count limit exceeded in folder '%s'. Current: %d, Limit: %d",
                                    subfolder, fileCount, MAX_FILES_PER_FOLDER));
                }
            }
        } catch (IOException e) {
            log.warn("Could not check storage limits for {}: {}", subfolder, e.getMessage());
            // Don't fail upload due to storage check issues
        }
    }

    private void validateFileWithinUploadDirectory(Path filePath) {
        try {
            Path uploadDir = Paths.get(uploadDirectory).toAbsolutePath().normalize();
            Path resolvedPath = filePath.toAbsolutePath().normalize();

            if (!resolvedPath.startsWith(uploadDir)) {
                throw new ValidationException("File path is outside allowed upload directory");
            }
        } catch (Exception e) {
            throw new ValidationException("Invalid file path: " + e.getMessage());
        }
    }

    // ============ HELPER METHODS ============

    private Path createUploadDirectory(String subfolder) throws IOException {
        Path uploadPath = Paths.get(uploadDirectory, subfolder);

        if (!Files.exists(uploadPath)) {
            try {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath);
            } catch (IOException e) {
                log.error("Failed to create upload directory: {}", uploadPath);
                throw new IOException("Could not create upload directory: " + e.getMessage());
            }
        }

        // Verify directory is writable
        if (!Files.isWritable(uploadPath)) {
            throw new IOException("Upload directory is not writable: " + uploadPath);
        }

        return uploadPath;
    }

    private String generateSafeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return UUID.randomUUID().toString() + ".tmp";
        }

        String extension = FilenameUtils.getExtension(originalFilename);
        String baseName = FilenameUtils.getBaseName(originalFilename);

        // Create a safe base name
        String safeBaseName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeBaseName.length() > 100) {
            safeBaseName = safeBaseName.substring(0, 100);
        }

        // Generate unique filename
        String uniqueId = UUID.randomUUID().toString();

        if (StringUtils.hasText(extension)) {
            return String.format("%s_%s.%s", safeBaseName, uniqueId, extension);
        } else {
            return String.format("%s_%s", safeBaseName, uniqueId);
        }
    }

    private Path performFileUpload(MultipartFile file, Path uploadPath, String filename) throws IOException {
        Path filePath = uploadPath.resolve(filename);

        // Ensure we're not overwriting existing files
        int counter = 1;
        Path originalPath = filePath;
        while (Files.exists(filePath)) {
            String nameWithoutExt = FilenameUtils.removeExtension(originalPath.getFileName().toString());
            String ext = FilenameUtils.getExtension(originalPath.getFileName().toString());
            String newName = String.format("%s_(%d).%s", nameWithoutExt, counter, ext);
            filePath = originalPath.getParent().resolve(newName);
            counter++;

            if (counter > 1000) {
                throw new IOException("Too many files with similar names exist");
            }
        }

        // Perform the actual file copy
        try {
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Cleanup on failure
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException cleanupException) {
                log.warn("Failed to cleanup file after upload error: {}", cleanupException.getMessage());
            }
            throw e;
        }

        // Verify upload was successful
        if (!Files.exists(filePath) || Files.size(filePath) != file.getSize()) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException cleanupException) {
                log.warn("Failed to cleanup incomplete upload: {}", cleanupException.getMessage());
            }
            throw new IOException("File upload verification failed");
        }

        return filePath;
    }

    private Path resolveSecurePath(String filePath) {
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }
        return Paths.get(uploadDirectory, filePath).normalize();
    }

    private String getContentTypeFromExtension(String filePath) {
        String extension = FilenameUtils.getExtension(filePath);
        if (!StringUtils.hasText(extension)) {
            return "application/octet-stream";
        }

        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    // ============ ADMINISTRATIVE METHODS ============

    public void cleanupOldFiles(String subfolder, long maxAgeMs) {
        log.info("Cleaning up old files in subfolder: {} older than {} ms", subfolder, maxAgeMs);

        String context = ExceptionHelper.createErrorContext(
                "cleanupOldFiles", "File", null,
                String.format("subfolder: %s, maxAge: %d", subfolder, maxAgeMs));

        ExceptionHelper.wrapBusinessOperation(() -> {
            validateSubfolder(subfolder);

            if (maxAgeMs <= 0) {
                throw new ValidationException("Max age must be positive");
            }

            try {
                Path uploadPath = Paths.get(uploadDirectory, subfolder);

                if (!Files.exists(uploadPath)) {
                    log.info("Subfolder does not exist: {}", subfolder);
                    return null;
                }

                long cutoffTime = System.currentTimeMillis() - maxAgeMs;
                int deletedCount = 0;

                try (var files = Files.list(uploadPath)) {
                    for (Path file : files.toList()) {
                        if (Files.isRegularFile(file)) {
                            long lastModified = Files.getLastModifiedTime(file).toMillis();
                            if (lastModified < cutoffTime) {
                                try {
                                    Files.delete(file);
                                    deletedCount++;
                                    log.debug("Deleted old file: {}", file);
                                } catch (IOException e) {
                                    log.warn("Failed to delete old file {}: {}", file, e.getMessage());
                                }
                            }
                        }
                    }
                }

                log.info("Cleanup completed - Deleted {} old files from {}", deletedCount, subfolder);

            } catch (IOException e) {
                log.error("Error during cleanup of {}: {}", subfolder, e.getMessage());
                throw new BusinessLogicException("Cleanup operation failed: " + e.getMessage());
            }

            return null;
        }, context);
    }

    public long getSubfolderSize(String subfolder) {
        log.debug("Getting total size for subfolder: {}", subfolder);

        String context = ExceptionHelper.createErrorContext("getSubfolderSize", "File", null, "subfolder: " + subfolder);

        return ExceptionHelper.wrapBusinessOperation(() -> {
            validateSubfolder(subfolder);

            try {
                Path uploadPath = Paths.get(uploadDirectory, subfolder);

                if (!Files.exists(uploadPath)) {
                    return 0L;
                }

                return Files.walk(uploadPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(path -> {
                            try {
                                return Files.size(path);
                            } catch (IOException e) {
                                log.warn("Could not get size for file {}: {}", path, e.getMessage());
                                return 0L;
                            }
                        })
                        .sum();
            } catch (IOException e) {
                log.error("Error calculating subfolder size for {}: {}", subfolder, e.getMessage());
                throw new BusinessLogicException("Failed to calculate subfolder size: " + e.getMessage());
            }
        }, context);
    }

    public int getFileCount(String subfolder) {
        log.debug("Getting file count for subfolder: {}", subfolder);

        String context = ExceptionHelper.createErrorContext("getFileCount", "File", null, "subfolder: " + subfolder);

        return ExceptionHelper.wrapBusinessOperation(() -> {
            validateSubfolder(subfolder);

            try {
                Path uploadPath = Paths.get(uploadDirectory, subfolder);

                if (!Files.exists(uploadPath)) {
                    return 0;
                }

                try (var files = Files.list(uploadPath)) {
                    return (int) files.filter(Files::isRegularFile).count();
                }
            } catch (IOException e) {
                log.error("Error counting files in {}: {}", subfolder, e.getMessage());
                throw new BusinessLogicException("Failed to count files: " + e.getMessage());
            }
        }, context);
    }
}