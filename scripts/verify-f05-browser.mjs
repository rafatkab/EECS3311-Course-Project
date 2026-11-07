// Protocol and UI checks only; the scripted provider is not an application fallback.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', headless: true });
const page = await browser.newPage(); page.setDefaultTimeout(30_000);
const mode = async value => fetch('http://127.0.0.1:8092/mode', { method: 'POST', body: JSON.stringify({ mode: value }) });
try {
  await mode('valid'); await page.goto('http://localhost:8090');
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
  await page.getByRole('button', { name: 'Open course CLI verification course', exact: true }).click();
  await page.locator('#chat-send').waitFor({ state: 'visible' });
  await page.waitForFunction(() => !document.querySelector('#chat-send').disabled);
  await page.locator('.chat-turn').first().waitFor();
  const count = await page.locator('.chat-turn').count();
  await page.locator('#chat-question textarea').fill('How do repository interfaces help?');
  await page.locator('#chat-send').click();
  await page.getByText('Finding sources and answering…', { exact: true }).waitFor();
  await page.waitForFunction(expected => document.querySelectorAll('.chat-turn').length === expected, count + 1);
  const turn = page.locator('.chat-turn').last();
  assert.ok((await turn.innerText()).includes('Protocol test fixture'));
  await turn.locator('vaadin-details').first().click();
  await turn.getByRole('button', { name: 'View source text', exact: true }).first().click();
  await page.locator('vaadin-dialog-overlay pre').waitFor();
  assert.ok((await page.locator('vaadin-dialog-overlay pre').innerText()).length > 0);
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  await mode('long-answer');
  await page.locator('#chat-question textarea').fill('Explain Repository patterns in detail.');
  await page.locator('#chat-send').click();
  await page.waitForFunction(expected => document.querySelectorAll('.chat-turn').length === expected, count + 2);
  const longAnswer = page.locator('.chat-turn').last().locator('pre');
  const longTurnId = await page.locator('.chat-turn').last().getAttribute('data-turn-id');
  const headers = { Authorization: 'Basic ' + Buffer.from(process.env.STUDY_USERNAME + ':' + process.env.STUDY_PASSWORD).toString('base64') };
  const courses = await (await fetch('http://localhost:8091/api/courses', { headers })).json();
  const courseId = courses.find(course => course.name === 'CLI verification course').courseId;
  const history = await (await fetch(`http://localhost:8091/api/courses/${courseId}/chat`, { headers })).json();
  const savedAnswer = history.find(turn => turn.turnId === longTurnId).answer;
  // Reproduce a missing/stale custom text rule: wrapping must remain safe without this class.
  await longAnswer.evaluate(element => element.classList.remove('material-text'));
  for (const width of [1920, 1280, 390]) {
    await page.setViewportSize({ width, height: 844 });
    assert.equal(await longAnswer.innerText(), savedAnswer);
    const layout = await longAnswer.evaluate(element => {
      const bounds = element.getBoundingClientRect();
      const end = document.createRange();
      end.selectNodeContents(element); end.setStart(element.firstChild, element.textContent.length - 54);
      const tail = end.getBoundingClientRect();
      return { whitespace: getComputedStyle(element).whiteSpace, width: element.clientWidth, scrollWidth: element.scrollWidth,
        height: element.clientHeight, scrollHeight: element.scrollHeight, right: bounds.right, tailRight: tail.right, tailBottom: tail.bottom, bottom: bounds.bottom,
        pageWidth: document.documentElement.scrollWidth, viewport: innerWidth };
    });
    assert.equal(layout.whitespace, 'pre-wrap', `answer wrapping at ${width}px`);
    assert.ok(layout.scrollWidth <= layout.width + 1, `answer overflows horizontally at ${width}px`);
    assert.ok(layout.scrollHeight <= layout.height + 1, `answer is vertically clipped at ${width}px`);
    assert.ok(layout.right <= width + 1 && layout.tailRight <= layout.right + 1 && layout.tailBottom <= layout.bottom + 1);
    assert.ok(layout.pageWidth <= layout.viewport, `page overflows horizontally at ${width}px`);
  }
  await longAnswer.evaluate(element => element.classList.add('material-text'));
  await page.setViewportSize({ width: 1280, height: 844 });
  console.log('PASS: full saved long answer and final sentence remain visible at desktop/mobile widths, even without the custom text class.');
  await mode('unsupported');
  await page.locator('#chat-question textarea').fill('What is the weather on Mars?');
  await page.locator('#chat-send').click();
  await page.getByText('The available course excerpts do not contain enough information to answer this question.', { exact: true }).waitFor();
  const saved = await page.locator('.chat-turn').count();
  for (const failure of ['unavailable', 'invalid']) {
    await mode(failure);
    await page.locator('#chat-question textarea').fill('Explain Repository patterns again.');
    await page.locator('#chat-send').click();
    await page.locator('#course-chat .error-message').filter({ hasText: failure === 'invalid' ? 'invalid course answer' : 'AI provider rejected' }).waitFor();
    assert.equal(await page.locator('.chat-turn').count(), saved);
    assert.equal(await page.locator('#chat-question textarea').inputValue(), 'Explain Repository patterns again.');
  }
  await mode('valid'); await page.reload();
  await page.locator('#username input').fill(process.env.STUDY_USERNAME);
  await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();
  await page.getByRole('button', { name: 'Open course CLI verification course', exact: true }).click();
  await page.waitForFunction(expected => document.querySelectorAll('.chat-turn').length === expected, saved);
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.screenshot({ path: 'frontend/target/f05-chat-mobile.png', fullPage: true });
  console.log('PASS: F05 GUI questions, quotations/source viewing, unsupported answers, preserved input/history on failure, reload, and mobile layout.');
} catch (error) {
  console.log(error.stack); await page.screenshot({ path: 'frontend/target/f05-failure.png', fullPage: true }); process.exitCode = 1;
} finally { await mode('valid'); await browser.close(); }
