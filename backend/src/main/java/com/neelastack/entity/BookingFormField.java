package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * One admin-configured dynamic intake question attached to a MeetingType's public
 * booking form. See master prompt section 7.
 */
@Entity
@Table(name = "booking_form_fields")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingFormField {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "meeting_type_id", nullable = false)
    private UUID meetingTypeId;

    @Column(name = "field_key", nullable = false, length = 60)
    private String fieldKey;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    @Builder.Default
    private FormFieldType fieldType = FormFieldType.TEXT;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = false;

    /** JSON array of option labels, only meaningful for SELECT/MULTI_SELECT/RADIO. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String options;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
