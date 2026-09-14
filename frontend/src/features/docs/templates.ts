export function normalizeBaseUrl(baseUrl: string): string {
  return baseUrl.replace(/\/+$/, '');
}

function shellSingleQuote(value: string): string {
  return `'${value.replace(/'/g, `'\\''`)}'`;
}

export function buildCurlNonStreaming(baseUrl: string, model: string): string {
  const base = normalizeBaseUrl(baseUrl);
  return [
    `curl -sS --no-buffer ${base}/chat/completions \\`,
    `  -H "Authorization: Bearer $LANG_API_KEY" \\`,
    `  -H "Content-Type: application/json" \\`,
    `  -d ${shellSingleQuote(JSON.stringify({ model, stream: false, messages: [{ role: 'user', content: 'Hello' }] }))}`,
  ].join('\n');
}

export function buildCurlStreaming(baseUrl: string, model: string): string {
  const base = normalizeBaseUrl(baseUrl);
  return [
    `curl -sS -N --no-buffer ${base}/chat/completions \\`,
    `  -H "Authorization: Bearer $LANG_API_KEY" \\`,
    `  -H "Content-Type: application/json" \\`,
    `  -H "Accept: text/event-stream" \\`,
    `  -d ${shellSingleQuote(JSON.stringify({ model, stream: true, messages: [{ role: 'user', content: 'Hello' }] }))}`,
  ].join('\n');
}

export function buildSdkNonStreaming(baseUrl: string, model: string): string {
  const base = normalizeBaseUrl(baseUrl);
  return [
    `import OpenAI from 'openai';`,
    ``,
    `const client = new OpenAI({ baseURL: ${JSON.stringify(base)}, apiKey: process.env.LANG_API_KEY });`,
    ``,
    `const res = await client.chat.completions.create({`,
    `  model: ${JSON.stringify(model)},`,
    `  stream: false,`,
    `  messages: [{ role: 'user', content: 'Hello' }],`,
    `});`,
    `console.log(res.choices[0]?.message?.content);`,
  ].join('\n');
}

export function buildSdkStreaming(baseUrl: string, model: string): string {
  const base = normalizeBaseUrl(baseUrl);
  return [
    `import OpenAI from 'openai';`,
    ``,
    `const client = new OpenAI({ baseURL: ${JSON.stringify(base)}, apiKey: process.env.LANG_API_KEY });`,
    ``,
    `const stream = await client.chat.completions.create({`,
    `  model: ${JSON.stringify(model)},`,
    `  stream: true,`,
    `  messages: [{ role: 'user', content: 'Hello' }],`,
    `});`,
    `for await (const chunk of stream) {`,
    `  process.stdout.write(chunk.choices[0]?.delta?.content ?? '');`,
    `}`,
  ].join('\n');
}
