import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import {
  ChangeRequest,
  ChangeRequestCreatePayload,
  ChangeRequestQuotePayload,
  Engagement,
  EngagementPayload,
  EngagementStatus,
  Milestone,
  MilestoneApproval,
  MilestonePayload,
  MilestoneStatus,
  ProjectActivity,
  ProjectFile,
  ProjectMessage,
  ProjectMessagePayload,
  ProjectTask,
  ProjectTaskPayload,
  TaskStatus,
  UnreadCount,
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

  // ---- Client-facing messaging ----
  getMessages(id: string) {
    return this.http.get<ProjectMessage[]>(`${this.clientBase}/${id}/messages`);
  }

  sendMessage(id: string, payload: ProjectMessagePayload) {
    return this.http.post<ProjectMessage>(`${this.clientBase}/${id}/messages`, payload);
  }

  markMessagesRead(id: string) {
    return this.http.post<void>(`${this.clientBase}/${id}/messages/read`, {});
  }

  getUnreadMessageCount(id: string) {
    return this.http.get<UnreadCount>(`${this.clientBase}/${id}/messages/unread-count`);
  }

  // ---- Client-facing activity + tasks (read-only) ----
  getActivity(id: string) {
    return this.http.get<ProjectActivity[]>(`${this.clientBase}/${id}/activity`);
  }

  getTasks(id: string) {
    return this.http.get<ProjectTask[]>(`${this.clientBase}/${id}/tasks`);
  }

  getMilestoneApprovals(id: string) {
    return this.http.get<MilestoneApproval[]>(`${this.clientBase}/${id}/milestone-approvals`);
  }

  approveMilestone(milestoneId: string) {
    return this.http.post<Milestone>(`${this.clientBase}/milestones/${milestoneId}/approve`, {});
  }

  requestMilestoneChanges(milestoneId: string, comment: string) {
    return this.http.post<Milestone>(`${this.clientBase}/milestones/${milestoneId}/request-changes`, { comment });
  }

  getChangeRequests(id: string) {
    return this.http.get<ChangeRequest[]>(`${this.clientBase}/${id}/change-requests`);
  }

  submitChangeRequest(id: string, payload: ChangeRequestCreatePayload) {
    return this.http.post<ChangeRequest>(`${this.clientBase}/${id}/change-requests`, payload);
  }

  acceptChangeRequest(changeRequestId: string) {
    return this.http.post<ChangeRequest>(`${this.clientBase}/change-requests/${changeRequestId}/accept`, {});
  }

  declineChangeRequest(changeRequestId: string) {
    return this.http.post<ChangeRequest>(`${this.clientBase}/change-requests/${changeRequestId}/decline`, {});
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

  // ---- Admin-facing activity + tasks ----
  getActivityAsAdmin(id: string) {
    return this.http.get<ProjectActivity[]>(`${this.adminBase}/${id}/activity`);
  }

  getTasksAsAdmin(id: string) {
    return this.http.get<ProjectTask[]>(`${this.adminBase}/${id}/tasks`);
  }

  addTask(milestoneId: string, payload: ProjectTaskPayload) {
    return this.http.post<ProjectTask>(`${this.adminBase}/milestones/${milestoneId}/tasks`, payload);
  }

  updateTaskStatus(taskId: string, status: TaskStatus) {
    return this.http.patch<ProjectTask>(`${this.adminBase}/tasks/${taskId}/status`, { status });
  }

  assignTask(taskId: string, assigneeEmail: string | null) {
    return this.http.patch<ProjectTask>(`${this.adminBase}/tasks/${taskId}/assign`, { assigneeEmail });
  }

  getMilestoneApprovalsAsAdmin(id: string) {
    return this.http.get<MilestoneApproval[]>(`${this.adminBase}/${id}/milestone-approvals`);
  }

  getChangeRequestsAsAdmin(id: string) {
    return this.http.get<ChangeRequest[]>(`${this.adminBase}/${id}/change-requests`);
  }

  quoteChangeRequest(changeRequestId: string, payload: ChangeRequestQuotePayload) {
    return this.http.patch<ChangeRequest>(`${this.adminBase}/change-requests/${changeRequestId}/quote`, payload);
  }

  completeChangeRequest(changeRequestId: string) {
    return this.http.post<ChangeRequest>(`${this.adminBase}/change-requests/${changeRequestId}/complete`, {});
  }

  // ---- Admin-facing messaging (same thread, staff side) ----
  getMessagesAsAdmin(id: string) {
    return this.http.get<ProjectMessage[]>(`${this.adminBase}/${id}/messages`);
  }

  sendMessageAsAdmin(id: string, payload: ProjectMessagePayload) {
    return this.http.post<ProjectMessage>(`${this.adminBase}/${id}/messages`, payload);
  }

  markMessagesReadAsAdmin(id: string) {
    return this.http.post<void>(`${this.adminBase}/${id}/messages/read`, {});
  }

  getUnreadMessageCountAsAdmin(id: string) {
    return this.http.get<UnreadCount>(`${this.adminBase}/${id}/messages/unread-count`);
  }
}
