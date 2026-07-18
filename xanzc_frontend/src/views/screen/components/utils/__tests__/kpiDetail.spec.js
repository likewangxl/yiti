import { describe, it, expect } from 'vitest';
import { parseKpiRows, gapText, kpiRadarData } from '../kpiDetail';

// KPI_DETAIL SNAPSHOT 固定列结构（spec §3.1）：完成率可 null（无目标），缺口可负（超额）
const COLS = ['metric_code', '细项名称', '目标值', '实际值', '权重', '得分', '完成率', '缺口'];
const ROWS = [
  ['DEP_BAL', '存款余额', 1000, 800, 30, 24, 80, 200],
  ['LOAN_BAL', '贷款余额', 500, 600, 20, 20, 120, -100],
  ['CUST_NEW', '新增客户', 0, 15, 10, 10, null, -15]
];

describe('kpiDetail.parseKpiRows（KPI_DETAIL SNAPSHOT 列结构解析）', () => {
  it('按固定列名解析为结构化对象，完成率 null 保留', () => {
    const items = parseKpiRows(COLS, ROWS);
    expect(items.length).toBe(3);
    expect(items[0]).toMatchObject({ code: 'DEP_BAL', name: '存款余额', target: 1000, actual: 800, weight: 30, score: 24, rate: 80, gap: 200 });
    expect(items[1].gap).toBe(-100);
    expect(items[2].rate).toBeNull();
  });
  it('列名乱序也能按名对位', () => {
    const cols = ['细项名称', '得分', '完成率', '缺口'];
    const items = parseKpiRows(cols, [['存款', 24, 80, 200]]);
    expect(items[0].name).toBe('存款');
    expect(items[0].score).toBe(24);
    expect(items[0].target).toBeNull(); // 缺失列 → null
  });
  it('缺"细项名称"列时回退第 2 列作为名称；空数据返回 []', () => {
    const items = parseKpiRows(['code', 'label', '得分'], [['C1', '中收', 9]]);
    expect(items[0].name).toBe('中收');
    expect(parseKpiRows([], [])).toEqual([]);
  });
});

describe('kpiDetail.gapText（缺口文案：正=还差(红)、负=已超额(绿)、null=—）', () => {
  it('正数 → 还差 X（lack）', () => {
    expect(gapText(200, 2)).toEqual({ type: 'lack', text: '还差 200.00' });
  });
  it('负数 → 已超额 |X|（over）', () => {
    expect(gapText(-100.5, 2)).toEqual({ type: 'over', text: '已超额 100.50' });
  });
  it('0 → 已达标（over）；null/非数值 → —（none）', () => {
    expect(gapText(0, 2)).toEqual({ type: 'over', text: '已达标' });
    expect(gapText(null)).toEqual({ type: 'none', text: '—' });
    expect(gapText('x')).toEqual({ type: 'none', text: '—' });
  });
  it('小数位遵循传入 decimals（columnsMeta.decimals 或默认 2）', () => {
    expect(gapText(3.14159, 1).text).toBe('还差 3.1');
    expect(gapText(1234.5, 2).text).toBe('还差 1,234.50');
  });
});

describe('kpiDetail.kpiRadarData（雷达图维度=细项，值=得分|完成率）', () => {
  const items = parseKpiRows(COLS, ROWS);
  it('得分模式（默认）：indicator max=权重（满分=权重），值=得分', () => {
    const r = kpiRadarData(items);
    expect(r.indicators.map(i => i.name)).toEqual(['存款余额', '贷款余额', '新增客户']);
    expect(r.indicators[0].max).toBe(30);
    expect(r.values).toEqual([24, 20, 10]);
  });
  it('得分模式权重缺失/为 0 时回退全局最大得分向上取整', () => {
    const its = [{ name: 'a', score: 8.3, weight: null, rate: null }, { name: 'b', score: 12.6, weight: 0, rate: null }];
    const r = kpiRadarData(its, 'score');
    expect(r.indicators.every(i => i.max === 13)).toBe(true);
  });
  it('完成率模式：max=120 封顶，null→0，超 120 截断', () => {
    const its = [{ name: 'a', score: 1, weight: 10, rate: 150 }, { name: 'b', score: 1, weight: 10, rate: null }];
    const r = kpiRadarData(its, 'rate');
    expect(r.indicators.every(i => i.max === 120)).toBe(true);
    expect(r.values).toEqual([120, 0]);
  });
});
