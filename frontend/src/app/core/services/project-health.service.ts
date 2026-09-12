import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ActionItem, ProjectHealth, ProjectOperationsSummary } from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class ProjectHealthService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}`;

  getHealth(engagementId: string) {
    return this.http.get<ProjectHealth>(`${this.base}/engagements/${engagementId}/health`);
  }

  getActionItems(engagementId: string) {
    return this.http.get<ActionItem[]>(`${this.base}/engagements/${engagementId}/action-items`);
  }

  getOperationsSummary() {
    return this.http.get<ProjectOperationsSummary>(`${this.base}/admin/project-operations/summary`);
  }
}
