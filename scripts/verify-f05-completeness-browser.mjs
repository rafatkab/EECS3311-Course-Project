// Real Ollama + real assignment PDF. Uses isolated test credentials and data.
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
  await page.getByRole('button', { name: 'Open course Live assignment answer verification', exact: true }).click();
  await page.waitForFunction(() => document.querySelectorAll('.chat-turn').length === 1 && !document.querySelector('#chat-send').disabled);
  await page.locator('#chat-question textarea').fill('how do i do stage 2 of the assignment');
  await page.locator('#chat-send').click();
  await Promise.race([
    page.waitForFunction(() => document.querySelectorAll('.chat-turn').length === 2),
    page.locator('#course-chat .error-message').filter({ hasText: /.+/ }).waitFor().then(() => { throw new Error('Assignment question failed.'); }),
  ]);
  const text = (await page.locator('.chat-turn').last().locator('pre').innerText()).trim();
  assert.ok(text.length >= 100);
  assert.ok(!text.endsWith(':'));
  assert.match(text, /implement|report|design|feature|commit|video/i);
  await page.locator('.chat-turn').last().locator('vaadin-details').first().waitFor();
  await page.screenshot({ path: 'frontend/target/f05-complete-assignment.png', fullPage: true });
  console.log('PASS: browser displays substantive Stage 2 guidance beyond the introductory sentence.');
} catch (error) {
  console.log(error.stack); await page.screenshot({ path: 'frontend/target/f05-completeness-failure.png', fullPage: true }); process.exitCode = 1;
} finally { await browser.close(); }
