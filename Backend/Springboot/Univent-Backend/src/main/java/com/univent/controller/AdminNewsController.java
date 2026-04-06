package com.univent.controller;

import org.springframework.data.domain.Sort;
import com.univent.model.entity.StudentPost;
import com.univent.model.entity.User;
import com.univent.model.enums.PostStatus;
import com.univent.repository.StudentPostRepository;
import com.univent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/news")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminNewsController {

    private final StudentPostRepository studentPostRepository;
    private final UserRepository userRepository;

    @GetMapping("/posts/pending")
    public ResponseEntity<Page<StudentPost>> getPendingPosts(Pageable pageable) {
        return ResponseEntity.ok(studentPostRepository.findByStatus(PostStatus.PENDING, pageable));
    }

    @PutMapping("/posts/{postId}/approve")
    public ResponseEntity<StudentPost> approvePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID postId) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setStatus(PostStatus.PUBLISHED);
        post.setModeratedBy(admin);
        post.setModeratedAt(LocalDateTime.now());

        StudentPost saved = studentPostRepository.save(post);
        log.info("Admin {} approved student post: {}", admin.getAnonymousUsername(), postId);

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/posts/{postId}/reject")
    public ResponseEntity<Void> rejectPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID postId,
            @RequestParam String reason) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setStatus(PostStatus.REJECTED);
        post.setRejectionReason(reason);
        post.setModeratedBy(admin);
        post.setModeratedAt(LocalDateTime.now());

        studentPostRepository.save(post);
        log.info("Admin {} rejected student post: {} with reason: {}",
                admin.getAnonymousUsername(), postId, reason);

        return ResponseEntity.ok().build();
    }
}