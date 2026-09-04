import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { PricingRule, PricingRulePayload } from '../models/pricing.model';

/**
 * Admin-only client for the pricing_rules CMS (see AdminPricingController /
 * PricingRuleService on the backend). This is the "admin portal" side of the P0
 * dynamic pricing fix — every number the public estimator quotes is edited here.
 */
@Injectable({ providedIn: 'root' })
export class PricingService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/pricing-rules`;

  listAll() {
    return this.http.get<PricingRule[]>(this.base);
  }

  getById(id: string) {
    return this.http.get<PricingRule>(`${this.base}/${id}`);
  }

  create(payload: PricingRulePayload) {
    return this.http.post<PricingRule>(this.base, payload);
  }

  update(id: string, payload: PricingRulePayload) {
    return this.http.put<PricingRule>(`${this.base}/${id}`, payload);
  }

  delete(id: string) {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
