package com.univent.service;

import com.univent.model.dto.request.CollegeRequest;
import com.univent.model.dto.response.CollegeResponse;
import com.univent.model.entity.College;
import com.univent.repository.CollegeRepository;
import com.univent.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollegeService {

    private final CollegeRepository collegeRepository;
    private final SlugGenerator slugGenerator;

    @Transactional
    public CollegeResponse createCollege(CollegeRequest request) {
        College college = new College();
        college.setName(request.getName());
        college.setSlug(slugGenerator.generateSlug(request.getName()));
        college.setCity(request.getCity());
        college.setState(request.getState());
        college.setCollegeType(request.getCollegeType());
        college.setWebsite(request.getWebsite());
        college.setEmailDomain(request.getEmailDomain());
        college.setLogoUrl(request.getLogoUrl());
        college.setIsVerified(false);
        college.setAverageRating(java.math.BigDecimal.ZERO);
        college.setTotalReviews(0);

        College saved = collegeRepository.save(college);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CollegeResponse> getAllColleges(Pageable pageable) {
        return collegeRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public CollegeResponse getCollegeById(UUID id) {
        College college = collegeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("College not found with id: " + id));
        return mapToResponse(college);
    }

    @Transactional(readOnly = true)
    public CollegeResponse getCollegeBySlug(String slug) {
        College college = collegeRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("College not found with slug: " + slug));
        return mapToResponse(college);
    }

    @Transactional(readOnly = true)
    public Page<CollegeResponse> searchColleges(String query, Pageable pageable) {
        Page<College> colleges = collegeRepository.findByNameContainingIgnoreCaseOrCityContainingIgnoreCaseOrStateContainingIgnoreCase(
                query, query, query, pageable);
        return colleges.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<CollegeResponse> getCollegesByState(String state, Pageable pageable) {
        return collegeRepository.findByState(state, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<CollegeResponse> getCollegesByType(String collegeType, Pageable pageable) {
        return collegeRepository.findByCollegeType(collegeType, pageable).map(this::mapToResponse);
    }

    @Transactional
    public CollegeResponse updateCollege(UUID id, CollegeRequest request) {
        College college = collegeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("College not found with id: " + id));

        college.setName(request.getName());
        college.setSlug(slugGenerator.generateSlug(request.getName()));
        college.setCity(request.getCity());
        college.setState(request.getState());
        college.setCollegeType(request.getCollegeType());
        college.setWebsite(request.getWebsite());
        college.setEmailDomain(request.getEmailDomain());
        college.setLogoUrl(request.getLogoUrl());

        College updated = collegeRepository.save(college);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteCollege(UUID id) {
        if (!collegeRepository.existsById(id)) {
            throw new RuntimeException("College not found with id: " + id);
        }
        collegeRepository.deleteById(id);
    }

    public CollegeResponse mapToResponse(College college) {
        return CollegeResponse.builder()
                .id(college.getId())
                .name(college.getName())
                .slug(college.getSlug())
                .city(college.getCity())
                .state(college.getState())
                .collegeType(college.getCollegeType())
                .isVerified(college.getIsVerified())
                .website(college.getWebsite())
                .emailDomain(college.getEmailDomain())
                .logoUrl(college.getLogoUrl())
                .averageRating(college.getAverageRating())
                .totalReviews(college.getTotalReviews())
                .createdAt(college.getCreatedAt())
                .updatedAt(college.getUpdatedAt())
                .build();
    }
}