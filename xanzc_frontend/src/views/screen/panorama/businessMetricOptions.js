function text(value) {
  return String(value ?? '').trim();
}

function sourceIdOf(entry = {}) {
  return entry.datasourceId ?? entry.sourceId ?? entry.dsId ?? null;
}

function fieldOf(entry = {}) {
  return text(entry.field ?? entry.column);
}

function metricTexts(entry = {}) {
  return [
    entry.metricName,
    entry.field,
    entry.metricCode,
    entry.semantic,
    ...(Array.isArray(entry.semantics) ? entry.semantics : [])
  ].map(text).filter(Boolean);
}

function hasTestToken(value) {
  const candidate = text(value);
  if (!candidate) return false;
  if (candidate.includes('测试') || /\[TEST\]/i.test(candidate)) return true;
  return /(?:^|[^A-Z0-9])TEST(?:$|[^A-Z0-9])/i.test(candidate)
    || /^TEST_(?:[A-Z0-9]|$)/i.test(candidate);
}

/**
 * Test classification intentionally reads only metric identity fields.
 * Source descriptions may mention a test source and must remain searchable.
 */
export function isTestNamedMetric(entry = {}) {
  return [entry.metricName, entry.field, entry.metricCode].some(hasTestToken);
}

/**
 * Relevance is an explicit recommendation hint. It never changes disabled,
 * unit, scope, or source eligibility gates and never reads source description
 * to infer a field's business meaning.
 */
export function isRelatedBusinessMetric(entry = {}, context = {}) {
  const semantic = listOf(context.semantic);
  const keywords = listOf(context.keywords);
  if (!semantic.length && !keywords.length) return true;
  if (semanticMatches(entry, semantic)) return true;
  if (!keywords.length) return false;
  const values = metricTexts(entry).map(value => value.toLocaleLowerCase());
  return keywords.some(keyword => values.some(value => value.includes(keyword.toLocaleLowerCase())));
}

function selectionMatches(entry, selection) {
  if (selection === null || selection === undefined || selection === '') return false;
  if (typeof selection !== 'object') return String(entry.key) === String(selection);
  if (selection.key && String(selection.key) === String(entry.key)) return true;
  const sourceId = selection.datasourceId ?? selection.sourceId ?? selection.dsId;
  const field = selection.field ?? selection.column;
  return sourceId !== undefined && sourceId !== null && field !== undefined && field !== null
    && String(sourceId) === String(sourceIdOf(entry))
    && String(field) === fieldOf(entry);
}

function listOf(value) {
  if (Array.isArray(value)) return value.map(text).filter(Boolean);
  const item = text(value);
  return item ? [item] : [];
}

function semanticMatches(entry, semantic) {
  const targets = listOf(semantic);
  if (!targets.length) return false;
  const values = [entry.semantic, ...(Array.isArray(entry.semantics) ? entry.semantics : [])]
    .map(text);
  return targets.some(target => values.includes(target));
}

function keywordScore(entry, keywords) {
  const targets = listOf(keywords).map(keyword => keyword.toLocaleLowerCase());
  if (!targets.length) return 0;
  const values = metricTexts(entry).map(value => value.toLocaleLowerCase());
  return targets.reduce((score, keyword) => score + (values.some(value => value.includes(keyword)) ? 1 : 0), 0);
}

function preferredSource(entry, preferredSourceIds) {
  const ids = listOf(preferredSourceIds);
  return ids.includes(String(sourceIdOf(entry))) ? 1 : 0;
}

function nameForSort(entry) {
  return text(entry.metricName || entry.field || entry.metricCode || entry.sourceDescription);
}

function sourceNameForSort(entry) {
  return text(entry.sourceDescription);
}

/**
 * Stable business recommendation ordering. It never filters, enables, or
 * merges entries; sourceId+field identity remains owned by each input item.
 */
export function sortBusinessMetricOptions(entries = [], context = {}) {
  const currentSelection = context.currentSelection ?? context.modelValue;
  const semantic = context.semantic;
  const keywords = context.keywords;
  const preferredSourceIds = context.preferredSourceIds;
  return (Array.isArray(entries) ? entries : [])
    .map((entry, index) => ({ entry, index }))
    .sort((left, right) => {
      const a = left.entry;
      const b = right.entry;
      const disabled = Number(Boolean(a?.disabled)) - Number(Boolean(b?.disabled));
      if (disabled) return disabled;
      const current = Number(selectionMatches(a, currentSelection)) - Number(selectionMatches(b, currentSelection));
      if (current) return -current;
      const semanticRank = Number(semanticMatches(a, semantic)) - Number(semanticMatches(b, semantic));
      if (semanticRank) return -semanticRank;
      const keywordRank = keywordScore(a, keywords) - keywordScore(b, keywords);
      if (keywordRank) return -keywordRank;
      const sourceRank = preferredSource(a, preferredSourceIds) - preferredSource(b, preferredSourceIds);
      if (sourceRank) return -sourceRank;
      const nameRank = nameForSort(a).localeCompare(nameForSort(b), 'zh-CN', { sensitivity: 'base' });
      if (nameRank) return nameRank;
      const sourceNameRank = sourceNameForSort(a).localeCompare(sourceNameForSort(b), 'zh-CN', { sensitivity: 'base' });
      return sourceNameRank || left.index - right.index;
    })
    .map(item => item.entry);
}
