import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rawDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(rawDir, '..');
const jsonDir = path.join(root, 'json');
const manifestPath = path.join(jsonDir, 'redaction-manifest.json');
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const previousManifest = fs.existsSync(manifestPath)
  ? JSON.parse(fs.readFileSync(manifestPath, 'utf8')) : null;
const endpointRules = [
  [/(\/api\/admin\/roles\/)[^/?#]+(?=\/resources(?:[/?#]|$))/g, '$1<redacted-id>'],
  [/(\/api\/admin\/users\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>'],
  [/(\/api\/admin\/workflow\/flows\/)(?!meta(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>'],
  [/(\/api\/perf\/kpi-schemes\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>'],
  [/(\/api\/perf\/metrics\/)(?!categories(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>'],
  [/(\/api\/portal\/announcements\/)(?!recent(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>'],
  [/(\/api\/reports\/amas-approvals\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>'],
  [/(\/api\/reports\/amas-price-approvals\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>'],
  [/(\/api\/sys\/dicts\/)[^/?#]+(?=\/items(?:[/?#]|$))/g, '$1<redacted-id>']
];

const sanitizeString = raw => {
  let value = String(raw);
  for (const [pattern, replacement] of endpointRules) value = value.replace(pattern, replacement);
  if (value.includes('/api/')) value = value.replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>');
  return value;
};

const sanitize = value => {
  if (typeof value === 'string') return sanitizeString(value);
  if (Array.isArray(value)) return value.map(sanitize);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, sanitize(item)]));
  }
  return value;
};

const diff = (before, after, changes, pointer = '$') => {
  if (Object.is(before, after)) return;
  if (typeof before !== typeof after || before === null || after === null || typeof before !== 'object') {
    changes.push({ pointer, beforeType: typeof before, after });
    return;
  }
  if (Array.isArray(before) && Array.isArray(after)) {
    const count = Math.max(before.length, after.length);
    for (let index = 0; index < count; index += 1) diff(before[index], after[index], changes, `${pointer}[${index}]`);
    return;
  }
  const keys = new Set([...Object.keys(before), ...Object.keys(after)]);
  for (const key of keys) diff(before[key], after[key], changes, `${pointer}.${key}`);
};

const scalarMetrics = value => {
  if (typeof value === 'number' || typeof value === 'boolean' || value === null) return value;
  if (Array.isArray(value)) return value.map(scalarMetrics);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, scalarMetrics(item)]));
  }
  return typeof value;
};

const routeFingerprint = evidence => ({
  summary: evidence.summary,
  routes: (evidence.routes || []).map(route => ({
    name: route.name,
    status: route.status,
    issues: route.issues,
    dom: scalarMetrics(route.dom || {}),
    responses: (route.network?.requests || []).map(item => [item.method, item.status, item.code]),
    failedCount: (route.network?.failed || []).length,
    writeCount: (route.network?.writes || []).length,
    badCount: (route.network?.bad || []).length
  }))
});

const xianFingerprint = evidence => ({
  status: evidence.status,
  summary: evidence.summary,
  branches: (evidence.branches || []).map(branch => ({
    name: branch.name,
    orgCode: branch.orgCode,
    status: branch.status,
    issues: branch.issues,
    route: branch.route,
    dataRequestCount: branch.dataRequestCount,
    responses: (branch.network?.requests || []).map(item => [item.method, item.status, item.code, item.body]),
    dataResponses: (branch.network?.dataRequests || []).map(item => [item.method, item.status, item.code, item.body]),
    dataContracts: branch.dataContracts
  }))
});

const artifacts = fs.readdirSync(jsonDir)
  .filter(name => name.endsWith('.json') && name !== path.basename(manifestPath))
  .sort();
const files = [];
const allChanges = [];
const previousFiles = new Map((previousManifest?.files || []).map(item => [item.artifact, item]));
let routeInvariant = null;
let xianInvariant = null;

for (const name of artifacts) {
  const absolute = path.join(jsonDir, name);
  const sourceText = fs.readFileSync(absolute, 'utf8');
  const source = JSON.parse(sourceText);
  const published = sanitize(structuredClone(source));
  if (name === 'routes-59.json') {
    for (const route of published.routes || []) {
      if (route.dom?.actualPath && route.path?.includes(':')) route.dom.actualPath = route.path;
    }
    routeInvariant = JSON.stringify(routeFingerprint(source)) === JSON.stringify(routeFingerprint(published));
  }
  if (name === 'xian-drill.json') {
    xianInvariant = JSON.stringify(xianFingerprint(source)) === JSON.stringify(xianFingerprint(published));
  }
  const changes = [];
  diff(source, published, changes);
  const publishedText = `${JSON.stringify(published, null, 2)}\n`;
  fs.writeFileSync(absolute, publishedText, 'utf8');
  const artifact = `json/${name}`;
  const previousFile = previousFiles.get(artifact);
  files.push({
    artifact,
    sourceSha256: changes.length ? sha256(sourceText) : previousFile?.sourceSha256 || sha256(sourceText),
    publishedSha256: sha256(publishedText),
    changedValueCount: changes.length || previousFile?.changedValueCount || 0
  });
  for (const change of changes) allChanges.push({ artifact, ...change });
}

if (!allChanges.length && previousManifest?.changedValues) {
  allChanges.push(...previousManifest.changedValues.map(item => ({
    artifact: item.artifact,
    pointer: item.pointer,
    beforeType: item.beforeType,
    after: item.after
  })));
}

const routeEvidence = JSON.parse(fs.readFileSync(path.join(jsonDir, 'routes-59.json'), 'utf8'));
const parameterRoutes = (routeEvidence.routes || [])
  .filter(route => route.path?.includes(':'))
  .map(route => ({ route: route.name, template: route.path, redaction: 'template-and-endpoint-segment' }));
const manifest = {
  schemaVersion: 1,
  operation: 'mechanical-redaction-only-no-browser-rerun',
  files,
  changedValueCount: allChanges.length,
  changedValues: allChanges,
  parameterRoutes,
  invariants: {
    routeSummaryHttpDomPreserved: routeInvariant,
    xianSummaryHttpBodyPreserved: xianInvariant,
    routeCount: routeEvidence.routes?.length,
    passedRouteCount: routeEvidence.summary?.passed,
    xianRequestCount: JSON.parse(fs.readFileSync(path.join(jsonDir, 'xian-drill.json'), 'utf8')).summary?.screenDataRequests
  },
  publicationRules: {
    actualPath: 'route-template-when-present',
    entityPathSegment: '<redacted-id>',
    queryValue: '<redacted>',
    originalIdsRetained: false,
    singleIdHashesRetained: false
  }
};
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

console.log(JSON.stringify({
  artifacts: files.length,
  changedValueCount: allChanges.length,
  parameterRouteCount: parameterRoutes.length,
  invariants: manifest.invariants
}, null, 2));
