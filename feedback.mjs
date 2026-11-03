/** Anonymous app feedback is forwarded only to an explicitly verified private GitHub repository. */
export function createFeedbackHandler({ repo, token, githubFetch = fetch, now = Date.now } = {}) {
  const enabled = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repo || '') && typeof token === 'string' && token.length >= 20 && !/[\r\n]/.test(token);
  const rates = new Map(); const receipts = new Map();
  let pending = false;
  const send = (res, status, body = {}) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(JSON.stringify(body)); };
  const handle = async (req, res) => {
    if (!enabled) { req.resume(); return send(res, 503); }
    if (req.method !== 'POST') { req.resume(); return send(res, 405); }
    if (!/^application\/json(?:;|$)/i.test(req.headers['content-type'] || '')) { req.resume(); return send(res, 415); }
    const address = req.socket.remoteAddress || '?'; // In memory only; never forwarded to GitHub or logged.
    const hour = Math.floor(now() / 3600000);
    const previous = rates.get(address);
    if (previous?.hour === hour && previous.count >= 10) { req.resume(); return send(res, 429); }
    if (!rates.has(address) && rates.size >= 1024) { req.resume(); return send(res, 503); }
    for (const [key, value] of rates) if (value.hour !== hour) rates.delete(key);
    rates.set(address, { hour, count: previous?.hour === hour ? previous.count + 1 : 1 });
    if (pending) { req.resume(); return send(res, 503); }
    pending = true;
    try {
      let size = 0; const chunks = [];
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 8192) { req.resume(); return send(res, 413); }
        chunks.push(chunk);
      }
      let input; try { input = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return send(res, 400); }
      if (!input || !/^[a-f0-9-]{36}$/.test(input.id || '') || !['BUG', 'IDEA', 'REVIEW'].includes(input.kind) ||
          typeof input.message !== 'string' || input.message.trim().length < 10 || input.message.length > 3000 ||
          !/^[A-Za-z0-9.+_-]{1,40}$/.test(input.version || '') ||
          (input.rating != null && (!Number.isInteger(input.rating) || input.rating < 1 || input.rating > 5))) return send(res, 400);
      for (const [id, at] of receipts) if (at < now() - 86400000) receipts.delete(id);
      if (receipts.has(input.id)) return send(res, 201, { received: true });
      if (receipts.size >= 500) return send(res, 503);
      const headers = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'Content-Type': 'application/json' };
      // Check every time: a repository made public must immediately stop accepting private reports.
      const metadata = await githubFetch(`https://api.github.com/repos/${repo}`, { headers, signal: AbortSignal.timeout(12000) });
      if (!metadata.ok || (await metadata.json()).private !== true) return send(res, 503);
      const titles = { BUG: 'Problème', IDEA: 'Suggestion', REVIEW: 'Avis' };
      const body = `Retour envoyé volontairement depuis SuperHealth Android ${input.version}.\n\n${input.rating ? `Note : ${input.rating}/5\n\n` : ''}` +
        '```text\n' + input.message.trim().replace(/```/g, "'''") + '\n```';
      const result = await githubFetch(`https://api.github.com/repos/${repo}/issues`, { method: 'POST', headers, signal: AbortSignal.timeout(12000),
        body: JSON.stringify({ title: `[Android ${input.version}] ${titles[input.kind]}`, body }) });
      if (result.status !== 201) return send(res, 503);
      receipts.set(input.id, now());
      return send(res, 201, { received: true });
    } catch { if (!res.headersSent && !res.destroyed) return send(res, 503); }
    finally { pending = false; }
  };
  handle.enabled = enabled;
  return handle;
}
