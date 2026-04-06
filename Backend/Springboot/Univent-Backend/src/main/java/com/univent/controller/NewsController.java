package com.univent.controller;

import com.univent.model.dto.request.StudentPostRequest;
import com.univent.model.dto.response.NewsItemResponse;
import com.univent.model.entity.StudentPost;
import com.univent.model.entity.User;
import com.univent.repository.UserRepository;
import com.univent.service.NewsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsService newsService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<NewsItemResponse>> getNewsFeed(
            @RequestParam(required = false) UUID collegeId,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Fetching news feed for college: {}", collegeId);
        return ResponseEntity.ok(newsService.getCombinedFeed(collegeId, pageable));
    }

    @PostMapping("/posts")
    public ResponseEntity<StudentPost> createStudentPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody StudentPostRequest request) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentPost post = newsService.createStudentPost(user, request.getCollegeId(), request.getContent());
        log.info("User {} created student post: {}", user.getAnonymousUsername(), post.getId());

        return ResponseEntity.ok(post);
    }

    @PostMapping("/articles/{articleId}/upvote")
    public ResponseEntity<Void> upvoteArticle(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID articleId) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        newsService.upvoteNewsArticle(user, articleId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/posts/{postId}/upvote")
    public ResponseEntity<Void> upvotePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID postId) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        newsService.upvoteStudentPost(user, postId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> refreshNews() {
        log.info("Admin triggered news refresh");
        newsService.refreshNews();
        return ResponseEntity.accepted().build();
    }
}