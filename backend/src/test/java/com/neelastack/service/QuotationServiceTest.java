package com.neelastack.service;

import com.neelastack.entity.Inquiry;
import com.neelastack.entity.Quotation;
import com.neelastack.entity.QuotationStatus;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.InquiryRepository;
import com.neelastack.repository.PricingRuleRepository;
import com.neelastack.repository.ProjectRepository;
import com.neelastack.repository.QuotationRepository;
import com.neelastack.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuotationServiceTest {

    private QuotationRepository quotationRepository;
    private InquiryRepository inquiryRepository;
    private PricingRuleRepository pricingRuleRepository;
    private ProjectRepository projectRepository;
    private ReviewRepository reviewRepository;
    private EmailService emailService;
    private AuditLogService auditLogService;
    private QuotationService quotationService;

    @BeforeEach
    void setUp() {
        quotationRepository = mock(QuotationRepository.class);
        inquiryRepository = mock(InquiryRepository.class);
        pricingRuleRepository = mock(PricingRuleRepository.class);
        projectRepository = mock(ProjectRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        emailService = mock(EmailService.class);
        auditLogService = mock(AuditLogService.class);
        quotationService = new QuotationService(quotationRepository, inquiryRepository, pricingRuleRepository, projectRepository, reviewRepository, emailService, auditLogService);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Quotation quotation(QuotationStatus status, LocalDate validUntil) {
        Inquiry inquiry = Inquiry.builder().id(UUID.randomUUID()).name("Acme Pvt Ltd").build();
        return Quotation.builder()
                .id(UUID.randomUUID())
                .inquiry(inquiry)
                .title("Website revamp")
                .totalAmount(BigDecimal.valueOf(250000))
                .status(status)
                .validUntil(validUntil)
                .publicToken(UUID.randomUUID().toString())
                .lineItems(List.of())
                .build();
    }

    // --- draft quotations must not leak via the public token ---

    @Test
    void getByPublicToken_draftIsTreatedAsNotFound() {
        Quotation draft = quotation(QuotationStatus.DRAFT, null);
        when(quotationRepository.findByPublicToken(draft.getPublicToken())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> quotationService.getByPublicToken(draft.getPublicToken()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByPublicToken_unknownToken_notFound() {
        when(quotationRepository.findByPublicToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quotationService.getByPublicToken("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- expiration enforcement (review item #25) ---

    @Test
    void getByPublicToken_sentPastValidUntil_isMarkedExpired() {
        Quotation sent = quotation(QuotationStatus.SENT, LocalDate.now().minusDays(1));
        when(quotationRepository.findByPublicToken(sent.getPublicToken())).thenReturn(Optional.of(sent));

        var dto = quotationService.getByPublicToken(sent.getPublicToken());

        assertThat(dto.status()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(sent.getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        // Saved twice: once for the expiry flip, once for view-tracking (both real writes).
        verify(quotationRepository, times(2)).save(sent);
    }

    @Test
    void getByPublicToken_sentAndStillValid_recordsAViewButDoesNotChangeStatus() {
        Quotation sent = quotation(QuotationStatus.SENT, LocalDate.now().plusDays(5));
        when(quotationRepository.findByPublicToken(sent.getPublicToken())).thenReturn(Optional.of(sent));

        var dto = quotationService.getByPublicToken(sent.getPublicToken());

        assertThat(dto.status()).isEqualTo(QuotationStatus.SENT);
        assertThat(sent.getViewCount()).isEqualTo(1);
        assertThat(sent.getLastViewedAt()).isNotNull();
        verify(quotationRepository, times(1)).save(sent);
    }

    @Test
    void getByPublicToken_calledTwice_incrementsViewCountEachTime() {
        Quotation sent = quotation(QuotationStatus.SENT, LocalDate.now().plusDays(5));
        when(quotationRepository.findByPublicToken(sent.getPublicToken())).thenReturn(Optional.of(sent));

        quotationService.getByPublicToken(sent.getPublicToken());
        quotationService.getByPublicToken(sent.getPublicToken());

        assertThat(sent.getViewCount()).isEqualTo(2);
    }

    @Test
    void respondToQuotation_expired_cannotBeAccepted() {
        Quotation sent = quotation(QuotationStatus.SENT, LocalDate.now().minusDays(1));
        when(quotationRepository.findByPublicToken(sent.getPublicToken())).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> quotationService.respondToQuotation(sent.getPublicToken(), true, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");

        assertThat(sent.getStatus()).isEqualTo(QuotationStatus.EXPIRED);
    }

    // --- duplicate response / already-responded ---

    @Test
    void respondToQuotation_alreadyAccepted_rejectsSecondResponse() {
        Quotation accepted = quotation(QuotationStatus.ACCEPTED, LocalDate.now().plusDays(5));
        when(quotationRepository.findByPublicToken(accepted.getPublicToken())).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> quotationService.respondToQuotation(accepted.getPublicToken(), false, "changed my mind"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been responded to");
    }

    @Test
    void respondToQuotation_draft_rejected() {
        Quotation draft = quotation(QuotationStatus.DRAFT, LocalDate.now().plusDays(5));
        when(quotationRepository.findByPublicToken(draft.getPublicToken())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> quotationService.respondToQuotation(draft.getPublicToken(), true, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void respondToQuotation_validAcceptance_succeeds() {
        Quotation sent = quotation(QuotationStatus.SENT, LocalDate.now().plusDays(5));
        when(quotationRepository.findByPublicToken(sent.getPublicToken())).thenReturn(Optional.of(sent));

        var dto = quotationService.respondToQuotation(sent.getPublicToken(), true, null);

        assertThat(dto.status()).isEqualTo(QuotationStatus.ACCEPTED);
        assertThat(sent.getRespondedAt()).isNotNull();
        verify(emailService).sendQuotationResponseNotice(sent, true, null);
    }

    // --- nightly sweep ---

    @Test
    void expireOverdueQuotations_flipsAllOverdueSentQuotations() {
        Quotation q1 = quotation(QuotationStatus.SENT, LocalDate.now().minusDays(3));
        Quotation q2 = quotation(QuotationStatus.SENT, LocalDate.now().minusDays(1));
        when(quotationRepository.findByStatusAndValidUntilBefore(eq(QuotationStatus.SENT), any(LocalDate.class)))
                .thenReturn(List.of(q1, q2));

        quotationService.expireOverdueQuotations();

        assertThat(q1.getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(q2.getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        verify(quotationRepository).saveAll(List.of(q1, q2));
    }

    @Test
    void expireOverdueQuotations_nothingOverdue_doesNotCallSaveAll() {
        when(quotationRepository.findByStatusAndValidUntilBefore(eq(QuotationStatus.SENT), any(LocalDate.class)))
                .thenReturn(List.of());

        quotationService.expireOverdueQuotations();

        verify(quotationRepository, never()).saveAll(any());
    }
}
