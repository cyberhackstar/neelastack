package com.neelastack.controller;

import com.neelastack.dto.content.BlogPostDto;
import com.neelastack.dto.content.BlogPostSummaryDto;
import com.neelastack.dto.content.ProjectDto;
import com.neelastack.dto.content.ServiceDto;
import com.neelastack.dto.content.TechStackPageDto;
import com.neelastack.service.BlogPostService;
import com.neelastack.service.ProjectService;
import com.neelastack.service.ServiceContentService;
import com.neelastack.service.TechStackPageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public content", description = "Read-only endpoints for services, portfolio and blog")
public class PublicContentController {

    private final ServiceContentService serviceContentService;
    private final ProjectService projectService;
    private final BlogPostService blogPostService;
    private final TechStackPageService techStackPageService;

    @GetMapping("/services")
    public List<ServiceDto> services() {
        return serviceContentService.listPublished();
    }

    // ---- Programmatic SEO silo: tech-stack solution pages ----
    @GetMapping("/solutions")
    public List<TechStackPageDto> solutions() {
        return techStackPageService.listPublished();
    }

    @GetMapping("/solutions/{slug}")
    public TechStackPageDto solution(@PathVariable String slug) {
        return techStackPageService.getBySlug(slug);
    }


    @GetMapping("/projects")
    public List<ProjectDto> projects(@RequestParam(required = false, defaultValue = "false") boolean featuredOnly) {
        return featuredOnly ? projectService.listFeatured() : projectService.listPublished();
    }

    @GetMapping("/projects/{slug}")
    public ProjectDto project(@PathVariable String slug) {
        return projectService.getBySlug(slug);
    }

    @GetMapping("/blog")
    public Page<BlogPostSummaryDto> blog(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "9") int size,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) String tag) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        if ((q != null && !q.isBlank()) || (tag != null && !tag.isBlank())) {
            return blogPostService.search(q, tag, pageable);
        }
        return blogPostService.listPublished(pageable);
    }

    @GetMapping("/blog/{slug}/related")
    public List<BlogPostSummaryDto> relatedPosts(@PathVariable String slug) {
        return blogPostService.getRelated(slug);
    }

    @GetMapping("/blog/{slug}")
    public BlogPostDto blogPost(@PathVariable String slug) {
        return blogPostService.getBySlug(slug);
    }
}
