import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminStaff, StaffSummary } from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class StaffService {
  private readonly http = inject(HttpClient);

  // This service is used by the admin project-detail task assignment UI.
  // The former /admin/staff endpoint does not exist; staff administration lives
  // under /admin/staff-management.
  private readonly base = `${environment.apiBaseUrl}/admin/staff-management`;

  list() {
    return this.http.get<AdminStaff[]>(this.base).pipe(
      map((staff): StaffSummary[] =>
        staff.map(({ id, fullName, email }) => ({ id, fullName, email })),
      ),
    );
  }
}
