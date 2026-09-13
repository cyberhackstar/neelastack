import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { timeout } from 'rxjs';
import { SeoService } from '../../../core/services/seo.service';
import { UpiPaymentService } from '../../../core/services/upi-payment.service';
import { PaymentHistoryService } from '../../../core/services/payment-history.service';
import { PaymentScheduleService } from '../../../core/services/payment-schedule.service';
import { ProjectHealthService } from '../../../core/services/project-health.service';
import { EngagementService } from '../../../core/services/engagement.service';
import {
  Engagement,
  PaymentHistoryItem,
  ProjectOperationsSummary,
  UpiPaymentMethod,
  UpiSubmission,
  PaymentSchedule,
} from '../../../core/models/content.model';

@Component({
  selector: 'app-admin-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './admin-payments.component.html',
  styleUrl: './admin-payments.component.scss',
})
export class AdminPaymentsComponent implements OnInit {
  private readonly seo = inject(SeoService);
  private readonly upi = inject(UpiPaymentService);
  private readonly historyService = inject(PaymentHistoryService);
  private readonly schedules = inject(PaymentScheduleService);
  private readonly health = inject(ProjectHealthService);
  private readonly engagements = inject(EngagementService);

  methods = signal<UpiPaymentMethod[]>([]);
  pending = signal<UpiSubmission[]>([]);
  history = signal<PaymentHistoryItem[]>([]);
  ops = signal<ProjectOperationsSummary | null>(null);
  historyLoading = signal(false);
  exporting = signal(false);
  projects = signal<Engagement[]>([]);
  selectedProjectId = '';
  projectSchedule = signal<PaymentSchedule | null>(null);

  newLabel = '';
  newVpa = '';
  newPayee = '';
  qrFile: File | null = null;
  methodBusy = signal(false);
  actionBusy = signal<string | null>(null);
  message = signal<string | null>(null);
  historyError = signal<string | null>(null);
  totalAmount: number | null = null;
  scheduleCurrency = 'INR';
  installmentText = '';
  scheduleBusy = signal(false);
  scheduleMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.seo.update({
      title: 'Payments & Operations',
      description: 'Manage UPI payments, payment plans and project operations.',
      noindex: true,
    });
    this.reload();
    this.loadHistory();
    this.engagements.listAllForAdmin().subscribe({ next: (v) => this.projects.set(v) });
  }

  reload(): void {
    this.message.set(null);
    this.upi.listAllMethods().subscribe({
      next: (v) => this.methods.set(v),
      error: (e) => this.setAdminPaymentError(e, 'Could not load UPI methods.'),
    });
    this.upi.listPendingSubmissions().subscribe({
      next: (v) => this.pending.set(v),
      error: (e) => this.setAdminPaymentError(e, 'Could not load pending UPI submissions.'),
    });
    this.health.getOperationsSummary().subscribe({
      next: (v) => this.ops.set(v),
      error: (e) => this.setAdminPaymentError(e, 'Could not load operations summary.'),
    });
  }

  loadHistory(): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.historyService.list().subscribe({
      next: (items) => {
        this.history.set(items);
        this.historyLoading.set(false);
      },
      error: (e) => {
        this.historyLoading.set(false);
        this.historyError.set(e?.error?.message ?? 'Could not load payment history.');
      },
    });
  }

  exportHistory(): void {
    this.exporting.set(true);
    this.historyService.exportExcel().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `neelastack-payment-history-${new Date().toISOString().slice(0, 10)}.xlsx`;
        link.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: (e) => {
        this.exporting.set(false);
        this.historyError.set(e?.error?.message ?? 'Could not export payment history.');
      },
    });
  }

  onQr(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.qrFile = input.files?.[0] ?? null;
  }

  async addMethod(): Promise<void> {
    if (!this.newLabel.trim() || !this.qrFile) {
      this.message.set('Label and QR image are required.');
      return;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(this.qrFile.type) || this.qrFile.size > 5 * 1024 * 1024) {
      this.message.set('QR image must be JPG, PNG or WebP and 5MB or smaller.');
      return;
    }
    this.methodBusy.set(true);
    this.message.set(null);
    try {
      const qr = this.qrFile.size > 1.75 * 1024 * 1024 ? await this.compressQr(this.qrFile) : this.qrFile;
      this.upi.createMethod(
        { label: this.newLabel.trim(), vpa: this.newVpa.trim() || undefined, payeeName: this.newPayee.trim() || undefined },
        qr,
      ).pipe(timeout(90_000)).subscribe({
        next: (v) => {
          this.methods.set([v, ...this.methods()]);
          this.newLabel = '';
          this.newVpa = '';
          this.newPayee = '';
          this.qrFile = null;
          this.methodBusy.set(false);
        },
        error: (e) => {
          this.methodBusy.set(false);
          if (e?.name === 'TimeoutError') {
            this.message.set('The QR upload is taking too long. Please try a smaller image and retry.');
          } else {
            this.setAdminPaymentError(e, 'Could not create UPI method.');
          }
        },
      });
    } catch {
      this.methodBusy.set(false);
      this.message.set('Could not prepare the QR image. Please choose another JPG, PNG or WebP file.');
    }
  }

  private compressQr(file: File): Promise<File> {
    return new Promise((resolve, reject) => {
      const objectUrl = URL.createObjectURL(file);
      const image = new Image();
      image.onload = () => {
        URL.revokeObjectURL(objectUrl);
        const maxSide = 1600;
        const scale = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
        const context = canvas.getContext('2d');
        if (!context) { reject(new Error('Canvas unavailable')); return; }
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
        canvas.toBlob((blob) => {
          if (!blob) { reject(new Error('Image compression failed')); return; }
          const base = file.name.replace(/\.[^.]+$/, '') || 'upi-qr';
          resolve(new File([blob], `${base}.jpg`, { type: 'image/jpeg', lastModified: Date.now() }));
        }, 'image/jpeg', 0.9);
      };
      image.onerror = () => { URL.revokeObjectURL(objectUrl); reject(new Error('Image could not be decoded')); };
      image.src = objectUrl;
    });
  }

  toggleMethod(m: UpiPaymentMethod): void {
    this.actionBusy.set(m.id);
    const call = m.active ? this.upi.deactivateMethod(m.id) : this.upi.activateMethod(m.id);
    call.subscribe({
      next: (v) => {
        this.methods.set(this.methods().map((x) => (x.id === v.id ? v : x)));
        this.actionBusy.set(null);
      },
      error: (e) => {
        this.actionBusy.set(null);
        this.setAdminPaymentError(e, 'Could not update UPI method.');
      },
    });
  }

  deleteMethod(m: UpiPaymentMethod): void {
    if (!confirm(`Delete ${m.label}?`)) return;
    this.actionBusy.set(m.id);
    this.upi.deleteMethod(m.id).subscribe({
      next: () => {
        this.methods.set(this.methods().filter((x) => x.id !== m.id));
        this.actionBusy.set(null);
      },
      error: (e) => {
        this.actionBusy.set(null);
        this.setAdminPaymentError(e, 'Could not delete UPI method.');
      },
    });
  }

  verify(s: UpiSubmission, approve: boolean): void {
    this.actionBusy.set(s.id);
    this.upi.verifySubmission(s.id, approve).subscribe({
      next: () => {
        this.pending.set(this.pending().filter((x) => x.id !== s.id));
        this.actionBusy.set(null);
        this.reload();
        this.loadHistory();
      },
      error: (e) => {
        this.actionBusy.set(null);
        if (e?.status === 403 && e?.error?.error === 'MFA required') {
          this.message.set('Payment verification requires MFA. Enable MFA in Security Settings, then verify this payment again.');
        } else if (e?.status === 403 && typeof e?.error?.message === 'string' && e.error.message.toLowerCase().includes('step-up')) {
          this.message.set('Payment verification needs a fresh MFA code. The security prompt should appear; retry the action if it does not.');
        } else {
          this.message.set(e?.error?.message ?? 'Could not verify payment.');
        }
      },
    });
  }

  loadSchedule(): void {
    if (!this.selectedProjectId) return;
    this.schedules.getForEngagementAsAdmin(this.selectedProjectId).subscribe({
      next: (v) => this.projectSchedule.set(v),
      error: () => this.projectSchedule.set(null),
    });
  }

  createSchedule(): void {
    if (!this.selectedProjectId || !this.totalAmount || !this.installmentText.trim()) {
      this.scheduleMessage.set('Select a project, enter a total, and add installments.');
      return;
    }
    const installments = this.installmentText
      .split('\n')
      .map((line, i) => line.split('|').map((x) => x.trim()))
      .filter((a) => a.length >= 2)
      .map((a, i) => ({ label: a[0], amount: Number(a[1]), dueDate: a[2] || undefined, displayOrder: i }));
    if (installments.some((x) => !x.label || !Number.isFinite(x.amount) || x.amount <= 0)) {
      this.scheduleMessage.set('Each installment must be: Label | Amount | YYYY-MM-DD');
      return;
    }
    this.scheduleBusy.set(true);
    this.scheduleMessage.set(null);
    this.schedules.create({ engagementId: this.selectedProjectId, totalAmount: this.totalAmount, currency: this.scheduleCurrency, installments }).subscribe({
      next: (v) => {
        this.projectSchedule.set(v);
        this.scheduleBusy.set(false);
        this.scheduleMessage.set('Payment plan created.');
      },
      error: (e) => {
        this.scheduleBusy.set(false);
        this.scheduleMessage.set(e?.error?.message ?? 'Could not create payment plan.');
      },
    });
  }

  raiseInvoice(id: string): void {
    this.actionBusy.set(id);
    this.schedules.raiseInvoice(id).subscribe({
      next: () => {
        this.loadSchedule();
        this.actionBusy.set(null);
      },
      error: (e) => {
        this.actionBusy.set(null);
        this.scheduleMessage.set(e?.error?.message ?? 'Could not raise invoice.');
      },
    });
  }

  private setAdminPaymentError(error: any, fallback: string): void {
    if (error?.status === 403 && error?.error?.error === 'MFA required') {
      this.message.set('Payment settings and verification require MFA. Enable MFA in Security Settings.');
      return;
    }
    if (error?.status === 401) {
      this.message.set('Your admin session has expired. Please sign in again.');
      return;
    }
    if (error?.status !== 403) {
      this.message.set(error?.error?.message ?? fallback);
    }
  }
}
