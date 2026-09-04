import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { CheckoutOrder, Invoice, InvoicePayload } from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private http = inject(HttpClient);
  private readonly clientBase = `${environment.apiBaseUrl}/client`;
  private readonly adminBase = `${environment.apiBaseUrl}/admin/invoices`;

  listForEngagement(engagementId: string) {
    return this.http.get<Invoice[]>(`${this.clientBase}/engagements/${engagementId}/invoices`);
  }

  createOrder(invoiceId: string) {
    return this.http.post<CheckoutOrder>(`${this.clientBase}/invoices/${invoiceId}/checkout`, {});
  }

  verifyPayment(invoiceId: string, payload: { razorpayOrderId: string; razorpayPaymentId: string; razorpaySignature: string }) {
    return this.http.post<Invoice>(`${this.clientBase}/invoices/${invoiceId}/verify`, payload);
  }

  downloadPdf(invoiceId: string) {
    return this.http.get(`${this.clientBase}/invoices/${invoiceId}/pdf`, { responseType: 'blob' });
  }

  // ---- Admin ----
  createInvoice(payload: InvoicePayload) {
    return this.http.post<Invoice>(this.adminBase, payload);
  }
}
