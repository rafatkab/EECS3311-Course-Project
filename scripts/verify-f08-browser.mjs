// Scripted provider verifies integration, not model quality or distractor correctness.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({ executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true });
const page = await browser.newPage(); page.setDefaultTimeout(30_000);
const headers = { Authorization: 'Basic ' + Buffer.from(process.env.STUDY_USERNAME + ':' + process.env.STUDY_PASSWORD).toString('base64') };
const courses = await (await fetch('http://localhost:8091/api/courses', { headers })).json();
const course = courses.find(c => c.name === 'CLI verification course');
const path = `http://localhost:8091/api/courses/${course.courseId}/quizzes`;
const read = async () => (await fetch(path, { headers })).json();
const mode = async value => fetch('http://127.0.0.1:8092/mode', { method: 'POST', body: JSON.stringify({ mode: value }) });
const idle = () => page.waitForFunction(() => !document.querySelector('#quizzes').inert);
const login = async () => {
  await page.goto('http://localhost:8090');
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
  await page.getByRole('button', { name: 'Open course CLI verification course', exact: true }).click();
  await page.getByText('Quizzes', { exact: true }).click(); await idle();
};
const openSaved = async count => {
  await page.locator('#quiz-saved').click(); await page.getByRole('option', { name: new RegExp(`${count} questions`) }).click(); await idle();
};
try {
  await login(); await openSaved(4);
  await page.locator('#quiz-start').click(); await page.getByText('Quiz attempt', { exact: true }).waitFor(); await idle();
  let attemptId = await page.locator('[data-attempt-id]').getAttribute('data-attempt-id');
  const originalQuiz = (await read())[0].resourceId;
  const stateUrl = `${path}/${originalQuiz}/attempts/${attemptId}`;
  const state = async () => (await fetch(stateUrl, { headers })).json();
  assert.deepEqual((await state()).answerKey, []);
  await page.locator('#quizzes textarea').first().fill('GUI draft answer');
  await page.locator('#quiz-save').click(); await idle();
  for (let i=0;i<100 && (await state()).attempt.version!==1;i++) await new Promise(resolve => setTimeout(resolve,100));
  await idle();
  assert.equal((await state()).attempt.version, 1);
  await page.reload(); await login(); await openSaved(4);
  await page.locator('#quiz-attempts').click(); await page.getByRole('option', { name: /Draft/ }).first().click();
  await page.getByText('Quiz attempt', { exact: true }).waitFor(); await idle();
  assert.equal(await page.locator('#quizzes textarea').first().inputValue(), 'GUI draft answer');
  await page.locator('#quiz-finish').click();
  await page.locator('#quizzes .error-message').filter({ hasText: 'Answer every question' }).waitFor(); await idle();
  for (const row of await page.locator('.quiz-question').all()) {
    if (await row.locator('textarea').count()) await row.locator('textarea').fill('GUI complete answer');
    else await row.locator('vaadin-radio-button').first().click();
  }
  await page.locator('#quiz-finish').click(); await page.getByText('Finished quiz — answer review', { exact: true }).waitFor(); await idle();
  assert.equal((await state()).answerKey.length, 4); assert.ok((await state()).attempt.submittedAt);
  assert.equal(await page.getByText(/^Expected answer:/).count(), 4);
  await page.locator('#quiz-count input').fill('3'); await page.locator('#quiz-count input').press('Tab');
  await page.locator('#quiz-topics input').fill('course');
  await page.locator('#quiz-difficulty').click(); await page.getByRole('option', { name: 'HARD', exact: true }).click();
  await page.locator('#quiz-generate').click(); await page.getByText('Generating and validating quiz questions…', { exact: true }).waitFor();
  await page.waitForFunction(() => !document.querySelector('#quizzes').inert); await page.locator('#quiz-start').waitFor();
  assert.equal((await read()).length, 2); assert.equal((await read())[0].questionCount, 3); assert.equal((await read())[0].difficulty, 'HARD');
  await mode('invalid'); await page.locator('#quiz-generate').click();
  await page.locator('#quizzes .error-message').filter({ hasText: 'valid quiz questions' }).waitFor(); await idle();
  assert.equal((await read()).length, 2); assert.equal(await page.locator('#quiz-topics input').inputValue(), 'course');
  await openSaved(4); await page.locator('#quiz-attempts').click(); await page.getByRole('option', { name: /Finished/ }).first().click();
  await page.getByText('Finished quiz — answer review', { exact: true }).waitFor(); await idle();
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.screenshot({ path: 'frontend/target/f08-quiz.png', fullPage: true });
  console.log('PASS: F08 mixed quiz, draft save/resume, complete-answer validation, finish/review, generation controls, preserved history on errors, and mobile layout.');
} catch (error) { console.log(error.stack); process.exitCode = 1; }
finally { await mode('valid'); await browser.close(); }
