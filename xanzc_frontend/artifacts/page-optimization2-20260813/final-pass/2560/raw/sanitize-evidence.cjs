const fs = require('fs');
const path = require('path');

const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/2560';
const jsonDir = path.join(root, 'json');
const head = '4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4';

function sanitizeString(raw) {
  let value = String(raw)
    .replaceAll('http://127.0.0.1:8091', '')
    .replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>');
  value = value.replace(/\/([^/?#]+)/g, (match, segment) => {
    let decoded = segment;
    try { decoded = decodeURIComponent(segment); } catch { /* continue */ }
    const dynamicId = decoded.includes('<redacted-id>')
      || /^[0-9a-f]{16,}$/i.test(decoded)
      || /^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(decoded)
      || /^\d{2,}$/.test(decoded)
      || /^[A-Z0-9]+_[A-Z0-9_-]*\d[A-Z0-9_-]*$/i.test(decoded);
    return dynamicId ? '/<redacted-id>' : match;
  });
  return value;
}

for (const name of fs.readdirSync(jsonDir).filter(name => name.endsWith('.json'))) {
  const target = path.join(jsonDir, name);
  const data = JSON.parse(fs.readFileSync(target, 'utf8'));
  const clean = JSON.parse(JSON.stringify(data, (key, value) => (
    typeof value === 'string' ? sanitizeString(value) : value
  )));
  if (Object.hasOwn(clean, 'gitHead')) clean.gitHead = head;
  if (Object.hasOwn(clean, 'generatedAt')) clean.generatedAt = null;
  clean.evidenceSanitized = true;
  clean.credentialsArchived = false;
  clean.responseBodiesArchived = false;
  clean.realEntityIdsArchived = false;
  fs.writeFileSync(target, `${JSON.stringify(clean, null, 2)}\n`, 'utf8');
}
