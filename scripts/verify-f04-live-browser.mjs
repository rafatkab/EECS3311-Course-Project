// Uses real local Ollama, never the scripted fixture.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
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
  await material.getByRole('button', { name: 'View saved concepts', exact: true }).click();
  await page.locator('[data-concepts-id]').waitFor();
  const previous = await page.locator('[data-concepts-id]').getAttribute('data-concepts-id');
  await page.getByRole('button', { name: 'Regenerate concepts', exact: true }).click();
  await Promise.race([
    page.locator(`[data-concepts-id]:not([data-concepts-id="${previous}"])`).waitFor(),
    page.getByText('No new concepts were saved.', { exact: false }).waitFor().then(() => { throw new Error('Real-model regeneration failed validation.'); }),
  ]);
  assert.ok(await page.locator('.concept-item').count());
  const source = await readFile('documentation/samples/f03-lecture.txt', 'utf8');
  for (const quotation of await page.locator('.concept-item .summary-quote').allTextContents()) assert.ok(source.includes(quotation));
  await page.screenshot({ path: 'frontend/target/f04-live-concepts.png', fullPage: true });
  console.log('PASS: real Ollama concepts viewed and regenerated through the browser with exact source quotations.');
} catch (error) {
  console.log(error.stack);
  await page.screenshot({ path: 'frontend/target/f04-live-failure.png', fullPage: true });
  process.exitCode = 1;
} finally { await browser.close(); }
