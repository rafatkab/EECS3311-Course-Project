// Real Ollama grades a controlled four-question biology attempt; no scripted score provider.
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const {chromium}=require('../frontend/target/browser-tools/node_modules/playwright-core');
const headers={Authorization:'Basic '+Buffer.from(process.env.STUDY_USERNAME+':'+process.env.STUDY_PASSWORD).toString('base64')};
const courses=await(await fetch('http://localhost:8091/api/courses',{headers})).json();const course=courses.find(c=>c.name==='Live grading verification');
const base=`http://localhost:8091/api/courses/${course.courseId}/quizzes`;
const id=(await(await fetch(base,{headers})).json())[0].resourceId;
const quiz=await(await fetch(`${base}/${id}`,{headers})).json();
const source=await readFile(new URL('../documentation/samples/f04-biology.txt',import.meta.url),'utf8');
const facts=source.split(/(?<=[.!?])\s+|\r?\n\s*\r?\n/).map(s=>s.replace(/\s+/g,' ').trim()).filter(Boolean);
const keys=new Map();
for(const q of quiz.questions){
  const sentence=q.question.slice(q.question.indexOf('\n')+1);const [prefix,suffix]=sentence.split('____');
  const candidates=facts.filter(f=>f.startsWith(prefix)&&f.endsWith(suffix)).map(f=>f.slice(prefix.length,suffix.length?f.length-suffix.length:f.length)).filter(a=>a.length>=2&&a.length<=120);
  assert.equal(candidates.length,1);keys.set(q.questionId,candidates[0]);
}
const browser=await chromium.launch({executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',headless:true});
const page=await browser.newPage();page.setDefaultTimeout(200_000);
const idle=()=>page.waitForFunction(()=>!document.querySelector('#quizzes').inert);
try{
  await page.goto('http://localhost:8090');await page.locator('#username input').fill(process.env.STUDY_USERNAME);await page.locator('#password input').fill(process.env.STUDY_PASSWORD);await page.locator('#sign-in').click();
  await page.getByRole('button',{name:'Open course Live grading verification',exact:true}).click();await page.getByText('Quizzes',{exact:true}).click();await idle();
  await page.locator('#quiz-saved').click();await page.getByRole('option',{name:/4 questions/}).click();await page.locator('#quiz-start').click();await page.getByText('Quiz attempt',{exact:true}).waitFor();await idle();
  const attempt=await page.locator('[data-attempt-id]').getAttribute('data-attempt-id');
  let short=0,choice=0;
  for(const row of await page.locator('.quiz-question').all()){
    const key=keys.get(await row.getAttribute('data-question-id'));
    if(await row.locator('textarea').count()){
      const equivalent='The missing phrase is '+key;
      await row.locator('textarea').fill(short++===0?equivalent:'a rubber duck');
    }else{
      for(const button of await row.locator('vaadin-radio-button').all()){const label=(await button.innerText()).trim();if(choice===0?label===key:label!==key){await button.click();break;}}
      choice++;
    }
  }
  await page.locator('#quiz-finish').click();await page.locator('#quiz-grade').waitFor();await idle();
  const state=await(await fetch(`${base}/${id}/attempts/${attempt}`,{headers})).json();
  assert.equal(state.gradeResult.earnedPoints,2);assert.equal(state.gradeResult.totalPoints,4);assert.equal(state.gradeResult.percentage,50);
  assert.deepEqual(state.gradeResult.questions.map(q=>q.earnedPoints),[1,1,0,0]);
  assert.ok(state.gradeResult.weakAreas.length>0);assert.ok(state.gradeResult.modelCalls>=2);
  assert.equal(state.gradeResult.questions.filter(q=>q.method==='DETERMINISTIC').length,2);
  assert.equal(state.gradeResult.questions.filter(q=>q.method==='AI').length,2);
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.screenshot({path:'frontend/target/f09-live-grades.png',fullPage:true});
  console.log('PASS: real mixed grading produced 2/4, per-question feedback and weak areas, with two AI-graded and two objective answers.');
}catch(error){console.log(error.stack);process.exitCode=1;}
finally{await browser.close();}
