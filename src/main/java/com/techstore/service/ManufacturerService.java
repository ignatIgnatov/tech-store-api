package com.techstore.service;

import com.techstore.dto.request.ManufacturerRequestDto;
import com.techstore.dto.response.ManufacturerResponseDto;
import com.techstore.entity.Manufacturer;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.ManufacturerMapper;
import com.techstore.repository.ManufacturerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManufacturerService {

    private final ManufacturerRepository manufacturerRepository;
    private final ManufacturerMapper manufacturerMapper;

    @Cacheable(value = "manufacturers", key = "'all'")
    public List<ManufacturerResponseDto> getAllManufacturers(String language) {
        List<Manufacturer> manufacturers = manufacturerRepository.findAllByOrderByNameAsc();
        return manufacturers.stream()
                .map(manufacturerMapper::toResponseDto)
                .toList();
    }

    @Cacheable(value = "manufacturers", key = "#id")
    public ManufacturerResponseDto getManufacturerById(Long id, String language) {
        Manufacturer manufacturer = manufacturerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer not found with id: " + id));

        return manufacturerMapper.toResponseDto(manufacturer);
    }

    public ManufacturerResponseDto createManufacturer(ManufacturerRequestDto requestDto) {
        // ✅ Check for existing manufacturer by external ID first
        if (requestDto.getId() != null) {
            // Note: You need to add this method to ManufacturerRepository:
            // Optional<Manufacturer> findByExternalId(Long externalId);
            Optional<Manufacturer> existing = manufacturerRepository.findByExternalId(requestDto.getId());
            if (existing.isPresent()) {
                throw new DuplicateResourceException("Manufacturer already exists with external ID: " + requestDto.getId());
            }
        }

        // Secondary check by name (for manual creation)
        if (manufacturerRepository.existsByNameIgnoreCase(requestDto.getName())) {
            throw new DuplicateResourceException("Manufacturer already exists with name " + requestDto.getName());
        }

        Manufacturer manufacturer = new Manufacturer();

        // ✅ Set external ID if provided
        if (requestDto.getId() != null) {
            manufacturer.setExternalId(requestDto.getId());
        }

        manufacturer.setName(requestDto.getName());

        if (requestDto.getInformation() != null) {
            manufacturer.setInformationName(requestDto.getInformation().getName());
            manufacturer.setInformationEmail(requestDto.getInformation().getEmail());
            manufacturer.setInformationAddress(requestDto.getInformation().getAddress());
        }

        if (requestDto.getEuRepresentative() != null) {
            manufacturer.setEuRepresentativeName(requestDto.getEuRepresentative().getName());
            manufacturer.setEuRepresentativeEmail(requestDto.getEuRepresentative().getEmail());
            manufacturer.setEuRepresentativeAddress(requestDto.getEuRepresentative().getAddress());
        }

        manufacturer = manufacturerRepository.save(manufacturer);
        return manufacturerMapper.toResponseDto(manufacturer);
    }

    public Optional<Manufacturer> findByExternalId(Long externalId) {
        return manufacturerRepository.findByExternalId(externalId);
    }

    public ManufacturerResponseDto updateManufacturer(Long id, ManufacturerRequestDto requestDto) {
        Manufacturer manufacturer = findById(id);

        manufacturer.setName(requestDto.getName());

        if (requestDto.getInformation() != null) {
            manufacturer.setInformationName(requestDto.getInformation().getName());
            manufacturer.setInformationEmail(requestDto.getInformation().getEmail());
            manufacturer.setInformationAddress(requestDto.getInformation().getAddress());
        }

        if (requestDto.getEuRepresentative() != null) {
            manufacturer.setEuRepresentativeName(requestDto.getEuRepresentative().getName());
            manufacturer.setEuRepresentativeEmail(requestDto.getEuRepresentative().getEmail());
            manufacturer.setEuRepresentativeAddress(requestDto.getEuRepresentative().getAddress());
        }

        manufacturerRepository.save(manufacturer);
        return manufacturerMapper.toResponseDto(manufacturer);
    }

    public void deleteManufacturer(Long id) {
        if (!manufacturerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Manufacturer with id " + id + " not found");
        }
        manufacturerRepository.deleteById(id);
    }

    private Manufacturer findById(Long id) {
        return manufacturerRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Manufacturer not found with Id: " + id)
        );
    }
}