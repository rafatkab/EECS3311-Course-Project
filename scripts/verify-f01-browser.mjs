// Install playwright-core under frontend/target/browser-tools before running.
// Invoked by verify-f01.ps1 against isolated verification servers and credentials.
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({
  executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  headless: true,
});
const page = await browser.newPage();
const errors = [];
page.on('pageerror', error => errors.push(error.message));
const origin = process.env.QA_FRONTEND_URL || 'http://localhost:8090';
const login = async (password = process.env.STUDY_PASSWORD) => {
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(password);
  await page.locator('#sign-in').click();
};
const waitText = async text => page.getByText(text, { exact: false }).first().waitFor();
try {
  await page.goto(origin);
  if (process.argv.includes('--offline')) {
    await login();
    await waitText('Cannot reach the backend');
    console.log('PASS: backend-unavailable error shown without a fake dashboard.');
  } else {
    await login('deliberately-wrong');
    await waitText('Username or password is incorrect');
    await login();
    await waitText('My courses');
    await waitText('CLI verification course');
    await page.locator('#create-course').click();
    await page.getByRole('button', { name: 'Cancel', exact: true }).click();
    await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden' });
    await page.locator('#create-course').click();
    await page.locator('#save-course').click();
    await waitText('Check the highlighted fields');
    await page.locator('#course-name input').fill('  GUI verification course  ');
    await page.locator('#course-description textarea').fill('Software design and testing');
    // Use the component's public value API and native value-change event. Dates
    // are tested through the same browser/server binding as typed date input.
    const setDate = async (id, value) => {
      await page.locator(id).evaluate((field, date) => {
        field.value = date;
        field.dispatchEvent(new CustomEvent('change', { bubbles: true }));
      }, value);
    };
    await setDate('#course-start', '2026-09-01');
    await setDate('#course-end', '2026-08-01');
    await page.locator('#save-course').click();
    await waitText('Check the highlighted fields');
    await setDate('#course-end', '2026-12-20');
    await page.locator('#save-course').click();
    await waitText('GUI verification course');
    await page.getByRole('button', { name: 'Refresh', exact: true }).click();
    await waitText('GUI verification course');
    assert.equal(await page.locator('.course-card').count(), 2);
    await page.screenshot({ path: fileURLToPath(new URL('../frontend/target/f01-dashboard.png', import.meta.url)), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.screenshot({ path: fileURLToPath(new URL('../frontend/target/f01-mobile.png', import.meta.url)), fullPage: true });
    await page.reload();
    await login();
    await waitText('GUI verification course');
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await page.locator('#sign-in').waitFor();
    console.log('PASS: real GUI login, failed login, cancel, validation, create, refresh, reload, sign-out, and mobile layout.');
  }
  assert.deepEqual(errors, [], 'Unexpected browser errors');
} catch (error) {
  console.log(error.stack);
  await page.screenshot({ path: fileURLToPath(new URL('../frontend/target/f01-failure.png', import.meta.url)), fullPage: true });
  process.exitCode = 1;
} finally {
  await browser.close();
}
