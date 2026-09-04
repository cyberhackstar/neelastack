import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { PublicQuotation } from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class PublicQuotationService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/public/quotations`;

  get(token: string) {
    return this.http.get<PublicQuotation>(`${this.base}/${token}`);
  }

  respond(token: string, accept: boolean, reason?: string) {
    return this.http.post<PublicQuotation>(`${this.base}/${token}/respond`, { accept, reason });
  }
}
