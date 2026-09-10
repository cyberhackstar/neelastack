package com.neelastack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.dto.booking.FormFieldDto;
import com.neelastack.dto.booking.FormFieldRequest;
import com.neelastack.dto.booking.MeetingTypeDto;
import com.neelastack.dto.booking.MeetingTypeRequest;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.BookingFormField;
import com.neelastack.entity.MeetingType;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.BookingFormFieldRepository;
import com.neelastack.repository.MeetingTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin CRUD for meeting types and their dynamic form fields, plus the public
 * "which meeting types can I book" listing. See master prompt section 1/7.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingTypeService {

    private final MeetingTypeRepository meetingTypeRepository;
    private final BookingFormFieldRepository formFieldRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<MeetingTypeDto> listPublic() {
        return meetingTypeRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<MeetingTypeDto> listAllForAdmin() {
        return meetingTypeRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MeetingTypeDto getPublicBySlug(String slug) {
        MeetingType meetingType = getBySlugOrThrow(slug);
        if (!Boolean.TRUE.equals(meetingType.getIsActive())) {
            throw new ResourceNotFoundException("Meeting type not found: " + slug);
        }
        return toDto(meetingType);
    }

    @Transactional(readOnly = true)
    public MeetingType getBySlugOrThrow(String slug) {
        return meetingTypeRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting type not found: " + slug));
    }

    @Transactional(readOnly = true)
    public MeetingType getByIdOrThrow(UUID id) {
        return meetingTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting type not found: " + id));
    }

    @Transactional
    public MeetingTypeDto create(MeetingTypeRequest request) {
        if (meetingTypeRepository.existsBySlug(request.slug())) {
            throw new BadRequestException("A meeting type with slug '" + request.slug() + "' already exists");
        }
        MeetingType meetingType = MeetingType.builder()
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .durationMinutes(request.durationMinutes())
                .bufferBeforeMinutes(nvl(request.bufferBeforeMinutes(), 0))
                .bufferAfterMinutes(nvl(request.bufferAfterMinutes(), 0))
                .minNoticeMinutes(nvl(request.minNoticeMinutes(), 120))
                .maxHorizonDays(nvl(request.maxHorizonDays(), 30))
                .locationType(request.locationType())
                .locationDetail(request.locationDetail())
                .price(request.price())
                .currency(request.currency() == null || request.currency().isBlank() ? "INR" : request.currency())
                .requiresPayment(Boolean.TRUE.equals(request.requiresPayment()))
                .cancellableUntilHours(nvl(request.cancellableUntilHours(), 12))
                .isActive(request.isActive() == null || request.isActive())
                .sortOrder(nvl(request.sortOrder(), 0))
                .build();
        meetingType = meetingTypeRepository.save(meetingType);

        saveFormFields(meetingType.getId(), request.formFields());

        auditLogService.recordBestEffort(AuditAction.MEETING_TYPE_CREATED, "MeetingType",
                meetingType.getId().toString(), Map.of("name", meetingType.getName(), "slug", meetingType.getSlug()));

        return toDto(meetingType);
    }

    @Transactional
    public MeetingTypeDto update(UUID id, MeetingTypeRequest request) {
        MeetingType meetingType = getByIdOrThrow(id);
        if (meetingTypeRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw new BadRequestException("A meeting type with slug '" + request.slug() + "' already exists");
        }

        meetingType.setName(request.name());
        meetingType.setSlug(request.slug());
        meetingType.setDescription(request.description());
        meetingType.setDurationMinutes(request.durationMinutes());
        meetingType.setBufferBeforeMinutes(nvl(request.bufferBeforeMinutes(), 0));
        meetingType.setBufferAfterMinutes(nvl(request.bufferAfterMinutes(), 0));
        meetingType.setMinNoticeMinutes(nvl(request.minNoticeMinutes(), 120));
        meetingType.setMaxHorizonDays(nvl(request.maxHorizonDays(), 30));
        meetingType.setLocationType(request.locationType());
        meetingType.setLocationDetail(request.locationDetail());
        meetingType.setPrice(request.price());
        meetingType.setCurrency(request.currency() == null || request.currency().isBlank() ? "INR" : request.currency());
        meetingType.setRequiresPayment(Boolean.TRUE.equals(request.requiresPayment()));
        meetingType.setCancellableUntilHours(nvl(request.cancellableUntilHours(), 12));
        if (request.isActive() != null) {
            meetingType.setIsActive(request.isActive());
        }
        meetingType.setSortOrder(nvl(request.sortOrder(), 0));
        meetingType = meetingTypeRepository.save(meetingType);

        saveFormFields(meetingType.getId(), request.formFields());

        auditLogService.recordBestEffort(AuditAction.MEETING_TYPE_UPDATED, "MeetingType",
                meetingType.getId().toString(), Map.of("name", meetingType.getName()));

        return toDto(meetingType);
    }

    @Transactional
    public void delete(UUID id) {
        MeetingType meetingType = getByIdOrThrow(id);
        meetingTypeRepository.delete(meetingType);
        auditLogService.recordBestEffort(AuditAction.MEETING_TYPE_DELETED, "MeetingType", id.toString(), null);
    }

    private void saveFormFields(UUID meetingTypeId, List<FormFieldRequest> fields) {
        formFieldRepository.deleteByMeetingTypeId(meetingTypeId);
        if (fields == null || fields.isEmpty()) {
            return;
        }
        int i = 0;
        for (FormFieldRequest f : fields) {
            String optionsJson = null;
            if (f.options() != null && !f.options().isEmpty()) {
                try {
                    optionsJson = objectMapper.writeValueAsString(f.options());
                } catch (Exception e) {
                    log.warn("Failed to serialize form field options for {}: {}", f.fieldKey(), e.getMessage());
                }
            }
            formFieldRepository.save(BookingFormField.builder()
                    .meetingTypeId(meetingTypeId)
                    .fieldKey(f.fieldKey())
                    .label(f.label())
                    .fieldType(f.fieldType())
                    .isRequired(Boolean.TRUE.equals(f.isRequired()))
                    .options(optionsJson)
                    .sortOrder(f.sortOrder() == null ? i : f.sortOrder())
                    .build());
            i++;
        }
    }

    private MeetingTypeDto toDto(MeetingType m) {
        List<FormFieldDto> fields = formFieldRepository.findByMeetingTypeIdOrderBySortOrderAsc(m.getId()).stream()
                .map(f -> {
                    List<String> options = null;
                    if (f.getOptions() != null) {
                        try {
                            options = objectMapper.readValue(f.getOptions(), List.class);
                        } catch (Exception e) {
                            options = List.of();
                        }
                    }
                    return FormFieldDto.builder()
                            .fieldKey(f.getFieldKey()).label(f.getLabel()).fieldType(f.getFieldType())
                            .isRequired(f.getIsRequired()).options(options).sortOrder(f.getSortOrder())
                            .build();
                }).toList();

        return MeetingTypeDto.builder()
                .id(m.getId()).name(m.getName()).slug(m.getSlug()).description(m.getDescription())
                .durationMinutes(m.getDurationMinutes()).bufferBeforeMinutes(m.getBufferBeforeMinutes())
                .bufferAfterMinutes(m.getBufferAfterMinutes()).minNoticeMinutes(m.getMinNoticeMinutes())
                .maxHorizonDays(m.getMaxHorizonDays()).locationType(m.getLocationType())
                .locationDetail(m.getLocationDetail()).price(m.getPrice()).currency(m.getCurrency())
                .requiresPayment(m.getRequiresPayment()).cancellableUntilHours(m.getCancellableUntilHours())
                .isActive(m.getIsActive()).sortOrder(m.getSortOrder()).formFields(fields)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private Integer nvl(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
