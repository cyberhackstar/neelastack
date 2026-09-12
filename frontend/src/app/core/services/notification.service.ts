import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AppNotification } from '../models/content.model';

/** Backs the notification bell (P0 #2). Polled on an interval by the shell layout, the same
 *  way the client workspace already polls messages every 15s — see dashboard-detail's
 *  existing polling pattern — rather than a WebSocket, matching this app's current v1
 *  real-time approach documented in the client-workspace review. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/notifications`;

  list() {
    return this.http.get<AppNotification[]>(this.base);
  }

  unreadCount() {
    return this.http.get<{ unreadCount: number }>(`${this.base}/unread-count`);
  }

  markRead(id: string) {
    return this.http.post<AppNotification>(`${this.base}/${id}/read`, {});
  }

  markAllRead() {
    return this.http.post<void>(`${this.base}/mark-all-read`, {});
  }
}
