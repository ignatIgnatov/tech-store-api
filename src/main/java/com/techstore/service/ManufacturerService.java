package com.techstore.service;

import com.techstore.dto.response.ManufacturerResponseDto;
import com.techstore.entity.Manufacturer;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.ManufacturerMapper;
import com.techstore.repository.ManufacturerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Cacheable(value = "manufacturers", key = "'with_products'")
    public List<ManufacturerResponseDto> getManufacturersWithProducts(String language) {
        List<Manufacturer> manufacturers = manufacturerRepository.findManufacturersWithAvailableProducts();
        return manufacturers.stream()
                .map(manufacturerMapper::toResponseDto)
                .toList();
    }
}