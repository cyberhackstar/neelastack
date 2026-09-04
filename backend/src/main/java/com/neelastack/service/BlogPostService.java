package com.neelastack.service;

import com.neelastack.dto.content.BlogPostDto;
import com.neelastack.dto.content.BlogPostRequest;
import com.neelastack.dto.content.BlogPostSummaryDto;
import com.neelastack.entity.BlogPost;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlogPostService {

    private final BlogPostRepository blogPostRepository;
    private final IndexNowService indexNowService;

    public Page<BlogPostSummaryDto> listPublished(Pageable pageable) {
        return blogPostRepository.findByPublishedTrueOrderByPublishedAtDesc(pageable)
                .map(this::toSummaryDto);
    }

    /** Includes drafts — used by the admin CMS, never exposed publicly. */
    public List<BlogPostSummaryDto> listAllForAdmin() {
        return blogPostRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream().map(this::toSummaryDto).toList();
    }

    public BlogPostDto getById(UUID id) {
        return blogPostRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + id));
    }

    public Page<BlogPostSummaryDto> search(String query, String tag, Pageable pageable) {
        String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();
        String normalizedTag = (tag == null || tag.isBlank()) ? null : tag.trim();
        return blogPostRepository.search(normalizedQuery, normalizedTag, pageable).map(this::toSummaryDto);
    }

    @Cacheable("blogPostBySlug")
    public BlogPostDto getBySlug(String slug) {
        return blogPostRepository.findBySlugAndPublishedTrue(slug)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + slug));
    }

    public List<BlogPostSummaryDto> getRelated(String slug) {
        BlogPost post = blogPostRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + slug));

        if (post.getCategory() == null) {
            return List.of();
        }

        return blogPostRepository
                .findTop3ByCategoryAndIdNotAndPublishedTrueOrderByPublishedAtDesc(post.getCategory(), post.getId())
                .stream().map(this::toSummaryDto).toList();
    }

    @Transactional
    public BlogPostDto create(BlogPostRequest request) {
        if (blogPostRepository.existsBySlug(request.slug())) {
            throw new BadRequestException("An article with slug '" + request.slug() + "' already exists");
        }
        BlogPost entity = BlogPost.builder()
                .title(request.title())
                .slug(request.slug())
                .excerpt(request.excerpt())
                .content(request.content())
                .coverImageUrl(request.coverImageUrl())
                .authorName(request.authorName())
                .category(request.category())
                .tags(request.tags() != null ? request.tags() : List.of())
                .metaTitle(request.metaTitle())
                .metaDescription(request.metaDescription())
                .published(request.published())
                .publishedAt(request.published() ? LocalDateTime.now() : null)
                .build();
        BlogPostDto saved = toDto(blogPostRepository.save(entity));
        if (request.published()) {
            indexNowService.notifyContentPublished("/blog/" + saved.slug());
            indexNowService.notifyContentPublished("/blog");
        }
        return saved;
    }

    @CacheEvict(value = "blogPostBySlug", allEntries = true)
    @Transactional
    public BlogPostDto update(UUID id, BlogPostRequest request) {
        BlogPost entity = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + id));

        boolean justPublished = !entity.isPublished() && request.published();

        entity.setTitle(request.title());
        entity.setSlug(request.slug());
        entity.setExcerpt(request.excerpt());
        entity.setContent(request.content());
        entity.setCoverImageUrl(request.coverImageUrl());
        entity.setAuthorName(request.authorName());
        entity.setCategory(request.category());
        entity.setTags(request.tags() != null ? request.tags() : entity.getTags());
        entity.setMetaTitle(request.metaTitle());
        entity.setMetaDescription(request.metaDescription());
        entity.setPublished(request.published());

        if (justPublished) {
            entity.setPublishedAt(LocalDateTime.now());
        }

        BlogPostDto saved = toDto(blogPostRepository.save(entity));
        // Ping on every save while published, not just the publish transition — edits
        // to an already-live article (a correction, a refreshed section) are exactly
        // the kind of update search engines should be told to recrawl.
        if (saved.publishedAt() != null && entity.isPublished()) {
            indexNowService.notifyContentPublished("/blog/" + saved.slug());
        }

        return saved;
    }

    @CacheEvict(value = "blogPostBySlug", allEntries = true)
    @Transactional
    public void delete(UUID id) {
        if (!blogPostRepository.existsById(id)) {
            throw new ResourceNotFoundException("Article not found: " + id);
        }
        blogPostRepository.deleteById(id);
    }

    private BlogPostSummaryDto toSummaryDto(BlogPost p) {
        return BlogPostSummaryDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .slug(p.getSlug())
                .excerpt(p.getExcerpt())
                .coverImageUrl(p.getCoverImageUrl())
                .authorName(p.getAuthorName())
                .category(p.getCategory())
                .tags(p.getTags())
                .published(p.isPublished())
                .publishedAt(p.getPublishedAt())
                .build();
    }

    private BlogPostDto toDto(BlogPost p) {
        return BlogPostDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .slug(p.getSlug())
                .excerpt(p.getExcerpt())
                .content(p.getContent())
                .coverImageUrl(p.getCoverImageUrl())
                .authorName(p.getAuthorName())
                .category(p.getCategory())
                .tags(p.getTags())
                .metaTitle(p.getMetaTitle())
                .metaDescription(p.getMetaDescription())
                .publishedAt(p.getPublishedAt())
                .build();
    }
}
