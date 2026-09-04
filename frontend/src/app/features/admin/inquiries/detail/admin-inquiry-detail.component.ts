import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, FormArray, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { InquiryService } from '../../../../core/services/inquiry.service';
import { EngagementService } from '../../../../core/services/engagement.service';
import { Inquiry, InquiryStatus, Quotation } from '../../../../core/models/content.model';
import { SeoService } from '../../../../core/services/seo.service';

@Component({
  selector: 'app-admin-inquiry-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, ReactiveFormsModule, FormsModule],
  templateUrl: './admin-inquiry-detail.component.html',
  styleUrl: './admin-inquiry-detail.component.scss',
})
export class AdminInquiryDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private seo = inject(SeoService);
  private inquiryService = inject(InquiryService);
  private engagementService = inject(EngagementService);
  private fb = inject(FormBuilder);

  inquiry = signal<Inquiry | null>(null);
  quotations = signal<Quotation[]>([]);
  creating = signal(false);
  sendingId = signal<string | null>(null);
  creatingEngagement = signal(false);
  engagementCreated = signal(false);
  engagementError = signal<string | null>(null);

  readonly statuses: InquiryStatus[] = ['NEW', 'CONTACTED', 'QUOTED', 'WON', 'LOST'];

  engagementForm = this.fb.nonNullable.group({
    title: ['', Validators.required],
    description: [''],
    startDate: [''],
    targetEndDate: [''],
  });

  form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    scopeSummary: [''],
    currency: ['INR'],
    validUntil: [''],
    notes: [''],
    lineItems: this.fb.array([this.buildLineItem()]),
  });

  get lineItems(): FormArray {
    return this.form.get('lineItems') as FormArray;
  }

  private buildLineItem() {
    return this.fb.nonNullable.group({
      description: ['', Validators.required],
      amount: [0, [Validators.required, Validators.min(1)]],
    });
  }

  addLineItem(): void {
    this.lineItems.push(this.buildLineItem());
  }

  removeLineItem(index: number): void {
    if (this.lineItems.length > 1) {
      this.lineItems.removeAt(index);
    }
  }

  ngOnInit(): void {
    this.seo.update({ title: 'Inquiry', description: 'Neelastack inquiry detail.', noindex: true });
    const id = this.route.snapshot.paramMap.get('id')!;
    this.load(id);
  }

  private load(id: string): void {
    this.inquiryService.getInquiry(id).subscribe((inquiry) => this.inquiry.set(inquiry));
    this.inquiryService.getQuotations(id).subscribe((quotes) => this.quotations.set(quotes));
  }

  updateStatus(status: InquiryStatus): void {
    const inquiry = this.inquiry();
    if (!inquiry) return;
    this.inquiryService.updateStatus(inquiry.id, status).subscribe((updated) => this.inquiry.set(updated));
  }

  createQuotation(): void {
    const inquiry = this.inquiry();
    if (!inquiry || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.creating.set(true);
    this.inquiryService
      .createQuotation({ inquiryId: inquiry.id, ...this.form.getRawValue() })
      .subscribe({
        next: (quotation) => {
          this.creating.set(false);
          this.quotations.set([quotation, ...this.quotations()]);
          this.form.reset({ title: '', scopeSummary: '', currency: 'INR', validUntil: '', notes: '' });
          this.lineItems.clear();
          this.addLineItem();
        },
        error: () => this.creating.set(false),
      });
  }

  sendQuotation(id: string): void {
    this.sendingId.set(id);
    this.inquiryService.sendQuotation(id).subscribe({
      next: (updated) => {
        this.sendingId.set(null);
        this.quotations.set(this.quotations().map((q) => (q.id === updated.id ? updated : q)));
        const inquiry = this.inquiry();
        if (inquiry) this.inquiry.set({ ...inquiry, status: 'QUOTED' });
      },
      error: () => this.sendingId.set(null),
    });
  }

  createEngagement(): void {
    const inquiry = this.inquiry();
    if (!inquiry || this.engagementForm.invalid) {
      this.engagementForm.markAllAsTouched();
      return;
    }

    this.creatingEngagement.set(true);
    this.engagementError.set(null);

    this.engagementService
      .createEngagement({ clientEmail: inquiry.email, inquiryId: inquiry.id, ...this.engagementForm.getRawValue() })
      .subscribe({
        next: () => {
          this.creatingEngagement.set(false);
          this.engagementCreated.set(true);
          this.updateStatus('WON');
        },
        error: (err) => {
          this.creatingEngagement.set(false);
          this.engagementError.set(
            err?.error?.message ?? 'Could not create the project — the client may not have a registered account yet.',
          );
        },
      });
  }
}
