package com.univent.service;

import com.univent.model.dto.response.NewsItemResponse;
import com.univent.model.entity.*;
import com.univent.model.enums.PostStatus;
import com.univent.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final NewsArticleRepository newsArticleRepository;
    private final StudentPostRepository studentPostRepository;
    private final NewsUpvoteRepository newsUpvoteRepository;
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final RssFeedService rssFeedService;

    @Transactional
    public void refreshNews() {
        rssFeedService.fetchAndStoreNews();
    }

    @Transactional(readOnly = true)
    public Page<NewsItemResponse> getCombinedFeed(UUID collegeId, Pageable pageable) {
        // Fetch both news articles and student posts
        Page<NewsArticle> articles;
        Page<StudentPost> posts;

        if (collegeId != null) {
            articles = newsArticleRepository.findByCollegeIdOrderByPublishedAtDesc(collegeId, pageable);
            posts = studentPostRepository.findByCollegeIdAndStatusOrderByCreatedAtDesc(
                    collegeId, PostStatus.PUBLISHED, pageable);
        } else {
            articles = newsArticleRepository.findByOrderByPublishedAtDesc(pageable);
            posts = studentPostRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED, pageable);
        }

        // Combine and sort by date
        List<NewsItemResponse> combined = new ArrayList<>();

        articles.forEach(article -> combined.add(convertToNewsItem(article)));
        posts.forEach(post -> combined.add(convertToNewsItem(post)));

        combined.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));

        // Paginate the combined list
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), combined.size());

        return new org.springframework.data.domain.PageImpl<>(
                combined.subList(start, end),
                pageable,
                combined.size()
        );
    }

    @Transactional
    public StudentPost createStudentPost(User user, UUID collegeId, String content) {
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new RuntimeException("College not found"));

        if (content.length() > 500) {
            throw new RuntimeException("Post content must be less than 500 characters");
        }

        StudentPost post = new StudentPost();
        post.setUser(user);
        post.setCollege(college);
        post.setContent(content);
        post.setStatus(PostStatus.PENDING);
        post.setUpvotes(0);

        return studentPostRepository.save(post);
    }

    @Transactional
    public void upvoteNewsArticle(User user, UUID articleId) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("News article not found"));

        if (newsUpvoteRepository.existsByNewsArticleIdAndUserId(articleId, user.getId())) {
            // Remove upvote
            newsUpvoteRepository.deleteByNewsArticleIdAndUserId(articleId, user.getId());
            article.setUpvotes(article.getUpvotes() - 1);
        } else {
            // Add upvote
            NewsUpvote upvote = new NewsUpvote();
            upvote.setNewsArticle(article);
            upvote.setUser(user);
            newsUpvoteRepository.save(upvote);
            article.setUpvotes(article.getUpvotes() + 1);
        }

        newsArticleRepository.save(article);
    }

    @Transactional
    public void upvoteStudentPost(User user, UUID postId) {
        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Student post not found"));

        if (newsUpvoteRepository.existsByStudentPostIdAndUserId(postId, user.getId())) {
            // Remove upvote
            newsUpvoteRepository.deleteByStudentPostIdAndUserId(postId, user.getId());
            post.setUpvotes(post.getUpvotes() - 1);
        } else {
            // Add upvote
            NewsUpvote upvote = new NewsUpvote();
            upvote.setStudentPost(post);
            upvote.setUser(user);
            newsUpvoteRepository.save(upvote);
            post.setUpvotes(post.getUpvotes() + 1);
        }

        studentPostRepository.save(post);
    }

    private NewsItemResponse convertToNewsItem(NewsArticle article) {
        return NewsItemResponse.builder()
                .id(article.getId())
                .type("ARTICLE")
                .title(article.getTitle())
                .content(article.getSummary())
                .source(article.getSourceName())
                .url(article.getArticleUrl())
                .collegeId(article.getCollege() != null ? article.getCollege().getId() : null)
                .collegeName(article.getCollege() != null ? article.getCollege().getName() : null)
                .upvotes(article.getUpvotes())
                .publishedAt(article.getPublishedAt())
                .build();
    }

    private NewsItemResponse convertToNewsItem(StudentPost post) {
        return NewsItemResponse.builder()
                .id(post.getId())
                .type("POST")
                .title(post.getUser().getAnonymousUsername() + " posted an update")
                .content(post.getContent())
                .author(post.getUser().getAnonymousUsername())
                .authorAvatarColor(post.getUser().getAvatarColor())
                .collegeId(post.getCollege().getId())
                .collegeName(post.getCollege().getName())
                .upvotes(post.getUpvotes())
                .publishedAt(post.getCreatedAt())
                .status(post.getStatus().toString())
                .build();
    }
}