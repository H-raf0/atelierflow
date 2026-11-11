import { expect, Locator, Page } from '@playwright/test';

export const DEMO_PASSWORD = 'Demo-Atelier-2026';

export async function signIn(page: Page, username: string): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('username').fill(username);
  await page.getByTestId('password').fill(DEMO_PASSWORD);
  await page.getByTestId('login').click();
  await expect(page).toHaveURL(/\/board$/);
}

/**
 * Drags a kanban card to a column with real, progressive mouse moves: the CDK drag & drop only
 * starts after the pointer has moved a few pixels.
 */
export async function dragCard(page: Page, card: Locator, column: Locator): Promise<void> {
  await card.scrollIntoViewIfNeeded();
  const from = (await card.boundingBox())!;
  const to = (await column.locator('.cards').boundingBox())!;
  await page.mouse.move(from.x + 30, from.y + 20);
  await page.mouse.down();
  await page.mouse.move(from.x + 40, from.y + 30, { steps: 5 });
  // release at the card's height (it is on screen), inside the target column
  const y = Math.min(Math.max(from.y + 20, to.y + 20), to.y + to.height - 20);
  await page.mouse.move(to.x + to.width / 2, y, { steps: 20 });
  await page.mouse.up();
}

/** Picks an option of an Angular Material select. */
export async function choose(page: Page, select: Locator, option: string | RegExp): Promise<void> {
  await select.click();
  await page.getByRole('option', { name: option }).click();
}
