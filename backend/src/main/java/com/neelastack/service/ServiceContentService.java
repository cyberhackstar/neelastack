package com.neelastack.service;

import com.neelastack.dto.content.FaqDto;
import com.neelastack.dto.content.FaqRequest;
import com.neelastack.dto.content.ServiceDto;
import com.neelastack.dto.content.ServiceRequest;
import com.neelastack.entity.ServiceFaq;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.ServiceFaqRepository;
import com.neelastack.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ServiceContentService {

    private final ServiceRepository serviceRepository;
    private final ServiceFaqRepository serviceFaqRepository;
    private final IndexNowService indexNowService;

    @Cacheable("services")
    public List<ServiceDto> listPublished() {
        return serviceRepository.findByPublishedTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    /** Includes drafts — used by the admin CMS, never exposed publicly. */
    public List<ServiceDto> listAllForAdmin() {
        return serviceRepository.findAll(org.springframework.data.domain.Sort.by("displayOrder"))
                .stream().map(this::toDto).toList();
    }

    public ServiceDto getById(UUID id) {
        return serviceRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceDto create(ServiceRequest request) {
        if (serviceRepository.existsBySlug(request.slug())) {
            throw new BadRequestException("A service with slug '" + request.slug() + "' already exists");
        }
        com.neelastack.entity.Service entity = com.neelastack.entity.Service.builder()
                .title(request.title())
                .slug(request.slug())
                .summary(request.summary())
                .description(request.description())
                .icon(request.icon())
                .startingPrice(request.startingPrice())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .published(request.published())
                .build();
        ServiceDto saved = toDto(serviceRepository.save(entity));
        if (saved.published()) {
            indexNowService.notifyContentPublished("/services");
        }
        return saved;
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public ServiceDto update(UUID id, ServiceRequest request) {
        com.neelastack.entity.Service entity = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
        entity.setTitle(request.title());
        entity.setSlug(request.slug());
        entity.setSummary(request.summary());
        entity.setDescription(request.description());
        entity.setIcon(request.icon());
        entity.setStartingPrice(request.startingPrice());
        entity.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : entity.getDisplayOrder());
        entity.setPublished(request.published());
        ServiceDto saved = toDto(serviceRepository.save(entity));
        if (saved.published()) {
            indexNowService.notifyContentPublished("/services");
        }
        return saved;
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public void delete(UUID id) {
        if (!serviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Service not found: " + id);
        }
        serviceRepository.deleteById(id);
    }

    // ---- FAQs (FAQPage structured data source) ----

    public List<FaqDto> listFaqs(UUID serviceId) {
        return serviceFaqRepository.findByServiceIdOrderByDisplayOrderAsc(serviceId)
                .stream().map(this::toFaqDto).toList();
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public FaqDto addFaq(UUID serviceId, FaqRequest request) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service not found: " + serviceId);
        }
        ServiceFaq faq = ServiceFaq.builder()
                .serviceId(serviceId)
                .question(request.question())
                .answer(request.answer())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();
        return toFaqDto(serviceFaqRepository.save(faq));
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public FaqDto updateFaq(UUID serviceId, UUID faqId, FaqRequest request) {
        ServiceFaq faq = serviceFaqRepository.findById(faqId)
                .filter(f -> f.getServiceId().equals(serviceId))
                .orElseThrow(() -> new ResourceNotFoundException("FAQ not found: " + faqId));
        faq.setQuestion(request.question());
        faq.setAnswer(request.answer());
        faq.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : faq.getDisplayOrder());
        return toFaqDto(serviceFaqRepository.save(faq));
    }

    @CacheEvict(value = "services", allEntries = true)
    @Transactional
    public void deleteFaq(UUID serviceId, UUID faqId) {
        ServiceFaq faq = serviceFaqRepository.findById(faqId)
                .filter(f -> f.getServiceId().equals(serviceId))
                .orElseThrow(() -> new ResourceNotFoundException("FAQ not found: " + faqId));
        serviceFaqRepository.delete(faq);
    }

    private ServiceDto toDto(com.neelastack.entity.Service s) {
        return ServiceDto.builder()
                .id(s.getId())
                .title(s.getTitle())
                .slug(s.getSlug())
                .summary(s.getSummary())
                .description(s.getDescription())
                .icon(s.getIcon())
                .startingPrice(s.getStartingPrice())
                .displayOrder(s.getDisplayOrder())
                .published(s.isPublished())
                .faqs(listFaqs(s.getId()))
                .build();
    }

    private FaqDto toFaqDto(ServiceFaq f) {
        return FaqDto.builder()
                .id(f.getId())
                .question(f.getQuestion())
                .answer(f.getAnswer())
                .displayOrder(f.getDisplayOrder())
                .build();
    }
}
