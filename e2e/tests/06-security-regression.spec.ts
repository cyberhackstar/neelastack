import { test, expect } from '@playwright/test';

const API = process.env['API_BASE_URL'] ?? 'http://127.0.0.1:8080';

test.describe('Security regression', () => {
  test('unverified registration never receives session tokens', async ({ request }) => {
    const email = `security-${Date.now()}@example.com`;
    const res = await request.post(`${API}/api/v1/auth/register`, {
      data: { fullName: 'Security Test', email, password: 'E2eTestPassword!23', phone: '' },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.accessToken ?? null).toBeNull();
    expect(body.refreshToken ?? null).toBeNull();
    expect(body.emailVerified).toBe(false);
    expect(body.verificationRequired).toBe(true);
  });

  test('unverified account cannot login before verification', async ({ request }) => {
    const email = `security-login-${Date.now()}@example.com`;
    await expect((async () => {
      const register = await request.post(`${API}/api/v1/auth/register`, { data: { fullName: 'Security Test', email, password: 'E2eTestPassword!23', phone: '' } });
      expect(register.ok()).toBeTruthy();
      const login = await request.post(`${API}/api/v1/auth/login`, { data: { email, password: 'E2eTestPassword!23' } });
      expect(login.status()).toBeGreaterThanOrEqual(400);
      expect(login.status()).toBeLessThan(500);
    })()).resolves.not.toThrow();
  });
});
