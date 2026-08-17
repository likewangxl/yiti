import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rawDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(rawDir, '..');
const evidencePath = path.join(root, 'json', 'route-dom-probes-1920-22108e35.json');
const manifestPath = path.join(root, 'json', 'redaction-manifest-1920-22108e35.json');
const sumsPath = path.join(root, 'SHA256SUMS.txt');
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const previousManifest = fs.existsSync(manifestPath)
  ? JSON.parse(fs.readFileSync(manifestPath, 'utf8')) : null;

const sourceText = fs.readFileSync(evidencePath, 'utf8');
const source = JSON.parse(sourceText);
const published = structuredClone(source);

const realIds = [];
for (const route of source.routes || []) {
  if (!route.realIdSource || !route.templatePath || !route.dom?.actualPath) continue;
  const templateSegments = route.templatePath.split('/');
  const actualSegments = route.dom.actualPath.split('/');
  const parameterIndex = templateSegments.findIndex(segment => segment.startsWith(':'));
  const value = parameterIndex >= 0 ? actualSegments[parameterIndex] : '';
  if (!value || value.startsWith(':') || value.startsWith('<redacted')) continue;
  realIds.push({
    route: route.name,
    source: route.realIdSource,
    template: route.templatePath,
    parameter: templateSegments[parameterIndex],
    value,
    redaction: 'template-path'
  });
}

const escapeRegExp = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const sanitizeString = raw => {
  let value = String(raw);
  for (const item of realIds) {
    const encoded = encodeURIComponent(item.value);
    value = value.replace(new RegExp(`/${escapeRegExp(item.value)}(?=/|[?#]|$)`, 'g'), '/<redacted-id>');
    value = value.replace(new RegExp(`/${escapeRegExp(encoded)}(?=/|[?#]|$)`, 'g'), '/<redacted-id>');
  }
  return value
    .replace(/(\/api\/admin\/roles\/)[^/?#]+(?=\/resources(?:[/?#]|$))/g, '$1<redacted-id>')
    .replace(/(\/api\/admin\/users\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
    .replace(/(\/api\/admin\/workflow\/flows\/)(?!meta(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
    .replace(/(\/api\/perf\/kpi-schemes\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
    .replace(/(\/api\/perf\/metrics\/)(?!categories(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
    .replace(/(\/api\/portal\/announcements\/)(?!recent(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
    .replace(/(\/api\/reports\/amas-approvals\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
    .replace(/(\/api\/reports\/amas-price-approvals\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
    .replace(/(\/api\/sys\/dicts\/)[^/?#]+(?=\/items(?:[/?#]|$))/g, '$1<redacted-id>');
};

const sanitize = value => {
  if (typeof value === 'string') return sanitizeString(value);
  if (Array.isArray(value)) return value.map(sanitize);
  if (value && typeof value === 'object') {
    for (const [key, item] of Object.entries(value)) value[key] = sanitize(item);
  }
  return value;
};
sanitize(published);
for (const route of published.routes || []) {
  if (route.realIdSource && route.dom) route.dom.actualPath = route.templatePath;
}

const changes = [];
const diff = (before, after, pointer = '$') => {
  if (Object.is(before, after)) return;
  if (typeof before !== typeof after || before === null || after === null
    || typeof before !== 'object') {
    changes.push({
      pointer,
      beforeType: typeof before,
      after
    });
    return;
  }
  if (Array.isArray(before) && Array.isArray(after)) {
    const count = Math.max(before.length, after.length);
    for (let index = 0; index < count; index += 1) diff(before[index], after[index], `${pointer}[${index}]`);
    return;
  }
  const keys = new Set([...Object.keys(before), ...Object.keys(after)]);
  for (const key of keys) diff(before[key], after[key], `${pointer}.${key}`);
};
diff(source, published);

const domMetricFingerprint = evidence => sha256(JSON.stringify((evidence.routes || []).map(route => {
  const dom = structuredClone(route.dom || {});
  delete dom.actualPath;
  const numericAndBoolean = value => {
    if (typeof value === 'number' || typeof value === 'boolean' || value === null) return value;
    if (Array.isArray(value)) return value.map(numericAndBoolean);
    if (value && typeof value === 'object') {
      return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, numericAndBoolean(item)]));
    }
    return typeof value;
  };
  return { name: route.name, dom: numericAndBoolean(dom) };
})));
const responseFingerprint = evidence => sha256(JSON.stringify((evidence.routes || []).map(route => ({
  name: route.name,
  status: route.status,
  issues: route.issues,
  network: {
    requestStatuses: (route.network?.requests || []).map(item => [item.method, item.status]),
    failedCount: (route.network?.failed || []).length,
    unexpectedWriteCount: (route.network?.unexpectedWrites || []).length,
    badResponseCount: (route.network?.badResponses || []).length
  }
}))));

const publishedText = `${JSON.stringify(published, null, 2)}\n`;
fs.writeFileSync(evidencePath, publishedText, 'utf8');

const parameterRoutes = realIds.length
  ? realIds.map(({ value, ...item }) => item)
  : (previousManifest?.parameterRoutes || []).map(item => ({
      route: item.route, source: item.source, template: item.template,
      parameter: item.parameter, redaction: 'template-path'
    }));
const changedValues = changes.length ? changes
  : (previousManifest?.changedValues || []).map(item => ({
      pointer: item.pointer, beforeType: item.beforeType, after: item.after
    }));

const manifest = {
  schemaVersion: 1,
  artifact: 'json/route-dom-probes-1920-22108e35.json',
  operation: 'mechanical-redaction-only-no-browser-rerun',
  sourceSha256: realIds.length ? sha256(sourceText) : previousManifest?.sourceSha256,
  publishedSha256: sha256(publishedText),
  parameterRoutes,
  changedValueCount: changedValues.length,
  changedValues,
  invariants: {
    summaryPreserved: JSON.stringify(source.summary) === JSON.stringify(published.summary),
    routeNamesAndStatusesPreserved: JSON.stringify(source.routes.map(route => [route.name, route.status]))
      === JSON.stringify(published.routes.map(route => [route.name, route.status])),
    responseStatusesPreserved: responseFingerprint(source) === responseFingerprint(published),
    domNumericAndBooleanMetricsPreserved: domMetricFingerprint(source) === domMetricFingerprint(published),
    routeCount: published.routes.length,
    parameterRouteCount: parameterRoutes.length
  },
  publicationRules: {
    routeActualPath: 'templatePath',
    entityPathSegment: '<redacted-id>',
    queryValue: '<redacted>',
    realIdsRetained: false
  }
};
const manifestText = `${JSON.stringify(manifest, null, 2)}\n`;
fs.writeFileSync(manifestPath, manifestText, 'utf8');

const listFiles = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
  const absolute = path.join(directory, entry.name);
  return entry.isDirectory() ? listFiles(absolute) : [path.relative(root, absolute)];
});
const sumFiles = listFiles(root).filter(relative => relative !== 'SHA256SUMS.txt').sort();
const sums = sumFiles.map(relative => `${sha256(fs.readFileSync(path.join(root, relative)))}  ${relative}`).join('\n');
fs.writeFileSync(sumsPath, `${sums}\n`, 'utf8');

console.log(JSON.stringify({
  artifact: path.relative(root, evidencePath),
  changedValueCount: changedValues.length,
  parameterRouteCount: parameterRoutes.length,
  publishedSha256: manifest.publishedSha256,
  invariants: manifest.invariants
}, null, 2));
