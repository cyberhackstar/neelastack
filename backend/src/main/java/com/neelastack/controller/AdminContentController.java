package com.neelastack.controller;

import com.neelastack.dto.content.*;
import com.neelastack.service.BlogPostService;
import com.neelastack.service.ProjectService;
import com.neelastack.service.ServiceContentService;
import com.neelastack.service.TechStackPageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin content", description = "CMS endpoints — requires ROLE_ADMIN")
public class AdminContentController {

    private final ServiceContentService serviceContentService;
    private final ProjectService projectService;
    private final BlogPostService blogPostService;
    private final TechStackPageService techStackPageService;

    // ---- Services ----
    @GetMapping("/services")
    public List<ServiceDto> listAllServices() {
        return serviceContentService.listAllForAdmin();
    }

    @GetMapping("/services/{id}")
    public ServiceDto getService(@PathVariable UUID id) {
        return serviceContentService.getById(id);
    }

    @PostMapping("/services")
    public ResponseEntity<ServiceDto> createService(@Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceContentService.create(request));
    }

    @PutMapping("/services/{id}")
    public ServiceDto updateService(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
        return serviceContentService.update(id, request);
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable UUID id) {
        serviceContentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Projects ----
    @GetMapping("/projects")
    public List<ProjectDto> listAllProjects() {
        return projectService.listAllForAdmin();
    }

    @GetMapping("/projects/{id}")
    public ProjectDto getProject(@PathVariable UUID id) {
        return projectService.getById(id);
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectDto> createProject(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @PutMapping("/projects/{id}")
    public ProjectDto updateProject(@PathVariable UUID id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Blog ----
    @GetMapping("/blog")
    public List<BlogPostSummaryDto> listAllBlogPosts() {
        return blogPostService.listAllForAdmin();
    }

    @GetMapping("/blog/{id}")
    public BlogPostDto getBlogPost(@PathVariable UUID id) {
        return blogPostService.getById(id);
    }

    @PostMapping("/blog")
    public ResponseEntity<BlogPostDto> createBlogPost(@Valid @RequestBody BlogPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(blogPostService.create(request));
    }

    @PutMapping("/blog/{id}")
    public BlogPostDto updateBlogPost(@PathVariable UUID id, @Valid @RequestBody BlogPostRequest request) {
        return blogPostService.update(id, request);
    }

    @DeleteMapping("/blog/{id}")
    public ResponseEntity<Void> deleteBlogPost(@PathVariable UUID id) {
        blogPostService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Programmatic SEO silo: tech-stack solution pages ----
    @GetMapping("/solutions")
    public List<TechStackPageDto> listAllSolutions() {
        return techStackPageService.listAllForAdmin();
    }

    @GetMapping("/solutions/{id}")
    public TechStackPageDto getSolution(@PathVariable UUID id) {
        return techStackPageService.getById(id);
    }

    @PostMapping("/solutions")
    public ResponseEntity<TechStackPageDto> createSolution(@Valid @RequestBody TechStackPageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(techStackPageService.create(request));
    }

    @PutMapping("/solutions/{id}")
    public TechStackPageDto updateSolution(@PathVariable UUID id, @Valid @RequestBody TechStackPageRequest request) {
        return techStackPageService.update(id, request);
    }

    @DeleteMapping("/solutions/{id}")
    public ResponseEntity<Void> deleteSolution(@PathVariable UUID id) {
        techStackPageService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Service FAQs (FAQPage structured data source) ----
    @GetMapping("/services/{serviceId}/faqs")
    public List<FaqDto> listFaqs(@PathVariable UUID serviceId) {
        return serviceContentService.listFaqs(serviceId);
    }

    @PostMapping("/services/{serviceId}/faqs")
    public ResponseEntity<FaqDto> addFaq(@PathVariable UUID serviceId, @Valid @RequestBody FaqRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceContentService.addFaq(serviceId, request));
    }

    @PutMapping("/services/{serviceId}/faqs/{faqId}")
    public FaqDto updateFaq(@PathVariable UUID serviceId, @PathVariable UUID faqId,
                             @Valid @RequestBody FaqRequest request) {
        return serviceContentService.updateFaq(serviceId, faqId, request);
    }

    @DeleteMapping("/services/{serviceId}/faqs/{faqId}")
    public ResponseEntity<Void> deleteFaq(@PathVariable UUID serviceId, @PathVariable UUID faqId) {
        serviceContentService.deleteFaq(serviceId, faqId);
        return ResponseEntity.noContent().build();
    }

    // ---- Project reviews (Review / AggregateRating structured data source) ----
    @GetMapping("/projects/{projectId}/reviews")
    public List<ReviewDto> listReviews(@PathVariable UUID projectId) {
        return projectService.listReviewsForAdmin(projectId);
    }

    @PostMapping("/projects/{projectId}/reviews")
    public ResponseEntity<ReviewDto> addReview(@PathVariable UUID projectId,
                                                @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.addReview(projectId, request));
    }

    @PutMapping("/projects/{projectId}/reviews/{reviewId}")
    public ReviewDto updateReview(@PathVariable UUID projectId, @PathVariable UUID reviewId,
                                   @Valid @RequestBody ReviewRequest request) {
        return projectService.updateReview(projectId, reviewId, request);
    }

    @DeleteMapping("/projects/{projectId}/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID projectId, @PathVariable UUID reviewId) {
        projectService.deleteReview(projectId, reviewId);
        return ResponseEntity.noContent().build();
    }

    // ---- Client testimonials (module 4: post-invoice testimonial loop) ----
    // Not scoped under /projects/{projectId} like the CMS review endpoints above --
    // a freshly submitted testimonial may not have a project assigned yet.

    @GetMapping("/testimonials/pending")
    public List<ReviewDto> listPendingTestimonials() {
        return projectService.listPendingTestimonials();
    }

    @PutMapping("/testimonials/{reviewId}/moderate")
    public ReviewDto moderateTestimonial(@PathVariable UUID reviewId,
                                          @Valid @RequestBody ModerateTestimonialRequest request) {
        return projectService.moderateTestimonial(reviewId, request.projectId(), request.published());
    }
}
