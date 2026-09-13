import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { PaymentHistoryItem } from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class PaymentHistoryService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/payments/history`;

  list() {
    return this.http.get<PaymentHistoryItem[]>(this.base);
  }

  exportExcel() {
    return this.http.get(`${this.base}/export.xlsx`, { responseType: 'blob' });
  }
}
