// Run through verify-f01.ps1 -IncludeF02 against isolated servers and fixtures.
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true });
const page = await browser.newPage();
const errors = [];
page.on('pageerror', error => errors.push(error.message));
const waitText = async text => page.getByText(text, { exact: false }).first().waitFor();
const signIn = async () => {
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
};
try {
  await page.goto('http://localhost:8090');
  await signIn();
  await page.getByRole('button', { name: 'Open course CLI verification course', exact: true }).press('Enter');
  await waitText('Add learning materials');
  await waitText('cli-notes.txt');
  const upload = page.locator('vaadin-upload input[type=file]');
  await upload.setInputFiles([
    { name: 'gui-notes.txt', mimeType: 'text/plain', buffer: Buffer.from('GUI lecture notes about Strategy and Observer patterns.') },
    { name: 'stage2-report.pdf', mimeType: 'application/pdf', buffer: await readFile(new URL('../documentation/reports/stage2-report.pdf', import.meta.url)) },
  ]);
  await page.locator('.material-row').filter({ hasText: 'gui-notes.txt' }).waitFor();
  await page.locator('.material-row').filter({ hasText: 'stage2-report.pdf' }).waitFor();
  assert.equal(await page.locator('.material-row').count(), 3);
  await page.locator('.material-row').filter({ hasText: 'gui-notes.txt' }).getByRole('button', { name: 'View text' }).click();
  await waitText('GUI lecture notes about Strategy and Observer patterns.');
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden' });
  for (const [name, expected] of [
    ['gui-notes.txt', Buffer.from('GUI lecture notes about Strategy and Observer patterns.')],
    ['stage2-report.pdf', await readFile(new URL('../documentation/reports/stage2-report.pdf', import.meta.url))],
  ]) {
    const ready = page.waitForEvent('download');
    await page.locator('.material-row').filter({ hasText: name }).getByRole('link', { name: 'Download original' }).click();
    const download = await ready;
    assert.equal(download.suggestedFilename(), name);
    assert.equal(await download.failure(), null);
    assert.deepEqual(await readFile(await download.path()), expected);
  }
  await page.getByRole('button', { name: 'Clear upload list' }).click();
  await upload.setInputFiles({ name: 'broken.pdf', mimeType: 'application/pdf', buffer: Buffer.from('not a real PDF') });
  await waitText('corrupted, encrypted, or unreadable');
  assert.equal(await page.locator('.material-row').count(), 3);
  await page.getByRole('button', { name: 'Clear upload list' }).click();
  await upload.setInputFiles({ name: 'unsupported.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('binary') });
  await waitText('Use PDF, DOCX, or UTF-8 TXT');
  await page.getByRole('button', { name: 'Clear upload list' }).click();
  await upload.setInputFiles({ name: 'too-large.txt', mimeType: 'text/plain', buffer: Buffer.alloc(10 * 1024 * 1024 + 1, 65) });
  await waitText('Use PDF, DOCX, or UTF-8 TXT');
  await page.getByRole('button', { name: 'Clear upload list' }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.screenshot({ path: fileURLToPath(new URL('../frontend/target/f02-mobile.png', import.meta.url)), fullPage: true });
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.screenshot({ path: fileURLToPath(new URL('../frontend/target/f02-materials.png', import.meta.url)), fullPage: true });
  await page.getByRole('button', { name: 'Back to courses' }).click();
  await page.getByRole('button', { name: 'Open course GUI verification course', exact: true }).click();
  await waitText('Your learning materials go here');
  assert.equal(await page.locator('.material-row').count(), 0);
  await page.reload();
  await signIn();
  await page.getByRole('button', { name: 'Open course CLI verification course', exact: true }).click();
  await page.locator('.material-row').filter({ hasText: 'gui-notes.txt' }).waitFor();
  assert.equal(await page.locator('.material-row').count(), 3);
  assert.deepEqual(errors, []);
  console.log('PASS: F02 uploads, text preview, original TXT/PDF browser downloads (exact names/bytes), validation, isolation, reload, keyboard navigation, and mobile layout.');
} catch (error) {
  console.log(error.stack);
  await page.screenshot({ path: fileURLToPath(new URL('../frontend/target/f02-failure.png', import.meta.url)), fullPage: true });
  process.exitCode = 1;
} finally {
  await browser.close();
}
