package com.univent.controller;

import com.univent.model.dto.response.ReviewResponse;
import com.univent.model.dto.response.SavedComparisonResponse;
import com.univent.model.dto.response.UserProfileResponse;
import com.univent.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProfileService.getCurrentUserProfile(userDetails.getUsername()));
    }

    @GetMapping("/me/reviews")
    public ResponseEntity<Page<ReviewResponse>> getMyReviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "published") String status,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(userProfileService.getCurrentUserReviews(userDetails.getUsername(), status, pageable));
    }

    @GetMapping("/me/saved-comparisons")
    public ResponseEntity<Page<SavedComparisonResponse>> getMySavedComparisons(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(userProfileService.getCurrentUserSavedComparisons(userDetails.getUsername(), pageable));
    }
}
