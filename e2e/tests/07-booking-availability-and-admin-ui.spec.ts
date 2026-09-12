import { test, expect } from '@playwright/test';

test.describe('Booking/admin regression', () => {
  test('booking settings route is registered', async ({ page }) => {
    await page.goto('/admin/booking-settings');
    await expect(page).not.toHaveURL(/\/404(?:\?|$)/);
  });

  test('admin invite acceptance route is registered', async ({ page }) => {
    await page.goto('/accept-admin-invitation?token=test-only-invalid-token');
    await expect(page).not.toHaveURL(/\/404(?:\?|$)/);
  });
});
