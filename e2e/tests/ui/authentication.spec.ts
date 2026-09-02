import { expect, test } from '@playwright/test'

test.describe('UI baseline', () => {
  test.skip(!process.env.E2E_LOGIN || !process.env.E2E_PASSWORD, 'Set E2E_LOGIN and E2E_PASSWORD for UI tests.')

  test('logs in and opens the projects page', async ({ page }) => {
    const login = process.env.E2E_LOGIN!
    const password = process.env.E2E_PASSWORD!

    await page.goto('/login')
    await page.locator('input[autocomplete="username"]').fill(login!)
    await page.locator('input[autocomplete="current-password"]').fill(password!)
    await page.getByRole('button', { name: /sign in/i }).click()

    await expect(page).toHaveURL(/\/app\/home(?:[/?#]|$)/)
    await page.locator('a[href="/app/projects"]').click()

    await expect(page).toHaveURL(/\/app\/projects(?:[/?#]|$)/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Projects')
  })
})
