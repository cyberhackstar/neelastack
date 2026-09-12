import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { UpiPaymentMethod, UpiPaymentMethodPayload, UpiSubmission, UpiSubmissionPayload } from '../models/content.model';

/** Direct UPI QR payments — settles straight into the business bank account with no
 *  Razorpay/gateway commission. Sits alongside InvoiceService's Razorpay checkout as an
 *  alternative payment path, not a replacement. */
@Injectable({ providedIn: 'root' })
export class UpiPaymentService {
  private http = inject(HttpClient);
  private readonly clientBase = `${environment.apiBaseUrl}/client`;
  private readonly adminBase = `${environment.apiBaseUrl}/admin/upi`;

  // ---- Client ----
  listActiveMethods() {
    return this.http.get<UpiPaymentMethod[]>(`${this.clientBase}/upi-methods`);
  }

  listSubmissionsForInvoice(invoiceId: string) {
    return this.http.get<UpiSubmission[]>(`${this.clientBase}/invoices/${invoiceId}/upi-submissions`);
  }

  submitPayment(invoiceId: string, payload: UpiSubmissionPayload, screenshot?: File | null) {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    if (screenshot) form.append('screenshot', screenshot);
    return this.http.post<UpiSubmission>(`${this.clientBase}/invoices/${invoiceId}/upi-submissions`, form);
  }

  // ---- Admin ----
  listAllMethods() {
    return this.http.get<UpiPaymentMethod[]>(`${this.adminBase}/methods`);
  }

  createMethod(payload: UpiPaymentMethodPayload, qrImage: File) {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    form.append('qrImage', qrImage);
    return this.http.post<UpiPaymentMethod>(`${this.adminBase}/methods`, form);
  }

  activateMethod(id: string) {
    return this.http.post<UpiPaymentMethod>(`${this.adminBase}/methods/${id}/activate`, {});
  }

  deactivateMethod(id: string) {
    return this.http.post<UpiPaymentMethod>(`${this.adminBase}/methods/${id}/deactivate`, {});
  }

  deleteMethod(id: string) {
    return this.http.delete<void>(`${this.adminBase}/methods/${id}`);
  }

  listPendingSubmissions() {
    return this.http.get<UpiSubmission[]>(`${this.adminBase}/submissions/pending`);
  }

  verifySubmission(id: string, approve: boolean, adminNote?: string) {
    return this.http.post<UpiSubmission>(`${this.adminBase}/submissions/${id}/verify`, { approve, adminNote });
  }
}
