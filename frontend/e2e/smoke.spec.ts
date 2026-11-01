import { expect, test } from "@playwright/test";

test("landing page links to sign up and sign in", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Plan the trip. Know the cost." })).toBeVisible();
  await page.getByRole("link", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test("protected pages redirect to sign in without a session", async ({ page }) => {
  await page.goto("/trips/new");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
});

test("login form validates before calling the API", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Enter a valid email")).toBeVisible();
});
