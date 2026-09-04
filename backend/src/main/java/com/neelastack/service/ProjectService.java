package com.neelastack.service;

import com.neelastack.dto.content.ProjectDto;
import com.neelastack.dto.content.ProjectRequest;
import com.neelastack.dto.content.ReviewDto;
import com.neelastack.dto.content.ReviewRequest;
import com.neelastack.entity.Project;
import com.neelastack.entity.Review;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.ProjectRepository;
import com.neelastack.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ReviewRepository reviewRepository;
    private final IndexNowService indexNowService;

    @Cacheable("projects")
    @Transactional(readOnly = true)
    public List<ProjectDto> listPublished() {
        return projectRepository.findByPublishedTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    /** Includes drafts — used by the admin CMS, never exposed publicly. */
    @Transactional(readOnly = true)
    public List<ProjectDto> listAllForAdmin() {
        return projectRepository.findAll(org.springframework.data.domain.Sort.by("displayOrder"))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto getById(UUID id) {
        return projectRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    @Cacheable("featuredProjects")
    @Transactional(readOnly = true)
    public List<ProjectDto> listFeatured() {
        return projectRepository.findByPublishedTrueAndFeaturedTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto getBySlug(String slug) {
        return projectRepository.findBySlugAndPublishedTrue(slug)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + slug));
    }

    @CacheEvict(value = {"projects", "featuredProjects"}, allEntries = true)
    @Transactional
    public ProjectDto create(ProjectRequest request) {
        if (projectRepository.existsBySlug(request.slug())) {
            throw new BadRequestException("A project with slug '" + request.slug() + "' already exists");
        }
        Project entity = Project.builder()
                .title(request.title())
                .slug(request.slug())
                .summary(request.summary())
                .problemStatement(request.problemStatement())
                .solution(request.solution())
                .outcome(request.outcome())
                .coverImageUrl(request.coverImageUrl())
                .techStack(request.techStack() != null ? request.techStack() : List.of())
                .liveUrl(request.liveUrl())
                .repoUrl(request.repoUrl())
                .featured(request.featured())
                .published(request.published())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .serviceCategories(request.serviceCategories() != null ? request.serviceCategories() : List.of())
                .keyMetrics(request.keyMetrics() != null ? request.keyMetrics() : List.of())
                .build();
        ProjectDto saved = toDto(projectRepository.save(entity));
        if (saved.published()) {
            indexNowService.notifyContentPublished("/portfolio/" + saved.slug());
            indexNowService.notifyContentPublished("/portfolio");
        }
        return saved;
    }

    @CacheEvict(value = {"projects", "featuredProjects"}, allEntries = true)
    @Transactional
    public ProjectDto update(UUID id, ProjectRequest request) {
        Project entity = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        entity.setTitle(request.title());
        entity.setSlug(request.slug());
        entity.setSummary(request.summary());
        entity.setProblemStatement(request.problemStatement());
        entity.setSolution(request.solution());
        entity.setOutcome(request.outcome());
        entity.setCoverImageUrl(request.coverImageUrl());
        entity.setTechStack(request.techStack() != null ? request.techStack() : entity.getTechStack());
        entity.setLiveUrl(request.liveUrl());
        entity.setRepoUrl(request.repoUrl());
        entity.setFeatured(request.featured());
        entity.setPublished(request.published());
        entity.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : entity.getDisplayOrder());
        entity.setServiceCategories(request.serviceCategories() != null ? request.serviceCategories() : entity.getServiceCategories());
        entity.setKeyMetrics(request.keyMetrics() != null ? request.keyMetrics() : entity.getKeyMetrics());
        ProjectDto saved = toDto(projectRepository.save(entity));
        if (saved.published()) {
            indexNowService.notifyContentPublished("/portfolio/" + saved.slug());
            indexNowService.notifyContentPublished("/portfolio");
        }
        return saved;
    }

    @CacheEvict(value = {"projects", "featuredProjects"}, allEntries = true)
    @Transactional
    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }

    // ---- Reviews (Review / AggregateRating structured data source) ----

    /** Admin view — includes unpublished drafts awaiting approval. */
    @Transactional(readOnly = true)
    public List<ReviewDto> listReviewsForAdmin(UUID projectId) {
        return reviewRepository.findByProjectIdOrderByDisplayOrderAsc(projectId)
                .stream().map(this::toReviewDto).toList();
    }

    @CacheEvict(value = {"projects", "featuredProjects"}, allEntries = true)
    @Transactional
    public ReviewDto addReview(UUID projectId, ReviewRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }
        Review review = Review.builder()
                .projectId(projectId)
                .authorName(request.authorName())
                .authorTitle(request.authorTitle())
                .rating(request.rating())
                .reviewBody(request.reviewBody())
                .published(request.published())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();
        return toReviewDto(reviewRepository.save(review));
    }

    @CacheEvict(value = {"projects", "featuredProjects"}, allEntries = true)
    @Transactional
    public ReviewDto updateReview(UUID projectId, UUID reviewId, ReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        review.setAuthorName(request.authorName());
        review.setAuthorTitle(request.authorTitle());
        review.setRating(request.rating());
        review.setReviewBody(request.reviewBody());
        review.setPublished(request.published());
        review.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : review.getDisplayOrder());
        return toReviewDto(reviewRepository.save(review));
    }

    @CacheEvict(value = {"projects", "featuredProjects"}, allEntries = true)
    @Transactional
    public void deleteReview(UUID projectId, UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        reviewRepository.delete(review);
    }

    /**
     * Module 4 moderation queue: client-submitted testimonials (from
     * TestimonialService#submit) awaiting an admin's publish decision. Not scoped
     * to a projectId path like the CMS review endpoints above, since a fresh
     * testimonial may not have a project assigned yet.
     */
    @Transactional(readOnly = true)
    public List<ReviewDto> listPendingTestimonials() {
        return reviewRepository.findBySubmittedViaAndPublishedFalseOrderByCreatedAtDesc(
                        com.neelastack.entity.ReviewSource.CLIENT_TESTIMONIAL)
                .stream().map(this::toReviewDto).toList();
    }

    /**
     * Publishes (or rejects) a client-submitted testimonial, optionally assigning
     * it to a case study for the first time. This is the only path by which a
     * CLIENT_TESTIMONIAL-sourced review can go live -- submission alone
     * (TestimonialService#submit) never sets published=true.
     */
    @CacheEvict(value = {"projects", "featuredProjects"}, allEntries = true)
    @Transactional
    public ReviewDto moderateTestimonial(UUID reviewId, UUID projectId, boolean published) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        if (review.getSubmittedVia() != com.neelastack.entity.ReviewSource.CLIENT_TESTIMONIAL) {
            throw new BadRequestException("Only client-submitted testimonials go through this moderation endpoint.");
        }
        if (projectId != null) {
            if (!projectRepository.existsById(projectId)) {
                throw new ResourceNotFoundException("Project not found: " + projectId);
            }
            review.setProjectId(projectId);
        }
        review.setPublished(published);
        return toReviewDto(reviewRepository.save(review));
    }

    private ProjectDto toDto(Project p) {
        List<ReviewDto> publishedReviews = reviewRepository
                .findByProjectIdAndPublishedTrueOrderByDisplayOrderAsc(p.getId())
                .stream().map(this::toReviewDto).toList();
        Double averageRating = reviewRepository.findAverageRatingByProjectId(p.getId()).orElse(null);
        long reviewCount = reviewRepository.countByProjectIdAndPublishedTrue(p.getId());

        return ProjectDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .slug(p.getSlug())
                .summary(p.getSummary())
                .problemStatement(p.getProblemStatement())
                .solution(p.getSolution())
                .outcome(p.getOutcome())
                .coverImageUrl(p.getCoverImageUrl())
                .techStack(new ArrayList<>(p.getTechStack()))
                .liveUrl(p.getLiveUrl())
                .repoUrl(p.getRepoUrl())
                .featured(p.isFeatured())
                .published(p.isPublished())
                .reviews(publishedReviews)
                .averageRating(averageRating != null ? Math.round(averageRating * 10) / 10.0 : null)
                .reviewCount((int) reviewCount)
                .serviceCategories(new ArrayList<>(p.getServiceCategories()))
                .keyMetrics(new ArrayList<>(p.getKeyMetrics()))
                .build();
    }

    private ReviewDto toReviewDto(Review r) {
        return ReviewDto.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .authorName(r.getAuthorName())
                .authorTitle(r.getAuthorTitle())
                .rating(r.getRating())
                .reviewBody(r.getReviewBody())
                .published(r.isPublished())
                .displayOrder(r.getDisplayOrder())
                .videoUrl(r.getVideoUrl())
                .submittedVia(r.getSubmittedVia().name())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
