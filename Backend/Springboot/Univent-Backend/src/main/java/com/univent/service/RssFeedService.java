package com.univent.service;

import com.univent.model.entity.College;
import com.univent.model.entity.NewsArticle;
import com.univent.repository.CollegeRepository;
import com.univent.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilderFactory;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssFeedService {

    private final NewsArticleRepository newsArticleRepository;
    private final CollegeRepository collegeRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    // RSS Feed sources
    private static final List<RssSource> RSS_SOURCES = List.of(
            new RssSource("Google News - Education", "https://news.google.com/rss/search?q=education+india&hl=en-IN&gl=IN&ceid=IN:en"),
            new RssSource("NDTV Education", "https://feeds.feedburner.com/ndtveducation"),
            new RssSource("Times of India - Education", "https://timesofindia.indiatimes.com/rssfeeds/913168846.cms")
    );

    // College name patterns for matching
    private static final Pattern COLLEGE_NAME_PATTERN = Pattern.compile(
            "IIT|NIT|IIIT|BITS|VIT|SRM|Christ|Jadavpur|BHU|AMU|JNU|DU|University"
    );

    @Transactional
    public int fetchAndStoreNews() {
        log.info("Starting RSS feed fetch");
        int newArticlesCount = 0;

        for (RssSource source : RSS_SOURCES) {
            try {
                String rssXml = restTemplate.getForObject(source.url, String.class);
                List<NewsArticle> articles = parseRssFeed(rssXml, source);

                for (NewsArticle article : articles) {
                    if (!newsArticleRepository.findByArticleUrl(article.getArticleUrl()).isPresent()) {
                        // Try to match with a college
                        matchCollegeToArticle(article);

                        newsArticleRepository.save(article);
                        newArticlesCount++;
                        log.info("Saved new article: {}", article.getTitle());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch RSS from {}: {}", source.name, e.getMessage());
            }
        }

        log.info("RSS fetch completed. New articles: {}", newArticlesCount);
        return newArticlesCount;
    }

    private List<NewsArticle> parseRssFeed(String rssXml, RssSource source) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            var factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();
            var document = builder.parse(new java.io.ByteArrayInputStream(rssXml.getBytes()));

            var itemList = document.getElementsByTagName("item");

            for (int i = 0; i < itemList.getLength(); i++) {
                var item = itemList.item(i);
                var title = getElementValue(item, "title");
                var link = getElementValue(item, "link");
                var pubDate = getElementValue(item, "pubDate");
                var description = getElementValue(item, "description");

                if (title == null || link == null) continue;

                NewsArticle article = new NewsArticle();
                article.setTitle(title);
                article.setSourceName(source.name);
                article.setSourceUrl(source.url);
                article.setArticleUrl(link);
                article.setPublishedAt(parsePubDate(pubDate));
                article.setSummary(description != null && description.length() > 500 ?
                        description.substring(0, 500) : description);
                article.setScrapedAt(LocalDateTime.now());
                article.setIsActive(true);
                article.setUpvotes(0);

                articles.add(article);
            }
        } catch (Exception e) {
            log.error("Failed to parse RSS feed: {}", e.getMessage());
        }

        return articles;
    }

    private String getElementValue(org.w3c.dom.Node item, String tagName) {
        var elements = ((org.w3c.dom.Element) item).getElementsByTagName(tagName);
        if (elements.getLength() > 0) {
            return elements.item(0).getTextContent();
        }
        return null;
    }

    private LocalDateTime parsePubDate(String pubDate) {
        try {
            // Handle RFC 822 date format
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(pubDate,
                    DateTimeFormatter.RFC_1123_DATE_TIME);
            return zonedDateTime.toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private void matchCollegeToArticle(NewsArticle article) {
        String title = article.getTitle().toLowerCase();
        String summary = article.getSummary() != null ? article.getSummary().toLowerCase() : "";

        // Find all colleges that might be mentioned
        List<College> allColleges = collegeRepository.findAll();

        for (College college : allColleges) {
            String collegeName = college.getName().toLowerCase();
            if (title.contains(collegeName) || summary.contains(collegeName)) {
                article.setCollege(college);
                log.debug("Matched article '{}' to college: {}", article.getTitle(), college.getName());
                break;
            }
        }
    }

    private record RssSource(String name, String url) {}
}