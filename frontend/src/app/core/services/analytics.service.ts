import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import {
  AnalyticsSummary,
  AttributionBreakdown,
  AttributionDimension,
  FollowUpTask,
  SalesIntelligence,
} from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);

  getSummary() {
    return this.http.get<AnalyticsSummary>(`${environment.apiBaseUrl}/admin/analytics/summary`);
  }

  getSalesIntelligence() {
    return this.http.get<SalesIntelligence>(`${environment.apiBaseUrl}/admin/analytics/sales-intelligence`);
  }

  getRevenueByAttribution(dimension: AttributionDimension) {
    const params = new HttpParams().set('dimension', dimension);
    return this.http.get<AttributionBreakdown[]>(
      `${environment.apiBaseUrl}/admin/analytics/revenue-by-attribution`,
      { params },
    );
  }

  getFollowUps() {
    return this.http.get<FollowUpTask[]>(`${environment.apiBaseUrl}/admin/analytics/follow-ups`);
  }

  dismissFollowUp(quotationId: string, reason?: string) {
    return this.http.post<void>(
      `${environment.apiBaseUrl}/admin/analytics/follow-ups/${quotationId}/dismiss`,
      reason ? { reason } : {},
    );
  }

  snoozeFollowUp(quotationId: string, until: string, reason?: string) {
    return this.http.post<void>(
      `${environment.apiBaseUrl}/admin/analytics/follow-ups/${quotationId}/snooze`,
      { until, reason },
    );
  }
}
