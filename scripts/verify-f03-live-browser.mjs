// Real Ollama verification, separate from the scripted protocol fixtures.
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true });
const page = await browser.newPage();
page.setDefaultTimeout(200_000);
try {
  await page.goto('http://localhost:8090');
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
  await page.getByRole('button', { name: 'Open course Live Ollama verification', exact: true }).click();
  const material = page.locator('.material-row').filter({ hasText: 'f03-lecture.txt' });
  await material.getByRole('button', { name: 'View saved summary', exact: true }).click();
  await page.getByRole('heading', { name: 'Supporting source quotations' }).waitFor();
  const previousId = await page.locator('vaadin-dialog-overlay pre[data-summary-id]').getAttribute('data-summary-id');
  await page.getByRole('button', { name: 'Regenerate summary', exact: true }).click();
  await page.locator(`vaadin-dialog-overlay pre[data-summary-id]:not([data-summary-id="${previousId}"])`).waitFor();
  await page.getByRole('heading', { name: 'Supporting source quotations' }).waitFor();
  assert.ok((await page.locator('vaadin-dialog-overlay .material-text').innerText()).length >= 20);
  assert.ok(await page.locator('.summary-quote').count());
  await page.screenshot({ path: fileURLToPath(new URL('../frontend/target/f03-live-summary.png', import.meta.url)), fullPage: true });
  console.log('PASS: real local Ollama summary viewed and regenerated through the browser.');
} catch (error) {
  console.log(error.stack);
  await page.screenshot({ path: fileURLToPath(new URL('../frontend/target/f03-live-failure.png', import.meta.url)), fullPage: true });
  process.exitCode = 1;
} finally { await browser.close(); }
