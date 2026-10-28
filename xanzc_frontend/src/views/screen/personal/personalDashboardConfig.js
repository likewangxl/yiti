/** 个人经营驾驶舱的展示配置与通用画布节点转换。运行协议由服务端决定。 */

export const PERSONAL_SCREEN_CODE = "PERSONAL_COCKPIT";
export const PERSONAL_SETTINGS_ID = "personal-cockpit-settings";
export const PERSONAL_BINDING_KINDS = Object.freeze([
  "current",
  "history",
  "organization",
  "target",
]);
export function parseCanvasResponse(response) {
  if (!response || typeof response !== "object") return null;
  if (
    !Number.isSafeInteger(response.canvasVersion) ||
    response.canvasVersion < 0
  )
    return null;
  if (
    response.canvasVersion === 0 &&
    response.publishStatus === 0 &&
    response.canvasStyleJson === null &&
    response.canvasDraftJson === null &&
    Array.isArray(response.blocks) &&
    response.blocks.length === 0
  ) {
    return {
      ...response,
      canvasStyle: { schemaVersion: 1, designWidth: 1920, designHeight: 1080 },
      components: [],
    };
  }
  const parse = (value) => {
    if (value && typeof value === "object") return value;
    if (typeof value !== "string") return null;
    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  };
  const style = parse(response.canvasStyleJson);
  const draft = parse(response.canvasDraftJson);
  const version = Number(response.canvasVersion);
  if (
    !style ||
    Array.isArray(style) ||
    !draft ||
    !Number.isSafeInteger(version) ||
    version < 0 ||
    !Array.isArray(draft.components)
  )
    return null;
  return {
    ...response,
    canvasVersion: version,
    canvasStyle: style,
    components: draft.components,
  };
}

const DEFAULT_TITLES = Object.freeze({
  title: "个人经营驾驶舱",
  metricsTitle: "核心指标",
  trendTitle: "指标趋势",
  shareTitle: "机构贡献",
  completionTitle: "目标完成度",
});

function clone(value) {
  if (Array.isArray(value)) return value.map(clone);
  if (value && typeof value === "object")
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, clone(item)]),
    );
  return value;
}

function normalizeBinding(binding) {
  if (!binding || typeof binding !== "object" || Array.isArray(binding))
    return null;
  return {
    dsId: binding.dsId ?? "",
    valueField: String(binding.valueField ?? "").trim(),
    dateField: String(binding.dateField ?? "").trim(),
    ...(binding.unit ? { unit: String(binding.unit).trim() } : {}),
  };
}

function normalizeMetric(metric = {}) {
  return {
    metricCode: String(metric.metricCode ?? "").trim(),
    metricName: String(metric.metricName ?? "").trim(),
    unit: String(metric.unit ?? "").trim(),
    color: String(metric.color ?? "").trim(),
    bindings: Object.fromEntries(
      PERSONAL_BINDING_KINDS.map((kind) => [
        kind,
        normalizeBinding(metric.bindings?.[kind]),
      ]),
    ),
  };
}

export function createPersonalSettings() {
  return { version: 1, ...DEFAULT_TITLES, defaultRange: "7d", metrics: [] };
}

export function readPersonalSettings(components) {
  if (!Array.isArray(components)) return null;
  const nodes = components.filter(
    (item) =>
      item?.id === PERSONAL_SETTINGS_ID && item?.component === "TextLabel",
  );
  if (nodes.length !== 1) return null;
  const node = nodes[0];
  const value = node?.propValue?.personalDashboard;
  if (
    !value ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    value.version !== 1 || !Array.isArray(value.metrics)
  )
    return null;
  return clone({
    ...createPersonalSettings(),
    ...value,
    metrics: Array.isArray(value.metrics)
      ? value.metrics.map(normalizeMetric)
      : [],
  });
}

export function validatePersonalSettings(settings = {}) {
  const errors = [];
  if (settings.version !== 1) errors.push("配置版本必须为 1");
  if (!String(settings.title ?? "").trim()) errors.push("标题不能为空");
  if (!["7d", "6m", "1y"].includes(String(settings.defaultRange || "")))
    errors.push("默认时间范围无效");
  if (!Array.isArray(settings.metrics)) {
    errors.push("指标必须为数组");
    return errors;
  }
  const codes = new Set();
  settings.metrics.forEach((metric, index) => {
    const prefix = `指标${index + 1}`;
    const code = String(metric?.metricCode ?? "").trim();
    if (!code) errors.push(`${prefix}指标编码不能为空`);
    else if (codes.has(code)) errors.push(`${prefix}指标编码重复：${code}`);
    else codes.add(code);
    if (!String(metric?.metricName ?? "").trim())
      errors.push(`${prefix}指标名称不能为空`);
    for (const kind of PERSONAL_BINDING_KINDS) {
      const binding = metric?.bindings?.[kind];
      if (binding === null || binding === undefined) continue;
      const ds = String(binding.dsId ?? "").trim();
      const value = String(binding.valueField ?? "").trim();
      const date = String(binding.dateField ?? "").trim();
      if (!ds && !value && !date) continue;
      if (!ds) errors.push(`${prefix}${kind}绑定数据源不能为空`);
      if (!value) errors.push(`${prefix}${kind}绑定值字段不能为空`);
      if (!date) errors.push(`${prefix}${kind}绑定日期字段不能为空`);
      if (!String(binding.unit ?? "").trim())
        errors.push(`${prefix}${kind}绑定计量单位不能为空`);
      else if (
        String(metric?.unit ?? "").trim() &&
        String(binding.unit).trim() !== String(metric.unit).trim()
      )
        errors.push(`${prefix}${kind}绑定计量单位必须与指标单位一致`);
    }
  });
  return errors;
}

function bindingNode(metric, kind, existing) {
  const binding = normalizeBinding(metric.bindings?.[kind]);
  if (
    !binding ||
    !binding.valueField ||
    !binding.dateField ||
    !Number.isSafeInteger(Number(binding.dsId)) ||
    Number(binding.dsId) <= 0
  )
    return null;
  const id = `personal-${encodeURIComponent(metric.metricCode)}-${kind}`;
  const node = existing
    ? { ...existing }
    : {
        id,
        component: "ChartWidget",
        isShow: true,
        style: { top: 0, left: 0, width: 320, height: 180 },
      };
  return {
    ...node,
    id,
    component: "ChartWidget",
    style: node.style || { top: 0, left: 0, width: 320, height: 180 },
    innerType: "TABLE_LIST",
    propValue: {
      ...(node.propValue || {}),
      personalMetricCode: metric.metricCode,
      personalSourceKind: kind,
      personalUnit: binding.unit || metric.unit || "",
    },
    bindJson: JSON.stringify({
      dsId: Number(binding.dsId),
      period: "LATEST",
      fieldMap: { value: binding.valueField, date: binding.dateField },
    }),
  };
}

export function buildPersonalComponents(settings, existingComponents = []) {
  const safeSettings = {
    ...createPersonalSettings(),
    ...clone(settings),
    metrics: (settings?.metrics || []).map(normalizeMetric),
  };
  const previous = Array.isArray(existingComponents) ? existingComponents : [];
  const personalIds = new Set([PERSONAL_SETTINGS_ID]);
  safeSettings.metrics.forEach((metric) =>
    PERSONAL_BINDING_KINDS.forEach((kind) =>
      personalIds.add(
        `personal-${encodeURIComponent(metric.metricCode)}-${kind}`,
      ),
    ),
  );
  const byId = new Map(previous.map((item) => [item?.id, item]));
  const components = previous.filter(
    (item) =>
      !personalIds.has(item?.id) &&
      !(
        item?.propValue?.personalMetricCode ||
        item?.propValue?.personalSourceKind
      ),
  );
  const settingsNode = byId.get(PERSONAL_SETTINGS_ID) || {
    id: PERSONAL_SETTINGS_ID,
    component: "TextLabel",
    isShow: true,
  };
  components.push({
    ...settingsNode,
    id: PERSONAL_SETTINGS_ID,
    component: "TextLabel",
    style: settingsNode.style || { top: 0, left: 0, width: 320, height: 180 },
    propValue: {
      ...(settingsNode.propValue || {}),
      personalDashboard: clone(safeSettings),
    },
  });
  safeSettings.metrics.forEach((metric) =>
    PERSONAL_BINDING_KINDS.forEach((kind) => {
      const node = bindingNode(
        metric,
        kind,
        byId.get(`personal-${encodeURIComponent(metric.metricCode)}-${kind}`),
      );
      if (node) components.push(node);
    }),
  );
  return components;
}
