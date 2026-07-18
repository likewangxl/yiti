// 大屏图表通用数据变换纯函数（无 Vue 依赖，vitest 直测）。
// 数据契约：POST /api/screen/data → { columns:[...], rows:[[...]], columnsMeta?:[{col,alias,role,unit,decimals}] }
// columnsMeta 是后端可选扩展，所有函数必须对其缺失容错（缺失时回退原列名/默认格式化）。

/** 按列名查 columnsMeta 行；columnsMeta 缺失或未命中返回 null */
export function metaOf(col, columnsMeta) {
  if (!Array.isArray(columnsMeta)) return null;
  return columnsMeta.find(m => m && m.col === col) || null;
}

/** 列显示名：columnsMeta.alias 优先，缺失回退原列名 */
export function displayName(col, columnsMeta) {
  return metaOf(col, columnsMeta)?.alias || col;
}

/**
 * 数值格式化：null/空串 → '—'；非数值原样字符串返回；数值按 decimals 位小数 + 千分位。
 * 与 MetricCard 现有格式化口径一致（toLocaleString zh-CN）。
 */
export function fmtNum(v, decimals = 2) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return n.toLocaleString('zh-CN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

/** 百分比封顶：null/NaN/负 → 0，超 100 → 100（进度条宽度/仪表指针/水位统一用） */
export function clampPct(v) {
  const n = Number(v);
  if (v == null || Number.isNaN(n)) return 0;
  return Math.min(100, Math.max(0, n));
}

/** 值是否可视为数值（null 视为"数值列里的空洞"，不否定该列） */
function isNumericish(v) {
  return v == null || v === '' || !Number.isNaN(Number(v));
}

/**
 * 行列转系列（柱状对比/堆叠面积共用）：首列为类目（类目名或 data_date），其余数值列为系列。
 * @param {string[]} columns 列名
 * @param {Array[]} rows 行数据
 * @param {string[]|null} seriesCols 指定系列列（bind.items 场景）；空则自动取"其余数值列"
 * @returns {{categories: any[], series: {name: string, data: (number|null)[]}[]}}
 */
export function rowsToSeries(columns, rows, seriesCols = null) {
  const cols = Array.isArray(columns) ? columns : [];
  const data = Array.isArray(rows) ? rows : [];
  const categories = data.map(r => r[0]);
  let picked;
  if (Array.isArray(seriesCols) && seriesCols.length) {
    picked = seriesCols.filter(c => cols.includes(c)); // 忽略响应里不存在的绑定列
  } else {
    // 自动模式：排除存在"非空且非数值"取值的列（如备注文本列），保留含 null 空洞的数值列
    picked = cols.slice(1).filter(c => {
      const idx = cols.indexOf(c);
      return data.every(r => isNumericish(r[idx]));
    });
  }
  const series = picked.map(c => {
    const idx = cols.indexOf(c);
    return {
      name: c,
      data: data.map(r => {
        const v = r[idx];
        if (v == null || v === '') return null;
        const n = Number(v);
        return Number.isNaN(n) ? null : n;
      })
    };
  });
  return { categories, series };
}

/**
 * 单值取列（仪表盘/水波完成度）：优先级 指定列 > 名称或别名含"完成率" > 首个数值列。
 * @param {string[]} columns 列名
 * @param {Array[]} rows 行数据（取末行探测数值列）
 * @param {string|null} preferred bind.valueCol（属性面板选列）
 * @param {Array|null} columnsMeta 可选字段元数据（别名参与"完成率"匹配）
 * @returns {number} 命中列下标，无可用列返回 -1
 */
export function pickValueCol(columns, rows, preferred, columnsMeta) {
  const cols = Array.isArray(columns) ? columns : [];
  if (!cols.length) return -1;
  if (preferred) {
    const idx = cols.indexOf(preferred);
    if (idx >= 0) return idx;
  }
  const byName = cols.findIndex(c => String(c).includes('完成率') || String(displayName(c, columnsMeta)).includes('完成率'));
  if (byName >= 0) return byName;
  // 回退：末行中首个可转数值的列（跳过首列之外无限制——首列若是数值也可命中，如单列单值数据源）
  const last = Array.isArray(rows) && rows.length ? rows[rows.length - 1] : null;
  if (!last) return -1;
  for (let i = 0; i < cols.length; i++) {
    const v = last[i];
    if (v != null && v !== '' && !Number.isNaN(Number(v))) return i;
  }
  return -1;
}
