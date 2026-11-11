import { expect, test } from '@playwright/test';
import { signIn } from './helpers';

/** Not a test: regenerates the README screenshots from the running demo (`npm run screenshots`). */
const OUT = '../docs/screenshots';

test('README screenshots', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('login')).toBeVisible();
  await page.screenshot({ path: `${OUT}/login.png` });

  await signIn(page, 'responsable');
  await expect(page.locator('.card').first()).toBeVisible();
  await page.screenshot({ path: `${OUT}/kanban.png` });

  await page.goto('/dashboard');
  await expect(page.locator('canvas').nth(2)).toBeVisible();
  await page.waitForTimeout(1000); // chart animations
  await page.screenshot({ path: `${OUT}/dashboard.png`, fullPage: true });

  await page.goto('/equipment');
  await expect(page.getByTestId('equipment-row').first()).toBeVisible();
  await page.screenshot({ path: `${OUT}/equipment.png` });

  await page.goto('/board');
  await page.getByTestId('column-DONE').locator('.reference').first().click();
  await expect(page.getByTestId('history').locator(':scope > li')).toHaveCount(4);
  await page.screenshot({ path: `${OUT}/work-order.png` });
});
