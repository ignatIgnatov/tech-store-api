package com.techstore.util;

import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;

import jakarta.persistence.EntityNotFoundException;
import java.util.function.Supplier;

@Slf4j
public class ExceptionHelper {

    /**
     * Wraps database operations and converts common exceptions to business exceptions
     */
    public static <T> T wrapDatabaseOperation(Supplier<T> operation, String context) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException e) {
            String message = parseDatabaseConstraintError(e, context);
            log.error("Database constraint violation in {}: {}", context, e.getMessage());
            throw new DuplicateResourceException(message);
        } catch (EmptyResultDataAccessException | EntityNotFoundException e) {
            log.error("Entity not found in {}: {}", context, e.getMessage());
            throw new ResourceNotFoundException("Resource not found in " + context);
        } catch (Exception e) {
            log.error("Unexpected database error in {}: {}", context, e.getMessage(), e);
            throw new BusinessLogicException("Database operation failed: " + e.getMessage());
        }
    }

    /**
     * Wraps validation operations
     */
    public static void validateAndThrow(boolean condition, String message) {
        if (!condition) {
            log.error("Validation failed: {}", message);
            throw new ValidationException(message);
        }
    }

    /**
     * Wraps business logic operations
     */
    public static <T> T wrapBusinessOperation(Supplier<T> operation, String context) {
        try {
            return operation.get();
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in {}: {}", context, e.getMessage());
            throw new ValidationException("Invalid input: " + e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Invalid state in {}: {}", context, e.getMessage());
            throw new BusinessLogicException("Operation not allowed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in {}: {}", context, e.getMessage(), e);
            throw new BusinessLogicException("Business operation failed: " + e.getMessage());
        }
    }

    /**
     * Finds resource or throws exception with context
     */
    public static <T> T findOrThrow(T resource, String resourceType, Object identifier) {
        if (resource == null) {
            String message = String.format("%s not found with identifier: %s", resourceType, identifier);
            log.error(message);
            throw new ResourceNotFoundException(message);
        }
        return resource;
    }

    /**
     * Parses database constraint errors to user-friendly messages
     */
    private static String parseDatabaseConstraintError(DataIntegrityViolationException e, String context) {
        String rootCause = e.getRootCause() != null ? e.getRootCause().getMessage().toLowerCase() : "";

        if (rootCause.contains("unique") || rootCause.contains("duplicate")) {
            if (rootCause.contains("email")) {
                return "Email address is already in use";
            } else if (rootCause.contains("username")) {
                return "Username is already taken";
            } else if (rootCause.contains("reference_number")) {
                return "Reference number already exists";
            } else if (rootCause.contains("slug")) {
                return "URL slug is already in use";
            }
            return "A record with this information already exists";
        } else if (rootCause.contains("foreign key")) {
            return "Cannot complete operation due to related data dependencies";
        } else if (rootCause.contains("not null")) {
            return "Required field cannot be empty";
        } else if (rootCause.contains("check")) {
            return "Data does not meet validation requirements";
        }

        return "Database constraint violation occurred";
    }

    /**
     * Creates detailed error context for logging
     */
    public static String createErrorContext(String operation, String entity, Object identifier, String additionalInfo) {
        StringBuilder context = new StringBuilder();
        context.append("Operation: ").append(operation);
        if (entity != null) {
            context.append(", Entity: ").append(entity);
        }
        if (identifier != null) {
            context.append(", ID: ").append(identifier);
        }
        if (additionalInfo != null) {
            context.append(", Info: ").append(additionalInfo);
        }
        return context.toString();
    }
}

/**
 * Example usage in services:
 *
 * @Service
 * public class ProductService {
 *
 *     public Product createProduct(ProductRequestDTO dto) {
 *         return ExceptionHelper.wrapDatabaseOperation(() -> {
 *             // Validation
 *             ExceptionHelper.validateAndThrow(
 *                 !productRepository.existsByReferenceNumber(dto.getReferenceNumber()),
 *                 "Product with reference number already exists: " + dto.getReferenceNumber()
 *             );
 *
 *             // Business logic
 *             Product product = convertToEntity(dto);
 *             return productRepository.save(product);
 *         }, "create product");
 *     }
 *
 *     public Product getProduct(Long id) {
 *         Product product = productRepository.findById(id).orElse(null);
 *         return ExceptionHelper.findOrThrow(product, "Product", id);
 *     }
 * }
 */