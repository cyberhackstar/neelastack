import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { TestimonialRequestPublic, TestimonialSubmission } from '../models/content.model';

/** Module 4: the client-facing half of the post-invoice testimonial loop. */
@Injectable({ providedIn: 'root' })
export class TestimonialService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/public/testimonials`;

  get(token: string) {
    return this.http.get<TestimonialRequestPublic>(`${this.base}/${token}`);
  }

  submit(token: string, payload: TestimonialSubmission) {
    return this.http.post<void>(`${this.base}/${token}`, payload);
  }
}
