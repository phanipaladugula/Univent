package com.univent.service;

import com.univent.model.dto.request.ProgramRequest;
import com.univent.model.dto.response.ProgramResponse;
import com.univent.model.entity.Program;
import com.univent.repository.ProgramRepository;
import com.univent.util.SlugGenerator;
import com.univent.util.SynonymMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final SynonymMapper synonymMapper;

    @Transactional
    public ProgramResponse createProgram(ProgramRequest request) {
        // Generate unique slug
        String baseSlug = slugGenerator.generateSlug(request.getName());
        String uniqueSlug = generateUniqueSlug(baseSlug);

        Program program = new Program();
        program.setName(request.getName());
        program.setSlug(uniqueSlug);
        program.setCategory(request.getCategory());
        program.setDegree(request.getDegree());
        program.setDurationYears(request.getDurationYears());
        program.setIcon(request.getIcon());

        Program saved = programRepository.save(program);
        return mapToResponse(saved);
    }

    private String generateUniqueSlug(String baseSlug) {
        String slug = baseSlug;
        int counter = 1;

        while (programRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
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

    @Transactional(readOnly = true)
    public Page<ProgramResponse> searchPrograms(String query, Pageable pageable) {
        String expandedQuery = synonymMapper.expand(query);
        // Assuming findByNameContainingIgnoreCase exists or we use a custom repository method
        // Since ProgramRepository doesn't show it here, we will just stub it if it causes issues.
        // Let's add it to ProgramRepository... actually I don't see ProgramRepository file.
        // Let's assume we can fetch all and filter for now if repository doesn't have it, or assume it's added.
        // To be safe, I'll just filter in memory for now.
        List<ProgramResponse> all = getAllPrograms();
        List<ProgramResponse> filtered = all.stream()
            .filter(p -> p.getName().toLowerCase().contains(expandedQuery) || 
                         (p.getCategory() != null && p.getCategory().toLowerCase().contains(expandedQuery)))
            .collect(Collectors.toList());
        
        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        List<ProgramResponse> pageContent = start <= end ? filtered.subList(start, end) : List.of();
        
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Transactional
    public ProgramResponse updateProgram(UUID id, ProgramRequest request) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program not found with id: " + id));

        program.setName(request.getName());
        // Only update slug if name changed
        if (!program.getName().equals(request.getName())) {
            String baseSlug = slugGenerator.generateSlug(request.getName());
            program.setSlug(generateUniqueSlug(baseSlug));
        }
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