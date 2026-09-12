import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { NotificationService } from '../../core/services/notification.service';
import { AppNotification } from '../../core/models/content.model';

/**
 * P0 #2 (client-workspace review) — the notification bell. Polls unread count every 30s
 * (same lightweight polling approach the rest of the workspace uses for messages) and lazily
 * loads the full feed only when the panel is opened, so most polling cycles cost a single
 * cheap COUNT query rather than the full notification list.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="bell-wrap">
      <button class="bell-btn" (click)="togglePanel()" aria-label="Notifications">
        <span class="bell-icon">🔔</span>
        <span class="badge" *ngIf="unreadCount > 0">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
      </button>

      <div class="panel" *ngIf="open">
        <div class="panel-header">
          <span>Notifications</span>
          <button class="link-btn" (click)="markAllRead()" *ngIf="unreadCount > 0">Mark all read</button>
        </div>
        <div class="panel-list" *ngIf="notifications.length; else empty">
          <a *ngFor="let n of notifications"
             class="notif-row"
             [class.unread]="!n.read"
             [routerLink]="n.deepLink ? [n.deepLink] : null"
             (click)="onNotificationClick(n)">
            <div class="notif-title">{{ n.title }}</div>
            <div class="notif-body" *ngIf="n.body">{{ n.body }}</div>
            <div class="notif-time">{{ n.createdAt | date:'MMM d, h:mm a' }}</div>
          </a>
        </div>
        <ng-template #empty>
          <div class="panel-empty">No notifications yet</div>
        </ng-template>
      </div>
    </div>
  `,
  styles: [`
    .bell-wrap { position: relative; display: inline-block; }
    .bell-btn { position: relative; background: none; border: none; cursor: pointer; font-size: 1.25rem; padding: 6px; }
    .badge { position: absolute; top: 0; right: 0; background: #e11d48; color: #fff; border-radius: 999px;
             font-size: 0.65rem; padding: 1px 5px; line-height: 1.2; }
    .panel { position: absolute; right: 0; top: 100%; width: 340px; max-height: 420px; overflow-y: auto;
             background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; box-shadow: 0 8px 24px rgba(0,0,0,0.12);
             z-index: 50; }
    .panel-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px;
                    border-bottom: 1px solid #f1f5f9; font-weight: 600; font-size: 0.9rem; }
    .link-btn { background: none; border: none; color: #2563eb; font-size: 0.8rem; cursor: pointer; }
    .notif-row { display: block; padding: 10px 14px; border-bottom: 1px solid #f8fafc; text-decoration: none; color: inherit; }
    .notif-row.unread { background: #f0f7ff; }
    .notif-title { font-weight: 600; font-size: 0.85rem; }
    .notif-body { font-size: 0.8rem; color: #475569; margin-top: 2px; }
    .notif-time { font-size: 0.7rem; color: #94a3b8; margin-top: 4px; }
    .panel-empty { padding: 24px; text-align: center; color: #94a3b8; font-size: 0.85rem; }
  `]
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private notificationService = inject(NotificationService);

  unreadCount = 0;
  notifications: AppNotification[] = [];
  open = false;
  private pollHandle: ReturnType<typeof setInterval> | undefined;

  ngOnInit(): void {
    this.refreshCount();
    this.pollHandle = setInterval(() => this.refreshCount(), 30000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

  togglePanel(): void {
    this.open = !this.open;
    if (this.open) {
      this.notificationService.list().subscribe(list => (this.notifications = list));
    }
  }

  onNotificationClick(n: AppNotification): void {
    if (!n.read) {
      this.notificationService.markRead(n.id).subscribe(() => this.refreshCount());
    }
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe(() => {
      this.unreadCount = 0;
      this.notifications = this.notifications.map(n => ({ ...n, read: true }));
    });
  }

  private refreshCount(): void {
    this.notificationService.unreadCount().subscribe(res => (this.unreadCount = res.unreadCount));
  }
}
