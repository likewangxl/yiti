export const DISPLAY_SCHEMA_VERSION = 1;

export const DISPLAY_COMPONENT_TYPES = Object.freeze([
  'METRIC_CARD',
  'COMPLETION',
  'TREND',
  'COMPOSITION_TABS',
  'RANKING',
  'MAP',
  'DETAIL_TABLE'
]);

export const DISPLAY_UNITS = Object.freeze([
  'AUTO', 'YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION',
  'COUNT', 'TEN_THOUSAND_COUNT', 'PERCENT', 'RATIO'
]);

export const INTERACTION_ACTIONS = Object.freeze([
  'NONE', 'OPEN_BUSINESS_LINE', 'OPEN_CITY', 'OPEN_INSTITUTION', 'OPEN_METRIC_DETAIL'
]);

const LAYOUT_REGIONS = new Set(['HEADER', 'LEFT', 'CENTER', 'RIGHT', 'BOTTOM', 'OVERLAY']);
const TITLE_MODES = new Set(['AUTO', 'CUSTOM']);
const NEGATIVE_STYLES = new Set(['SIGNED', 'COLOR', 'NONE']);
const DATA_REF_ROLES = new Set(['PRIMARY', 'SECONDARY', 'DIMENSION']);
const SOURCE_DIMENSIONS = new Set(['ORG', 'EMP', 'CUST', 'COMMON']);
const SORT_DIRECTIONS = new Set(['ASC', 'DESC']);
const INSTITUTION_NAME_KEYWORD_MAX_LENGTH = 40;
const UNIT_KINDS = Object.freeze({
  YUAN: 'amount', TEN_THOUSAND: 'amount', HUNDRED_MILLION: 'amount',
  COUNT: 'count', TEN_THOUSAND_COUNT: 'count',
  PERCENT: 'ratio', RATIO: 'ratio'
});
const DISPLAY_ORG_CODE_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

const ALLOWED_KEYS = Object.freeze({
  presentation: new Set(['type', 'template', 'displaySchemaVersion', 'institutionRules', 'display']),
  display: new Set(['components']),
  component: new Set(['componentId', 'componentType', 'layoutRegion', 'order', 'visible', 'text', 'format', 'content', 'interaction', 'dataRefs']),
  text: new Set(['titleMode', 'title', 'subtitle', 'description']),
  format: new Set(['displayUnit', 'decimals', 'thousandsSeparator', 'negativeStyle', 'emptyText']),
  content: new Set(['mainField', 'subFields', 'series', 'columns', 'tabs', 'rankingMetrics']),
  interaction: new Set(['action', 'target']),
  dataRef: new Set(['blockId', 'role', 'metricCode', 'metricName', 'unit', 'dimension', 'formula']),
  series: new Set(['seriesKey', 'field', 'label', 'unit']),
  column: new Set(['columnKey', 'field', 'label', 'unit', 'visible']),
  tab: new Set(['tabKey', 'label', 'corporateField', 'retailField', 'totalField', 'unit']),
  rankingMetric: new Set(['metricKey', 'field', 'label', 'unit', 'direction'])
});

function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function string(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function copyArray(value) {
  return Array.isArray(value) ? value.map(item => object(item) ? { ...item } : item) : [];
}

function unknownKeys(value, allowed, prefix, issues) {
  if (!object(value)) return;
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) issues.push(`${prefix}包含未知字段: ${key}`);
  }
}

function unitKind(unit) {
  return UNIT_KINDS[unit] || null;
}

function validKey(value) {
  return /^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(string(value));
}

function requireUnique(items, key, prefix, issues) {
  const seen = new Set();
  for (const [index, item] of items.entries()) {
    const value = string(item?.[key]);
    if (!validKey(value)) issues.push(`${prefix}[${index}].${key}不合法`);
    else if (seen.has(value)) issues.push(`${prefix}.${key}重复: ${value}`);
    seen.add(value);
  }
}

function normalizeComponent(raw = {}) {
  return {
    componentId: string(raw.componentId),
    componentType: string(raw.componentType),
    layoutRegion: string(raw.layoutRegion),
    order: Number.isInteger(raw.order) ? raw.order : 0,
    visible: raw.visible !== false,
    text: object(raw.text) ? { ...raw.text } : { titleMode: 'AUTO', title: '' },
    format: object(raw.format) ? { ...raw.format } : { displayUnit: 'AUTO' },
    content: object(raw.content) ? {
      ...raw.content,
      subFields: Array.isArray(raw.content.subFields) ? [...raw.content.subFields] : [],
      series: copyArray(raw.content.series),
      columns: copyArray(raw.content.columns),
      tabs: copyArray(raw.content.tabs),
      rankingMetrics: copyArray(raw.content.rankingMetrics)
    } : { subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
    interaction: object(raw.interaction) ? { ...raw.interaction } : { action: 'NONE' },
    dataRefs: copyArray(raw.dataRefs)
  };
}

function normalizeInstitutionRules(value) {
  if (!object(value)) return null;
  const normalized = {
    allowedOperatingLevels: Array.isArray(value.allowedOperatingLevels)
      ? [...value.allowedOperatingLevels] : [],
    allowedOrgNatures: Array.isArray(value.allowedOrgNatures)
      ? [...value.allowedOrgNatures] : []
  };
  if (Object.prototype.hasOwnProperty.call(value, 'excludedOrgNameKeywords')) {
    normalized.excludedOrgNameKeywords = Array.isArray(value.excludedOrgNameKeywords)
      ? [...value.excludedOrgNameKeywords] : [];
  }
  if (Object.prototype.hasOwnProperty.call(value, 'displayOrgCodes')) {
    normalized.displayOrgCodes = Array.isArray(value.displayOrgCodes)
      ? [...value.displayOrgCodes] : [];
  }
  return normalized;
}

/** 旧 presentation 没有展示子协议时返回 null，调用方继续走原展示路径。 */
export function normalizeDisplayConfig(presentation) {
  if (!object(presentation) || presentation.displaySchemaVersion === undefined) return null;
  const normalized = {
    displaySchemaVersion: presentation.displaySchemaVersion,
    components: Array.isArray(presentation.display?.components)
      ? presentation.display.components.map(normalizeComponent)
      : []
  };
  if (Object.prototype.hasOwnProperty.call(presentation, 'institutionRules')) {
    normalized.institutionRules = normalizeInstitutionRules(presentation.institutionRules);
  }
  return normalized;
}

export function resolveComponentTitle(component, context = {}) {
  const custom = component?.text?.titleMode === 'CUSTOM' ? string(component?.text?.title) : '';
  return custom || string(context.metricName) || string(context.legacyMetricLabel)
    || string(context.templateTitle) || '';
}

function validateAction(interaction, prefix, issues) {
  const action = string(interaction?.action) || 'NONE';
  if (!INTERACTION_ACTIONS.includes(action)) {
    issues.push(`${prefix}交互动作不受支持`);
    return;
  }
  const target = string(interaction?.target);
  if (target && !/^[A-Z][A-Z0-9_]{0,63}$/.test(target)) issues.push(`${prefix}交互目标不合法`);
  if (action === 'NONE' && target) issues.push(`${prefix}NONE 交互不能配置目标`);
  if (action === 'OPEN_BUSINESS_LINE' && !['CORP', 'RETAIL', 'COMMON'].includes(target)) {
    issues.push(`${prefix}业务条线目标不合法`);
  }
}

function validateContent(component, prefix, issues) {
  const content = component.content;
  if (!object(content)) {
    issues.push(`${prefix}展示内容不能为空`);
    return;
  }
  const requiredArray = {
    TREND: 'series', COMPOSITION_TABS: 'tabs', RANKING: 'rankingMetrics', DETAIL_TABLE: 'columns'
  }[component.componentType];
  if (requiredArray && (!Array.isArray(content[requiredArray]) || !content[requiredArray].length)) {
    issues.push(`${prefix}${requiredArray}至少配置一项`);
  }
  if (['METRIC_CARD', 'COMPLETION', 'MAP'].includes(component.componentType) && !string(content.mainField)) {
    issues.push(`${prefix}mainField不能为空`);
  }
  const series = Array.isArray(content.series) ? content.series : [];
  const columns = Array.isArray(content.columns) ? content.columns : [];
  const tabs = Array.isArray(content.tabs) ? content.tabs : [];
  const rankingMetrics = Array.isArray(content.rankingMetrics) ? content.rankingMetrics : [];
  requireUnique(series, 'seriesKey', `${prefix}series`, issues);
  requireUnique(columns, 'columnKey', `${prefix}columns`, issues);
  requireUnique(tabs, 'tabKey', `${prefix}tabs`, issues);
  requireUnique(rankingMetrics, 'metricKey', `${prefix}rankingMetrics`, issues);
  for (const [key, type] of [['series', 'series'], ['columns', 'column'], ['tabs', 'tab'], ['rankingMetrics', 'rankingMetric']]) {
    for (const [index, item] of (Array.isArray(content[key]) ? content[key] : []).entries()) {
      unknownKeys(item, ALLOWED_KEYS[type], `${prefix}${key}[${index}]`, issues);
    }
  }
  for (const [index, item] of series.entries()) {
    if (!string(item.field) || !string(item.label)) issues.push(`${prefix}series[${index}]字段和标签不能为空`);
    if (!DISPLAY_UNITS.includes(item.unit) || item.unit === 'AUTO') issues.push(`${prefix}series[${index}]单位不合法`);
  }
  for (const [index, item] of columns.entries()) {
    if (!string(item.field) || !string(item.label)) issues.push(`${prefix}columns[${index}]字段和标签不能为空`);
    if (!DISPLAY_UNITS.includes(item.unit)) issues.push(`${prefix}columns[${index}]单位不合法`);
    if (typeof item.visible !== 'boolean') issues.push(`${prefix}columns[${index}].visible必须为布尔值`);
  }
  for (const [index, item] of tabs.entries()) {
    if (!string(item.label) || !string(item.corporateField) || !string(item.retailField)) {
      issues.push(`${prefix}tabs[${index}]标签和公司/零售字段不能为空`);
    }
    if (!DISPLAY_UNITS.includes(item.unit) || item.unit === 'AUTO') issues.push(`${prefix}tabs[${index}]单位不合法`);
  }
  for (const [index, item] of rankingMetrics.entries()) {
    if (!string(item.field) || !string(item.label)) issues.push(`${prefix}rankingMetrics[${index}]字段和标签不能为空`);
    if (!DISPLAY_UNITS.includes(item.unit) || item.unit === 'AUTO') issues.push(`${prefix}rankingMetrics[${index}]单位不合法`);
    if (!SORT_DIRECTIONS.has(item.direction)) issues.push(`${prefix}rankingMetrics[${index}]排序方向不合法`);
  }
}

function validateComponent(component, index, issues) {
  const prefix = `components[${index}] `;
  unknownKeys(component, ALLOWED_KEYS.component, prefix, issues);
  if (!/^[A-Za-z][A-Za-z0-9_-]{1,63}$/.test(string(component.componentId))) issues.push(`${prefix}组件ID不合法`);
  if (!DISPLAY_COMPONENT_TYPES.includes(component.componentType)) issues.push(`${prefix}组件类型不受支持`);
  if (!LAYOUT_REGIONS.has(component.layoutRegion)) issues.push(`${prefix}布局区域不受支持`);
  if (!Number.isInteger(component.order) || component.order < 0) issues.push(`${prefix}顺序必须是非负整数`);
  if (typeof component.visible !== 'boolean') issues.push(`${prefix}visible必须为布尔值`);

  if (!object(component.text)) issues.push(`${prefix}text不能为空`);
  unknownKeys(component.text, ALLOWED_KEYS.text, `${prefix}text `, issues);
  const titleMode = string(component.text?.titleMode) || 'AUTO';
  if (!TITLE_MODES.has(titleMode)) issues.push(`${prefix}标题模式不受支持`);
  if (titleMode === 'CUSTOM' && !string(component.text?.title)) issues.push(`${prefix}自定义标题不能为空`);

  if (!object(component.format)) issues.push(`${prefix}format不能为空`);
  unknownKeys(component.format, ALLOWED_KEYS.format, `${prefix}format `, issues);
  const displayUnit = string(component.format?.displayUnit) || 'AUTO';
  if (!DISPLAY_UNITS.includes(displayUnit)) issues.push(`${prefix}展示单位不受支持`);
  if (component.format?.negativeStyle !== undefined
      && !NEGATIVE_STYLES.has(component.format.negativeStyle)) issues.push(`${prefix}负数样式不受支持`);
  if (component.format?.decimals !== undefined
      && (!Number.isInteger(component.format.decimals) || component.format.decimals < 0 || component.format.decimals > 8)) {
    issues.push(`${prefix}小数位必须是0到8`);
  }

  unknownKeys(component.content, ALLOWED_KEYS.content, `${prefix}content `, issues);
  validateContent(component, prefix, issues);
  if (!object(component.interaction)) issues.push(`${prefix}interaction不能为空`);
  unknownKeys(component.interaction, ALLOWED_KEYS.interaction, `${prefix}interaction `, issues);
  validateAction(component.interaction, prefix, issues);

  if (!Array.isArray(component.dataRefs) || !component.dataRefs.length) issues.push(`${prefix}至少引用一个blockId`);
  const blocks = new Set();
  for (const [refIndex, ref] of (Array.isArray(component.dataRefs) ? component.dataRefs : []).entries()) {
    unknownKeys(ref, ALLOWED_KEYS.dataRef, `${prefix}dataRefs[${refIndex}] `, issues);
    if (!Number.isSafeInteger(ref.blockId) || ref.blockId <= 0) issues.push(`${prefix}blockId不合法`);
    if (blocks.has(ref.blockId)) issues.push(`${prefix}blockId重复: ${ref.blockId}`);
    blocks.add(ref.blockId);
    if (!DISPLAY_UNITS.includes(ref.unit)) issues.push(`${prefix}来源单位不受支持`);
    if (ref.unit === 'AUTO') issues.push(`${prefix}来源原始单位不能为AUTO`);
    if (!DATA_REF_ROLES.has(ref.role)) issues.push(`${prefix}数据引用角色不受支持`);
    if (ref.dimension !== undefined && !SOURCE_DIMENSIONS.has(ref.dimension)) issues.push(`${prefix}来源维度不受支持`);
    const displayKind = unitKind(displayUnit);
    const sourceKind = unitKind(ref.unit);
    if (displayKind && sourceKind && displayKind !== sourceKind
        && ['METRIC_CARD', 'COMPLETION', 'COMPOSITION_TABS', 'RANKING'].includes(component.componentType)) {
      issues.push(`${prefix}单位类型冲突: ${displayUnit}/${ref.unit}`);
    }
  }
}

function validPlainText(value, maxLength) {
  if (typeof value !== 'string' || !value.trim()) return false;
  if ([...value].length > maxLength) return false;
  if (value.includes('<') || value.includes('>')) return false;
  return ![...value].some(character => {
    const codePoint = character.codePointAt(0);
    return codePoint < 0x20 || codePoint === 0x7F;
  });
}

function validateInstitutionRules(rules, issues) {
  const prefix = 'institutionRules ';
  if (!object(rules)) {
    issues.push(`${prefix}必须是对象`);
    return;
  }
  unknownKeys(rules, new Set([
    'allowedOperatingLevels', 'allowedOrgNatures', 'excludedOrgNameKeywords', 'displayOrgCodes'
  ]), prefix, issues);
  for (const [key, label] of [
    ['allowedOperatingLevels', '机构层级白名单'],
    ['allowedOrgNatures', '机构性质白名单']
  ]) {
    const values = rules[key];
    if (!Array.isArray(values) || values.length === 0) {
      issues.push(`${prefix}${label}不能为空`);
      continue;
    }
    const seen = new Set();
    values.forEach((value, index) => {
      if (typeof value !== 'string' || !value.trim()) {
        issues.push(`${prefix}${label}[${index}]必须是非空文本`);
        return;
      }
      const normalized = value.trim().toUpperCase();
      if (seen.has(normalized)) issues.push(`${prefix}${label}不能重复: ${value.trim()}`);
      seen.add(normalized);
    });
  }
  if (Object.prototype.hasOwnProperty.call(rules, 'excludedOrgNameKeywords')) {
    const keywords = rules.excludedOrgNameKeywords;
    if (!Array.isArray(keywords) || keywords.length === 0) {
      issues.push(`${prefix}机构名称排除关键词不能为空`);
    } else {
      const seen = new Set();
      keywords.forEach((value, index) => {
        if (!validPlainText(value, INSTITUTION_NAME_KEYWORD_MAX_LENGTH)) {
          issues.push(`${prefix}机构名称排除关键词[${index}]必须是合法非空文本`);
          return;
        }
        const normalized = value.trim().toUpperCase();
        if (seen.has(normalized)) issues.push(`${prefix}机构名称排除关键词不能重复: ${value.trim()}`);
        seen.add(normalized);
      });
    }
  }
  if (Object.prototype.hasOwnProperty.call(rules, 'displayOrgCodes')) {
    const codes = rules.displayOrgCodes;
    if (!Array.isArray(codes) || codes.length === 0) {
      issues.push(`${prefix}展示机构编码不能为空`);
    } else {
      const seen = new Set();
      codes.forEach((value, index) => {
        if (typeof value !== 'string' || !DISPLAY_ORG_CODE_PATTERN.test(value)) {
          issues.push(`${prefix}展示机构编码[${index}]必须是合法非空文本`);
          return;
        }
        const normalized = value.toUpperCase();
        if (seen.has(normalized)) issues.push(`${prefix}展示机构编码不能重复: ${value.trim()}`);
        seen.add(normalized);
      });
    }
  }
}

export function validateDisplayConfig(presentation) {
  if (!object(presentation)) return [];
  if (presentation.displaySchemaVersion === undefined) {
    const legacyIssues = [];
    if (Object.prototype.hasOwnProperty.call(presentation, 'institutionRules')) {
      validateInstitutionRules(presentation.institutionRules, legacyIssues);
    }
    return legacyIssues;
  }
  const issues = [];
  unknownKeys(presentation, ALLOWED_KEYS.presentation, 'presentation ', issues);
  if (Object.prototype.hasOwnProperty.call(presentation, 'institutionRules')) {
    validateInstitutionRules(presentation.institutionRules, issues);
  }
  if (presentation.displaySchemaVersion !== DISPLAY_SCHEMA_VERSION) issues.push('展示协议版本不受支持');
  unknownKeys(presentation.display, ALLOWED_KEYS.display, 'display ', issues);
  if (!Array.isArray(presentation.display?.components)) {
    issues.push('display.components必须为数组');
    return issues;
  }
  const ids = new Set();
  for (const [index, raw] of presentation.display.components.entries()) {
    const component = normalizeComponent(raw);
    if (ids.has(component.componentId)) issues.push(`组件ID重复: ${component.componentId}`);
    ids.add(component.componentId);
    validateComponent(raw, index, issues);
  }
  return issues;
}
