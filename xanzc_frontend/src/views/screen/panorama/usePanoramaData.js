import { computed, getCurrentInstance, isRef, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { queryScreenData } from '@/api/screen';
import { buildScreenDataRequest, runtimeSchemaVersion } from '@/utils/screenScope';
import {
  adaptPanoramaResults,
  applyBranchTrend,
  createEmptyPanoramaModel
} from './dataAdapter';
import { adaptRetailResults, createEmptyRetailModel } from './retailDataAdapter';
import {
  RETAIL_SLOT_ORDER,
  RETAIL_TEMPLATE,
  isRetailBindingSlot,
  normalizeRetailBinding
} from './retailBindings';
import { BRANCH_SLOT_ORDER, isBindingSlot, normalizeBinding } from './bindings';

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

function allowedSlotsForPackage(pkg) {
  return new Set(isRetailPackage(pkg) ? RETAIL_SLOT_ORDER : BRANCH_SLOT_ORDER);
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
    const binding = retailPackage && isRetailBindingSlot(slot)
      ? normalizeRetailBinding(snapshot.bind, slot)
      : normalizeBinding(snapshot.bind, slot);
    slots.set(slot, { slot, blockId, binding, component, snapshot });
  }
  return { package: pkg, slots, issues, template: presentationTemplate(pkg), retail: retailPackage };
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

  function currentView() { return valueOf(viewSource) || {}; }

  function schemaVersion(view, context) {
    const explicit = context.schemaVersion ?? context.runtimeSchemaVersion;
    return explicit === undefined || explicit === null || explicit === ''
      ? runtimeSchemaVersion(view) : explicit;
  }

  function makeRequest(bindingEntry, view, context, selectedBranch) {
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
      contextParams: requestContext
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

  async function load(loadOptions = {}) {
    if (!alive.value || disposed.value) return model.value;
    const view = currentView();
    const context = contextSnapshot(contextSource);
    const packageInfo = packageBindings(view);
    const retail = packageInfo.retail;
    const preserveModel = Boolean(loadOptions.preserveModel);
    const onlySlots = Array.isArray(loadOptions.onlySlots)
      ? new Set(loadOptions.onlySlots) : null;
    const branchOnly = !retail && preserveModel && onlySlots?.has('branchTrend');
    const kind = branchOnly ? 'branch' : 'screen';
    const currentGeneration = kind === 'branch'
      ? ++branchGeneration.value : ++screenGeneration.value;
    // A full refresh changes the screen/context and invalidates prior branch selections.
    if (kind === 'screen') branchGeneration.value += 1;
    generation.value += 1;
    if (!preserveModel) {
      model.value = retail ? createEmptyRetailModel({ view }) : createEmptyPanoramaModel();
      error.value = '';
    }
    pendingLoads += 1;
    loading.value = true;
    try {
      const { slots } = packageInfo;
      const allIssues = [...packageInfo.issues];
      const selectedBranch = String(loadOptions.branchOrgCode ?? branchOrgCode.value ?? '').trim();
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
          const { body, schema } = makeRequest(entry, view, context, selectedBranch);
          requests.push({ slot, entry, body, schema });
        } catch (requestError) {
          addIssue(allIssues, slot, 'INVALID_REQUEST', errorMessage(requestError, '运行请求不合法'));
        }
      }
      const settled = await runQueue(requests, kind, currentGeneration);
      const isCurrentLoad = alive.value && !disposed.value
        && (kind === 'branch' ? branchGeneration.value === currentGeneration : screenGeneration.value === currentGeneration);
      if (!isCurrentLoad) return model.value;

      const permissionError = settled.find(item => permissionStatus(item.error));
      if (permissionError) {
        model.value = retail ? createEmptyRetailModel({ view }) : createEmptyPanoramaModel();
        error.value = errorMessage(permissionError.error, `没有权限（${permissionStatus(permissionError.error)}）`);
        return model.value;
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
        model.value = nextModel;
      } else {
        const nextModel = retail
          ? adaptRetailResults(resultMap, {
            view,
            panoramaInstitutions: view.panoramaInstitutions || view.panorama_institutions
          })
          : adaptPanoramaResults(resultMap, {
            view,
            panoramaInstitutions: view.panoramaInstitutions || view.panorama_institutions,
            title: view.screenName || view.screen_name
          });
        nextModel.issues = [...allIssues, ...(nextModel.issues || [])];
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
    if (isRetailPackage(screenPackage(currentView()))) return model.value;
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
    slotIssues,
    branchOrgCode,
    generation,
    refresh,
    selectBranch,
    dispose
  };
}

export { MAX_CONCURRENCY, packageBindings };
