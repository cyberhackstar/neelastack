import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { StaffService } from './staff.service';

describe('StaffService', () => {
  let service: StaffService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StaffService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StaffService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads staff from the implemented staff-management endpoint', () => {
    let result: unknown;
    service.list().subscribe((value) => (result = value));

    const req = http.expectOne(`${environment.apiBaseUrl}/admin/staff-management`);
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: '1',
        fullName: 'Admin User',
        email: 'admin@example.com',
        role: 'ADMIN',
        enabled: true,
        mfaEnabled: true,
        mustChangePassword: false,
        createdAt: null,
      },
    ]);

    expect(result).toEqual([
      { id: '1', fullName: 'Admin User', email: 'admin@example.com' },
    ]);
  });
});
