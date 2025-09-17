package com.techstore.controller;

import com.techstore.dto.request.ParameterRequestDto;
import com.techstore.dto.response.ParameterResponseDto;
import com.techstore.service.ParameterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/parameters")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ParameterController {

    private final ParameterService parameterService;

    @PostMapping
    public ResponseEntity<ParameterResponseDto> createParameter(
            @Valid @RequestBody ParameterRequestDto requestDto,
            @RequestParam(defaultValue = "en") String language) {

        log.info("Creating parameter for category ID: {}", requestDto.getCategoryId());
        ParameterResponseDto parameter = parameterService.createParameter(requestDto, language);
        return ResponseEntity.status(HttpStatus.CREATED).body(parameter);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParameterResponseDto> updateParameter(
            @PathVariable("id") Long id,
            @Valid @RequestBody ParameterRequestDto requestDto,
            @RequestParam(defaultValue = "en") String language) {

        log.info("Updating parameter with ID: {}", id);
        ParameterResponseDto parameter = parameterService.updateParameter(id, requestDto, language);
        return ResponseEntity.ok(parameter);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParameter(@PathVariable @NotNull Long id) {
        log.info("Deleting parameter with ID: {}", id);
        parameterService.deleteParameter(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ParameterResponseDto>> getParametersByCategory(
            @PathVariable @NotNull Long categoryId,
            @RequestParam(defaultValue = "en") String language) {

        log.info("Fetching parameters for category ID: {} in language: {}", categoryId, language);
        List<ParameterResponseDto> parameters = parameterService.findByCategory(categoryId, language);
        return ResponseEntity.ok(parameters);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParameterResponseDto> getParameterById(
            @PathVariable @NotNull Long id,
            @RequestParam(defaultValue = "en") String language) {

        log.info("Fetching parameter with ID: {} in language: {}", id, language);
        ParameterResponseDto parameter = parameterService.getParameterById(id, language);
        return ResponseEntity.ok(parameter);
    }

    @GetMapping("/all")
    public ResponseEntity<List<ParameterResponseDto>> getAllParameters(
            @RequestParam(defaultValue = "en") String language
    ) {
        List<ParameterResponseDto> response = parameterService.getAllParameters(language);
        return ResponseEntity.ok(response);
    }
}