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
import { BRANCH_SLOT_ORDER, isBindingSlot, normalizeBinding } from './bindings';
import {
  compareBatchQuality,
  isUsableBatchQuality,
  mergeBatchQualities,
  readBatchQuality
} from './batchQuality';

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

function allowedSlotsForPackage(pkg) {
  return new Set(isCorporatePackage(pkg) ? CORPORATE_SLOT_ORDER : isRetailPackage(pkg) ? RETAIL_SLOT_ORDER : BRANCH_SLOT_ORDER);
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
  return next;
}

function contextSnapshot(context) {
  const value = valueOf(context) || {};
  return isObject(value) ? value : {};
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
  const alive = ref(true);
  const disposed = ref(false);
  const requestCache = new Map();
  const taskQueue = [];
  const scheduledTasks = new Map();
  let activeTasks = 0;
  let pendingLoads = 0;
  let watchReady = false;
  const lastQueriedAt = ref('');

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
    const requestContext = bindingEntry.slot === 'branchTrend'
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

  async function prefetchBranchTrends(modelValue, packageInfo, view, context, generationToken, allIssues, branchesResponse, branchesEntry, batchState = null) {
    if (packageInfo.retail || packageInfo.corporate || !branchesResponse || !Array.isArray(modelValue?.institutions)) return {};
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
    const retail = packageInfo.retail;
    const requiresBatch = Boolean(options.batchRequired && !retail && !packageInfo.corporate);
    const preserveModel = Boolean(loadOptions.preserveModel);
    const onlySlots = Array.isArray(loadOptions.onlySlots)
      ? new Set(loadOptions.onlySlots) : null;
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
      model.value = emptyModel(retail, view, queriedAt);
      error.value = '';
    }
    pendingLoads += 1;
    loading.value = true;
    try {
      const { slots } = packageInfo;
      const allIssues = [...packageInfo.issues];
      const selectedBranch = String(loadOptions.branchOrgCode ?? branchOrgCode.value ?? '').trim();
      const clearBatchModel = (quality, guard) => {
        const next = emptyModel(retail, view, queriedAt, quality, guard);
        if (guard) addIssue(allIssues, 'batch', guard.code, guard.message);
        next.issues = [...allIssues];
        model.value = next;
        return next;
      };
      const requests = [];
      for (const [slot, entry] of slots.entries()) {
        if (onlySlots && !onlySlots.has(slot)) continue;
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
        resultMap[item.slot] = { binding: item.entry.binding, response: item.response };
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
        if (requiresBatch) {
          nextModel.quality = mergeBatchQualities([...(batchState.qualities || [])]);
          nextModel.batchId = nextModel.quality?.batchId || null;
          nextModel.qualityGuard = null;
          nextModel.queriedAt = queriedAt;
        }
        model.value = nextModel;
      } else {
        const nextModel = packageInfo.corporate
          ? adaptCorporateResults(resultMap, { view, panoramaInstitutions: view.panoramaInstitutions || view.panorama_institutions })
          : retail
          ? adaptRetailResults(resultMap, {
            view,
            panoramaInstitutions: view.panoramaInstitutions || view.panorama_institutions
          })
          : adaptPanoramaResults(resultMap, {
            view,
            panoramaInstitutions: view.panoramaInstitutions || view.panorama_institutions,
            title: view.screenName || view.screen_name
          });
        const prefetchResult = await prefetchBranchTrends(nextModel, packageInfo, view, context, currentGeneration, allIssues, resultMap.branches?.response, packageInfo.slots.get('branches'), batchState);
        if (!alive.value || disposed.value || screenGeneration.value !== currentGeneration) return model.value;
        if (prefetchResult?.permissionError) {
          model.value = emptyModel(retail, view, queriedAt);
          error.value = errorMessage(prefetchResult.permissionError, `没有权限（${permissionStatus(prefetchResult.permissionError)}）`);
          return model.value;
        }
        if (prefetchResult?.qualityError) {
          return clearBatchModel(batchState.quality, prefetchResult.qualityError);
        }
        nextModel.issues = [...allIssues, ...(nextModel.issues || [])];
        nextModel.sourceQualities = collectSourceQualities(resultMap);
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
    watch(() => [valueOf(viewSource), contextSnapshot(contextSource)], () => {
      if (!watchReady || !alive.value) return;
      refresh();
    }, { deep: true });
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

export { MAX_CONCURRENCY, packageBindings };
