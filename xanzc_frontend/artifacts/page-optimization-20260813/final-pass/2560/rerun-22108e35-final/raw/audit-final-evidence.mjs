import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rawDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(rawDir, '..');
const reportPath = path.join(rawDir, 'redaction-scan.txt');
const sumsPath = path.join(root, 'SHA256SUMS.txt');
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const listFiles = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
  const absolute = path.join(directory, entry.name);
  return entry.isDirectory() ? listFiles(absolute) : [absolute];
});
const relative = absolute => path.relative(root, absolute);
const textExtensions = new Set(['.json', '.js', '.mjs', '.md', '.txt']);
const legacyPlaceholder = ':' + 'real-id';
const forbiddenFiles = /(?:unavailable|fail-stopped|failed-batch|old-batch)/i;
const credentialValue = '123' + '456';
const endpointRules = [
  { pattern: /\/api\/admin\/roles\/([^/?#\s"']+)\/resources/g, allowed: ['<redacted-id>'] },
  { pattern: /\/api\/admin\/users\/([^/?#\s"']+)/g, allowed: ['<redacted-id>'] },
  { pattern: /\/api\/admin\/workflow\/flows\/([^/?#\s"']+)/g, allowed: ['meta', '<redacted-id>'] },
  { pattern: /\/api\/perf\/kpi-schemes\/([^/?#\s"']+)/g, allowed: ['<redacted-id>'] },
  { pattern: /\/api\/perf\/metrics\/([^/?#\s"']+)/g, allowed: ['categories', '<redacted-id>'] },
  { pattern: /\/api\/portal\/announcements\/([^/?#\s"']+)/g, allowed: ['recent', '<redacted-id>'] },
  { pattern: /\/api\/reports\/amas-approvals\/([^/?#\s"']+)/g, allowed: ['<redacted-id>'] },
  { pattern: /\/api\/reports\/amas-price-approvals\/([^/?#\s"']+)/g, allowed: ['<redacted-id>'] },
  { pattern: /\/api\/sys\/dicts\/([^/?#\s"']+)\/items/g, allowed: ['<redacted-id>'] }
];
const sensitiveQuery = /[?&](?:id|userId|roleId|batchId|planId|priceApprId|perfAdjustNo|empId)=((?!<redacted>)[^&#\s"']+)/gi;
const uuid = /\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b/gi;
const longOpaqueId = /\b[0-9a-f]{24,32}\b/gi;
const credentialPatterns = [
  new RegExp(`\\b${credentialValue}\\b`, 'g'),
  /(?:authorization|cookie|set-cookie|jsessionid)\s*[:=]\s*(?!<redacted>|omitted)[^\s,;]+/gi,
  /bearer\s+[a-z0-9._~-]+/gi
];
const findings = [];
const add = (file, category, value) => findings.push({ file: relative(file), category, value: String(value).slice(0, 160) });

const initialFiles = listFiles(root).filter(file => file !== reportPath && file !== sumsPath);
for (const file of initialFiles) {
  const name = relative(file);
  if (name.includes(legacyPlaceholder)) add(file, 'malformed-placeholder-filename', name);
  if (forbiddenFiles.test(path.basename(file))) add(file, 'historical-failure-file', path.basename(file));
  if (uuid.test(path.basename(file)) || longOpaqueId.test(path.basename(file))) add(file, 'dynamic-id-filename', path.basename(file));
  uuid.lastIndex = 0;
  longOpaqueId.lastIndex = 0;
  if (!textExtensions.has(path.extname(file))) continue;
  const text = fs.readFileSync(file, 'utf8');
  if (text.includes(legacyPlaceholder)) add(file, 'malformed-placeholder-content', legacyPlaceholder);
  for (const rule of endpointRules) {
    for (const match of text.matchAll(rule.pattern)) {
      if (!rule.allowed.includes(match[1])) add(file, 'unredacted-entity-endpoint', match[0]);
    }
    rule.pattern.lastIndex = 0;
  }
  for (const match of text.matchAll(sensitiveQuery)) add(file, 'unredacted-entity-query', match[0]);
  sensitiveQuery.lastIndex = 0;
  for (const match of text.matchAll(uuid)) add(file, 'uuid', match[0]);
  uuid.lastIndex = 0;
  for (const match of text.matchAll(longOpaqueId)) add(file, 'opaque-id', match[0]);
  longOpaqueId.lastIndex = 0;
  for (const pattern of credentialPatterns) {
    for (const match of text.matchAll(pattern)) add(file, 'credential-or-session', match[0]);
    pattern.lastIndex = 0;
  }
}

for (const file of initialFiles.filter(file => path.extname(file) === '.json')) {
  const document = JSON.parse(fs.readFileSync(file, 'utf8'));
  const walk = (value, pointer = '$') => {
    if (Array.isArray(value)) return value.forEach((item, index) => walk(item, `${pointer}[${index}]`));
    if (!value || typeof value !== 'object') return;
    if (typeof value.actualPath === 'string') {
      const template = value.path || value.templatePath;
      if (template?.includes(':') && value.actualPath !== template) add(file, 'actual-path-real-parameter', pointer);
    }
    for (const [key, item] of Object.entries(value)) walk(item, `${pointer}.${key}`);
  };
  walk(document);
}

const report = [
  '2560 final evidence recursive redaction audit',
  'scope=json, raw, commands, README and screenshot filenames',
  'rules=malformed placeholder fragments; known dynamic entity endpoint segments; entity query identifiers; actualPath parameters; UUID/opaque IDs; credentials/session values; historical failure files',
  `files_scanned=${initialFiles.length}`,
  `findings=${findings.length}`,
  `result=${findings.length === 0 ? 'PASS' : 'FAIL'}`,
  ...findings.map(item => `${item.category}\t${item.file}\t${item.value}`)
].join('\n');
fs.writeFileSync(reportPath, `${report}\n`, 'utf8');

const sumFiles = listFiles(root).filter(file => file !== sumsPath).sort((a, b) => relative(a).localeCompare(relative(b)));
const sums = sumFiles.map(file => `${sha256(fs.readFileSync(file))}  ${relative(file)}`).join('\n');
fs.writeFileSync(sumsPath, `${sums}\n`, 'utf8');

console.log(JSON.stringify({ filesScanned: initialFiles.length, findings: findings.length, result: findings.length ? 'FAIL' : 'PASS', checksumEntries: sumFiles.length }, null, 2));
if (findings.length) process.exitCode = 1;
