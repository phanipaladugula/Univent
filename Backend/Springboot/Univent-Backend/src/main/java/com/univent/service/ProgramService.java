package com.univent.service;

import com.univent.model.dto.request.ProgramRequest;
import com.univent.model.dto.response.ProgramResponse;
import com.univent.model.entity.Program;
import com.univent.repository.ProgramRepository;
import com.univent.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramRepository programRepository;
    private final SlugGenerator slugGenerator;

    @Transactional
    public ProgramResponse createProgram(ProgramRequest request) {
        Program program = new Program();
        program.setName(request.getName());
        program.setSlug(slugGenerator.generateSlug(request.getName()));
        program.setCategory(request.getCategory());
        program.setDegree(request.getDegree());
        program.setDurationYears(request.getDurationYears());
        program.setIcon(request.getIcon());

        Program saved = programRepository.save(program);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProgramResponse> getAllPrograms() {
        return programRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProgramResponse getProgramById(UUID id) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program not found with id: " + id));
        return mapToResponse(program);
    }

    @Transactional(readOnly = true)
    public ProgramResponse getProgramBySlug(String slug) {
        Program program = programRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Program not found with slug: " + slug));
        return mapToResponse(program);
    }

    @Transactional(readOnly = true)
    public List<ProgramResponse> getProgramsByCategory(String category) {
        return programRepository.findByCategory(category).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProgramResponse updateProgram(UUID id, ProgramRequest request) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program not found with id: " + id));

        program.setName(request.getName());
        program.setSlug(slugGenerator.generateSlug(request.getName()));
        program.setCategory(request.getCategory());
        program.setDegree(request.getDegree());
        program.setDurationYears(request.getDurationYears());
        program.setIcon(request.getIcon());

        Program updated = programRepository.save(program);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteProgram(UUID id) {
        if (!programRepository.existsById(id)) {
            throw new RuntimeException("Program not found with id: " + id);
        }
        programRepository.deleteById(id);
    }

    public ProgramResponse mapToResponse(Program program) {
        return ProgramResponse.builder()
                .id(program.getId())
                .name(program.getName())
                .slug(program.getSlug())
                .category(program.getCategory())
                .degree(program.getDegree())
                .durationYears(program.getDurationYears())
                .icon(program.getIcon())
                .createdAt(program.getCreatedAt())
                .updatedAt(program.getUpdatedAt())
                .build();
    }
    public com.univent.model.entity.Program findEntityById(UUID id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program not found"));
    }
}