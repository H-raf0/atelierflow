import { expect, test } from '@playwright/test';
import { DEMO_PASSWORD, signIn } from './helpers';

test.describe('Parcours 1 — connexion', () => {
  test('a protected page sends to the login page, which brings the user back after sign-in', async ({ page }) => {
    await page.goto('/equipment');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fequipment/);

    await page.getByTestId('username').fill('responsable');
    await page.getByTestId('password').fill(DEMO_PASSWORD);
    await page.getByTestId('login').click();

    await expect(page).toHaveURL(/\/equipment$/);
    await expect(page.getByRole('heading', { name: 'Parc machines' })).toBeVisible();
    await expect(page.getByTestId('equipment-row')).toHaveCount(20);
    await expect(page.getByTestId('user-menu')).toContainText('Sophie Bernard');
  });

  test('wrong credentials are refused with a clear message', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('username').fill('responsable');
    await page.getByTestId('password').fill('mauvais-mot-de-passe');
    await page.getByTestId('login').click();

    await expect(page.getByRole('alert')).toHaveText('Identifiant ou mot de passe incorrect.');
    await expect(page).toHaveURL(/\/login/);
  });

  test('the session survives a page reload (refresh cookie) and ends on sign-out', async ({ page }) => {
    await signIn(page, 'technicien');

    await page.reload();
    await expect(page.getByTestId('user-menu')).toContainText('Karim Haddad');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('logout').click();
    await expect(page).toHaveURL(/\/login/);

    await page.goto('/board');
    await expect(page).toHaveURL(/\/login/);
  });

  test('a technician does not get the managers controls', async ({ page }) => {
    await signIn(page, 'technicien');
    await expect(page.getByText('Vous pouvez démarrer et terminer les OT qui vous sont assignés')).toBeVisible();

    await page.goto('/equipment');
    await expect(page.getByTestId('equipment-row').first()).toBeVisible();
    await expect(page.getByTestId('new-equipment')).toHaveCount(0);
  });
});
