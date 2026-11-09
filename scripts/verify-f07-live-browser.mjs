// Uses a real saved Ollama-generated deck, never a scripted provider.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({ executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true });
const page = await browser.newPage(); page.setDefaultTimeout(30_000);
try {
  await page.goto('http://localhost:8090');
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
  await page.getByRole('button', { name: 'Open course Live flashcard verification', exact: true }).click();
  await page.getByText('Flashcards', { exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('#flashcards > div').inert);
  await page.locator('#flashcard-saved').click(); await page.getByRole('option').first().click();
  await page.locator('#flashcard-question').waitFor();
  assert.equal(await page.locator('#flashcard-answer').isVisible(), false);
  await page.locator('#flashcard-question').click(); await page.locator('#flashcard-answer').waitFor();
  assert.ok((await page.locator('#flashcard-answer').innerText()).length > 10);
  if (await page.locator('#flashcard-next').isEnabled()) {
    await page.locator('#flashcard-next').click();
    await page.getByText(/^Card 2 of/).waitFor();
    assert.equal(await page.locator('#flashcard-answer').isVisible(), false);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  console.log('PASS: real saved Ollama flashcards, answer reveal, navigation, and mobile layout.');
} catch (error) { console.log(error.stack); process.exitCode = 1; }
finally { await browser.close(); }
