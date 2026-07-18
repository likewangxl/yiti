// 大屏数据源 config_json 组装/解析纯函数（spec 2026-07-17 §3，供 Datasources.vue 使用，vitest 全覆盖）
//
// 表单模型（Datasources.vue 弹窗绑定的响应式对象）与库表 config_json 的双向转换集中在本文件，
// 保证"保存时组装"与"编辑时回填"口径一致；校验规则与后端 ScreenDatasourceServiceImpl 的
// 43009（SCREEN_DS_CONFIG_INVALID）分支一一对齐，尽量把报错拦在提交前并给出友好中文文案。
//
// 后端契约要点（report-analytics-center 已交付）：
//   - KPI_DETAIL：SNAPSHOT 强制 ds_type=SINGLE、TREND 强制 TIMESERIES（显式传不一致会 43009）；
//     TREND 必须携带 metrics 快照且每项 metricCode+metricName 非空；valueCol ∈ score|completeRate（可选，默认 score）
//   - fieldMeta（全 source_kind 通用）：col 非空且不重复、role ∈ DIM|METRIC 必填
//   - WIDE_TABLE aggregation：groupBy ∈ NONE|SUBJECT|DATE（DATE→TIMESERIES 否则 SINGLE，后端强制）、
//     agg ∈ SUM|AVG|MAX|MIN|COUNT；filters 的 op 白名单，IN 的 value 为逗号分隔字符串（后端拆成多 ? 绑定）
//   - scopeMode ∈ SUBJECT|GLOBAL（缺省 SUBJECT；GLOBAL 表示全省聚合类，查询需 ALL/省级数据范围）

// ===== 枚举常量（与后端白名单一致，模板下拉复用） =====
export const FIELD_ROLES = ['DIM', 'METRIC'];
export const AGG_GROUP_BYS = ['NONE', 'SUBJECT', 'DATE'];
export const AGG_FUNCS = ['SUM', 'AVG', 'MAX', 'MIN', 'COUNT'];
export const FILTER_OPS = ['EQ', 'NE', 'IN', 'GT', 'GE', 'LT', 'LE'];
export const KPI_VALUE_COLS = ['score', 'completeRate'];
export const SCOPE_MODES = ['SUBJECT', 'GLOBAL'];
/** 预设周期模板（与设计器周期下拉一致） */
export const TIME_PARAM_PRESETS = ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'];

/** 新建弹窗的默认表单模型（编辑时以 parseConfigJson 的补丁覆盖） */
export function defaultDsModel() {
  return {
    sourceKind: 'WIDE_TABLE',
    // 仅 CUSTOM_SQL 由用户选择；其余类型由 deriveDsType 按配置推导（只读展示）
    dsType: 'SINGLE',
    wide: { table: 'EMP_INDEX_RESULT', metricCodes: [], slotCols: [], timeParams: [...TIME_PARAM_PRESETS] },
    kpi: { cycleType: 'MONTHLY' },
    kpiDetail: {
      schemeCode: '', subjectType: 'EMP', mode: 'SNAPSHOT',
      metrics: [], valueCol: 'score', timeParams: [...TIME_PARAM_PRESETS]
    },
    sql: { text: '', dateCol: '' },
    // ===== 全类型通用段 =====
    fieldMeta: [],          // [{col, alias, role, unit, decimals}]
    aggEnabled: false,      // 仅 WIDE_TABLE：聚合开关（关闭时 aggregation 不落盘）
    aggregation: { groupBy: 'NONE', agg: 'SUM', filters: [] },
    scopeMode: 'SUBJECT'
  };
}

/**
 * 推导 ds_type（与后端保存时的强制规则一致，前端用于只读展示与提交值）：
 *   WIDE_TABLE：未开聚合 → TIMESERIES（现状）；开聚合 groupBy=DATE → TIMESERIES，NONE/SUBJECT → SINGLE
 *   KPI_RESULT：TIMESERIES（后端强制）
 *   KPI_DETAIL：SNAPSHOT → SINGLE、TREND → TIMESERIES
 *   CUSTOM_SQL：跟随用户选择
 */
export function deriveDsType(model) {
  switch (model.sourceKind) {
    case 'WIDE_TABLE':
      if (model.aggEnabled) return model.aggregation.groupBy === 'DATE' ? 'TIMESERIES' : 'SINGLE';
      return 'TIMESERIES';
    case 'KPI_RESULT':
      return 'TIMESERIES';
    case 'KPI_DETAIL':
      return model.kpiDetail.mode === 'SNAPSHOT' ? 'SINGLE' : 'TIMESERIES';
    default:
      return model.dsType;
  }
}

/** 整行为空的 fieldMeta 行（用户点了"加一行"但没填）→ 组装/校验时统一跳过 */
function isEmptyFieldMetaRow(r) {
  return !String(r.col || '').trim() && !String(r.alias || '').trim()
    && !String(r.unit || '').trim() && (r.decimals === null || r.decimals === undefined || r.decimals === '');
}

/** fieldMeta 表单行 → 落盘形态：trim、空可选项不落键、decimals 数字化；无有效行返回 null */
function normalizeFieldMeta(rows) {
  const out = [];
  for (const r of rows || []) {
    if (isEmptyFieldMetaRow(r)) continue;
    const item = { col: String(r.col || '').trim() };
    if (String(r.alias || '').trim()) item.alias = String(r.alias).trim();
    item.role = r.role;  // role 后端必填校验，UI 上默认 METRIC 不会为空
    if (String(r.unit || '').trim()) item.unit = String(r.unit).trim();
    if (r.decimals !== null && r.decimals !== undefined && r.decimals !== '') {
      item.decimals = Number(r.decimals);
    }
    out.push(item);
  }
  return out.length ? out : null;
}

/** filters 表单行 → 落盘形态：丢弃整行空；value 保持字符串（IN 为逗号分隔，后端拆分） */
function normalizeFilters(filters) {
  const out = (filters || [])
    .filter(f => String(f.col || '').trim() || String(f.value || '').trim())
    .map(f => ({ col: String(f.col || '').trim(), op: f.op, value: String(f.value ?? '').trim() }));
  return out.length ? out : null;
}

/**
 * 表单模型 → config_json 对象（调用方 JSON.stringify 后提交）。
 * 统一携带 schemaVersion:2 与 scopeMode（显式优于隐式）；fieldMeta/aggregation 无内容时不落键，
 * 保持与旧数据 diff 最小。
 */
export function buildConfigJson(model) {
  let cfg;
  switch (model.sourceKind) {
    case 'WIDE_TABLE':
      // 只传 metricCode，槽位/名称快照由后端重写（沿用现状）
      cfg = {
        schemaVersion: 2,
        table: model.wide.table,
        metrics: model.wide.metricCodes.map(c => ({ metricCode: c }))
      };
      if (model.aggEnabled) {
        const agg = { groupBy: model.aggregation.groupBy, agg: model.aggregation.agg };
        const filters = normalizeFilters(model.aggregation.filters);
        if (filters) agg.filters = filters;
        cfg.aggregation = agg;
      }
      break;
    case 'KPI_RESULT':
      cfg = { schemaVersion: 2, cycleType: model.kpi.cycleType };
      break;
    case 'KPI_DETAIL': {
      const d = model.kpiDetail;
      cfg = { schemaVersion: 2, schemeCode: d.schemeCode, subjectType: d.subjectType, mode: d.mode };
      if (d.mode === 'TREND') {
        // 保存时带 metricName 快照（后端校验 code+name 均非空，细项列名依赖它）
        cfg.metrics = d.metrics.map(m => ({ metricCode: m.metricCode, metricName: m.metricName }));
        cfg.valueCol = d.valueCol || 'score';
      }
      break;
    }
    default:
      cfg = { schemaVersion: 2, sql: model.sql.text, dateCol: model.sql.dateCol || null };
  }
  const fieldMeta = normalizeFieldMeta(model.fieldMeta);
  if (fieldMeta) cfg.fieldMeta = fieldMeta;
  cfg.scopeMode = model.scopeMode || 'SUBJECT';
  return cfg;
}

/** 预设周期 → time_param_json：WIDE_TABLE 与 KPI_DETAIL(TREND) 携带，其余 null */
export function buildTimeParamJson(model) {
  if (model.sourceKind === 'WIDE_TABLE') return JSON.stringify(model.wide.timeParams || []);
  if (model.sourceKind === 'KPI_DETAIL' && model.kpiDetail.mode === 'TREND') {
    return JSON.stringify(model.kpiDetail.timeParams || []);
  }
  return null;
}

/** fieldMeta 落盘行 → 表单行（补齐可选字段默认值，便于 v-model 绑定） */
function fieldMetaToRows(fieldMeta) {
  return (Array.isArray(fieldMeta) ? fieldMeta : []).map(f => ({
    col: f.col || '', alias: f.alias || '', role: f.role || 'METRIC',
    unit: f.unit || '', decimals: f.decimals ?? null
  }));
}

/**
 * config_json（+ time_param_json）→ 表单模型补丁（编辑回填，读时兼容 v1 旧数据：
 * 缺 fieldMeta/aggregation/scopeMode 时补默认值）。返回的补丁按 Object.assign 语义覆盖 defaultDsModel。
 */
export function parseConfigJson(sourceKind, cfg, timeParamJson) {
  const c = cfg || {};
  const base = defaultDsModel();
  const patch = {
    fieldMeta: fieldMetaToRows(c.fieldMeta),
    scopeMode: SCOPE_MODES.includes(c.scopeMode) ? c.scopeMode : 'SUBJECT',
    aggEnabled: false
  };
  let timeParams = null;
  try {
    const t = JSON.parse(timeParamJson || 'null');
    if (Array.isArray(t)) timeParams = t;
  } catch { /* 脏数据回退默认 */ }

  if (sourceKind === 'WIDE_TABLE') {
    const metrics = Array.isArray(c.metrics) ? c.metrics : [];
    patch.wide = {
      table: c.table || base.wide.table,
      metricCodes: metrics.map(m => m.metricCode).filter(Boolean),
      // 后端重写后的 config 携带 slot → 转成 val_N 供聚合过滤列下拉提示
      slotCols: metrics.filter(m => m.slot !== undefined && m.slot !== null).map(m => `val_${m.slot}`),
      timeParams: timeParams || [...TIME_PARAM_PRESETS]
    };
    const agg = c.aggregation;
    if (agg && typeof agg === 'object') {
      patch.aggEnabled = true;
      patch.aggregation = {
        groupBy: AGG_GROUP_BYS.includes(agg.groupBy) ? agg.groupBy : 'NONE',
        agg: AGG_FUNCS.includes(agg.agg) ? agg.agg : 'SUM',
        filters: (Array.isArray(agg.filters) ? agg.filters : [])
          .map(f => ({ col: f.col || '', op: FILTER_OPS.includes(f.op) ? f.op : 'EQ', value: f.value ?? '' }))
      };
    }
  } else if (sourceKind === 'KPI_RESULT') {
    patch.kpi = { cycleType: c.cycleType || 'MONTHLY' };
  } else if (sourceKind === 'KPI_DETAIL') {
    patch.kpiDetail = {
      schemeCode: c.schemeCode || '',
      subjectType: c.subjectType === 'ORG' ? 'ORG' : 'EMP',
      mode: c.mode === 'TREND' ? 'TREND' : 'SNAPSHOT',
      metrics: (Array.isArray(c.metrics) ? c.metrics : [])
        .map(m => ({ metricCode: m.metricCode || '', metricName: m.metricName || '' })),
      valueCol: KPI_VALUE_COLS.includes(c.valueCol) ? c.valueCol : 'score',
      timeParams: timeParams || [...TIME_PARAM_PRESETS]
    };
  } else {
    patch.sql = { text: c.sql || '', dateCol: c.dateCol || '' };
  }
  return patch;
}

/**
 * 提交前校验（与后端 43009 规则对齐），返回中文错误文案数组（空数组=通过）。
 * 名称/操作原因等非 config 字段仍由 Datasources.vue 单独校验。
 */
export function validateDsModel(model) {
  const errors = [];
  switch (model.sourceKind) {
    case 'WIDE_TABLE':
      if (!model.wide.metricCodes.length) errors.push('请至少选择一个指标');
      if (model.aggEnabled) {
        if (!AGG_GROUP_BYS.includes(model.aggregation.groupBy)) errors.push('聚合维度（groupBy）取值非法');
        if (!AGG_FUNCS.includes(model.aggregation.agg)) errors.push('聚合函数（agg）取值非法');
        (model.aggregation.filters || []).forEach((f, i) => {
          const hasAny = String(f.col || '').trim() || String(f.value || '').trim();
          if (!hasAny) return; // 整行空 → 组装时会丢弃，不报错
          if (!String(f.col || '').trim() || !FILTER_OPS.includes(f.op) || !String(f.value ?? '').trim()) {
            errors.push(`过滤条件第 ${i + 1} 行需填全 列/运算符/值（IN 用逗号分隔多值）`);
          }
        });
      }
      break;
    case 'KPI_DETAIL': {
      const d = model.kpiDetail;
      if (!String(d.schemeCode || '').trim()) errors.push('请选择 KPI 方案');
      if (!['EMP', 'ORG'].includes(d.subjectType)) errors.push('主体类型只能是 EMP 或 ORG');
      if (!['SNAPSHOT', 'TREND'].includes(d.mode)) errors.push('模式只能是 SNAPSHOT 或 TREND');
      if (d.mode === 'TREND') {
        if (!d.metrics.length) {
          errors.push('趋势模式必须至少选择一个指标（细项）');
        } else if (d.metrics.some(m => !String(m.metricCode || '').trim() || !String(m.metricName || '').trim())) {
          errors.push('指标快照缺少编码或名称，请重新选择指标');
        }
        if (!KPI_VALUE_COLS.includes(d.valueCol)) errors.push('取值列只能是 score 或 completeRate');
      }
      break;
    }
    case 'CUSTOM_SQL':
      if (!String(model.sql.text || '').trim()) errors.push('SQL 语句必填');
      if (model.dsType === 'TIMESERIES' && !String(model.sql.dateCol || '').trim()) {
        errors.push('时序型必须声明日期列');
      }
      break;
    default:
      break;
  }
  // fieldMeta 通用校验（col 非空不重复、role 枚举、decimals 非负整数）——整行空跳过
  const seen = new Set();
  (model.fieldMeta || []).forEach((r, i) => {
    if (isEmptyFieldMetaRow(r)) return;
    const col = String(r.col || '').trim();
    if (!col) { errors.push(`字段元数据第 ${i + 1} 行缺少列名（col）`); return; }
    if (seen.has(col)) errors.push(`字段元数据列名重复：${col}`);
    seen.add(col);
    if (!FIELD_ROLES.includes(r.role)) errors.push(`字段元数据第 ${i + 1} 行角色只能是 DIM 或 METRIC`);
    if (r.decimals !== null && r.decimals !== undefined && r.decimals !== '') {
      const n = Number(r.decimals);
      if (!Number.isInteger(n) || n < 0) errors.push(`字段元数据第 ${i + 1} 行小数位必须是非负整数`);
    }
  });
  if (!SCOPE_MODES.includes(model.scopeMode)) errors.push('数据范围模式只能是 SUBJECT 或 GLOBAL');
  return errors;
}

/**
 * 试跑预览列头：按 col 匹配 columnsMeta（可空，旧接口无此字段时退化为原始列名），
 * 别名替换列头、单位以全角括号附注。
 */
export function buildPreviewColumns(columns, columnsMeta) {
  const metaByCol = new Map((Array.isArray(columnsMeta) ? columnsMeta : []).map(m => [m.col, m]));
  return (columns || []).map(col => {
    const meta = metaByCol.get(col) || null;
    let label = meta?.alias || col;
    if (meta?.unit) label += `（${meta.unit}）`;
    return { col, label, meta };
  });
}

/**
 * 试跑预览单元格：null/undefined → "—"（如完成率 target=0 时后端返回 NULL）；
 * 数值且 meta.decimals 存在时按小数位格式化；其余原样返回。
 */
export function formatPreviewCell(value, meta) {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'number' && Number.isFinite(value)
    && meta && meta.decimals !== null && meta.decimals !== undefined) {
    return value.toFixed(meta.decimals);
  }
  return value;
}
