// Scripted protocol provider verifies the GUI, scheduling, and persistence, not model quality.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true });
const page = await browser.newPage(); page.setDefaultTimeout(30_000);
const headers = { Authorization: 'Basic ' + Buffer.from(process.env.STUDY_USERNAME + ':' + process.env.STUDY_PASSWORD).toString('base64') };
const courses = await (await fetch('http://localhost:8091/api/courses', { headers })).json();
const course = courses.find(value => value.name === 'CLI verification course');
const read = async () => (await fetch(`http://localhost:8091/api/courses/${course.courseId}/planning`, { headers })).json();
const mode = async value => fetch('http://127.0.0.1:8092/mode', { method: 'POST', body: JSON.stringify({ mode: value }) });
const poll = async predicate => {
  for (let i = 0; i < 150; i++) { const value = await read(); if (predicate(value)) return value; await new Promise(resolve => setTimeout(resolve, 100)); }
  throw new Error('Planning state did not reach the expected value.');
};
const idle = () => page.waitForFunction(() => !document.querySelector('#planning-controls').inert);
const login = async () => {
  await page.goto('http://localhost:8090');
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
  await page.getByRole('button', { name: 'Open course CLI verification course', exact: true }).click();
  await page.getByText('Study plan and availability', { exact: true }).click();
  await page.locator('#study-planning').waitFor(); await idle();
};
try {
  await mode('valid'); await login();
  let state = await read(); const originalId = state.plan.resourceId;
  await page.getByText('Saved study plan', { exact: true }).click();
  await page.locator(`[data-plan-id="${originalId}"]`).waitFor();
  assert.equal(await page.locator('.study-session').count(), 4);
  await page.locator('#planning-weekly input').fill('15'); await page.locator('#planning-weekly input').press('Tab');
  await page.locator('#planning-save-profile').click();
  await poll(value => value.profile.availableStudyTime === 15); await idle();
  await page.locator('#planning-generate').click();
  await page.locator('#study-planning .error-message').filter({ hasText: 'Not enough available study time' }).waitFor();
  assert.equal((await read()).plan.resourceId, originalId);
  await page.locator('#planning-weekly input').fill('180'); await page.locator('#planning-weekly input').press('Tab');
  await page.locator('#planning-save-profile').click(); await poll(value => value.profile.availableStudyTime === 180); await idle();
  await page.locator('#planning-event-title input').fill('Work shift');
  await page.locator('#planning-event-date').evaluate((field, value) => { field.value = value; field.dispatchEvent(new CustomEvent('change', { bubbles: true })); }, state.plan.request.startDate);
  await page.getByRole('button', { name: 'Add calendar event', exact: true }).click();
  await poll(value => value.events.some(event => event.title === 'Work shift')); await idle();
  await page.locator('#planning-generate').click();
  await page.getByText('Planning topics and scheduling sessions…', { exact: true }).waitFor();
  state = await poll(value => value.plan.resourceId !== originalId); await idle();
  await page.locator(`[data-plan-id="${state.plan.resourceId}"]`).waitFor();
  const events = state.events.filter(event => event.type === 'STUDY' && event.courseId === course.courseId);
  assert.equal(events.length, 4);
  const busy = state.events.find(event => event.title === 'Work shift');
  for (const event of events) assert.ok(Date.parse(event.endTime) <= Date.parse(busy.startTime) || Date.parse(event.startTime) >= Date.parse(busy.endTime));
  const savedId = state.plan.resourceId;
  await mode('invalid'); await page.locator('#planning-generate').click();
  await page.locator('#study-planning .error-message').filter({ hasText: 'valid study recommendations' }).waitFor();
  assert.equal((await read()).plan.resourceId, savedId);
  assert.equal((await read()).events.filter(event => event.type === 'STUDY' && event.courseId === course.courseId).length, 4);
  await mode('valid');
  await page.locator('.planning-event').filter({ hasText: 'Work shift' }).getByRole('button', { name: 'Remove event', exact: true }).click();
  await poll(value => !value.events.some(event => event.title === 'Work shift')); await idle();
  await page.reload(); await login();
  await page.getByText('Saved study plan', { exact: true }).click();
  await page.locator(`[data-plan-id="${savedId}"]`).waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  for (const height of await page.locator('.study-session vaadin-details:not([opened])').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().height)))
    assert.ok(height < 100, 'Closed source quotations should not leave large blank gaps.');
  await page.screenshot({ path: 'frontend/target/f06-plan-mobile.png', fullPage: true });
  console.log('PASS: F06 GUI profile editing, insufficient-time errors, busy event add/remove, plan regeneration, calendar synchronization, failure preservation, reload, and mobile layout.');
} catch (error) {
  console.log(error.stack); await page.screenshot({ path: 'frontend/target/f06-failure.png', fullPage: true }); process.exitCode = 1;
} finally { await mode('valid'); await browser.close(); }
