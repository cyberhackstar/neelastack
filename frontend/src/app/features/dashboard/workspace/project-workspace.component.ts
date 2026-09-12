import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProjectHealthService } from '../../../core/services/project-health.service';
import { PaymentScheduleService } from '../../../core/services/payment-schedule.service';
import { UpiPaymentService } from '../../../core/services/upi-payment.service';
import { UpiPaymentMethod, UpiSubmission, PaymentSchedule, ProjectHealth, ActionItem } from '../../../core/models/content.model';

@Component({
  selector: 'app-project-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, DecimalPipe],
  templateUrl: './project-workspace.component.html',
  styleUrl: './project-workspace.component.scss',
})
export class ProjectWorkspaceComponent implements OnInit {
  private healthService = inject(ProjectHealthService);
  private scheduleService = inject(PaymentScheduleService);
  private upiService = inject(UpiPaymentService);

  @Input({ required: true }) engagementId!: string;
  @Input() invoices: Array<{ id: string; invoiceNumber: string; amount: number; currency: string; status: string }> = [];
  @Input() isAdmin = false;

  health = signal<ProjectHealth | null>(null);
  actions = signal<ActionItem[]>([]);
  schedule = signal<PaymentSchedule | null>(null);
  upiMethods = signal<UpiPaymentMethod[]>([]);
  submissions = signal<Record<string, UpiSubmission[]>>({});
  selectedInvoiceId = signal<string | null>(null);
  selectedMethodId = signal('');
  utrReference = signal('');
  payerUpiId = signal('');
  amountClaimed = signal<number | null>(null);
  screenshot = signal<File | null>(null);
  submittingUpi = signal(false);
  upiMessage = signal<string | null>(null);
  upiError = signal<string | null>(null);

  ngOnInit(): void {
    this.healthService.getHealth(this.engagementId).subscribe({ next: (v) => this.health.set(v) });
    this.healthService.getActionItems(this.engagementId).subscribe({ next: (v) => this.actions.set(v) });
    this.scheduleService.getForEngagement(this.engagementId).subscribe({ next: (v) => this.schedule.set(v), error: () => this.schedule.set(null) });
    if (!this.isAdmin) {
      this.upiService.listActiveMethods().subscribe({ next: (v) => this.upiMethods.set(v) });
    }
  }

  openUpi(invoiceId: string, amount: number): void {
    this.selectedInvoiceId.set(invoiceId);
    this.amountClaimed.set(amount);
    this.utrReference.set('');
    this.payerUpiId.set('');
    this.screenshot.set(null);
    this.upiMessage.set(null);
    this.upiError.set(null);
    this.upiService.listSubmissionsForInvoice(invoiceId).subscribe({
      next: (rows) => this.submissions.set({ ...this.submissions(), [invoiceId]: rows }),
    });
  }

  closeUpi(): void { this.selectedInvoiceId.set(null); }

  onScreenshot(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.screenshot.set(input.files?.[0] ?? null);
  }

  submitUpi(invoiceId: string): void {
    if (!this.selectedMethodId() || !this.utrReference().trim() || !this.amountClaimed() || this.amountClaimed()! <= 0) {
      this.upiError.set('Select a UPI method, enter the UTR/reference, and confirm the amount.');
      return;
    }
    this.submittingUpi.set(true);
    this.upiError.set(null);
    this.upiService.submitPayment(invoiceId, {
      upiMethodId: this.selectedMethodId(),
      utrReference: this.utrReference().trim(),
      payerUpiId: this.payerUpiId().trim() || undefined,
      amountClaimed: this.amountClaimed()!,
    }, this.screenshot()).subscribe({
      next: (submission) => {
        this.submittingUpi.set(false);
        this.upiMessage.set('Payment proof submitted. It will appear as pending until the team verifies it.');
        this.submissions.set({ ...this.submissions(), [invoiceId]: [submission, ...(this.submissions()[invoiceId] ?? [])] });
      },
      error: (err) => {
        this.submittingUpi.set(false);
        this.upiError.set(err?.error?.message ?? 'Could not submit the UPI payment. Please try again.');
      },
    });
  }

  statusLabel(status: string): string {
    return status.replaceAll('_', ' ');
  }
}
