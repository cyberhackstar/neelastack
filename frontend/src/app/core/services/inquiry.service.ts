import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import {
  ArchitectureReviewPayload,
  AuditPreviewPayload,
  AuditPreviewResult,
  AuditUnlockPayload,
  AuditUnlockResult,
  EstimatorPayload,
  EstimatorResponse,
  Inquiry,
  InquiryPayload,
  InquiryStatus,
  Page,
  Quotation,
  QuotationPayload,
} from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class InquiryService {
  private http = inject(HttpClient);
  private readonly publicBase = `${environment.apiBaseUrl}/public`;
  private readonly adminBase = `${environment.apiBaseUrl}/admin`;

  submitInquiry(payload: InquiryPayload) {
    return this.http.post<Inquiry>(`${this.publicBase}/inquiries`, payload);
  }

  submitEstimate(payload: EstimatorPayload) {
    return this.http.post<EstimatorResponse>(`${this.publicBase}/estimator`, payload);
  }

  submitArchitectureReview(payload: ArchitectureReviewPayload) {
    return this.http.post<Inquiry>(`${this.publicBase}/architecture-review`, payload);
  }

  /** Module 1, step 1 — free, anonymous, nothing persisted server-side. */
  scoreAuditPreview(payload: AuditPreviewPayload) {
    return this.http.post<AuditPreviewResult>(`${this.publicBase}/audit-preview/score`, payload);
  }

  /** Module 1, step 2 — creates a real Inquiry and returns the full unlocked report. */
  unlockAuditPreview(payload: AuditUnlockPayload) {
    return this.http.post<AuditUnlockResult>(`${this.publicBase}/audit-preview/unlock`, payload);
  }

  listInquiries(page = 0, size = 20) {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Inquiry>>(`${this.adminBase}/inquiries`, { params });
  }

  getInquiry(id: string) {
    return this.http.get<Inquiry>(`${this.adminBase}/inquiries/${id}`);
  }

  updateStatus(id: string, status: InquiryStatus) {
    return this.http.patch<Inquiry>(`${this.adminBase}/inquiries/${id}/status`, { status });
  }

  getQuotations(inquiryId: string) {
    return this.http.get<Quotation[]>(`${this.adminBase}/inquiries/${inquiryId}/quotations`);
  }

  createQuotation(payload: QuotationPayload) {
    return this.http.post<Quotation>(`${this.adminBase}/quotations`, payload);
  }

  sendQuotation(id: string) {
    return this.http.post<Quotation>(`${this.adminBase}/quotations/${id}/send`, {});
  }
}
