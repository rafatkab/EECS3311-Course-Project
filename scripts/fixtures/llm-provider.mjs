// TEST FIXTURE ONLY. A scripted protocol server, not a model or application fallback.
// It must never be used as evidence of live model quality or real AI behavior.
import http from 'node:http';
let calls = 0;
let mode = 'valid';
const server = http.createServer(async (request, response) => {
  if (request.url === '/health' || request.url === '/stats') {
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ fixture: true, calls, mode }));
    return;
  }
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString('utf8');
  if (request.url === '/mode' && request.method === 'POST') {
    mode = JSON.parse(raw).mode;
    response.end('{}');
    return;
  }
  if (request.url !== '/v1/chat/completions' || request.method !== 'POST') {
    response.writeHead(404).end(); return;
  }
  calls++;
  if (mode === 'unavailable') { response.writeHead(503).end('{}'); return; }
  try {
    const body = JSON.parse(raw);
    if (body.model !== 'fixture-model') { response.writeHead(400).end('{}'); return; }
    // A delay makes the real asynchronous UI progress state observable.
    await new Promise(resolve => setTimeout(resolve, 250));
    const conceptRequest = body.response_format?.json_schema?.schema?.properties?.concepts;
    const answerRequest = body.response_format?.json_schema?.schema?.properties?.answer;
    const planningRequest = body.response_format?.json_schema?.schema?.properties?.topics;
    const flashcardRequest = body.response_format?.json_schema?.schema?.properties?.cards;
    const input = JSON.parse(body.messages[1].content);
    const sourceExcerpt = (input.evidenceOptions || input.sources)[0].quote;
    const validContent = flashcardRequest ? { cards: Array.from({ length: input.batchLimit }, (_, i) => ({
      question: `What does repository example ${input.alreadyAskedQuestions.length + i + 1} separate?`,
      answer: input.answerOptions[i], evidence: [input.sources.find(s => s.quote.replace(/\s+/g, ' ').trim().includes(input.answerOptions[i])).id],
    })) } : planningRequest ? { topics: [{ topic: 'Repository pattern', priority: 1, activity: 'PRACTICE',
      instructions: 'Protocol test fixture: compare repository interfaces with direct SQL storage in the provided notes.', evidence: [1] }] } : answerRequest ? { answer: 'Protocol test fixture: repository interfaces separate storage from application behavior.',
      answerable: mode !== 'unsupported', evidence: mode === 'unsupported' ? [] : [1] } : conceptRequest ? { concepts: [{ name: sourceExcerpt.split(/\s+/).slice(0, 3).join(' '),
      description: 'Protocol test fixture: repository interfaces separate SQL storage from application behavior.', evidence: [1] }] } : {
      summary: 'Protocol test fixture: repository interfaces keep SQL storage separate from application behavior.',
      evidence: [1],
    };
    if (answerRequest && mode === 'long-answer') validContent.answer =
      ('Seam carving resizes an image by finding low-energy connected paths through its pixels and removing those paths while preserving important content. '.repeat(10))
      + '\n\nA long reference: https://example.test/' + 'source-reference-'.repeat(18)
      + '\n\nThis is the final sentence of the complete saved answer.';
    const content = mode === 'invalid' || mode === 'partial' && flashcardRequest && input.alreadyAskedQuestions.length > 0 ? 'invalid JSON' : JSON.stringify(validContent);
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ choices: [{ finish_reason: 'stop', message: { content } }], usage: { prompt_tokens: 30, completion_tokens: 12 } }));
  } catch {
    response.writeHead(400).end('{}');
  }
});
server.listen(8092, '127.0.0.1', () => console.log('F03 scripted TEST FIXTURE on 127.0.0.1:8092; no live model.'));
