import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { InquiryService } from '../../../../core/services/inquiry.service';
import { Inquiry } from '../../../../core/models/content.model';
import { SeoService } from '../../../../core/services/seo.service';

@Component({
  selector: 'app-admin-inquiries-list',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './admin-inquiries-list.component.html',
  styleUrl: './admin-inquiries-list.component.scss',
})
export class AdminInquiriesListComponent implements OnInit {
  private inquiryService = inject(InquiryService);
  private seo = inject(SeoService);

  inquiries = signal<Inquiry[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.seo.update({ title: 'Inquiries', description: 'Manage Neelastack inquiries.', noindex: true });
    this.inquiryService.listInquiries().subscribe({
      next: (page) => {
        this.inquiries.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
