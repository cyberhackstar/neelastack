package com.neelastack.service;

import com.neelastack.dto.content.TechStackPageDto;
import com.neelastack.dto.content.TechStackPageRequest;
import com.neelastack.entity.TechStackPage;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.TechStackPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Programmatic SEO silo pages — see the V17 migration for why this is a separate,
 * admin-authored table rather than an auto-generated combinatorial page factory. Every
 * page here is real, specific content an admin wrote for one tech-stack + engagement
 * combination; the "programmatic" part is the shared template/routing/schema
 * machinery, not the copy itself — auto-generating hundreds of thin near-duplicate
 * pages is a doorway-page pattern search engines actively penalize, the opposite of
 * the ranking goal this feature exists for.
 */
@Service
@RequiredArgsConstructor
public class TechStackPageService {

    private final TechStackPageRepository repository;
    private final IndexNowService indexNowService;

    @Cacheable("techStackPages")
    public List<TechStackPageDto> listPublished() {
        return repository.findByPublishedTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    /** Includes drafts — used by the admin CMS, never exposed publicly. */
    public List<TechStackPageDto> listAllForAdmin() {
        return repository.findAll(Sort.by("displayOrder"))
                .stream().map(this::toDto).toList();
    }

    public TechStackPageDto getById(UUID id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Solution page not found: " + id));
    }

    @Cacheable("techStackPageBySlug")
    public TechStackPageDto getBySlug(String slug) {
        return repository.findBySlugAndPublishedTrue(slug)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Solution page not found: " + slug));
    }

    @CacheEvict(value = {"techStackPages", "techStackPageBySlug"}, allEntries = true)
    @Transactional
    public TechStackPageDto create(TechStackPageRequest request) {
        if (repository.existsBySlug(request.slug())) {
            throw new BadRequestException("A solution page with slug '" + request.slug() + "' already exists");
        }
        TechStackPage entity = TechStackPage.builder()
                .slug(request.slug())
                .h1Title(request.h1Title())
                .metaTitle(request.metaTitle())
                .metaDescription(request.metaDescription())
                .intro(request.intro())
                .bodyContent(request.bodyContent())
                .primaryStack(request.primaryStack())
                .secondaryStack(request.secondaryStack())
                .targetIndustry(request.targetIndustry())
                .useCases(joinUseCases(request.useCases()))
                .startingPrice(request.startingPrice())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .published(request.published())
                .build();
        TechStackPageDto saved = toDto(repository.save(entity));
        if (saved.published()) {
            indexNowService.notifyContentPublished("/solutions/" + saved.slug());
            indexNowService.notifyContentPublished("/solutions");
        }
        return saved;
    }

    @CacheEvict(value = {"techStackPages", "techStackPageBySlug"}, allEntries = true)
    @Transactional
    public TechStackPageDto update(UUID id, TechStackPageRequest request) {
        TechStackPage entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solution page not found: " + id));

        entity.setSlug(request.slug());
        entity.setH1Title(request.h1Title());
        entity.setMetaTitle(request.metaTitle());
        entity.setMetaDescription(request.metaDescription());
        entity.setIntro(request.intro());
        entity.setBodyContent(request.bodyContent());
        entity.setPrimaryStack(request.primaryStack());
        entity.setSecondaryStack(request.secondaryStack());
        entity.setTargetIndustry(request.targetIndustry());
        entity.setUseCases(joinUseCases(request.useCases()));
        entity.setStartingPrice(request.startingPrice());
        entity.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : entity.getDisplayOrder());
        entity.setPublished(request.published());

        TechStackPageDto saved = toDto(repository.save(entity));
        if (saved.published()) {
            indexNowService.notifyContentPublished("/solutions/" + saved.slug());
        }
        return saved;
    }

    @CacheEvict(value = {"techStackPages", "techStackPageBySlug"}, allEntries = true)
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Solution page not found: " + id);
        }
        repository.deleteById(id);
    }

    private String joinUseCases(List<String> useCases) {
        return useCases == null || useCases.isEmpty() ? null : String.join("|", useCases);
    }

    private List<String> splitUseCases(String stored) {
        return stored == null || stored.isBlank() ? List.of() : Arrays.asList(stored.split("\\|"));
    }

    private TechStackPageDto toDto(TechStackPage p) {
        return TechStackPageDto.builder()
                .id(p.getId())
                .slug(p.getSlug())
                .h1Title(p.getH1Title())
                .metaTitle(p.getMetaTitle())
                .metaDescription(p.getMetaDescription())
                .intro(p.getIntro())
                .bodyContent(p.getBodyContent())
                .primaryStack(p.getPrimaryStack())
                .secondaryStack(p.getSecondaryStack())
                .targetIndustry(p.getTargetIndustry())
                .useCases(splitUseCases(p.getUseCases()))
                .startingPrice(p.getStartingPrice())
                .displayOrder(p.getDisplayOrder())
                .published(p.isPublished())
                .build();
    }
}
