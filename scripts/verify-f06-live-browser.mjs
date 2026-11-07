// Uses real Ollama with safe biology notes and isolated test data.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true });
const page = await browser.newPage(); page.setDefaultTimeout(250_000);
try {
  await page.goto('http://localhost:8090');
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
  await page.getByRole('button', { name: 'Open course Live personalized planning verification', exact: true }).click();
  await page.getByText('Study plan and availability', { exact: true }).click();
  await page.getByText('Saved study plan', { exact: true }).click();
  const previous = await page.locator('[data-plan-id]').getAttribute('data-plan-id');
  assert.equal(await page.locator('.study-session').count(), 4);
  await page.locator('#planning-generate').click();
  await Promise.race([
    page.locator(`[data-plan-id]:not([data-plan-id="${previous}"])`).waitFor(),
    page.locator('#study-planning .error-message').filter({ hasText: /.+/ }).waitFor().then(() => { throw new Error('Live planning returned an error.'); }),
  ]);
  assert.equal(await page.locator('.study-session').count(), 4);
  assert.ok((await page.locator('.study-session').first().innerText()).length > 40);
  await page.locator('.study-session').first().locator('vaadin-details').first().click();
  await page.screenshot({ path: 'frontend/target/f06-live-plan.png', fullPage: true });
  console.log('PASS: real local Ollama plan viewed and regenerated through the browser with four saved sessions.');
} catch (error) {
  console.log(error.stack); await page.screenshot({ path: 'frontend/target/f06-live-failure.png', fullPage: true }); process.exitCode = 1;
} finally { await browser.close(); }
