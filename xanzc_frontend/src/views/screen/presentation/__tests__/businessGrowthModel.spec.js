// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

import { buildBusinessGrowthModel } from '../model/businessGrowthModel.js';

const FIELDS = {
  retailDeposit: '测试_直营零售存款',
  retailLoan: '测试_直营零售贷款',
  corpDeposit: '测试_直营对公存款',
  corpLoan: '测试_直营对公贷款'
};

function presentation(series = Object.entries(FIELDS).map(([seriesKey, field]) => ({
  seriesKey,
  field,
  label: seriesKey
}))) {
  return {
    displaySchemaVersion: 1,
    template: 'branch-overview-v1',
    display: {
      components: [{
        componentId: 'trend-main',
        componentType: 'TREND',
        layoutRegion: 'CENTER',
        order: 1,
        visible: true,
        content: { series },
        dataRefs: [{ blockId: 57, role: 'PRIMARY', unit: 'YUAN' }]
      }, {
        componentId: 'branch-trend',
        componentType: 'TREND',
        layoutRegion: 'CENTER',
        order: 2,
        visible: true,
        content: { series: [{ seriesKey: 'deposit', field: 'deposit', label: '存款', unit: 'YUAN' }] },
        dataRefs: [{ blockId: 999, role: 'BRANCH_TREND', unit: 'YUAN', slot: 'branchTrend' }]
      }]
    }
  };
}

function modelWithRows(rows, unitByField = Object.fromEntries(Object.values(FIELDS).map(field => [field, 'YUAN']))) {
  return {
    blockResults: {
      57: { rows, unitByField },
      64: { rows: [{ date: '2026-09-28', [FIELDS.corpDeposit]: 999999 }] }
    }
  };
}

describe('buildBusinessGrowthModel', () => {
  it('从分行主趋势 block57 生成零售和对公两张各含存款/贷款的历史曲线，保留0和null', () => {
    const rows = [
      {
        date: '2026-09-27',
        [FIELDS.retailDeposit]: 120000,
        [FIELDS.retailLoan]: 0,
        [FIELDS.corpDeposit]: 300000,
        [FIELDS.corpLoan]: null
      },
      {
        data_date: '2026-09-28',
        [FIELDS.retailDeposit]: 130000,
        [FIELDS.retailLoan]: 200000,
        [FIELDS.corpDeposit]: 310000,
        [FIELDS.corpLoan]: 410000
      }
    ];

    const result = buildBusinessGrowthModel(presentation(), modelWithRows(rows));

    expect(result).toMatchObject({ enabled: true, sourceBlockId: 57, title: '业务增长曲线' });
    expect(result.groups.map(group => group.title)).toEqual(['零售业务', '对公业务']);
    expect(result.groups.map(group => group.series.map(item => item.key))).toEqual([
      ['retailDeposit', 'retailLoan'],
      ['corpDeposit', 'corpLoan']
    ]);
    expect(result.groups[0].rows).toEqual([
      { date: '2026-09-27', retailDeposit: 120000, retailLoan: 0 },
      { date: '2026-09-28', retailDeposit: 130000, retailLoan: 200000 }
    ]);
    expect(result.groups[0].unit).toBe('元');
    expect(result.groups[1].rows[0].corpLoan).toBeNull();
    expect(result.groups.every(group => group.status === 'READY')).toBe(true);
  });

  it('按字段单位把万元和亿元统一换算成元，并保留有明确单位的0', () => {
    const rows = [{
      date: '2026-09-28',
      [FIELDS.retailDeposit]: 12.5,
      [FIELDS.retailLoan]: 0,
      [FIELDS.corpDeposit]: 2,
      [FIELDS.corpLoan]: 3
    }];
    const result = buildBusinessGrowthModel(presentation(), modelWithRows(rows, {
      [FIELDS.retailDeposit]: '万元',
      [FIELDS.retailLoan]: 'TEN_THOUSAND',
      [FIELDS.corpDeposit]: '亿元',
      [FIELDS.corpLoan]: 'HUNDRED_MILLION'
    }));

    expect(result.groups[0].rows[0]).toMatchObject({ retailDeposit: 125000, retailLoan: 0 });
    expect(result.groups[1].rows[0]).toMatchObject({ corpDeposit: 200000000, corpLoan: 300000000 });
    expect(result.groups.flatMap(group => group.series).every(item => item.unit === '元')).toBe(true);
  });

  it.each([
    ['缺少固定字段', [{ date: '2026-09-28', [FIELDS.retailDeposit]: 1 }]],
    ['缺少单位证据', [{ date: '2026-09-28', [FIELDS.retailDeposit]: 1, [FIELDS.retailLoan]: 2, [FIELDS.corpDeposit]: 3, [FIELDS.corpLoan]: 4 }], {}],
    ['重复日期', [{ date: '2026-09-28', [FIELDS.retailDeposit]: 1, [FIELDS.retailLoan]: 2, [FIELDS.corpDeposit]: 3, [FIELDS.corpLoan]: 4 }, { data_date: '2026-09-28', [FIELDS.retailDeposit]: 5, [FIELDS.retailLoan]: 6, [FIELDS.corpDeposit]: 7, [FIELDS.corpLoan]: 8 }]],
    ['空日期', [{ date: '', [FIELDS.retailDeposit]: 1, [FIELDS.retailLoan]: 2, [FIELDS.corpDeposit]: 3, [FIELDS.corpLoan]: 4 }]],
    ['全部缺数', [{ date: '2026-09-28', [FIELDS.retailDeposit]: null, [FIELDS.retailLoan]: null, [FIELDS.corpDeposit]: null, [FIELDS.corpLoan]: null }]]
  ])('%s时各业务线进入明确空态', (reason, rows, unitByField) => {
    const configuredWithoutUnits = Object.entries(FIELDS).map(([seriesKey, field]) => ({ seriesKey, field, label: seriesKey }));
    const source = reason === '缺少单位证据' ? presentation(configuredWithoutUnits) : presentation();
    const result = buildBusinessGrowthModel(source, modelWithRows(rows, unitByField));

    expect(result.groups).toHaveLength(2);
    expect(result.groups.every(group => group.status === 'EMPTY')).toBe(true);
    expect(result.groups.map(group => group.emptyMessage)).toEqual([
      '零售业务历史存贷款数据待接入',
      '对公业务历史存贷款数据待接入'
    ]);
  });

  it('只读取分行主趋势的精确 block57，不从 block64 或非分行数据推导业务线历史', () => {
    const source = presentation();
    const sourceBefore = JSON.parse(JSON.stringify(source));
    const model = modelWithRows([{ date: '2026-09-28', [FIELDS.retailDeposit]: 1, [FIELDS.retailLoan]: 2, [FIELDS.corpDeposit]: 3, [FIELDS.corpLoan]: 4 }]);
    const modelBefore = JSON.parse(JSON.stringify(model));

    const result = buildBusinessGrowthModel(source, model);
    const nonBranchResult = buildBusinessGrowthModel({ ...source, template: 'province-overview-v1' }, model);

    expect(result.sourceBlockId).toBe(57);
    expect(result.groups[1].rows[0].corpDeposit).toBe(3);
    expect(nonBranchResult.enabled).toBe(false);
    expect(source).toEqual(sourceBefore);
    expect(model).toEqual(modelBefore);
  });

  it('没有分行主趋势 dataRef 时，即使 block57 存在也不消费未绑定数据', () => {
    const noMainRef = {
      ...presentation(),
      display: {
        components: [{
          componentId: 'branch-trend',
          componentType: 'TREND',
          layoutRegion: 'CENTER',
          visible: true,
          content: { series: [] },
          dataRefs: [{ blockId: 999, slot: 'branchTrend', unit: 'YUAN' }]
        }]
      }
    };
    const result = buildBusinessGrowthModel(noMainRef, modelWithRows([{
      date: '2026-09-28',
      [FIELDS.retailDeposit]: 1,
      [FIELDS.retailLoan]: 2,
      [FIELDS.corpDeposit]: 3,
      [FIELDS.corpLoan]: 4
    }]));

    expect(result.sourceBlockId).toBeNull();
    expect(result.groups.every(group => group.status === 'EMPTY')).toBe(true);
  });

  it('空白字符串、布尔值和非法数字保持缺失，不伪装成0', () => {
    const result = buildBusinessGrowthModel(presentation(), modelWithRows([{
      date: '2026-09-28',
      [FIELDS.retailDeposit]: '   ',
      [FIELDS.retailLoan]: false,
      [FIELDS.corpDeposit]: 'not-a-number',
      [FIELDS.corpLoan]: 1
    }]));

    expect(result.groups[0].status).toBe('EMPTY');
    expect(result.groups[1].status).toBe('READY');
    expect(result.groups[1].rows[0]).toMatchObject({ corpDeposit: null, corpLoan: 1 });
    expect(result.groups[1].rows[0].corpDeposit).not.toBe(0);
  });

  it('配置 seriesKey 指向业务语义但 field 不是固定原始列时，不借用该单位证据', () => {
    const wrongFieldPresentation = presentation(Object.keys(FIELDS).map(seriesKey => ({
      seriesKey,
      field: 'other_field',
      label: seriesKey,
      unit: 'YUAN'
    })));
    const result = buildBusinessGrowthModel(wrongFieldPresentation, modelWithRows([{
      date: '2026-09-28',
      [FIELDS.retailDeposit]: 1,
      [FIELDS.retailLoan]: 2,
      [FIELDS.corpDeposit]: 3,
      [FIELDS.corpLoan]: 4
    }], {}));

    expect(result.groups.every(group => group.status === 'EMPTY')).toBe(true);
  });
});
