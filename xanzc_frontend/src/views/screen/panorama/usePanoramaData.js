import { computed, getCurrentInstance, isRef, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { queryScreenData } from '@/api/screen';
import { buildScreenDataRequest, runtimeSchemaVersion } from '@/utils/screenScope';
import { collectSourceQualities } from './sourceQuality';
import {
  adaptPanoramaResults,
  applyBranchTrend,
  createEmptyPanoramaModel
} from './dataAdapter';
import { adaptCorporateResults, createEmptyCorporateModel } from './corporateDataAdapter';
import { CORPORATE_TEMPLATE, CORPORATE_SLOT_ORDER, normalizeCorporateBinding } from './corporateBindings';
import { adaptRetailResults, createEmptyRetailModel } from './retailDataAdapter';
import {
  RETAIL_SLOT_ORDER,
  RETAIL_TEMPLATE,
  isRetailBindingSlot,
  normalizeRetailBinding
} from './retailBindings';
import {
  BRANCH_OPTIONAL_SLOT_ORDER,
  BRANCH_SLOT_ORDER,
  isBindingSlot,
  normalizeBinding
} from './bindings';
import {
  compareBatchQuality,
  isUsableBatchQuality,
  mergeBatchQualities,
  readBatchQuality
} from './batchQuality';
import { comparisonDates } from '../presentation/model/explicitComparisons';

const MAX_CONCURRENCY = 3;

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function parseJson(value, fallback = {}) {
  if (isObject(value)) return value;
  if (typeof value !== 'string') return fallback;
  try {
    const parsed = JSON.parse(value);
    return isObject(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

function valueOf(source) {
  return isRef(source) ? source.value : source;
}

function screenPackage(view) {
  const raw = view?.renderPackage ?? view?.render_package;
  if (raw) return parseJson(raw, {});
  if (view?.renderPackageJson) return parseJson(view.renderPackageJson, {});
  return {};
}

function flattenComponents(components, out = []) {
  if (!Array.isArray(components)) return out;
  for (const component of components) {
    if (component?.component === 'Group') flattenComponents(component.children, out);
    else out.push(component);
  }
  return out;
}

function presentationTemplate(pkg) {
  const canvasStyle = parseJson(pkg?.canvasStyle ?? pkg?.canvas_style, {});
  const presentation = parseJson(canvasStyle?.presentation, {});
  return String(presentation?.template || pkg?.presentation?.template || '').trim();
}

function isRetailPackage(pkg) {
  return presentationTemplate(pkg) === RETAIL_TEMPLATE;
}

function isCorporatePackage(pkg) {
  return presentationTemplate(pkg) === CORPORATE_TEMPLATE;
}

function branchPackageSlots(pkg) {
  const components = flattenComponents(pkg?.components || []);
  const optional = BRANCH_OPTIONAL_SLOT_ORDER.filter(slot => components.some(component =>
    component?.component === 'ChartWidget' && component?.propValue?.bindingKey === slot
  ));
  return new Set([...BRANCH_SLOT_ORDER, ...optional]);
}

function allowedSlotsForPackage(pkg) {
  if (isCorporatePackage(pkg)) return new Set(CORPORATE_SLOT_ORDER);
  if (isRetailPackage(pkg)) return new Set(RETAIL_SLOT_ORDER);
  return branchPackageSlots(pkg);
}

function permissionStatus(error) {
  const status = error?.status ?? error?.response?.status ?? error?.response?.data?.status;
  if (status === 401 || status === 403) return status;
  if (error?.code === 401 || error?.code === 403 || error?.code === '401' || error?.code === '403') return Number(error.code);
  return null;
}

function errorMessage(error, fallback = '取数失败') {
  return String(error?.message || error?.response?.data?.message || fallback);
}

function batchGuard(code, message, status = 'PARTIAL') {
  return { code, status, message };
}

function emptyModel(retail, view, queriedAt, quality = null, qualityGuard = null) {
  const next = isCorporatePackage(screenPackage(view)) ? createEmptyCorporateModel({ view })
    : retail ? createEmptyRetailModel({ view }) : createEmptyPanoramaModel();
  next.quality = quality || null;
  next.qualityGuard = qualityGuard || null;
  next.batchId = quality?.batchId || null;
  next.queriedAt = queriedAt || '';
  next.sourceQualities = {};
  next.sourceDates = {};
  next.sourceMetadata = {};
  next.blockResults = {};
  next.comparisonResults = {};
  next.configuredSlots = [];
  next.permissionStatus = null;
  return next;
}

function contextSnapshot(context) {
  const value = valueOf(context) || {};
  return isObject(value) ? value : {};
}

function contextOrgCode(context) {
  const value = contextSnapshot(context);
  return String(value.orgCode ?? value.org_code ?? '').trim();
}

function publishedInstitutions(view) {
  const source = view?.panoramaInstitutions ?? view?.panorama_institutions;
  return Array.isArray(source) ? source : [];
}

function institutionOrgCode(item) {
  if (isObject(item)) return String(item.orgCode ?? item.org_code ?? '').trim();
  return String(item ?? '').trim();
}

function resolveSingleOrgScope(view, context) {
  const scopeMode = String(view?.orgScopeMode ?? view?.org_scope_mode ?? '').trim().toUpperCase();
  const orgCode = contextOrgCode(context);
  const directory = publishedInstitutions(view);
  if (scopeMode !== 'NAMED_GROUP') {
    return {
      valid: false,
      orgCode,
      directory: [],
      message: `singleOrg 仅允许 NAMED_GROUP 机构范围，当前为 ${scopeMode || '未提供'}`
    };
  }
  if (!orgCode) {
    return {
      valid: false,
      orgCode,
      directory: [],
      message: 'singleOrg 缺少上下文机构号'
    };
  }
  const selected = directory.find(item => institutionOrgCode(item) === orgCode);
  if (!selected) {
    return {
      valid: false,
      orgCode,
      directory: [],
      message: `singleOrg 上下文机构不在授权目录中: ${orgCode}`
    };
  }
  return { valid: true, orgCode, directory: [selected], message: '' };
}

function addIssue(issues, slot, code, message, field = '') {
  const key = `${slot}|${code}|${field}`;
  if (issues.some(item => `${item.slot}|${item.code}|${item.field || ''}` === key)) return;
  issues.push({ slot, code, field: field || undefined, message: message || `${slot}: ${code}` });
}

function packageBindings(view) {
  const pkg = screenPackage(view);
  const allowedSlots = allowedSlotsForPackage(pkg);
  const retailPackage = isRetailPackage(pkg);
  const snapshots = isObject(pkg.bindSnapshots) ? pkg.bindSnapshots
    : isObject(view?.bindSnapshots) ? view.bindSnapshots : {};
  const components = flattenComponents(pkg.components || view?.components || []);
  const slots = new Map();
  const issues = [];
  for (const component of components) {
    if (component?.component !== 'ChartWidget') continue;
    const slot = component?.propValue?.bindingKey;
    if (!isBindingSlot(slot) || slots.has(slot)) continue;
    if (!allowedSlots.has(slot)) {
      addIssue(issues, slot, 'SLOT_NOT_ALLOWED_FOR_TEMPLATE', `槽位不属于当前模板: ${slot}`);
      continue;
    }
    const blockId = component.blockId;
    const snapshot = snapshots[String(blockId)];
    if (!snapshot || !isObject(snapshot.bind)) {
      addIssue(issues, slot, 'MISSING_BINDING_SNAPSHOT', '发布组件缺少可信 bindSnapshots 身份');
      continue;
    }
    const binding = isCorporatePackage(pkg) ? normalizeCorporateBinding(snapshot.bind, slot)
      : retailPackage && isRetailBindingSlot(slot)
      ? normalizeRetailBinding(snapshot.bind, slot)
      : normalizeBinding(snapshot.bind, slot);
    if (BRANCH_OPTIONAL_SLOT_ORDER.includes(slot)
        && (!Number.isSafeInteger(Number(binding?.dsId)) || Number(binding.dsId) <= 0)) {
      addIssue(issues, slot, 'MISSING_DATASOURCE', '可选槽位未配置有效数据源');
      continue;
    }
    slots.set(slot, { slot, blockId, binding, component, snapshot });
  }
  return { package: pkg, slots, issues, template: presentationTemplate(pkg), retail: retailPackage, corporate: isCorporatePackage(pkg) };
}

function stableSerialize(value) {
  if (Array.isArray(value)) return `[${value.map(stableSerialize).join(',')}]`;
  if (!isObject(value)) return JSON.stringify(value);
  return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableSerialize(value[key])}`).join(',')}}`;
}

function requestKey(body) {
  return stableSerialize(body);
}

function presentationFromPackage(pkg) {
  return parseJson(pkg?.canvasStyle?.presentation || pkg?.canvas_style?.presentation
    || pkg?.renderPackage?.canvasStyle?.presentation, {});
}

function explicitComparisonConfigs(packageInfo) {
  const presentation = presentationFromPackage(packageInfo.package);
  const comparisons = isObject(presentation?.display?.comparisons) ? presentation.display.comparisons : {};
  return Object.entries(comparisons).filter(([, config]) => config?.enabled === true);
}

function trustedHistoryTrendBlocks(packageInfo) {
  const presentation = presentationFromPackage(packageInfo.package);
  const components = Array.isArray(presentation?.display?.components) ? presentation.display.components : [];
  return new Set(components
    .filter(component => component?.visible !== false && component?.componentType === 'TREND')
    .flatMap(component => Array.isArray(component.dataRefs) ? component.dataRefs : [])
    .map(ref => ref?.blockId)
    .filter(blockId => Number.isSafeInteger(blockId) && blockId > 0));
}

/**
 * 查询身份只包含会改变服务端请求或绑定解释的字段。
 * 标题、metricLabels、展示单位和 display 子协议属于纯展示配置，不能因为
 * 保存这些字段就重新打穿数据源；只有查询定义/来源快照变化才推进身份。
 */
function queryIdentity(view, context, options = {}) {
  const packageInfo = packageBindings(view);
  const contextValue = contextSnapshot(context);
  const bindings = [...packageInfo.slots.entries()].map(([slot, entry]) => {
    const sourceDefinition = entry.snapshot?.sourceDefinition ?? entry.snapshot?.source_definition
      ?? entry.binding?.sourceDefinition ?? entry.binding?.source_definition ?? null;
    return {
      slot,
      blockId: entry.blockId,
      dsId: entry.binding?.dsId ?? null,
      period: entry.binding?.period || 'LATEST',
      fields: entry.binding?.fields || {},
      sourceDefinitionHash: entry.snapshot?.definitionHash
        ?? entry.snapshot?.definition_hash ?? entry.snapshot?.sourceDefinitionHash
        ?? entry.snapshot?.source_definition_hash ?? entry.binding?.definitionHash
        ?? entry.binding?.definition_hash ?? entry.binding?.sourceDefinitionHash
        ?? entry.binding?.source_definition_hash ?? sourceDefinition?.definitionHash
        ?? sourceDefinition?.definition_hash ?? null,
      sourceDefinition
    };
  });
  return requestKey({
    screenCode: String(contextValue.screenCode || view?.screenCode || view?.screen_code || '').trim(),
    schemaVersion: contextValue.schemaVersion ?? contextValue.runtimeSchemaVersion ?? runtimeSchemaVersion(view),
    previewState: view?.state === 'draft' || contextValue.previewState === 'draft' ? 'draft' : '',
    dateFrom: contextValue.dateFrom || '',
    dateTo: contextValue.dateTo || '',
    orgCode: contextOrgCode(contextValue),
    businessLine: String(contextValue.businessLine || contextValue.bizLine || view?.bizLine || '').trim().toUpperCase(),
    orgScopeMode: String(view?.orgScopeMode || view?.org_scope_mode || '').trim().toUpperCase(),
    orgGroupCode: String(view?.orgGroupCode || view?.org_group_code || '').trim(),
    template: packageInfo.template,
    comparisons: presentationFromPackage(packageInfo.package)?.display?.comparisons || {},
    singleOrg: Boolean(options.singleOrg),
    bindings
  });
}

function unwrapResponse(response) {
  if (!isObject(response)) return response;
  if (response.columns === undefined && response.rows === undefined && isObject(response.data)) return response.data;
  return response;
}

function responseDataDate(response) {
  const table = unwrapResponse(response);
  const quality = readBatchQuality(table);
  return String(quality?.dataDate || table?.dataDate || table?.data_date || table?.date || '').trim();
}

function responseRowsPresent(response) {
  const table = unwrapResponse(response);
  return Array.isArray(table?.rows) && table.rows.length > 0;
}

function collectSourceDates(results = {}) {
  return Object.fromEntries(Object.entries(results).flatMap(([slot, result]) => {
    if (result?.error || !result?.response) return [];
    const date = responseDataDate(result.response);
    return date ? [[slot, date]] : [];
  }));
}

function collectSourceMetadata(results = {}) {
  return Object.fromEntries(Object.entries(results).flatMap(([slot, result]) => {
    if (result?.error || !result?.response) return [];
    const quality = readBatchQuality(result.response);
    return [[slot, {
      dataDate: responseDataDate(result.response),
      batchId: quality?.batchId || null,
      qualityStatus: quality?.status || null,
      sourceAsOf: quality?.sourceAsOf || null,
      hasRows: responseRowsPresent(result.response)
    }]];
  }));
}

function cloneRawValue(value) {
  if (Array.isArray(value)) return value.map(cloneRawValue);
  if (isObject(value)) return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneRawValue(item)]));
  return value;
}

/**
 * 把统一二维表响应整理成展示协议只读 block 结果。
 * 这里只按服务端 columns 与发布绑定 units 原样映射，不从标题或列名猜测业务字段。
 */
function normalizeBlockResult(blockId, binding, response) {
  const table = unwrapResponse(response);
  if (!isObject(table) || !Array.isArray(table.columns) || !Array.isArray(table.rows)) return null;
  const columns = table.columns.map(column => {
    if (typeof column === 'string') return column;
    if (isObject(column)) return String(column.col ?? column.name ?? column.key ?? '').trim();
    return String(column ?? '').trim();
  });
  if (!columns.length || columns.some(column => !column)) return null;
  const rawRows = table.rows.flatMap(rawRow => {
    if (!Array.isArray(rawRow)) return [];
    const row = {};
    columns.forEach((column, index) => { row[column] = rawRow[index] === undefined ? null : cloneRawValue(rawRow[index]); });
    return [Object.freeze(row)];
  });
  const reservedKeys = new Set(['blockId', 'rows', 'columns', 'columnsMeta', 'units', 'unitByField', 'quality', 'dataDate', 'aliasIssues']);
  const aliasIssues = [];
  const aliases = [];
  const rawOwners = new Map();
  for (const [semantic, rawColumnValue] of Object.entries(binding?.fields || {})) {
    const rawColumn = String(rawColumnValue ?? '').trim();
    if (!semantic || !rawColumn || reservedKeys.has(semantic)) {
      aliasIssues.push({ code: 'INVALID_SEMANTIC_ALIAS', semantic, rawColumn });
      continue;
    }
    if (!columns.includes(rawColumn)) {
      aliasIssues.push({ code: 'MISSING_ALIAS_COLUMN', semantic, rawColumn });
      continue;
    }
    if (rawOwners.has(rawColumn) && rawOwners.get(rawColumn) !== semantic) {
      aliasIssues.push({ code: 'DUPLICATE_ALIAS_COLUMN', semantic, rawColumn });
      continue;
    }
    rawOwners.set(rawColumn, semantic);
    aliases.push([semantic, rawColumn]);
  }
  const rows = rawRows.map(rawRow => {
    const row = { ...rawRow };
    for (const [semantic, rawColumn] of aliases) {
      if (!Object.prototype.hasOwnProperty.call(row, semantic)
        && Object.prototype.hasOwnProperty.call(row, rawColumn)) row[semantic] = row[rawColumn];
    }
    return Object.freeze(row);
  });
  const columnsMeta = Object.freeze(Array.isArray(table.columnsMeta)
    ? table.columnsMeta.map(meta => isObject(meta) ? Object.freeze(cloneRawValue(meta)) : meta) : []);
  const unitByField = {
    ...(isObject(table.unitByField) ? cloneRawValue(table.unitByField) : {}),
    ...Object.fromEntries(columnsMeta.flatMap(meta => {
    const column = String(meta?.col ?? meta?.name ?? '').trim();
    const unit = meta?.unit ?? meta?.amountScale;
    return column && unit ? [[column, cloneRawValue(unit)]] : [];
    }))
  };
  const quality = readBatchQuality(table);
  const firstRow = Object.fromEntries(Object.entries(rows[0] || {})
    .filter(([key]) => !reservedKeys.has(key)));
  const result = {
    ...firstRow,
    blockId,
    rows,
    columns: Object.freeze([...columns]),
    columnsMeta,
    units: Object.freeze(cloneRawValue(binding?.units || {})),
    unitByField: Object.freeze(unitByField),
    ...(aliasIssues.length ? { aliasIssues } : {}),
    ...(quality ? { quality } : {}),
    ...(responseDataDate(table) ? { dataDate: responseDataDate(table) } : {})
  };
  return Object.freeze(result);
}

function collectBlockResults(results = {}) {
  return Object.fromEntries(Object.entries(results).flatMap(([, result]) => {
    const blockId = result?.blockId;
    const normalized = normalizeBlockResult(blockId, result?.binding, result?.response);
    return blockId !== undefined && blockId !== null && normalized ? [[String(blockId), normalized]] : [];
  }));
}

/**
 * 代码化大屏运行时取数。
 * - 请求身份来自发布 components + bindSnapshots，绝不按标题或旧组件推断；
 * - 队列并发固定不超过三，同一身份请求共享 in-flight Promise；
 * - 每次刷新/切换机构都会推进 generation，迟到结果只能被丢弃。
 */
export function usePanoramaData(viewSource, contextSource, options = {}) {
  const model = ref(createEmptyPanoramaModel());
  const loading = ref(false);
  const error = ref('');
  // branchTrend is an on-demand drill query.  A route orgCode supplies the
  // screen scope, but it must not implicitly trigger the branch drill on the
  // initial full-screen load; only an explicit map/ranking selection does.
  const branchOrgCode = ref('');
  const generation = ref(0);
  const screenGeneration = ref(0);
  const branchGeneration = ref(0);
  const renderedSingleOrgCode = ref('');
  const alive = ref(true);
  const disposed = ref(false);
  const requestCache = new Map();
  const taskQueue = [];
  const scheduledTasks = new Map();
  let activeTasks = 0;
  let pendingLoads = 0;
  let watchReady = false;
  const lastQueriedAt = ref('');
  let committedQueryIdentity = '';

  function currentView() { return valueOf(viewSource) || {}; }

  function schemaVersion(view, context) {
    const explicit = context.schemaVersion ?? context.runtimeSchemaVersion;
    return explicit === undefined || explicit === null || explicit === ''
      ? runtimeSchemaVersion(view) : explicit;
  }

  function makeRequest(bindingEntry, view, context, selectedBranch, batchId = '') {
    const schema = schemaVersion(view, context);
    const screenCode = String(context.screenCode || view.screenCode || view.screen_code || '').trim();
    const branchCode = String(selectedBranch || '').trim();
    // A CODE panorama is already scoped by its published screen.  A route
    // orgCode/empId must never narrow ordinary slots (it can be stale from a
    // previous detail view); only the explicit branchTrend drill carries the
    // selected authorised branch code.
    const requestContext = options.singleOrg
      ? { orgCode: contextOrgCode(context) }
      : bindingEntry.slot === 'branchTrend'
      ? { orgCode: branchCode }
      : {};
    const body = buildScreenDataRequest({
      schemaVersion: schema,
      previewState: view.state === 'draft' || context.previewState === 'draft' ? 'draft' : undefined,
      screenCode,
      blockId: bindingEntry.blockId,
      dsId: bindingEntry.binding.dsId,
      period: bindingEntry.binding.period || 'LATEST',
      dateFrom: context.dateFrom,
      dateTo: context.dateTo,
      contextParams: requestContext,
      batchId: batchId || undefined
    });
    return { body, schema };
  }

  function requestData(body) {
    const key = requestKey(body);
    if (requestCache.has(key)) return requestCache.get(key);
    const promise = Promise.resolve().then(() => queryScreenData(body));
    requestCache.set(key, promise);
    promise.then(
      () => { if (requestCache.get(key) === promise) requestCache.delete(key); },
      () => { if (requestCache.get(key) === promise) requestCache.delete(key); }
    );
    return promise;
  }

  function taskIsCurrent(task) {
    if (!alive.value || disposed.value) return false;
    return task.kind === 'branch'
      ? branchGeneration.value === task.token
      : screenGeneration.value === task.token;
  }

  function pumpQueue() {
    while (activeTasks < MAX_CONCURRENCY && taskQueue.length) {
      const task = taskQueue.shift();
      if (!taskIsCurrent(task)) {
        task.resolve({ cancelled: true });
        continue;
      }
      task.started = true;
      activeTasks += 1;
      requestData(task.item.body).then(
        response => task.resolve({ response }),
        requestError => task.resolve({ error: requestError })
      ).finally(() => {
        activeTasks -= 1;
        pumpQueue();
      });
    }
  }

  function enqueueRequest(item, kind, token) {
    const key = `${kind}|${requestKey(item.body)}`;
    const existing = scheduledTasks.get(key);
    if (existing) {
      // 同一刷新批次常会因手动双击产生重复请求；未开始的旧任务由新 generation 接管，
      // 这样它既不会被旧批次取消，也不会再发一遍网络请求。
      if (!existing.started) {
        existing.kind = kind;
        existing.token = token;
      }
      return existing.promise;
    }
    let resolveTask;
    const promise = new Promise(resolve => { resolveTask = resolve; });
    const task = { item, kind, token, resolve: resolveTask, promise, started: false };
    scheduledTasks.set(key, task);
    taskQueue.push(task);
    promise.finally(() => {
      if (scheduledTasks.get(key) === task) scheduledTasks.delete(key);
    });
    pumpQueue();
    return promise;
  }

  async function runQueue(requests, kind, token) {
    const settled = await Promise.all(requests.map(item => enqueueRequest(item, kind, token)));
    return settled.map((result, index) => ({ ...requests[index], ...result }));
  }

  async function loadExplicitComparisons(packageInfo, view, context, modelValue, generationToken, batchState) {
    const configs = explicitComparisonConfigs(packageInfo);
    const empty = (issues = []) => ({ results: {}, issues });
    if (!configs.length) return empty();
    const dates = comparisonDates(modelValue?.dataDate);
    const issues = [];
    if (!modelValue?.dataDate || !dates.year) {
      configs.forEach(([key]) => addIssue(issues, 'comparison', 'INVALID_DATA_DATE', '主数据日无效，无法计算比较年末', key));
      return empty(issues);
    }
    const historyByBlock = new Map([...packageInfo.slots.values()].map(entry => [Number(entry.blockId), entry]));
    const trendBlockIds = trustedHistoryTrendBlocks(packageInfo);
    const requestsByKey = new Map();
    for (const [configKey, config] of configs) {
      const blockId = config?.historyBlockId;
      const entry = Number.isSafeInteger(blockId) ? historyByBlock.get(blockId) : null;
      if (!entry || !trendBlockIds.has(blockId) || !isObject(entry.snapshot?.bind)) {
        addIssue(issues, 'comparison', 'HISTORY_SOURCE_NOT_TRUSTED', '比较历史区块必须来自当前可见 LINE_TREND 且具备可信绑定快照', configKey);
        continue;
      }
      const { body, schema } = makeRequest(entry, view, context, '', batchState?.quality?.batchId || '');
      const rangeBody = {
        ...body, period: 'RANGE', dateFrom: dates.year, dateTo: modelValue.dataDate,
        batchId: batchState?.quality?.batchId || undefined
      };
      const dateKey = requestKey(rangeBody);
      if (requestsByKey.has(dateKey)) continue;
      requestsByKey.set(dateKey, { blockId, entry, body: rangeBody, schema });
    }
    const requests = [...requestsByKey.values()];
    if (!requests.length) return empty(issues);
    const settled = await runQueue(requests, 'comparison', generationToken);
    if (!alive.value || disposed.value || screenGeneration.value !== generationToken) return empty(issues);
    const results = {};
    for (const item of settled) {
      if (item.cancelled) {
        addIssue(issues, 'comparison', 'STALE_RESPONSE', '比较历史响应已被新一轮刷新接管', String(item.blockId));
        continue;
      }
      const permission = permissionStatus(item.error);
      if (permission) return { results: {}, issues, permissionError: item.error };
      if (item.error) {
        addIssue(issues, 'comparison', 'REQUEST_FAILED', errorMessage(item.error), String(item.blockId));
        continue;
      }
      if (!item.response) {
        addIssue(issues, 'comparison', 'QUALITY_MISSING', '比较历史响应为空', String(item.blockId));
        continue;
      }
      if (batchState?.required) {
        const quality = readBatchQuality(item.response);
        const comparison = compareBatchQuality(batchState.quality, quality);
        if (!comparison.ok) {
          addIssue(issues, 'comparison', comparison.code, comparison.message, String(item.blockId));
          continue;
        }
      }
      const normalized = normalizeBlockResult(item.blockId, item.entry.binding, item.response);
      if (normalized) results[String(item.blockId)] = normalized;
      else addIssue(issues, 'comparison', 'INVALID_RESPONSE', '比较历史响应不是有效二维数据', String(item.blockId));
    }
    return { results, issues };
  }

  async function prefetchBranchTrends(modelValue, packageInfo, view, context, generationToken, allIssues, branchesResponse, branchesEntry, batchState = null) {
    if (options.singleOrg || packageInfo.retail || packageInfo.corporate || !branchesResponse || !Array.isArray(modelValue?.institutions)) return {};
    const entry = packageInfo.slots.get('branchTrend');
    if (!entry) return {};
    const directory = Array.isArray(view.panoramaInstitutions || view.panorama_institutions)
      ? (view.panoramaInstitutions || view.panorama_institutions) : [];
    const authorizedCodes = new Set(directory.map(item => String(typeof item === 'object' ? item?.orgCode : item || '').trim()).filter(Boolean));
    const branchColumn = branchesEntry?.binding?.fields?.orgCode;
    const branchColumnIndex = Array.isArray(branchesResponse.columns) ? branchesResponse.columns.indexOf(branchColumn) : -1;
    const sourceCodes = new Set((Array.isArray(branchesResponse.rows) ? branchesResponse.rows : [])
      .map(row => branchColumnIndex >= 0 ? String(row?.[branchColumnIndex] || '').trim() : '')
      .filter(Boolean));
    const codes = modelValue.institutions
      .map(item => String(item?.orgCode || '').trim())
      .filter(code => code && authorizedCodes.has(code) && sourceCodes.has(code))
      // The live batch contract prefetches only the four institution trends
      // used by the current screen; retain the legacy 20-row compatibility
      // path for old non-batch packages.
      .slice(0, batchState?.required ? 4 : 20);
    if (!codes.length) return {};
    const requests = codes.map(code => {
      const { body, schema } = makeRequest(entry, view, context, code, batchState?.quality?.batchId || '');
      return { slot: 'branchTrend', entry, body, schema, branchOrgCode: code };
    });
    const settled = await runQueue(requests, 'screen', generationToken);
    if (!alive.value || disposed.value || screenGeneration.value !== generationToken) return {};
    const permissionItem = settled.find(item => permissionStatus(item.error));
    if (permissionItem) return { permissionError: permissionItem.error };
    const batchQualities = [];
    for (const item of settled) {
      const institution = modelValue.institutions.find(candidate => String(candidate?.orgCode) === item.branchOrgCode);
      if (!institution) continue;
      if (item.cancelled) continue;
      if (item.error) {
        if (batchState?.required) {
          return { qualityError: batchGuard('REQUEST_FAILED', `机构 ${item.branchOrgCode}：${errorMessage(item.error)}`) };
        }
        const message = `机构 ${item.branchOrgCode}：${errorMessage(item.error)}`;
        institution.trendIssue = message;
        addIssue(allIssues, 'branchTrend', 'PREFETCH_FAILED', message, item.branchOrgCode);
        continue;
      }
      if (!item.response) {
        if (batchState?.required) {
          return { qualityError: batchGuard('QUALITY_MISSING', `机构 ${item.branchOrgCode}：响应缺少 report Quality`) };
        }
        institution.trendIssue = `机构 ${item.branchOrgCode}：数据源返回为空`;
        addIssue(allIssues, 'branchTrend', 'PREFETCH_NO_VALUES', institution.trendIssue, item.branchOrgCode);
        continue;
      }
      if (batchState?.required) {
        const quality = readBatchQuality(item.response);
        const comparison = compareBatchQuality(batchState.quality, quality);
        if (!comparison.ok) {
          return { qualityError: batchGuard(comparison.code, `机构 ${item.branchOrgCode}：${comparison.message}`) };
        }
        batchQualities.push(quality);
      }
      applyBranchTrend(modelValue, item.response, item.entry.binding, item.branchOrgCode);
      institution.trendIssue = Array.isArray(institution.trend) && institution.trend.length
        ? '' : `机构 ${item.branchOrgCode}：最新周期无有效趋势值`;
    }
    return { qualities: batchQualities };
  }

  async function load(loadOptions = {}) {
    if (!alive.value || disposed.value) return model.value;
    const view = currentView();
    const context = contextSnapshot(contextSource);
    const packageInfo = packageBindings(view);
    const currentQueryIdentity = queryIdentity(view, context, options);
    const singleOrgScope = options.singleOrg ? resolveSingleOrgScope(view, context) : null;
    const retail = packageInfo.retail;
    const requiresBatch = Boolean(options.batchRequired && !retail && !packageInfo.corporate);
    const requestedPreserveModel = Boolean(loadOptions.preserveModel);
    const onlySlots = Array.isArray(loadOptions.onlySlots)
      ? new Set(loadOptions.onlySlots) : null;
    const canPreserveSingleOrgModel = !options.singleOrg
      || (singleOrgScope?.valid && renderedSingleOrgCode.value === singleOrgScope.orgCode);
    const preserveModel = requestedPreserveModel && canPreserveSingleOrgModel;
    const retainablePreviousBatch = !preserveModel
      && Boolean(committedQueryIdentity && committedQueryIdentity === currentQueryIdentity)
      && isUsableBatchQuality(model.value?.quality);
    const branchOnly = !retail && !packageInfo.corporate && preserveModel && onlySlots?.has('branchTrend');
    const kind = branchOnly ? 'branch' : 'screen';
    const currentGeneration = kind === 'branch'
      ? ++branchGeneration.value : ++screenGeneration.value;
    // A full refresh changes the screen/context and invalidates prior branch selections.
    if (kind === 'screen') branchGeneration.value += 1;
    generation.value += 1;
    const queriedAt = new Date().toISOString();
    lastQueriedAt.value = queriedAt;
    if (!preserveModel) {
      if (!retainablePreviousBatch) model.value = emptyModel(retail, view, queriedAt);
      model.value.configuredSlots = [...packageInfo.slots.keys()];
      if (options.singleOrg) renderedSingleOrgCode.value = '';
      error.value = '';
    }
    if (branchOnly) {
      const branchEntry = packageInfo.slots.get('branchTrend');
      const branchBlockKey = branchEntry?.blockId === undefined || branchEntry?.blockId === null
        ? '' : String(branchEntry.blockId);
      if (branchBlockKey && model.value?.blockResults && Object.prototype.hasOwnProperty.call(model.value.blockResults, branchBlockKey)) {
        const { [branchBlockKey]: _removed, ...remainingBlocks } = model.value.blockResults;
        model.value = { ...model.value, blockResults: remainingBlocks };
      }
    }
    pendingLoads += 1;
    loading.value = true;
    try {
      if (options.singleOrg && !singleOrgScope.valid) {
        // Invalid context changes must invalidate both full-screen and drill generations;
        // otherwise a response from the previously selected institution could repopulate
        // the cleared single-org model.
        screenGeneration.value += 1;
        branchGeneration.value += 1;
        const nextModel = emptyModel(retail, view, queriedAt);
        nextModel.configuredSlots = [...packageInfo.slots.keys()];
        nextModel.issues = [...packageInfo.issues, {
          slot: 'singleOrg',
          code: 'SINGLE_ORG_SCOPE_INVALID',
          message: singleOrgScope.message
        }];
        model.value = nextModel;
        renderedSingleOrgCode.value = '';
        error.value = singleOrgScope.message;
        return nextModel;
      }
      if (options.singleOrg) branchOrgCode.value = singleOrgScope.orgCode;
      const { slots } = packageInfo;
      const allIssues = [...packageInfo.issues];
      const selectedBranch = String(loadOptions.branchOrgCode ?? branchOrgCode.value ?? '').trim();
      const retainPreviousBatch = guard => {
        const previous = model.value;
        const next = { ...previous,
          quality: previous?.quality || null,
          qualityGuard: { ...guard, status: 'STALE', refreshStatus: guard?.status || null },
          queriedAt,
          configuredSlots: [...slots.keys()],
          permissionStatus: null,
          issues: [...(previous?.issues || []), { slot: 'batch', code: guard?.code, message: guard?.message }]
        };
        model.value = next;
        error.value = guard?.message || '刷新未完成，保留上一完整批次';
        return next;
      };
      const clearBatchModel = (quality, guard) => {
        if (retainablePreviousBatch && guard?.code !== 'PERMISSION_DENIED') return retainPreviousBatch(guard);
        const next = emptyModel(retail, view, queriedAt, quality, guard);
        next.configuredSlots = [...slots.keys()];
        if (guard) addIssue(allIssues, 'batch', guard.code, guard.message);
        next.issues = [...allIssues];
        model.value = next;
        return next;
      };
      const requests = [];
      for (const [slot, entry] of slots.entries()) {
        if (onlySlots && !onlySlots.has(slot)) continue;
        if (options.singleOrg && (slot === 'ranking' || slot === 'citySummary')) continue;
        if (slot === 'branchTrend' && !branchOnly) {
          // branchTrend is deliberately lazy: it is fetched only after an
          // explicit institution selection, so an untouched slot is not an
          // error on the full-screen request.
          continue;
        }
        if (slot === 'branchTrend' && !selectedBranch) {
          addIssue(allIssues, slot, 'MISSING_BRANCH_CONTEXT', '选中支行后才能查询支行趋势');
          continue;
        }
        try {
          const requestBatchId = requiresBatch && branchOnly ? model.value?.quality?.batchId || '' : '';
          const { body, schema } = makeRequest(entry, view, context, selectedBranch, requestBatchId);
          requests.push({ slot, entry, body, schema });
        } catch (requestError) {
          addIssue(allIssues, slot, 'INVALID_REQUEST', errorMessage(requestError, '运行请求不合法'));
        }
      }
      let settled = [];
      const batchState = { required: requiresBatch, quality: null, qualities: [] };
      const isCurrent = () => alive.value && !disposed.value
        && (kind === 'branch' ? branchGeneration.value === currentGeneration : screenGeneration.value === currentGeneration);

      if (requiresBatch && branchOnly) {
        batchState.quality = model.value?.quality || null;
        if (!isUsableBatchQuality(batchState.quality)) {
          return clearBatchModel(batchState.quality, batchGuard(
            'NO_COMPLETE_BATCH', '当前页面没有可用于机构下钻的完整批次'
          ));
        }
      }

      if (requiresBatch && !branchOnly) {
        const anchor = requests.find(item => String(item.entry?.binding?.period || '').toUpperCase() === 'LATEST');
        if (!anchor) {
          return clearBatchModel(null, batchGuard('NO_LATEST_BATCH_ANCHOR', '本轮没有可锁定批次的 LATEST 槽位', 'NO_COMPLETE_BATCH'));
        }
        const anchorSettled = await runQueue([anchor], kind, currentGeneration);
        if (!isCurrent()) return model.value;
        const anchorItem = anchorSettled[0];
        const anchorPermission = permissionStatus(anchorItem?.error);
        if (anchorPermission) {
          model.value = emptyModel(retail, view, queriedAt);
          model.value.configuredSlots = [...slots.keys()];
          model.value.permissionStatus = anchorPermission;
          error.value = errorMessage(anchorItem.error, `没有权限（${anchorPermission}）`);
          return model.value;
        }
        if (anchorItem?.error) {
          return clearBatchModel(null, batchGuard('REQUEST_FAILED', errorMessage(anchorItem.error)));
        }
        const anchorQuality = readBatchQuality(anchorItem?.response);
        if (!isUsableBatchQuality(anchorQuality)) {
          const status = anchorQuality?.status === 'NO_COMPLETE_BATCH' ? 'NO_COMPLETE_BATCH' : 'PARTIAL';
          return clearBatchModel(anchorQuality, batchGuard(
            anchorQuality ? 'QUALITY_NOT_USABLE' : 'QUALITY_MISSING',
            anchorQuality?.message || '首个 LATEST 响应没有可用完整批次', status
          ));
        }
        batchState.quality = anchorQuality;
        batchState.qualities.push(anchorQuality);
        const rest = requests.filter(item => item !== anchor).map(item => ({
          ...item,
          body: { ...item.body, batchId: anchorQuality.batchId }
        }));
        const restSettled = await runQueue(rest, kind, currentGeneration);
        if (!isCurrent()) return model.value;
        settled = [anchorItem, ...restSettled];
      } else {
        settled = await runQueue(requests, kind, currentGeneration);
      }
      const isCurrentLoad = alive.value && !disposed.value
        && (kind === 'branch' ? branchGeneration.value === currentGeneration : screenGeneration.value === currentGeneration);
      if (!isCurrentLoad) return model.value;

      const permissionError = settled.find(item => permissionStatus(item.error));
      if (permissionError) {
        model.value = emptyModel(retail, view, queriedAt);
        model.value.configuredSlots = [...slots.keys()];
        model.value.permissionStatus = permissionStatus(permissionError.error);
        error.value = errorMessage(permissionError.error, `没有权限（${permissionStatus(permissionError.error)}）`);
        return model.value;
      }

      if (requiresBatch) {
        for (const item of settled) {
          if (item.cancelled) {
            return clearBatchModel(batchState.quality, batchGuard('STALE_RESPONSE', '本轮请求已被新一轮刷新接管', 'PARTIAL'));
          }
          if (item.error) {
            return clearBatchModel(batchState.quality, batchGuard('REQUEST_FAILED', errorMessage(item.error)));
          }
          const quality = readBatchQuality(item.response);
          const comparison = compareBatchQuality(batchState.quality, quality);
          if (!comparison.ok) {
            return clearBatchModel(batchState.quality, batchGuard(comparison.code, comparison.message));
          }
          batchState.qualities.push(quality);
        }
      }

      const resultMap = {};
      const branchTrendResult = settled.find(item => item.slot === 'branchTrend' && !item.error && !item.cancelled);
      for (const item of settled) {
        if (item.cancelled) continue;
        if (item.error) {
          addIssue(allIssues, item.slot, 'REQUEST_FAILED', errorMessage(item.error), 'request');
          continue;
        }
        resultMap[item.slot] = { blockId: item.entry.blockId, binding: item.entry.binding, response: item.response };
      }

      if (branchOnly) {
        // branchTrend 是点选机构后的独立查询，保留已经成功取到的全屏模型。
        const nextModel = model.value;
        if (branchTrendResult) {
          applyBranchTrend(nextModel, branchTrendResult.response, branchTrendResult.entry.binding, selectedBranch);
        }
        // A new branch selection replaces the previous branchTrend state. Do
        // not keep an error from branch A visible after branch B succeeds (or
        // accumulate the same failure on repeated clicks).
        const screenIssues = (nextModel.issues || []).filter(item => item?.slot !== 'branchTrend');
        const branchIssues = allIssues.filter(item => item?.slot === 'branchTrend');
        nextModel.issues = [...screenIssues, ...branchIssues];
        const branchBlocks = collectBlockResults(resultMap);
        if (Object.keys(branchBlocks).length) {
          nextModel.blockResults = { ...(nextModel.blockResults || {}), ...branchBlocks };
        }
        if (requiresBatch) {
          nextModel.quality = mergeBatchQualities([...(batchState.qualities || [])]);
          nextModel.batchId = nextModel.quality?.batchId || null;
          nextModel.qualityGuard = null;
          nextModel.queriedAt = queriedAt;
        }
        model.value = nextModel;
      } else {
        const adapterInstitutions = options.singleOrg
          ? singleOrgScope.directory
          : (Array.isArray(view.panoramaInstitutions || view.panorama_institutions)
            ? (view.panoramaInstitutions || view.panorama_institutions) : []);
        const adapterView = options.singleOrg
          ? { ...view, panoramaInstitutions: adapterInstitutions, panorama_institutions: adapterInstitutions }
          : view;
        const nextModel = packageInfo.corporate
          ? adaptCorporateResults(resultMap, { view: adapterView, panoramaInstitutions: adapterInstitutions })
          : retail
          ? adaptRetailResults(resultMap, {
            view: adapterView,
            panoramaInstitutions: adapterInstitutions
          })
          : adaptPanoramaResults(resultMap, {
            view: adapterView,
            panoramaInstitutions: adapterInstitutions,
            title: view.screenName || view.screen_name
          });
        const prefetchResult = await prefetchBranchTrends(nextModel, packageInfo, view, context, currentGeneration, allIssues, resultMap.branches?.response, packageInfo.slots.get('branches'), batchState);
        if (!alive.value || disposed.value || screenGeneration.value !== currentGeneration) return model.value;
        if (prefetchResult?.permissionError) {
          model.value = emptyModel(retail, view, queriedAt);
          model.value.configuredSlots = [...slots.keys()];
          model.value.permissionStatus = permissionStatus(prefetchResult.permissionError);
          error.value = errorMessage(prefetchResult.permissionError, `没有权限（${permissionStatus(prefetchResult.permissionError)}）`);
          return model.value;
        }
        if (prefetchResult?.qualityError) {
          return clearBatchModel(batchState.quality, prefetchResult.qualityError);
        }
        nextModel.issues = [...allIssues, ...(nextModel.issues || [])];
        nextModel.sourceQualities = collectSourceQualities(resultMap);
        nextModel.sourceDates = collectSourceDates(resultMap);
        nextModel.sourceMetadata = collectSourceMetadata(resultMap);
        nextModel.blockResults = collectBlockResults(resultMap);
        const comparisonLoad = await loadExplicitComparisons(packageInfo, view, context, nextModel, currentGeneration, batchState);
        if (!alive.value || disposed.value || screenGeneration.value !== currentGeneration) return model.value;
        if (comparisonLoad?.permissionError) {
          model.value = emptyModel(retail, view, queriedAt);
          model.value.configuredSlots = [...slots.keys()];
          model.value.permissionStatus = permissionStatus(comparisonLoad.permissionError);
          error.value = errorMessage(comparisonLoad.permissionError, `没有权限（${permissionStatus(comparisonLoad.permissionError)}）`);
          return model.value;
        }
        nextModel.comparisonResults = comparisonLoad?.results || {};
        nextModel.issues = [...(nextModel.issues || []), ...(comparisonLoad?.issues || [])];
        nextModel.configuredSlots = [...slots.keys()];
        nextModel.permissionStatus = null;
        if (requiresBatch) {
          nextModel.quality = mergeBatchQualities([
            ...(batchState.qualities || []), ...(prefetchResult?.qualities || [])
          ]);
          nextModel.batchId = nextModel.quality?.batchId || null;
          nextModel.qualityGuard = null;
          nextModel.queriedAt = queriedAt;
        }
        model.value = nextModel;
      }
      if (options.singleOrg && !branchOnly) renderedSingleOrgCode.value = singleOrgScope.orgCode;
      committedQueryIdentity = currentQueryIdentity;
      error.value = '';
      return model.value;
    } finally {
      pendingLoads = Math.max(0, pendingLoads - 1);
      if (!pendingLoads) loading.value = false;
    }
  }

  async function refresh(refreshOptions = {}) {
    return load(refreshOptions);
  }

  async function selectBranch(orgCode) {
    const next = String(orgCode || '').trim();
    if (options.singleOrg) {
      const scope = resolveSingleOrgScope(currentView(), contextSnapshot(contextSource));
      branchOrgCode.value = scope.orgCode;
      if (!scope.valid) return load();
      if (!next || next !== scope.orgCode) return model.value;
      if (renderedSingleOrgCode.value !== scope.orgCode) return load();
      if (isRetailPackage(screenPackage(currentView())) || isCorporatePackage(screenPackage(currentView()))) return model.value;
      const selectedInstitution = model.value?.institutions?.find(item => String(item?.orgCode) === scope.orgCode);
      if (selectedInstitution) {
        selectedInstitution.trend = [];
        selectedInstitution.trendIssue = '';
      }
      return load({ onlySlots: ['branchTrend'], branchOrgCode: scope.orgCode, preserveModel: true });
    }
    branchOrgCode.value = next;
    if (!next) return model.value;
    if (isRetailPackage(screenPackage(currentView())) || isCorporatePackage(screenPackage(currentView()))) return model.value;
    const selectedInstitution = model.value?.institutions?.find(item => String(item?.orgCode) === next);
    if (selectedInstitution) {
      selectedInstitution.trend = [];
      selectedInstitution.trendIssue = '';
    }
    return load({ onlySlots: ['branchTrend'], branchOrgCode: next, preserveModel: true });
  }

  function dispose() {
    if (disposed.value) return;
    alive.value = false;
    disposed.value = true;
    generation.value += 1;
    loading.value = false;
  }

  const slotIssues = computed(() => {
    const grouped = {};
    for (const item of model.value.issues || []) {
      if (!item?.slot) continue;
      (grouped[item.slot] ||= []).push(item);
    }
    return grouped;
  });

  const instance = getCurrentInstance();
  if (instance) {
    onBeforeUnmount(dispose);
    if (options.autoLoad !== false) onMounted(() => { watchReady = true; refresh(); });
  }
  if (options.watch !== false) {
    const watchOptions = { flush: options.singleOrg ? 'sync' : 'pre' };
    watch(() => queryIdentity(currentView(), contextSnapshot(contextSource), options), (next, previous) => {
      if (!watchReady || !alive.value) return;
      if (next === previous) return;
      refresh();
    }, watchOptions);
  }

  return {
    model,
    loading,
    error,
    lastQueriedAt,
    slotIssues,
    branchOrgCode,
    generation,
    refresh,
    selectBranch,
    dispose
  };
}

export {
  MAX_CONCURRENCY,
  packageBindings,
  queryIdentity,
  collectSourceDates,
  collectSourceMetadata,
  normalizeBlockResult,
  collectBlockResults
};
