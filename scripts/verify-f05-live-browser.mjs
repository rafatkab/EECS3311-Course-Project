// Real Ollama verification, independent from scripted protocol fixtures.
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
  await page.getByRole('button', { name: 'Open course Live course chat verification', exact: true }).click();
  await page.waitForFunction(() => document.querySelectorAll('.chat-turn').length === 3 && !document.querySelector('#chat-send').disabled);
  await page.locator('#chat-question textarea').fill('Tell me about this course');
  await page.locator('#chat-send').click();
  await Promise.race([
    page.waitForFunction(() => document.querySelectorAll('.chat-turn').length === 4),
    page.locator('#course-chat .error-message').filter({ hasText: /.+/ }).waitFor().then(() => { throw new Error('Live chat returned an error.'); }),
  ]);
  const answer = page.locator('.chat-turn').last();
  assert.ok((await answer.locator('pre').innerText()).length >= 20);
  assert.ok(await answer.locator('vaadin-details').count());
  await answer.locator('vaadin-details').first().click();
  await page.locator('#chat-question textarea').fill('pls say something bruh');
  await page.locator('#chat-send').click();
  await Promise.race([
    page.waitForFunction(() => document.querySelectorAll('.chat-turn').length === 5),
    page.locator('#course-chat .error-message').filter({ hasText: /.+/ }).waitFor().then(() => { throw new Error('Casual overview request returned an error.'); }),
  ]);
  assert.ok(await page.locator('.chat-turn').last().locator('vaadin-details').count());
  await page.screenshot({ path: 'frontend/target/f05-live-chat.png', fullPage: true });
  console.log('PASS: real Ollama browser answered course overview and casual retry after an unrelated-question refusal, with source quotations.');
} catch (error) {
  console.log(error.stack); await page.screenshot({ path: 'frontend/target/f05-live-failure.png', fullPage: true }); process.exitCode = 1;
} finally { await browser.close(); }
