import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { StaffSummary } from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class StaffService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/staff`;

  list() {
    return this.http.get<StaffSummary[]>(this.base);
  }
}
