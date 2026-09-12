import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { PaymentSchedule, PaymentSchedulePayload, PaymentScheduleInstallment } from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class PaymentScheduleService {
  private http = inject(HttpClient);
  private readonly clientBase = `${environment.apiBaseUrl}/client`;
  private readonly adminBase = `${environment.apiBaseUrl}/admin/payment-schedules`;

  getForEngagement(engagementId: string) {
    return this.http.get<PaymentSchedule | null>(`${this.clientBase}/engagements/${engagementId}/payment-schedule`);
  }

  // ---- Admin ----
  create(payload: PaymentSchedulePayload) {
    return this.http.post<PaymentSchedule>(this.adminBase, payload);
  }

  getForEngagementAsAdmin(engagementId: string) {
    return this.http.get<PaymentSchedule>(`${this.adminBase}/engagement/${engagementId}`);
  }

  raiseInvoice(installmentId: string) {
    return this.http.post<PaymentScheduleInstallment>(`${this.adminBase}/installments/${installmentId}/raise-invoice`, {});
  }
}
