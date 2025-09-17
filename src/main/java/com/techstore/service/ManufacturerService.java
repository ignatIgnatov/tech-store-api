package com.techstore.service;

import com.techstore.dto.request.ManufacturerRequestDto;
import com.techstore.dto.response.ManufacturerResponseDto;
import com.techstore.entity.Manufacturer;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ValidationException;
import com.techstore.mapper.ManufacturerMapper;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.util.ExceptionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ManufacturerService {

    private final ManufacturerRepository manufacturerRepository;
    private final ManufacturerMapper manufacturerMapper;

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );

    @Transactional(readOnly = true)
    @Cacheable(value = "manufacturers", key = "'all'")
    public List<ManufacturerResponseDto> getAllManufacturers(String language) {
        log.debug("Fetching all manufacturers for language: {}", language);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            List<Manufacturer> manufacturers = manufacturerRepository.findAllByOrderByNameAsc();
            return manufacturers.stream()
                    .map(manufacturerMapper::toResponseDto)
                    .toList();
        }, "fetch all manufacturers");
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "manufacturers", key = "#id")
    public ManufacturerResponseDto getManufacturerById(Long id, String language) {
        log.debug("Fetching manufacturer with id: {}", id);

        validateManufacturerId(id);

        Manufacturer manufacturer = findManufacturerByIdOrThrow(id);
        return manufacturerMapper.toResponseDto(manufacturer);
    }

    @CacheEvict(value = "manufacturers", allEntries = true)
    public ManufacturerResponseDto createManufacturer(ManufacturerRequestDto requestDto) {
        log.info("Creating manufacturer: {}", requestDto.getName());

        String context = ExceptionHelper.createErrorContext(
                "createManufacturer", "Manufacturer", requestDto.getId(), requestDto.getName());

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Comprehensive validation
            validateManufacturerRequest(requestDto, true);

            // Check for duplicates
            checkForDuplicateManufacturer(requestDto);

            // Create manufacturer
            Manufacturer manufacturer = createManufacturerFromRequest(requestDto);
            manufacturer = manufacturerRepository.save(manufacturer);

            log.info("Manufacturer created successfully with id: {} and name: {}",
                    manufacturer.getId(), manufacturer.getName());

            return manufacturerMapper.toResponseDto(manufacturer);

        }, context);
    }

    @CacheEvict(value = "manufacturers", allEntries = true)
    public ManufacturerResponseDto updateManufacturer(Long id, ManufacturerRequestDto requestDto) {
        log.info("Updating manufacturer with id: {}", id);

        String context = ExceptionHelper.createErrorContext("updateManufacturer", "Manufacturer", id, null);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateManufacturerId(id);
            validateManufacturerRequest(requestDto, false);

            // Find existing manufacturer
            Manufacturer existingManufacturer = findManufacturerByIdOrThrow(id);

            // Check for name conflicts if name is changing
            if (!existingManufacturer.getName().equalsIgnoreCase(requestDto.getName())) {
                checkForDuplicateManufacturerName(requestDto.getName());
            }

            // Update manufacturer
            updateManufacturerFromRequest(existingManufacturer, requestDto);
            Manufacturer updatedManufacturer = manufacturerRepository.save(existingManufacturer);

            log.info("Manufacturer updated successfully with id: {}", id);
            return manufacturerMapper.toResponseDto(updatedManufacturer);

        }, context);
    }

    @CacheEvict(value = "manufacturers", allEntries = true)
    public void deleteManufacturer(Long id) {
        log.info("Deleting manufacturer with id: {}", id);

        String context = ExceptionHelper.createErrorContext("deleteManufacturer", "Manufacturer", id, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateManufacturerId(id);

            Manufacturer manufacturer = findManufacturerByIdOrThrow(id);

            // Business validation for deletion
            validateManufacturerDeletion(manufacturer);

            manufacturerRepository.deleteById(id);

            log.info("Manufacturer deleted successfully with id: {}", id);
            return null;
        }, context);
    }

    @Transactional(readOnly = true)
    public Optional<Manufacturer> findByExternalId(Long externalId) {
        log.debug("Finding manufacturer by external id: {}", externalId);

        if (externalId == null) {
            throw new ValidationException("External ID cannot be null");
        }

        return manufacturerRepository.findByExternalId(externalId);
    }

    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }

        return manufacturerRepository.existsByNameIgnoreCase(name.trim());
    }

    // ========== PRIVATE VALIDATION METHODS ==========

    private void validateManufacturerId(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Manufacturer ID must be a positive number");
        }
    }

    private void validateManufacturerRequest(ManufacturerRequestDto requestDto, boolean isCreate) {
        if (requestDto == null) {
            throw new ValidationException("Manufacturer request cannot be null");
        }

        // Validate name
        if (!StringUtils.hasText(requestDto.getName())) {
            throw new ValidationException("Manufacturer name is required");
        }

        String trimmedName = requestDto.getName().trim();
        if (trimmedName.length() > 255) {
            throw new ValidationException("Manufacturer name cannot exceed 255 characters");
        }

        if (trimmedName.length() < 2) {
            throw new ValidationException("Manufacturer name must be at least 2 characters long");
        }

        // Validate information section if present
        if (requestDto.getInformation() != null) {
            validateManufacturerInformation(requestDto.getInformation());
        }

        // Validate EU representative section if present
        if (requestDto.getEuRepresentative() != null) {
            validateEuRepresentative(requestDto.getEuRepresentative());
        }
    }

    private void validateManufacturerInformation(com.techstore.dto.request.ManufacturerInformationDto info) {
        if (StringUtils.hasText(info.getName()) && info.getName().length() > 255) {
            throw new ValidationException("Manufacturer information name cannot exceed 255 characters");
        }

        if (StringUtils.hasText(info.getEmail()) && !isValidEmail(info.getEmail())) {
            throw new ValidationException("Invalid email format in manufacturer information");
        }

        if (StringUtils.hasText(info.getAddress()) && info.getAddress().length() > 1000) {
            throw new ValidationException("Manufacturer information address cannot exceed 1000 characters");
        }
    }

    private void validateEuRepresentative(com.techstore.dto.request.ManufacturerEuRepresentativeDto euRep) {
        if (StringUtils.hasText(euRep.getName()) && euRep.getName().length() > 255) {
            throw new ValidationException("EU representative name cannot exceed 255 characters");
        }

        if (StringUtils.hasText(euRep.getEmail()) && !isValidEmail(euRep.getEmail())) {
            throw new ValidationException("Invalid email format in EU representative information");
        }

        if (StringUtils.hasText(euRep.getAddress()) && euRep.getAddress().length() > 1000) {
            throw new ValidationException("EU representative address cannot exceed 1000 characters");
        }
    }

    private void validateManufacturerDeletion(Manufacturer manufacturer) {
        // Check if manufacturer has products
        if (manufacturer.getProducts() != null && !manufacturer.getProducts().isEmpty()) {
            long activeProducts = manufacturer.getProducts().stream()
                    .mapToLong(product -> product.getActive() ? 1 : 0)
                    .sum();

            if (activeProducts > 0) {
                throw new BusinessLogicException(
                        String.format("Cannot delete manufacturer '%s' because it has %d active products. " +
                                        "Please reassign or delete the products first.",
                                manufacturer.getName(), activeProducts));
            }
        }
    }

    private boolean isValidEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    // ========== PRIVATE HELPER METHODS ==========

    private Manufacturer findManufacturerByIdOrThrow(Long id) {
        return ExceptionHelper.findOrThrow(
                manufacturerRepository.findById(id).orElse(null),
                "Manufacturer",
                id
        );
    }

    private void checkForDuplicateManufacturer(ManufacturerRequestDto requestDto) {
        // Check by external ID if provided
        if (requestDto.getId() != null && manufacturerRepository.findByExternalId(requestDto.getId()).isPresent()) {
            throw new DuplicateResourceException(
                    "Manufacturer already exists with external ID: " + requestDto.getId());
        }

        // Check by name
        checkForDuplicateManufacturerName(requestDto.getName());
    }

    private void checkForDuplicateManufacturerName(String name) {
        if (manufacturerRepository.existsByNameIgnoreCase(name.trim())) {
            throw new DuplicateResourceException(
                    "Manufacturer already exists with name: " + name);
        }
    }

    private Manufacturer createManufacturerFromRequest(ManufacturerRequestDto requestDto) {
        Manufacturer manufacturer = new Manufacturer();

        // Set external ID if provided
        if (requestDto.getId() != null) {
            manufacturer.setExternalId(requestDto.getId());
        }

        manufacturer.setName(requestDto.getName().trim());

        // Set information if provided
        if (requestDto.getInformation() != null) {
            setManufacturerInformation(manufacturer, requestDto.getInformation());
        }

        // Set EU representative if provided
        if (requestDto.getEuRepresentative() != null) {
            setEuRepresentative(manufacturer, requestDto.getEuRepresentative());
        }

        return manufacturer;
    }

    private void updateManufacturerFromRequest(Manufacturer manufacturer, ManufacturerRequestDto requestDto) {
        // Update name
        manufacturer.setName(requestDto.getName().trim());

        // Update information
        if (requestDto.getInformation() != null) {
            setManufacturerInformation(manufacturer, requestDto.getInformation());
        } else {
            // Clear information if not provided
            manufacturer.setInformationName(null);
            manufacturer.setInformationEmail(null);
            manufacturer.setInformationAddress(null);
        }

        // Update EU representative
        if (requestDto.getEuRepresentative() != null) {
            setEuRepresentative(manufacturer, requestDto.getEuRepresentative());
        } else {
            // Clear EU representative if not provided
            manufacturer.setEuRepresentativeName(null);
            manufacturer.setEuRepresentativeEmail(null);
            manufacturer.setEuRepresentativeAddress(null);
        }
    }

    private void setManufacturerInformation(Manufacturer manufacturer,
                                            com.techstore.dto.request.ManufacturerInformationDto info) {
        manufacturer.setInformationName(StringUtils.hasText(info.getName()) ? info.getName().trim() : null);
        manufacturer.setInformationEmail(StringUtils.hasText(info.getEmail()) ? info.getEmail().trim() : null);
        manufacturer.setInformationAddress(StringUtils.hasText(info.getAddress()) ? info.getAddress().trim() : null);
    }

    private void setEuRepresentative(Manufacturer manufacturer,
                                     com.techstore.dto.request.ManufacturerEuRepresentativeDto euRep) {
        manufacturer.setEuRepresentativeName(StringUtils.hasText(euRep.getName()) ? euRep.getName().trim() : null);
        manufacturer.setEuRepresentativeEmail(StringUtils.hasText(euRep.getEmail()) ? euRep.getEmail().trim() : null);
        manufacturer.setEuRepresentativeAddress(StringUtils.hasText(euRep.getAddress()) ? euRep.getAddress().trim() : null);
    }
}