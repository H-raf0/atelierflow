import { expect, test } from '@playwright/test';
import { choose, dragCard, signIn } from './helpers';

test.describe("Parcours 2 — cycle de vie d'un ordre de travail", () => {
  test('a breakdown goes OPEN → PLANNED → IN_PROGRESS → DONE on the kanban, live for other users', async ({ browser, page }) => {
    const title = `Fuite e2e ${Date.now()}`;

    // a technician watches the board in another browser
    const technicianContext = await browser.newContext();
    const technicianPage = await technicianContext.newPage();
    await signIn(technicianPage, 'technicien');

    // the manager reports the breakdown
    await signIn(page, 'responsable');
    await page.getByTestId('new-work-order').click();
    await choose(page, page.getByTestId('wo-equipment'), /^TCN-002/);
    await page.getByTestId('wo-title').fill(title);
    await page.getByTestId('wo-submit').click();

    const card = page.locator('[data-testid^="card-"]', { hasText: title });
    await expect(page.getByTestId('column-OPEN').locator('.card', { hasText: title })).toBeVisible();
    // pushed over WebSocket: the technician sees it without reloading
    await expect(technicianPage.getByTestId('column-OPEN').locator('.card', { hasText: title })).toBeVisible();

    // plan: assign the technician
    await dragCard(page, card, page.getByTestId('column-PLANNED'));
    await choose(page, page.getByTestId('assignee'), 'Karim Haddad');
    await page.getByTestId('confirm-plan').click();
    await expect(page.getByTestId('column-PLANNED').locator('.card', { hasText: title })).toContainText('Karim Haddad');
    await expect(technicianPage.getByTestId('column-PLANNED').locator('.card', { hasText: title })).toBeVisible();

    // the assigned technician starts the intervention from their own screen
    const technicianCard = technicianPage.locator('[data-testid^="card-"]', { hasText: title });
    await dragCard(technicianPage, technicianCard, technicianPage.getByTestId('column-IN_PROGRESS'));
    await expect(technicianPage.getByTestId('column-IN_PROGRESS').locator('.card', { hasText: title })).toBeVisible();
    await expect(page.getByTestId('column-IN_PROGRESS').locator('.card', { hasText: title })).toBeVisible();

    // and completes it
    await dragCard(technicianPage, technicianCard, technicianPage.getByTestId('column-DONE'));
    await technicianPage.getByTestId('time-spent').fill('45');
    await technicianPage.getByTestId('resolution').fill('Joint de vérin remplacé, essais OK');
    await technicianPage.getByTestId('confirm-complete').click();
    await expect(page.getByTestId('column-DONE').locator('.card', { hasText: title })).toBeVisible();

    // the work order page shows the final state and who did what
    await page.locator('.card', { hasText: title }).locator('.reference').click();
    await expect(page.getByTestId('wo-status')).toHaveText('Terminé');
    const history = page.getByTestId('history');
    await expect(history.locator(':scope > li')).toHaveCount(4);
    await expect(history).toContainText('Statut : En cours → Terminé');
    await expect(history).toContainText('Technicien : — → Karim Haddad');
    await expect(history).toContainText('technicien');

    await technicianContext.close();
  });

  test('an illegal move is not possible: a new work order cannot be dropped in DONE', async ({ page }) => {
    await signIn(page, 'responsable');
    const openCard = page.getByTestId('column-OPEN').locator('.card').first();
    const reference = await openCard.locator('.reference').textContent();

    await dragCard(page, openCard, page.getByTestId('column-DONE'));

    await expect(page.getByTestId('column-OPEN')).toContainText(reference!);
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });
});
