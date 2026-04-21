import { test, expect } from '@playwright/test';

test('should register a new user successfully', async ({ page }) => {
  await page.goto('http://localhost:5173/signup');

  await page.fill('input[name="firstName"]', 'John');
  await page.fill('input[name="lastName"]', 'Doe');
  await page.fill('input[name="username"]', 'johndoe_e2e');
  await page.fill('input[name="email"]', 'john_e2e@example.com');
  await page.fill('input[name="password"]', 'password123');

  // Intercept the API call to mock the response
  await page.route('**/api/v1/auth/signup', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ token: 'mock-token' }),
    });
  });

  await page.click('button:has-text("Sign Up")');

  // Should redirect to login page after successful signup
  await expect(page).toHaveURL(/.*login/);
});

test('should show error on registration failure', async ({ page }) => {
  await page.goto('http://localhost:5173/signup');

  // Intercept the API call to mock a failure
  await page.route('**/api/v1/auth/signup', async (route) => {
    await route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Registration failed' }),
    });
  });

  await page.click('button:has-text("Sign Up")');

  await expect(page.locator('.error-msg')).toContainText('Registration failed');
});
