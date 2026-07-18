// KPI_DETAIL 数据源（source_kind=KPI_DETAIL，mode=SNAPSHOT）专属数据变换纯函数。
// 固定列结构（spec §3.1）：metric_code、细项名称、目标值、实际值、权重、得分、完成率、缺口
//   完成率可能为 null（target=0 无目标口径），缺口可为负（超额完成）。
import { fmtNum } from './chartData';

/** KPI_DETAIL SNAPSHOT 固定列名 → 结构化字段映射 */
export const KPI_COLS = {
  code: 'metric_code',
  name: '细项名称',
  target: '目标值',
  actual: '实际值',
  weight: '权重',
  score: '得分',
  rate: '完成率',
  gap: '缺口'
};

/** 单值转数值：null/无效 → null（保留"无目标完成率"语义，绝不折成 0） */
function num(v) {
  if (v == null || v === '') return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
}

/**
 * KPI_DETAIL SNAPSHOT 行解析：按固定列名对位（乱序容错），缺失列字段置 null。
 * "细项名称"列缺失时回退第 2 列当名称（与 metric_code 首列的约定对齐）。
 * @returns {{code,name,target,actual,weight,score,rate,gap}[]}
 */
export function parseKpiRows(columns, rows) {
  const cols = Array.isArray(columns) ? columns : [];
  const data = Array.isArray(rows) ? rows : [];
  if (!cols.length || !data.length) return [];
  const idx = {};
  for (const [field, colName] of Object.entries(KPI_COLS)) idx[field] = cols.indexOf(colName);
  if (idx.name < 0 && cols.length > 1) idx.name = 1; // 名称列兜底：第 2 列
  return data.map(r => ({
    code: idx.code >= 0 ? r[idx.code] : null,
    name: idx.name >= 0 ? String(r[idx.name] ?? '') : '',
    target: idx.target >= 0 ? num(r[idx.target]) : null,
    actual: idx.actual >= 0 ? num(r[idx.actual]) : null,
    weight: idx.weight >= 0 ? num(r[idx.weight]) : null,
    score: idx.score >= 0 ? num(r[idx.score]) : null,
    rate: idx.rate >= 0 ? num(r[idx.rate]) : null,
    gap: idx.gap >= 0 ? num(r[idx.gap]) : null
  }));
}

/**
 * 缺口文案：正数=未达标"还差 X"（红），负数=超额"已超额 |X|"（绿），0=已达标（绿），null=—。
 * X 按 decimals（columnsMeta.decimals 或默认 2）千分位格式化。
 * @returns {{type: 'lack'|'over'|'none', text: string}}
 */
export function gapText(gap, decimals = 2) {
  const n = Number(gap);
  if (gap == null || Number.isNaN(n)) return { type: 'none', text: '—' };
  if (n > 0) return { type: 'lack', text: `还差 ${fmtNum(n, decimals)}` };
  if (n < 0) return { type: 'over', text: `已超额 ${fmtNum(Math.abs(n), decimals)}` };
  return { type: 'over', text: '已达标' };
}

/**
 * 雷达图数据：维度=细项名称，值=得分(默认)|完成率。
 * - score 模式：indicator max=该项权重（得分满分=权重的 KPI 口径）；权重缺失/为 0 时
 *   全体回退"全局最大得分向上取整"（保证各维刻度一致可读）。
 * - rate 模式：max 统一 120 封顶（超额可视但不至撑爆），null → 0，值超 120 截断。
 * @param {ReturnType<typeof parseKpiRows>} items
 * @param {'score'|'rate'} valueField
 * @returns {{indicators: {name: string, max: number}[], values: number[]}}
 */
export function kpiRadarData(items, valueField = 'score') {
  const list = Array.isArray(items) ? items : [];
  if (valueField === 'rate') {
    return {
      indicators: list.map(it => ({ name: it.name, max: 120 })),
      values: list.map(it => Math.min(120, Math.max(0, it.rate ?? 0)))
    };
  }
  const allWeighted = list.length > 0 && list.every(it => (it.weight ?? 0) > 0);
  const fallbackMax = Math.max(1, Math.ceil(list.reduce((m, it) => Math.max(m, it.score ?? 0), 0)));
  return {
    indicators: list.map(it => ({ name: it.name, max: allWeighted ? it.weight : fallbackMax })),
    values: list.map(it => it.score ?? 0)
  };
}
