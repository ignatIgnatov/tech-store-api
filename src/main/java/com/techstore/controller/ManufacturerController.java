package com.techstore.controller;

import com.techstore.dto.request.ManufacturerRequestDto;
import com.techstore.dto.response.ManufacturerResponseDto;
import com.techstore.service.ManufacturerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturers")
@RequiredArgsConstructor
public class ManufacturerController {

    private final ManufacturerService manufacturerService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ManufacturerResponseDto>> getAll(@RequestParam(required = false) String language) {
        return ResponseEntity.ok(manufacturerService.getAllManufacturers(language));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ManufacturerResponseDto> getById(
            @PathVariable Long id,
            @RequestParam(required = false) String language
    ) {
        return ResponseEntity.ok(manufacturerService.getManufacturerById(id, language));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
//    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ManufacturerResponseDto> create(@RequestBody ManufacturerRequestDto requestDto) {
        ManufacturerResponseDto responseDto = manufacturerService.createManufacturer(requestDto);
        return ResponseEntity.ok(responseDto);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
//    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ManufacturerResponseDto> update(
            @PathVariable Long id,
            @RequestBody ManufacturerRequestDto requestDto
    ) {
        ManufacturerResponseDto responseDto = manufacturerService.updateManufacturer(id, requestDto);
        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
//    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        manufacturerService.deleteManufacturer(id);
        return ResponseEntity.noContent().build();
    }
}

