import { Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { EngagementService } from '../../../core/services/engagement.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { RazorpayCheckoutService } from '../../../core/services/razorpay-checkout.service';
import { SeoService } from '../../../core/services/seo.service';
import {
  Engagement,
  EngagementStatus,
  Invoice,
  Milestone,
  MilestoneStatus,
  ProjectFile,
} from '../../../core/models/content.model';

@Component({
  selector: 'app-dashboard-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, ReactiveFormsModule, FormsModule],
  templateUrl: './dashboard-detail.component.html',
  styleUrl: './dashboard-detail.component.scss',
})
export class DashboardDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private engagementService = inject(EngagementService);
  private seo = inject(SeoService);
  private authService = inject(AuthService);
  private invoiceService = inject(InvoiceService);
  private razorpayCheckout = inject(RazorpayCheckoutService);
  private fb = inject(FormBuilder);

  engagement = signal<Engagement | null>(null);
  milestones = signal<Milestone[]>([]);
  files = signal<ProjectFile[]>([]);
  invoices = signal<Invoice[]>([]);

  uploading = signal(false);
  uploadError = signal<string | null>(null);
  addingMilestone = signal(false);
  creatingInvoice = signal(false);
  payingInvoiceId = signal<string | null>(null);
  paymentError = signal<string | null>(null);

  readonly engagementStatuses: EngagementStatus[] = ['ONBOARDING', 'IN_PROGRESS', 'REVIEW', 'COMPLETED', 'ON_HOLD'];
  readonly milestoneStatuses: MilestoneStatus[] = ['PENDING', 'IN_PROGRESS', 'DONE'];

  milestoneForm = this.fb.nonNullable.group({
    title: ['', Validators.required],
    description: [''],
    dueDate: [''],
  });

  invoiceForm = this.fb.nonNullable.group({
    description: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(1)]],
    dueDate: [''],
  });

  get isAdmin(): boolean {
    return this.authService.currentUser()?.role === 'ADMIN';
  }

  private engagementId!: string;

  ngOnInit(): void {
    this.seo.update({ title: 'Project', description: 'Your Neelastack project dashboard.', noindex: true });
    this.engagementId = this.route.snapshot.paramMap.get('id')!;
    this.load();
  }

  private load(): void {
    this.engagementService.getEngagement(this.engagementId).subscribe((e) => this.engagement.set(e));
    this.engagementService.getMilestones(this.engagementId).subscribe((m) => this.milestones.set(m));
    this.engagementService.getFiles(this.engagementId).subscribe((f) => this.files.set(f));
    this.invoiceService.listForEngagement(this.engagementId).subscribe((inv) => this.invoices.set(inv));
  }

  updateEngagementStatus(status: EngagementStatus): void {
    this.engagementService.updateEngagementStatus(this.engagementId, status).subscribe((e) => this.engagement.set(e));
  }

  updateMilestoneStatus(milestoneId: string, status: MilestoneStatus): void {
    this.engagementService.updateMilestoneStatus(milestoneId, status).subscribe((updated) => {
      this.milestones.set(this.milestones().map((m) => (m.id === updated.id ? updated : m)));
    });
  }

  addMilestone(): void {
    if (this.milestoneForm.invalid) {
      this.milestoneForm.markAllAsTouched();
      return;
    }
    this.addingMilestone.set(true);
    this.engagementService.addMilestone(this.engagementId, this.milestoneForm.getRawValue()).subscribe({
      next: (milestone) => {
        this.addingMilestone.set(false);
        this.milestones.set([...this.milestones(), milestone]);
        this.milestoneForm.reset();
      },
      error: () => this.addingMilestone.set(false),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.uploading.set(true);
    this.uploadError.set(null);

    this.engagementService.uploadFile(this.engagementId, file).subscribe({
      next: (uploaded) => {
        this.uploading.set(false);
        this.files.set([uploaded, ...this.files()]);
        input.value = '';
      },
      error: (err) => {
        this.uploading.set(false);
        this.uploadError.set(err?.error?.message ?? 'Upload failed. Please try again.');
        input.value = '';
      },
    });
  }

  deleteFile(fileId: string): void {
    this.engagementService.deleteFile(this.engagementId, fileId).subscribe(() => {
      this.files.set(this.files().filter((f) => f.id !== fileId));
    });
  }

  createInvoice(): void {
    if (this.invoiceForm.invalid) {
      this.invoiceForm.markAllAsTouched();
      return;
    }
    this.creatingInvoice.set(true);
    this.invoiceService
      .createInvoice({ engagementId: this.engagementId, ...this.invoiceForm.getRawValue() })
      .subscribe({
        next: (invoice) => {
          this.creatingInvoice.set(false);
          this.invoices.set([invoice, ...this.invoices()]);
          this.invoiceForm.reset({ description: '', amount: 0, dueDate: '' });
        },
        error: () => this.creatingInvoice.set(false),
      });
  }

  async payInvoice(invoice: Invoice): Promise<void> {
    const user = this.authService.currentUser();
    if (!user) return;

    this.paymentError.set(null);
    this.payingInvoiceId.set(invoice.id);

    try {
      const order = await firstValueFrom(this.invoiceService.createOrder(invoice.id));
      const result = await this.razorpayCheckout.open(order, { name: user.fullName, email: user.email });

      const updated = await firstValueFrom(
        this.invoiceService.verifyPayment(invoice.id, {
          razorpayOrderId: result.razorpay_order_id,
          razorpayPaymentId: result.razorpay_payment_id,
          razorpaySignature: result.razorpay_signature,
        }),
      );

      this.invoices.set(this.invoices().map((i) => (i.id === updated.id ? updated : i)));
    } catch (err: any) {
      this.paymentError.set(err?.error?.message ?? err?.message ?? 'Payment did not complete.');
    } finally {
      this.payingInvoiceId.set(null);
    }
  }

  downloadInvoicePdf(invoice: Invoice): void {
    this.invoiceService.downloadPdf(invoice.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${invoice.invoiceNumber}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
    });
  }

  formatSize(bytes?: number): string {
    if (!bytes) return '';
    const kb = bytes / 1024;
    return kb < 1024 ? `${kb.toFixed(0)} KB` : `${(kb / 1024).toFixed(1)} MB`;
  }
}
