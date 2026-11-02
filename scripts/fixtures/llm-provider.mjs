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
    const sourceExcerpt = JSON.parse(body.messages[1].content).evidenceOptions[0].quote;
    const validContent = conceptRequest ? { concepts: [{ name: sourceExcerpt.split(/\s+/).slice(0, 3).join(' '),
      description: 'Protocol test fixture: repository interfaces separate SQL storage from application behavior.', evidence: [1] }] } : {
      summary: 'Protocol test fixture: repository interfaces keep SQL storage separate from application behavior.',
      evidence: [1],
    };
    const content = mode === 'invalid' ? 'invalid JSON' : JSON.stringify(validContent);
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ choices: [{ finish_reason: 'stop', message: { content } }], usage: { prompt_tokens: 30, completion_tokens: 12 } }));
  } catch {
    response.writeHead(400).end('{}');
  }
});
server.listen(8092, '127.0.0.1', () => console.log('F03 scripted TEST FIXTURE on 127.0.0.1:8092; no live model.'));
