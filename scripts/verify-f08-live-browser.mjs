// Real Ollama quiz; source-backed keys are checked by the companion PowerShell script.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({ executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true });
const page = await browser.newPage(); page.setDefaultTimeout(30_000);
const headers = { Authorization: 'Basic ' + Buffer.from(process.env.STUDY_USERNAME + ':' + process.env.STUDY_PASSWORD).toString('base64') };
const courses = await (await fetch('http://localhost:8091/api/courses', { headers })).json();
const course = courses.find(c => c.name === 'Live quiz verification');
const count = (await (await fetch(`http://localhost:8091/api/courses/${course.courseId}/quizzes`, { headers })).json())[0].questionCount;
const idle = () => page.waitForFunction(() => !document.querySelector('#quizzes').inert);
try {
  await page.goto('http://localhost:8090');
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
  await page.getByRole('button', { name: 'Open course Live quiz verification', exact: true }).click();
  await page.getByText('Quizzes', { exact: true }).click(); await idle();
  await page.locator('#quiz-saved').click(); await page.getByRole('option', { name: new RegExp(`${count} questions`) }).click();
  await page.locator('#quiz-start').click(); await page.getByText('Quiz attempt', { exact: true }).waitFor(); await idle();
  assert.equal(await page.locator('.quiz-question').count(), count);
  assert.equal(await page.getByText(/^Expected answer:/).count(), 0);
  for (const row of await page.locator('.quiz-question').all()) {
    if (await row.locator('textarea').count()) await row.locator('textarea').fill('My practice answer');
    else await row.locator('vaadin-radio-button').first().click();
  }
  await page.locator('#quiz-save').click();
  await page.waitForTimeout(500); await idle();
  await page.getByRole('button', { name: 'Back to saved quiz', exact: true }).click();
  await page.locator('#quiz-attempts').click(); await page.getByRole('option', { name: /Draft/ }).first().click();
  await page.getByText('Quiz attempt', { exact: true }).waitFor(); await idle();
  assert.equal(await page.locator('#quizzes textarea').first().inputValue(), 'My practice answer');
  await page.locator('#quiz-finish').click(); await page.getByText('Finished quiz — answer review', { exact: true }).waitFor(); await idle();
  assert.equal(await page.getByText(/^Expected answer:/).count(), count);
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.screenshot({ path: 'frontend/target/f08-live-quiz.png', fullPage: true });
  console.log('PASS: real Ollama quiz start, draft persistence, resume, finished answer review, and mobile layout.');
} catch (error) { console.log(error.stack); process.exitCode = 1; }
finally { await browser.close(); }
