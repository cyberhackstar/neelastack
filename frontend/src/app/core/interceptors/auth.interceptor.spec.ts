import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { authInterceptor } from './auth.interceptor';

const ACCESS_TOKEN_KEY = 'neelastack_access_token';

describe('authInterceptor (browser platform)', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);

    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'browser' },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  });

  it('attaches an Authorization header when an access token is stored', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'test-access-token');

    httpClient.get('/api/v1/client/engagements').subscribe();

    const req = httpMock.expectOne('/api/v1/client/engagements');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-access-token');
    req.flush({});
  });

  it('sends the request unmodified when no access token is stored', () => {
    httpClient.get('/api/v1/public/ping').subscribe();

    const req = httpMock.expectOne('/api/v1/public/ping');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });
});

describe('authInterceptor (server/SSR platform)', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'server' },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('never reads localStorage or attaches a token during SSR', () => {
    // Even if a token happened to be present, localStorage isn't reliably available
    // (or meaningful) during server rendering — the interceptor must short-circuit
    // before touching it rather than throwing or leaking a browser session token.
    httpClient.get('/api/v1/client/engagements').subscribe();

    const req = httpMock.expectOne('/api/v1/client/engagements');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });
});
