import { describe, it, expect } from 'vitest';
import {
  displayName, metaOf, fmtNum, clampPct, rowsToSeries, pickValueCol, convertAmountScaleRows
} from '../chartData';

// columnsMeta 样例：后端可选扩展字段，组件必须对缺失容错
const META = [
  { col: '存款余额', alias: '一般性存款', role: 'METRIC', unit: '万元', decimals: 1 },
  { col: 'data_date', role: 'DIM' }
];

describe('chartData.displayName / metaOf（columnsMeta 容错）', () => {
  it('有 columnsMeta 时用别名，无别名/无 meta 时回退原列名', () => {
    expect(displayName('存款余额', META)).toBe('一般性存款');
    expect(displayName('data_date', META)).toBe('data_date');   // meta 行存在但无 alias
    expect(displayName('贷款余额', META)).toBe('贷款余额');     // meta 行不存在
    expect(displayName('存款余额', null)).toBe('存款余额');     // columnsMeta 整体缺失
    expect(displayName('存款余额', undefined)).toBe('存款余额');
  });
  it('metaOf 按 col 查 meta 行，缺失返回 null', () => {
    expect(metaOf('存款余额', META)?.unit).toBe('万元');
    expect(metaOf('不存在', META)).toBeNull();
    expect(metaOf('存款余额', null)).toBeNull();
  });
  it('metaOf 为合法金额量级派生展示单位和固定 2 位，不覆盖显式自定义字段', () => {
    expect(metaOf('金额', [{ col: '金额', role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN' }]))
      .toMatchObject({ amountScale: 'TEN_THOUSAND_YUAN', unit: '万元', decimals: 2 });
    expect(metaOf('自定义', [{
      col: '自定义', role: 'METRIC', amountScale: 'YUAN', unit: '元/户', decimals: 1
    }])).toMatchObject({ unit: '元/户', decimals: 1 });
  });
});

describe('chartData.fmtNum（数值格式化）', () => {
  it('null/空串显示 —，非数值原样返回', () => {
    expect(fmtNum(null)).toBe('—');
    expect(fmtNum('')).toBe('—');
    expect(fmtNum(undefined)).toBe('—');
    expect(fmtNum('abc')).toBe('abc');
  });
  it('数值按小数位 + 千分位格式化', () => {
    expect(fmtNum(1234.5, 2)).toBe('1,234.50');
    expect(fmtNum('88.456', 1)).toBe('88.5');
    expect(fmtNum(0, 0)).toBe('0');
  });
});

describe('chartData.clampPct（0-100 封顶）', () => {
  it('null/NaN→0，负→0，超 100 封顶', () => {
    expect(clampPct(null)).toBe(0);
    expect(clampPct('x')).toBe(0);
    expect(clampPct(-5)).toBe(0);
    expect(clampPct(56.7)).toBe(56.7);
    expect(clampPct(123)).toBe(100);
  });
});

describe('chartData.rowsToSeries（首列类目，其余数值列为系列）', () => {
  const columns = ['data_date', '存款', '贷款', '备注'];
  const rows = [
    ['07-01', 10, 20, 'a'],
    ['07-02', 11, null, 'b'],
    ['07-03', 12, 22, 'c']
  ];
  it('自动模式：排除含非数值文本的列（备注），保留含 null 的数值列', () => {
    const r = rowsToSeries(columns, rows);
    expect(r.categories).toEqual(['07-01', '07-02', '07-03']);
    expect(r.series.map(s => s.name)).toEqual(['存款', '贷款']);
    expect(r.series[1].data).toEqual([20, null, 22]);
  });
  it('指定 seriesCols 时按给定列取（忽略不存在的列）', () => {
    const r = rowsToSeries(columns, rows, ['贷款', '不存在']);
    expect(r.series.map(s => s.name)).toEqual(['贷款']);
  });
  it('空数据容错', () => {
    const r = rowsToSeries([], []);
    expect(r.categories).toEqual([]);
    expect(r.series).toEqual([]);
  });
});

describe('chartData.convertAmountScaleRows（金额量级展示转换）', () => {
  it('按元为原始值转换合法 METRIC 列，空值/非数值/手工单位和 DIM 保持原样且不修改原始 rows', () => {
    const columns = ['机构', '元', '万元', '亿元', '手工单位', '文本', '空值', '维度脏值'];
    const rows = [['甲行', 10000, 10000, 100000000, '10000', 'n/a', '', '10000']];
    const original = structuredClone(rows);
    const columnsMeta = [
      { col: '元', role: 'METRIC', amountScale: 'YUAN' },
      { col: '万元', role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN' },
      { col: '亿元', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' },
      { col: '手工单位', role: 'METRIC', unit: '万元' },
      { col: '文本', role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN' },
      { col: '空值', role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN' },
      { col: '维度脏值', role: 'DIM', amountScale: 'TEN_THOUSAND_YUAN' }
    ];

    expect(convertAmountScaleRows(columns, rows, columnsMeta)).toEqual([[
      '甲行', 10000, 1, 1, '10000', 'n/a', '', '10000'
    ]]);
    expect(rows).toEqual(original);
  });

  it('支持数值字符串，非法 amountScale 不转换', () => {
    expect(convertAmountScaleRows(
      ['amount', 'bad'],
      [['100000', '200000']],
      [
        { col: 'amount', role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN' },
        { col: 'bad', role: 'METRIC', amountScale: 'UNKNOWN' }
      ]
    )).toEqual([[10, '200000']]);
  });
});

describe('chartData.pickValueCol（gauge/水波取列：指定列 > 含"完成率" > 首个数值列）', () => {
  const columns = ['机构', '存款完成率', '得分'];
  const rows = [['A支行', 88.5, 90]];
  it('bind.valueCol 指定且存在时优先', () => {
    expect(pickValueCol(columns, rows, '得分', null)).toBe(2);
  });
  it('未指定时取名含"完成率"的列（含 columnsMeta 别名命中）', () => {
    expect(pickValueCol(columns, rows, '', null)).toBe(1);
    // 别名含"完成率"也可命中
    const meta = [{ col: 'v1', alias: '任务完成率' }];
    expect(pickValueCol(['机构', 'v1'], [['A', 66]], null, meta)).toBe(1);
  });
  it('无完成率列时回退首个数值列；全非数值返回 -1', () => {
    expect(pickValueCol(['机构', '名称', '余额'], [['A', 'x', 12]], null, null)).toBe(2);
    expect(pickValueCol(['机构'], [['A']], null, null)).toBe(-1);
    expect(pickValueCol([], [], null, null)).toBe(-1);
  });
});
