import { getScreenView, queryScreenData } from "./screen";
import {
  buildPersonalDashboardModel,
  getPersonalWorkspace,
} from "./personalDashboard";
import {
  PERSONAL_SCREEN_CODE,
  readPersonalSettings,
  validatePersonalSettings,
} from "@/views/screen/personal/personalDashboardConfig";

const RANGES = new Set(["7d", "6m", "1y"]);
const KINDS = ["current", "history", "organization", "target"];
const LABELS = {
  current: "当前值",
  history: "历史趋势",
  organization: "机构指标",
  target: "目标值",
};
const object = (value) =>
  value && typeof value === "object" && !Array.isArray(value);
const finite = (value) =>
  (typeof value === "number" || (typeof value === "string" && value.trim())) &&
  Number.isFinite(Number(value))
    ? Number(value)
    : null;
const pad = (value) => String(value).padStart(2, "0");
const localDay = (value) =>
  `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`;

function dataDay(value) {
  let text = String(value ?? "").trim();
  if (/^\d{8}$/.test(text))
    text = `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6)}`;
  else if (/^\d{4}-\d{2}-\d{2}[T ]/.test(text)) text = text.slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return "";
  const parsed = new Date(`${text}T00:00:00`);
  return Number.isFinite(parsed.getTime()) && localDay(parsed) === text
    ? text
    : "";
}

function rangeDates(range, now) {
  const from = new Date(now);
  if (range === "7d") from.setDate(from.getDate() - 6);
  else from.setMonth(from.getMonth() - (range === "6m" ? 5 : 11), 1);
  return { dateFrom: localDay(from), dateTo: localDay(now) };
}

/** 只接受服务端确认的画布身份；不得拿草稿节点上的旧绑定代替发布快照。 */
function parsePackage(view, preview) {
  if (
    !view ||
    view.screenCode !== PERSONAL_SCREEN_CODE ||
    view.state !== (preview === "draft" ? "draft" : "published")
  ) {
    throw new Error("个人驾驶舱渲染包身份不匹配");
  }
  if (view.state === "published" && view.renderPackageJson == null) return null;
  let pkg;
  try {
    pkg = JSON.parse(view.renderPackageJson);
  } catch {
    throw new Error("个人驾驶舱渲染包损坏");
  }
  if (
    !object(pkg) ||
    ![1, 2].includes(view.runtimeSchemaVersion) ||
    pkg.schemaVersion !== view.runtimeSchemaVersion ||
    !Array.isArray(pkg.components) ||
    !object(pkg.bindSnapshots)
  )
    throw new Error("个人驾驶舱渲染协议不可用");
  const settings = readPersonalSettings(pkg.components);
  if (!settings || validatePersonalSettings(settings).length)
    throw new Error("个人驾驶舱展示配置无效");
  const blocks = new Map();
  const blockIds = new Set();
  for (const node of pkg.components.filter(
    (item) =>
      item?.component === "ChartWidget" && item.propValue?.personalMetricCode,
  )) {
    const key = `${node.propValue.personalMetricCode}:${node.propValue.personalSourceKind}`;
    const snapshot = pkg.bindSnapshots[node.blockId];
    if (
      !Number.isSafeInteger(node.blockId) ||
      node.blockId <= 0 ||
      blockIds.has(node.blockId) ||
      blocks.has(key) ||
      !object(snapshot?.bind) ||
      !Number.isSafeInteger(snapshot.bind.dsId) ||
      snapshot.bind.dsId <= 0
    ) {
      throw new Error("个人驾驶舱数据绑定快照无效");
    }
    blockIds.add(node.blockId);
    blocks.set(key, { blockId: node.blockId, bind: snapshot.bind });
  }
  // 缺失来源可以配置为空；声明已绑定却没有对应快照则是坏包，不能静默当作无数据。
  for (const metric of settings.metrics)
    for (const kind of KINDS) {
      const binding = metric.bindings?.[kind];
      if (!binding?.dsId) continue;
      const block = blocks.get(`${metric.metricCode}:${kind}`);
      if (
        !block ||
        block.bind.dsId !== Number(binding.dsId) ||
        block.bind.fieldMap?.value !== binding.valueField ||
        block.bind.fieldMap?.date !== binding.dateField
      ) {
        throw new Error("个人驾驶舱展示字段与绑定快照不一致");
      }
    }
  return { settings, blocks, schemaVersion: pkg.schemaVersion };
}

/** 按 ScreenDataRespDTO 的 columns + 二维 rows 解析，拒绝歧义日期与错误质量批次。 */
function readPoints(response, binding) {
  if (
    !Array.isArray(response?.columns) ||
    !Array.isArray(response?.rows) ||
    response.columns.some((column) => typeof column !== "string") ||
    new Set(response.columns).size !== response.columns.length ||
    (response.quality?.status &&
      !["COMPLETE", "STALE"].includes(response.quality.status))
  ) {
    throw new Error("数据结构或批次质量不可用");
  }
  const dateIndex = response.columns.indexOf(binding.dateField);
  const valueIndex = response.columns.indexOf(binding.valueField);
  if (dateIndex < 0 || valueIndex < 0)
    throw new Error("数据源未返回配置的日期和值字段");
  const seen = new Set();
  const points = response.rows.map((row) => {
    if (!Array.isArray(row) || row.length !== response.columns.length)
      throw new Error("数据源行结构无效");
    const date = dataDay(row[dateIndex]);
    if (!date || seen.has(date)) throw new Error("数据日期缺失、无效或重复");
    seen.add(date);
    return { date, value: finite(row[valueIndex]) };
  });
  return points.sort((a, b) => a.date.localeCompare(b.date));
}

async function legacyModel(identity, range, now) {
  const workspace = await getPersonalWorkspace();
  const model = buildPersonalDashboardModel(
    { workspace: { status: "fulfilled", value: workspace } },
    identity,
  );
  return {
    ...model,
    identity,
    range: RANGES.has(range) ? range : "7d",
    settings: {},
    metrics: {
      ...model.metrics,
      message: [
        model.metrics?.message,
        "个人大屏尚未配置或发布，仅展示工作台指标快照；请在大屏管理配置趋势、机构指标及目标。",
      ]
        .filter(Boolean)
        .join("；"),
    },
    refreshedAt: `${localDay(now)} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`,
  };
}

/** 固定个人屏、固定当前员工及主机构；每次从授权发布包或草稿包读取配置。 */
export async function loadPersonalCockpit({
  identity,
  preview = "",
  range,
  screenCode = PERSONAL_SCREEN_CODE,
} = {}) {
  if (
    screenCode !== PERSONAL_SCREEN_CODE ||
    !String(identity?.empId || "").trim() ||
    !String(identity?.mainOrgCode || "").trim()
  ) {
    throw new Error("个人驾驶舱缺少已确认的员工或主机构身份");
  }
  const now = new Date();
  const displayIdentity = {
    empId: identity.empId,
    orgCode: identity.mainOrgCode,
    name: identity.displayName,
    orgName: identity.mainOrgName,
  };
  const requestedPreview = preview === "draft" ? "draft" : "";
  let view;
  try {
    view = await getScreenView(
      PERSONAL_SCREEN_CODE,
      requestedPreview || undefined,
    );
  } catch (error) {
    if (
      !requestedPreview &&
      error?.code === "RPT-43004" &&
      ![401, 403].includes(error?.response?.status)
    )
      return legacyModel(displayIdentity, range, now);
    throw error;
  }
  const parsed = parsePackage(view, requestedPreview);
  if (!parsed) return legacyModel(displayIdentity, range, now);
  const { settings, schemaVersion, blocks } = parsed;
  const selectedRange = RANGES.has(range)
    ? range
    : RANGES.has(settings.defaultRange)
      ? settings.defaultRange
      : "7d";
  const dates = rangeDates(selectedRange, now);
  const requestCache = new Map();
  const items = await Promise.all(
    settings.metrics.map(async (configured) => {
      const metric = {
        ...configured,
        currentValue: null,
        dataDate: null,
        history: [],
        orgValue: null,
        orgDataDate: null,
        orgUnit: null,
        targetValue: null,
        achievementRate: null,
        previousValue: null,
        mom: null,
        errors: {},
      };
      const results = await Promise.all(
        KINDS.map(async (kind) => {
          const binding = configured.bindings?.[kind];
          if (!binding?.dsId) return [kind, null];
          try {
            if (!binding.unit || binding.unit !== configured.unit)
              throw new Error("来源单位未配置或与指标单位不一致");
            const block = blocks.get(`${configured.metricCode}:${kind}`);
            const body = {
              schemaVersion,
              screenCode: PERSONAL_SCREEN_CODE,
              ...(requestedPreview || schemaVersion === 2
                ? { blockId: block.blockId }
                : { dsId: block.bind.dsId }),
              ...(requestedPreview ? { previewState: "draft" } : {}),
              period: kind === "history" ? "RANGE" : "LATEST",
              ...(kind === "history" ? dates : {}),
              contextParams: {
                empId: String(identity.empId),
                orgCode: String(identity.mainOrgCode),
              },
            };
            const cacheKey = JSON.stringify(body);
            if (!requestCache.has(cacheKey))
              requestCache.set(cacheKey, queryScreenData(body));
            const response = await requestCache.get(cacheKey);
            const points = readPoints(response, binding);
            return [
              kind,
              kind === "history"
                ? points.filter(
                    (point) =>
                      point.date >= dates.dateFrom &&
                      point.date <= dates.dateTo,
                  )
                : points.at(-1) || null,
            ];
          } catch (error) {
            metric.errors[kind] =
              `${LABELS[kind]}加载失败：${error?.response?.status === 403 ? "暂无权限" : error?.message || "来源不可用"}`;
            return [kind, null];
          }
        }),
      );
      const result = Object.fromEntries(results);
      if (result.current) {
        metric.currentValue = result.current.value;
        metric.dataDate = result.current.date;
      }
      metric.history = result.history || [];
      if (result.organization) {
        metric.orgValue = result.organization.value;
        metric.orgDataDate = result.organization.date;
        metric.orgUnit = configured.bindings.organization.unit;
      }
      if (
        result.target &&
        result.current &&
        result.target.date === result.current.date
      )
        metric.targetValue = result.target.value;
      else if (result.target)
        metric.errors.target = "目标值与当前指标的数据日期不一致";
      if (metric.currentValue !== null && metric.targetValue > 0)
        metric.achievementRate =
          (metric.currentValue / metric.targetValue) * 100;
      return metric;
    }),
  );
  return {
    settings,
    identity: displayIdentity,
    range: selectedRange,
    preview: requestedPreview,
    metrics: {
      status: items.length ? "ready" : "empty",
      items,
      message: items.some((item) => Object.keys(item.errors).length)
        ? "部分数据加载失败或口径不一致，请核对组件数据来源。"
        : "",
    },
    refreshedAt: `${localDay(now)} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`,
  };
}

export { PERSONAL_SCREEN_CODE };
