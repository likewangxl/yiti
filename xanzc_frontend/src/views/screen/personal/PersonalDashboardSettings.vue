<template>
  <section
    v-if="visible"
    class="personal-dashboard-settings"
    data-testid="personal-dashboard-settings"
  >
    <header><h2>个人经营驾驶舱设置</h2></header>
    <p v-if="loading" role="status">正在读取个人驾驶舱…</p>
    <p v-if="error" data-testid="personal-error" role="alert">{{ error }}</p>
    <p v-if="success" role="status">{{ success }}</p>
    <p v-if="!publishPermissionGranted" class="hint">
      当前账号没有发布权限，请联系管理员授权。
    </p>
    <template v-if="!loading && !screen && !error">
      <p>未找到 PERSONAL_COCKPIT 配置。</p>
      <button
        type="button"
        data-testid="personal-create-screen"
        @click="createScreen"
      >
        创建个人经营驾驶舱配置
      </button>
      <p class="hint">新屏默认没有查看权限，请在现有角色管理中授权。</p>
    </template>
    <form v-else-if="screen && canvas" @submit.prevent="save">
      <div class="personal-workbench">
        <aside class="personal-workbench__preview">
          <h3>页面缩略图</h3>
          <p class="hint">点击页面中的区域进行配置。</p>
          <PersonalConfigThumbnail :settings="settings" :selected-key="selectedConfigKey" :disabled="loading || saving || publishing || accessVisible" @select="selectConfig" />
          <p class="personal-workbench__caption">标题实时展示 · 数据仅为占位</p>
        </aside>
        <section class="personal-workbench__editor">
          <div class="personal-workbench__editor-heading">
            <div><h3>配置信息</h3><p>当前定位：<strong>{{ selectedConfigLabel }}</strong></p></div>
            <span class="personal-workbench__source">个人驾驶舱配置</span>
          </div>
          <fieldset :disabled="loading || saving || publishing || accessVisible">
        <label v-show="selectedConfigKey === 'page-title'">标题 <input v-model="settings.title" /></label>
        <label v-show="selectedConfigKey === 'page-title'">指标区标题 <input v-model="settings.metricsTitle" /></label>
        <label v-show="selectedConfigKey === 'page-title'">趋势区标题 <input v-model="settings.trendTitle" /></label>
        <label v-show="selectedConfigKey === 'page-title'">机构贡献标题 <input v-model="settings.shareTitle" /></label>
        <label v-show="selectedConfigKey === 'page-title'">目标完成标题 <input v-model="settings.completionTitle" /></label>
        <label v-show="selectedConfigKey === 'page-title'"
          >默认范围
          <select v-model="settings.defaultRange">
            <option value="7d">近7日</option>
            <option value="6m">近6月</option>
            <option value="1y">近一年</option>
          </select></label
        >
        <div v-if="selectedConfigKey === 'metrics'" class="personal-region-editor" data-testid="personal-region-editor-metrics">
          <label>指标区标题 <input v-model="settings.metricsTitle" /></label>
        </div>
        <div v-if="selectedConfigKey === 'trend'" class="personal-region-editor" data-testid="personal-region-editor-trend">
          <label>趋势区标题 <input v-model="settings.trendTitle" /></label>
          <label>默认范围 <select v-model="settings.defaultRange"><option value="7d">近7日</option><option value="6m">近6月</option><option value="1y">近一年</option></select></label>
          <div class="binding-summary"><strong>历史趋势</strong><span v-for="metric in settings.metrics" :key="`trend-${metric._key}`">{{ metric.metricName || metric.metricCode || '未命名' }}：{{ metric.bindings.history?.valueField || '值字段未配置' }} / {{ metric.bindings.history?.dateField || '日期字段未配置' }}</span></div>
        </div>
        <div v-if="selectedConfigKey === 'organization'" class="personal-region-editor" data-testid="personal-region-editor-organization">
          <label>机构贡献标题 <input v-model="settings.shareTitle" /></label>
          <div class="binding-summary"><strong>当前值 + 机构贡献</strong><span v-for="metric in settings.metrics" :key="`org-${metric._key}`">{{ metric.metricName || metric.metricCode || '未命名' }}：当前值 {{ metric.bindings.current?.valueField || '未配置' }}；机构贡献 {{ metric.bindings.organization?.valueField || '未配置' }}</span></div>
        </div>
        <div v-if="selectedConfigKey === 'target'" class="personal-region-editor" data-testid="personal-region-editor-target">
          <label>目标完成标题 <input v-model="settings.completionTitle" /></label>
          <div class="binding-summary"><strong>当前值 + 目标值</strong><span v-for="metric in settings.metrics" :key="`target-${metric._key}`">{{ metric.metricName || metric.metricCode || '未命名' }}：当前值 {{ metric.bindings.current?.valueField || '未配置' }}；目标值 {{ metric.bindings.target?.valueField || '未配置' }}</span></div>
        </div>
        <template v-for="(metric, index) in settings.metrics" :key="metric._key">
        <div
          v-show="selectedConfigKey === 'metrics' || selectedConfigKey === `metric-${metric._key || metric.metricCode || index}` || ['trend', 'organization', 'target'].includes(selectedConfigKey)"
          :data-testid="`personal-metric-${index}`"
          :data-config-key="`metric-${metric.metricCode || metric._key || index}`"
          :class="{ 'is-config-selected': selectedConfigKey === `metric-${metric._key || metric.metricCode || index}` }"
          class="metric"
        >
          <h3>指标 {{ index + 1 }}</h3>
          <label v-show="selectedConfigKey === 'metrics' || selectedConfigKey.startsWith('metric-')">编码 <input v-model="metric.metricCode" /></label
          ><label v-show="selectedConfigKey === 'metrics' || selectedConfigKey.startsWith('metric-')">名称 <input v-model="metric.metricName" /></label>
          <label v-show="selectedConfigKey === 'metrics' || selectedConfigKey.startsWith('metric-')">单位 <input v-model="metric.unit" /></label
          ><label v-show="selectedConfigKey === 'metrics' || selectedConfigKey.startsWith('metric-')">颜色 <input v-model="metric.color" type="color" /></label>
          <div v-for="kind in bindingKindsFor(selectedConfigKey)" :key="kind" class="binding">
            <strong>{{ bindingLabels[kind] }}</strong>
            <label
              >数据来源<select v-model="metric.bindings[kind].dsId">
                <option value="">未绑定</option>
                <option
                  v-for="source in datasourceOptionsFor(kind)"
                  :key="source.id"
                  :value="source.id"
                >
                  {{ source.dsName || source.name || source.id }}
                </option>
              </select></label
            >
            <label
              >值字段<input
                v-model="metric.bindings[kind].valueField"
                placeholder="选择或输入输出列"
                :list="`personal-fields-${index}-${kind}`"
            /></label>
            <label
              >日期字段<input
                v-model="metric.bindings[kind].dateField"
                placeholder="选择或输入输出列"
                :list="`personal-fields-${index}-${kind}`"
            /></label>
            <label
              >计量单位<input
                v-model="metric.bindings[kind].unit"
                placeholder="与指标单位一致"
            /></label>
            <datalist :id="`personal-fields-${index}-${kind}`">
              <option
                v-for="field in bindingFields(metric.bindings[kind])"
                :key="field"
                :value="field"
              />
            </datalist>
          </div>
          <button v-show="selectedConfigKey === 'metrics' || selectedConfigKey.startsWith('metric-')" type="button" @click="removeMetric(index)">删除指标</button>
          <button v-show="selectedConfigKey === 'metrics' || selectedConfigKey.startsWith('metric-')" v-if="index" type="button" @click="moveMetric(index, -1)">
            上移</button
          ><button v-show="selectedConfigKey === 'metrics' || selectedConfigKey.startsWith('metric-')"
            v-if="index < settings.metrics.length - 1"
            type="button"
            @click="moveMetric(index, 1)"
          >
            下移
          </button>
        </div>
        </template>
        <button
          type="button"
          data-testid="personal-add-metric"
          @click="addMetric"
        >
          添加指标
        </button>
        <label
          >发布原因 <input v-model="reason" placeholder="请填写发布原因"
        /></label>
        <p v-if="validationErrors.length" role="alert">
          {{ validationErrors.join("；") }}
        </p>
        <button
          type="submit"
          data-testid="personal-save"
          :disabled="saving || publishing || loading || !screen"
        >
          {{ saving ? "保存中…" : "保存草稿" }}
        </button>
        <button
          type="button"
          data-testid="personal-publish"
          :disabled="
            saving ||
            publishing ||
            loading ||
            dirty ||
            !publishPermissionGranted
          "
          @click="publish"
        >
          {{ publishing ? "发布中…" : "发布" }}
        </button>
        <button type="button" @click="preview">预览</button>
        <button type="button" :disabled="dirty" @click="accessVisible = true">
          查看角色设置
        </button>
          </fieldset>
        </section>
      </div>
    </form>
    <PanoramaSettings
      v-if="accessVisible"
      v-model:visible="accessVisible"
      :screen="accessScreen"
      roles-only
      @saved="load"
    />
  </section>
</template>

<script setup>
import {
  computed,
  defineAsyncComponent,
  onBeforeUnmount,
  reactive,
  ref,
  watch,
} from "vue";
import { useRouter } from "vue-router";
import { usePermissionStore } from "@/stores/permission";
import PersonalConfigThumbnail from './PersonalConfigThumbnail.vue';
import {
  getScreenCanvas,
  listScreenDatasources,
  listScreens,
  publishScreenCanvas,
  saveScreenCanvas,
  saveScreenMetadata,
} from "@/api/screen";
import { isDatasourceCompatible } from "@/utils/screenScope";
import { getDatasourceFieldOptions } from "../panorama/bindings";
import { metricColors } from './personalAnalytics';
import {
  buildPersonalComponents,
  createPersonalSettings,
  parseCanvasResponse,
  PERSONAL_BINDING_KINDS,
  PERSONAL_SCREEN_CODE,
  readPersonalSettings,
  validatePersonalSettings,
} from "./personalDashboardConfig";

const props = defineProps({ visible: { type: Boolean, default: true } });
const emit = defineEmits(["update:visible", "saved"]);
const router = useRouter();
const PanoramaSettings = defineAsyncComponent(() =>
  import("../panorama/PanoramaSettings.vue").then((module) => module.default),
);
const accessVisible = ref(false);
const settings = reactive(createPersonalSettings());
const screen = ref(null);
const canvas = ref(null);
const datasourceOptions = ref([]);
const error = ref("");
const success = ref("");
const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const dirty = ref(false);
const reason = ref("");
const baseline = ref("");
const generation = ref(0);
let disposed = false;
const permissionStore = usePermissionStore();
const PUBLISH_RESOURCE = "/api/screen/admin/canvas/publish";
const publishPermissionGranted = computed(
  () =>
    permissionStore.loaded === true &&
    typeof permissionStore.canAccess === "function" &&
    permissionStore.canAccess(PUBLISH_RESOURCE),
);
const bindingKinds = PERSONAL_BINDING_KINDS;
const bindingLabels = {
  current: "当前值",
  history: "历史趋势",
  organization: "机构对比",
  target: "目标值",
};
const selectedConfigKey = ref("metrics");
const selectedConfigLabel = computed(() => {
  const labels = { "page-title": settings.title || "个人经营驾驶舱", metrics: settings.metricsTitle || "核心指标", trend: settings.trendTitle || "指标趋势", organization: settings.shareTitle || "机构贡献", target: settings.completionTitle || "目标完成度" };
  if (labels[selectedConfigKey.value]) return labels[selectedConfigKey.value];
  const metric = settings.metrics.find((item) => `metric-${item._key || item.metricCode}` === selectedConfigKey.value);
  return metric ? `指标：${metric.metricName || metric.metricCode || "未命名"}` : "全局设置";
});
const validationErrors = computed(() => validatePersonalSettings(settings));
const accessScreen = computed(() => ({
  ...screen.value,
  canvasVersion: canvas.value?.canvasVersion,
}));

function blankMetric() {
  return {
    _key: `${Date.now()}-${Math.random()}`,
    metricCode: "",
    metricName: "",
    unit: "",
    color: metricColors([...settings.metrics, {}]).at(-1),
    bindings: Object.fromEntries(
      bindingKinds.map((kind) => [
        kind,
        { dsId: "", valueField: "", dateField: "", unit: "" },
      ]),
    ),
  };
}
function reset(value = createPersonalSettings()) {
  Object.assign(settings, createPersonalSettings(), value, {
    metrics: (value.metrics || []).map((metric) => ({
      ...metric,
      _key: `${Date.now()}-${Math.random()}`,
      bindings: Object.fromEntries(
        bindingKinds.map((kind) => [
          kind,
          {
            dsId: "",
            valueField: "",
            dateField: "",
            ...(metric.bindings?.[kind] || {}),
          },
        ]),
      ),
    })),
  });
}
function validId(value) {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
function current(token) {
  return !disposed && props.visible && token === generation.value;
}
function addMetric() {
  const metric = blankMetric();
  settings.metrics.push(metric);
  selectedConfigKey.value = `metric-${metric._key}`;
  dirty.value = true;
}
function selectConfig(key) {
  selectedConfigKey.value = key || "page-title";
}
function bindingKindsFor(key) {
  if (key === 'trend') return ['history'];
  if (key === 'organization') return ['current', 'organization'];
  if (key === 'target') return ['current', 'target'];
  return bindingKinds;
}
function removeMetric(index) {
  const removed = settings.metrics[index];
  settings.metrics.splice(index, 1);
  if (removed && selectedConfigKey.value === `metric-${removed._key}`) {
    selectedConfigKey.value = settings.metrics.length ? "metrics" : "page-title";
  }
  dirty.value = true;
}
function moveMetric(index, delta) {
  const next = index + delta;
  if (next < 0 || next >= settings.metrics.length) return;
  const [item] = settings.metrics.splice(index, 1);
  settings.metrics.splice(next, 0, item);
}
function settingsSnapshot(value = settings) {
  const metrics = (value.metrics || []).map(({ _key, ...metric }) => ({
    ...metric,
    bindings: Object.fromEntries(
      bindingKinds.map((kind) => {
        const binding = metric.bindings?.[kind];
        return [
          kind,
          binding
            ? {
                dsId: binding.dsId ?? "",
                valueField: binding.valueField ?? "",
                dateField: binding.dateField ?? "",
                unit: binding.unit ?? "",
              }
            : null,
        ];
      }),
    ),
  }));
  return JSON.stringify({
    version: value.version,
    title: value.title,
    metricsTitle: value.metricsTitle,
    trendTitle: value.trendTitle,
    shareTitle: value.shareTitle,
    completionTitle: value.completionTitle,
    defaultRange: value.defaultRange,
    metrics,
  });
}
watch(settingsSnapshot, (value) => {
  dirty.value = Boolean(baseline.value) && value !== baseline.value;
});
function sourceConfig(source) {
  const raw = source?.configJson ?? source?.config_json;
  if (raw && typeof raw === "object") return raw;
  try {
    return JSON.parse(raw || "{}");
  } catch {
    return {};
  }
}
function sourceSubject(source) {
  const config = sourceConfig(source);
  const kind = String(
    source.sourceKind || source.source_kind || "",
  ).toUpperCase();
  if (kind === "WIDE_TABLE")
    return config.aggregation
      ? ""
      : { EMP_INDEX_RESULT: "EMP", ORG_INDEX_RESULT: "ORG" }[config.table] ||
          "";
  if (kind === "KPI_RESULT") return "EMP"; // 引擎固定 WHERE emp_id = 当前员工。
  if (kind === "KPI_DETAIL") return config.subjectType || "";
  return "";
}
function datasourceOptionsFor(kind) {
  return datasourceOptions.value.filter(
    (source) =>
      (kind === "organization"
        ? sourceSubject(source) === "ORG"
        : sourceSubject(source) === "EMP") &&
      (kind !== "history" || source.dsType === "TIMESERIES"),
  );
}

async function load() {
  if (!props.visible) return;
  const token = ++generation.value;
  loading.value = true;
  error.value = "";
  screen.value = null;
  canvas.value = null;
  try {
    const catalog = await listScreens();
    if (!current(token)) return;
    if (!Array.isArray(catalog))
      throw new Error("大屏目录响应格式无效，已禁止创建");
    const matches = catalog.filter(
      (item) => item?.screenCode === PERSONAL_SCREEN_CODE,
    );
    if (matches.length > 1)
      throw new Error("PERSONAL_COCKPIT 存在多个配置，已停止加载");
    if (!matches.length) {
      reset();
      return;
    }
    screen.value = matches[0];
    const id = validId(screen.value.id || screen.value.screenId);
    if (!id) throw new Error("PERSONAL_COCKPIT 缺少有效屏 ID");
    const [canvasResult, sourcesResult] = await Promise.all([
      getScreenCanvas(id),
      listScreenDatasources(),
    ]);
    if (!current(token)) return;
    canvas.value = parseCanvasResponse(canvasResult);
    if (!canvas.value) throw new Error("画布响应格式无效，已禁止编辑和保存");
    const settingsNode = canvas.value.components.find(
      (item) => item?.id === "personal-cockpit-settings",
    );
    const loadedSettings = readPersonalSettings(canvas.value.components);
    if (settingsNode && !loadedSettings)
      throw new Error("个人驾驶舱设置节点损坏，已禁止覆盖保存");
    reset(loadedSettings || createPersonalSettings());
    baseline.value = settingsSnapshot();
    if (!Array.isArray(sourcesResult))
      throw new Error("数据源目录响应格式无效，已禁止保存");
    const sources = sourcesResult;
    datasourceOptions.value = sources
      .filter(
        (source) => source.status === undefined || source.status === "ACTIVE",
      )
      .filter((source) =>
        ["WIDE_TABLE", "KPI_RESULT", "KPI_DETAIL"].includes(
          String(source.sourceKind || source.source_kind || "").toUpperCase(),
        ),
      )
      .filter(
        (source) =>
          String(
            source.scopeMode ||
              source.scope_mode ||
              sourceConfig(source).scopeMode ||
              sourceConfig(source).scope_mode ||
              "SUBJECT",
          ).toUpperCase() === "SUBJECT" &&
          isDatasourceCompatible("COMMON", source.bizLine || source.biz_line),
      );
    dirty.value = false;
  } catch (loadError) {
    if (current(token)) {
      canvas.value = null;
      error.value = loadError?.message || "读取个人驾驶舱失败";
    }
  } finally {
    if (current(token)) loading.value = false;
  }
}

async function createScreen() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  const token = ++generation.value;
  try {
    const created = await saveScreenMetadata({
      screenCode: PERSONAL_SCREEN_CODE,
      screenName: "个人经营驾驶舱",
      viewLevel: "PERSON",
      bizLine: "COMMON",
      orgScopeMode: "LEGACY_CONTEXT",
      status: "ACTIVE",
    });
    if (!current(token)) return;
    screen.value = created;
    await load();
  } catch (createError) {
    if (current(token))
      error.value = createError?.message || "创建个人驾驶舱配置失败";
  } finally {
    if (current(token)) loading.value = false;
  }
}

function allowedFields(source) {
  const explicit = sourceConfig(source).columns;
  return [
    ...new Set([
      ...(Array.isArray(explicit)
        ? explicit.filter((item) => typeof item === "string")
        : []),
      ...getDatasourceFieldOptions(source)
        .filter((item) => item.role !== "NOTICE" && !item.disabled)
        .map((item) => item.col),
    ]),
  ];
}
function bindingFields(binding) {
  const source = datasourceOptions.value.find(
    (item) => String(item.id) === String(binding?.dsId),
  );
  return source ? allowedFields(source) : [];
}
function validateBindingsAgainstCatalog() {
  for (const metric of settings.metrics)
    for (const kind of bindingKinds) {
      const binding = metric.bindings?.[kind];
      if (!binding?.dsId) continue;
      const source = datasourceOptionsFor(kind).find(
        (item) => String(item.id) === String(binding.dsId),
      );
      if (!source)
        return `指标${metric.metricCode || ""}${bindingLabels[kind]}数据源不可用或主体/类型不匹配`;
      const fields = allowedFields(source);
      if (
        fields.length &&
        (!fields.includes(binding.valueField) ||
          !fields.includes(binding.dateField))
      )
        return `指标${metric.metricCode || ""}${bindingLabels[kind]}字段不在数据源列中`;
    }
  return "";
}
async function save() {
  if (saving.value || publishing.value || loading.value || !screen.value)
    return false;
  const errors = validationErrors.value;
  const bindingError = validateBindingsAgainstCatalog();
  if (errors.length || bindingError) {
    error.value = [...errors, bindingError].filter(Boolean).join("；");
    return false;
  }
  const screenId = validId(screen.value.id || screen.value.screenId);
  const oldVersion = Number(canvas.value?.canvasVersion);
  if (!screenId || !Number.isSafeInteger(oldVersion)) {
    error.value = "未读取到有效画布版本，已禁止保存";
    return false;
  }
  const token = generation.value;
  saving.value = true;
  error.value = "";
  success.value = "";
  try {
    const canvasStyle = { ...(canvas.value.canvasStyle || {}) };
    delete canvasStyle.presentation;
    const payload = {
      screenId,
      expectedVersion: oldVersion,
      canvasStyle,
      components: buildPersonalComponents(settings, canvas.value.components),
    };
    const sentSettings = settingsSnapshot();
    const selectedMetric = selectedConfigKey.value.startsWith('metric-')
      ? settings.metrics.find((metric) => `metric-${metric._key}` === selectedConfigKey.value)
      : null;
    const selectedMetricCode = selectedMetric?.metricCode || '';
    const response = await saveScreenCanvas(payload);
    if (!current(token)) return false;
    const reread = parseCanvasResponse(await getScreenCanvas(screenId));
    const rereadSettings = reread && readPersonalSettings(reread.components);
    const nextVersion = Number(reread?.canvasVersion);
    const responseVersion = Number(response?.canvasVersion);
    if (
      !current(token) ||
      !reread ||
      !rereadSettings ||
      settingsSnapshot(rereadSettings) !== sentSettings ||
      !Number.isSafeInteger(nextVersion) ||
      nextVersion <= oldVersion ||
      (Number.isSafeInteger(responseVersion) && nextVersion < responseVersion)
    )
      throw new Error("保存后回读未确认配置内容或版本，草稿仍保留");
    canvas.value = reread;
    reset(rereadSettings);
    if (selectedMetricCode) {
      const rereadMetric = settings.metrics.find((metric) => metric.metricCode === selectedMetricCode);
      selectedConfigKey.value = rereadMetric ? `metric-${rereadMetric._key}` : (settings.metrics.length ? 'metrics' : 'page-title');
    }
    baseline.value = settingsSnapshot();
    dirty.value = false;
    emit("saved", { action: "canvas", response, canvas: reread });
    return true;
  } catch (saveError) {
    if (current(token))
      error.value = saveError?.message || "保存个人驾驶舱失败";
    return false;
  } finally {
    if (current(token)) saving.value = false;
  }
}
async function publish() {
  if (saving.value || publishing.value || loading.value) return false;
  publishing.value = true;
  error.value = "";
  success.value = "";
  try {
    if (dirty.value) throw new Error("请先保存草稿再发布");
    if (!permissionStore.loaded) await permissionStore.load();
    if (!permissionStore.canAccess(PUBLISH_RESOURCE))
      throw new Error("当前账号没有发布大屏的权限");
    const screenId = validId(screen.value?.id || screen.value?.screenId);
    const version = Number(canvas.value?.canvasVersion);
    if (!screenId || !Number.isSafeInteger(version))
      throw new Error("未读取到有效画布版本，已禁止发布");
    const publishReason = String(reason.value || "").trim();
    if (!publishReason) throw new Error("发布个人驾驶舱必须填写原因");
    if (!window.confirm("确认发布个人经营驾驶舱？")) return false;
    const token = generation.value;
    await publishScreenCanvas({
      screenId,
      expectedVersion: version,
      reason: publishReason,
    });
    const reread = parseCanvasResponse(await getScreenCanvas(screenId));
    if (!current(token) || !reread)
      throw new Error("发布请求已提交，但发布后回读失败，请勿重复发布");
    canvas.value = reread;
    baseline.value = settingsSnapshot();
    success.value = "发布成功";
    return true;
  } catch (publishError) {
    if (!disposed) error.value = publishError?.message || "发布个人驾驶舱失败";
    return false;
  } finally {
    publishing.value = false;
  }
}
function preview() {
  if (dirty.value) {
    error.value = "请先保存草稿再预览";
    return;
  }
  router.push("/screen/PERSONAL_COCKPIT?preview=draft");
}
watch(
  () => props.visible,
  (visible) => {
    if (visible) load();
    else generation.value += 1;
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  disposed = true;
  generation.value += 1;
});
</script>

<style scoped>
.personal-dashboard-settings {
  max-width: 1560px;
  margin: 0 auto;
  padding: 24px;
  color: #24344d;
  background: #f6f8fc;
  border: 1px solid #d9e1ee;
  border-radius: 12px;
  box-sizing: border-box;
}
.personal-workbench { display:grid; grid-template-columns:minmax(360px,48fr) minmax(0,52fr); gap:16px; align-items:start; }
.personal-workbench__preview,.personal-workbench__editor { min-width:0; padding:20px; background:#fff; border:1px solid #d9e1ee; border-radius:8px; box-sizing:border-box; }
.personal-workbench__preview { background:#fff; }
.personal-workbench__preview h3,.personal-workbench__editor-heading h3 { margin:0; color:#182b49; font-size:20px; }
.personal-workbench__preview > .hint { margin:6px 0 14px; }
.personal-workbench__caption { margin:10px 0 0; color:#7184a0; font-size:12px; }
.personal-workbench__editor-heading { display:flex; justify-content:space-between; gap:12px; align-items:start; padding-bottom:14px; border-bottom:1px solid #e1e8f1; }
.personal-workbench__editor-heading p { margin:6px 0 0; color:#61738d; font-size:13px; }
.personal-workbench__editor-heading strong { color:#1464c2; }
.personal-workbench__source { color:#078d7f; font-size:12px; white-space:nowrap; }
.personal-workbench__editor fieldset { margin-top:16px; }
.metric.is-config-selected { border-color:#12bfe4; box-shadow:0 0 0 2px #12bfe433; }
.personal-region-editor { display:grid; gap:10px; margin-bottom:12px; padding:12px; border:1px solid #d7e2ef; border-radius:6px; background:#f8fbff; }
.binding-summary { display:grid; gap:6px; color:#526986; font-size:12px; }
.binding-summary strong { color:#1a4774; }
.binding-summary span { padding:6px 8px; border:1px solid #d9e4f0; border-radius:4px; background:#fff; overflow-wrap:anywhere; }
@media(max-width:960px) { .personal-workbench { grid-template-columns:1fr; } }
.personal-dashboard-settings header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.personal-dashboard-settings fieldset {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
.personal-dashboard-settings fieldset > label {
  display: inline-flex;
  flex-direction: column;
  width: calc(50% - 20px);
  margin: 0 18px 12px 0;
  gap: 5px;
}
.binding label {
  display: grid;
  gap: 5px;
  min-width: 0;
  font-size: 12px;
  color: #586a82;
}
.personal-dashboard-settings input,
.personal-dashboard-settings select {
  min-width: 0;
  min-height: 32px;
  padding: 5px 8px;
  color: #24344d;
  background: #fff;
  border: 1px solid #c7d2e2;
  border-radius: 5px;
  box-sizing: border-box;
}
.metric {
  margin: 18px 0;
  padding: 16px;
  border: 1px solid #d3ddeb;
  border-radius: 8px;
  background: #fff;
  box-sizing: border-box;
}
.metric > label {
  display: inline-flex;
  flex-direction: column;
  width: 22%;
  margin-right: 2%;
  gap: 5px;
}
.binding {
  display: grid;
  grid-template-columns: 120px repeat(4, minmax(0, 1fr));
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}
.personal-dashboard-settings button {
  margin: 6px 8px 6px 0;
  padding: 7px 14px;
  color: #fff;
  background: #2767ad;
  border: 0;
  border-radius: 5px;
  cursor: pointer;
}
.personal-dashboard-settings button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.personal-dashboard-settings [role="alert"] {
  color: #b42330;
}
.hint {
  color: #61728d;
}
@media (max-width: 760px) {
  .personal-dashboard-settings {
    padding: 14px;
  }
  .personal-dashboard-settings fieldset > label,
  .metric > label {
    width: 100%;
    margin-right: 0;
  }
  .binding {
    grid-template-columns: 1fr;
  }
}
</style>
