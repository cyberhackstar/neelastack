import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import {
  Engagement,
  EngagementPayload,
  EngagementStatus,
  Milestone,
  MilestonePayload,
  MilestoneStatus,
  ProjectFile,
} from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class EngagementService {
  private http = inject(HttpClient);
  private readonly clientBase = `${environment.apiBaseUrl}/client/engagements`;
  private readonly adminBase = `${environment.apiBaseUrl}/admin/engagements`;

  // ---- Client-facing (own engagements, or all if admin) ----
  myEngagements() {
    return this.http.get<Engagement[]>(this.clientBase);
  }

  getEngagement(id: string) {
    return this.http.get<Engagement>(`${this.clientBase}/${id}`);
  }

  getMilestones(id: string) {
    return this.http.get<Milestone[]>(`${this.clientBase}/${id}/milestones`);
  }

  getFiles(id: string) {
    return this.http.get<ProjectFile[]>(`${this.clientBase}/${id}/files`);
  }

  uploadFile(id: string, file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ProjectFile>(`${this.clientBase}/${id}/files`, formData);
  }

  deleteFile(id: string, fileId: string) {
    return this.http.delete<void>(`${this.clientBase}/${id}/files/${fileId}`);
  }

  // ---- Admin management ----
  listAllForAdmin() {
    return this.http.get<Engagement[]>(this.adminBase);
  }

  createEngagement(payload: EngagementPayload) {
    return this.http.post<Engagement>(this.adminBase, payload);
  }

  updateEngagementStatus(id: string, status: EngagementStatus) {
    return this.http.patch<Engagement>(`${this.adminBase}/${id}/status`, { status });
  }

  addMilestone(id: string, payload: MilestonePayload) {
    return this.http.post<Milestone>(`${this.adminBase}/${id}/milestones`, payload);
  }

  updateMilestoneStatus(milestoneId: string, status: MilestoneStatus) {
    return this.http.patch<Milestone>(`${this.adminBase}/milestones/${milestoneId}/status`, { status });
  }
}
