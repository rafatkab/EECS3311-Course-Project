// Scripted provider exercises fractional marks, failures, caching, and the real UI/API.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const {chromium}=require('../frontend/target/browser-tools/node_modules/playwright-core');
const browser=await chromium.launch({executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',headless:true});
const page=await browser.newPage();page.setDefaultTimeout(30_000);
const headers={Authorization:'Basic '+Buffer.from(process.env.STUDY_USERNAME+':'+process.env.STUDY_PASSWORD).toString('base64')};
const courses=await(await fetch('http://localhost:8091/api/courses',{headers})).json();
const course=courses.find(c=>c.name==='CLI verification course');
const base=`http://localhost:8091/api/courses/${course.courseId}/quizzes`;
const quizzes=await(await fetch(base,{headers})).json();const quiz=quizzes.find(q=>q.questionCount===4);
const attempts=await(await fetch(`${base}/${quiz.resourceId}/attempts`,{headers})).json();
const previous=await(await fetch(`${base}/${quiz.resourceId}/attempts/${attempts.find(a=>a.submittedAt).attemptId}`,{headers})).json();
const keys=new Map(previous.answerKey.map(q=>[q.questionId,q.expectedAnswer]));
const mode=async value=>fetch('http://127.0.0.1:8092/mode',{method:'POST',body:JSON.stringify({mode:value})});
const idle=()=>page.waitForFunction(()=>!document.querySelector('#quizzes').inert);
const login=async()=>{
  await page.goto('http://localhost:8090');await page.locator('#username input').fill(process.env.STUDY_USERNAME);await page.locator('#password input').fill(process.env.STUDY_PASSWORD);
  await page.locator('#sign-in').click();await page.getByRole('button',{name:'Open course CLI verification course',exact:true}).click();await page.getByText('Quizzes',{exact:true}).click();await idle();
  await page.locator('#quiz-saved').click();await page.getByRole('option',{name:/4 questions/}).click();
};
try {
  await mode('valid');await login();await page.locator('#quiz-start').click();await page.getByText('Quiz attempt',{exact:true}).waitFor();await idle();
  const attemptId=await page.locator('[data-attempt-id]').getAttribute('data-attempt-id');
  const stateUrl=`${base}/${quiz.resourceId}/attempts/${attemptId}`;
  const read=async()=> (await fetch(stateUrl,{headers})).json();
  let shortIndex=0,choiceIndex=0;
  for(const row of await page.locator('.quiz-question').all()) {
    const id=await row.getAttribute('data-question-id');
    if(await row.locator('textarea').count()) await row.locator('textarea').fill(shortIndex++===0?'partial answer':'wrong answer');
    else { const buttons=row.locator('vaadin-radio-button');
      for(const button of await buttons.all()) { const label=await button.innerText();if(choiceIndex===0?label.trim()===keys.get(id):label.trim()!==keys.get(id)){await button.click();break;} }
      choiceIndex++;
    }
  }
  await page.locator('#quiz-save').click();
  for(let i=0;i<100&&(await read()).attempt.version!==1;i++) await new Promise(r=>setTimeout(r,100));await idle();
  await mode('grading-invalid');await page.locator('#quiz-finish').click();
  await page.locator('#quizzes .error-message').filter({hasText:'valid grading feedback'}).waitFor();await idle();
  assert.equal((await read()).attempt.submittedAt,null);assert.equal((await read()).attempt.version,1);assert.equal((await read()).gradeResult,null);
  assert.equal(await page.locator('#quizzes textarea').first().inputValue(),'partial answer');
  await mode('valid');await page.locator('#quiz-finish').click();await page.locator('#quiz-grade').waitFor();await idle();
  const state=await read();assert.equal(state.gradeResult.earnedPoints,1.5);assert.equal(state.gradeResult.percentage,37.5);assert.ok(state.gradeResult.weakAreas.length);
  assert.equal(await page.locator('.question-grade').count(),4);
  const gradeId=state.gradeResult.gradeId;
  await mode('unavailable');const before=(await(await fetch('http://127.0.0.1:8092/stats')).json()).calls;
  await page.reload();await login();await page.locator('#quiz-attempts').click();await page.getByRole('option',{name:/Finished/}).first().click();
  await page.locator(`#quiz-grade[data-grade-id="${gradeId}"]`).waitFor();await idle();
  assert.equal((await(await fetch('http://127.0.0.1:8092/stats')).json()).calls,before);
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.screenshot({path:'frontend/target/f09-grades.png',fullPage:true});
  console.log('PASS: F09 weighted results, per-question feedback, weak areas, failed grading preserves drafts, retry, cached/offline reopening, and mobile layout.');
} catch(error){console.log(error.stack);process.exitCode=1;}
finally{await mode('valid');await browser.close();}
