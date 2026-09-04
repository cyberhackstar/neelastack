package com.neelastack.service;

import com.neelastack.entity.Inquiry;
import com.neelastack.entity.Quotation;
import com.neelastack.entity.QuotationStatus;
import com.neelastack.repository.BlogPostRepository;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.FollowUpDismissalRepository;
import com.neelastack.repository.InquiryRepository;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.ProjectRepository;
import com.neelastack.repository.QuotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private InquiryRepository inquiryRepository;
    private QuotationRepository quotationRepository;
    private InvoiceRepository invoiceRepository;
    private EngagementRepository engagementRepository;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        inquiryRepository = mock(InquiryRepository.class);
        engagementRepository = mock(EngagementRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        BlogPostRepository blogPostRepository = mock(BlogPostRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        quotationRepository = mock(QuotationRepository.class);

        when(inquiryRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        when(engagementRepository.countByStatus()).thenReturn(List.of());
        when(invoiceRepository.sumPaidAmount()).thenReturn(BigDecimal.ZERO);
        when(invoiceRepository.sumPendingAmount()).thenReturn(BigDecimal.ZERO);

        service = new AnalyticsService(
                inquiryRepository, engagementRepository, invoiceRepository,
                blogPostRepository, projectRepository, quotationRepository,
                mock(FollowUpDismissalRepository.class));
    }

    private Quotation quotationWithAmount(BigDecimal amount, QuotationStatus status) {
        return Quotation.builder()
                .id(UUID.randomUUID())
                .inquiry(Inquiry.builder().id(UUID.randomUUID()).build())
                .title("Test quotation")
                .totalAmount(amount)
                .status(status)
                .lineItems(List.of())
                .build();
    }

    @Test
    void summary_sumsSentQuotationsAsOpenPipeline() {
        when(quotationRepository.findByStatus(QuotationStatus.SENT)).thenReturn(List.of(
                quotationWithAmount(BigDecimal.valueOf(100_000), QuotationStatus.SENT),
                quotationWithAmount(BigDecimal.valueOf(250_000), QuotationStatus.SENT)));
        when(quotationRepository.findByStatus(QuotationStatus.ACCEPTED)).thenReturn(List.of());

        var summary = service.summary();

        assertThat(summary.openPipelineValue()).isEqualByComparingTo(BigDecimal.valueOf(350_000));
        assertThat(summary.wonPipelineValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void summary_sumsAcceptedQuotationsAsWonPipeline() {
        when(quotationRepository.findByStatus(QuotationStatus.SENT)).thenReturn(List.of());
        when(quotationRepository.findByStatus(QuotationStatus.ACCEPTED)).thenReturn(List.of(
                quotationWithAmount(BigDecimal.valueOf(500_000), QuotationStatus.ACCEPTED)));

        var summary = service.summary();

        assertThat(summary.wonPipelineValue()).isEqualByComparingTo(BigDecimal.valueOf(500_000));
    }

    @Test
    void summary_noQuotations_pipelineIsZeroNotNull() {
        when(quotationRepository.findByStatus(any())).thenReturn(List.of());

        var summary = service.summary();

        assertThat(summary.openPipelineValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.wonPipelineValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
