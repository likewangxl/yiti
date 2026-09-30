import { describe, expect, it } from 'vitest';
import { buildDisplayMetricsModel, formatDisplayMetric } from '../model/displayMetricsModel';

const ref = (blockId, metricCode, metricName, unit = 'YUAN') => ({
  blockId, role: 'PRIMARY', metricCode, metricName, unit, dimension: 'ORG'
});

const component = (componentId, componentType, overrides = {}) => ({
  componentId,
  componentType,
  layoutRegion: 'LEFT',
  order: 0,
  visible: true,
  text: { titleMode: 'AUTO', title: '', subtitle: '', description: '' },
  format: { displayUnit: 'AUTO', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED', emptyText: '—' },
  content: { mainField: 'value', subFields: [] },
  dataRefs: [ref(1, 'deposit', '存款余额')],
  ...overrides
});

describe('displayMetricsModel', () => {
  it('旧 presentation 不进入新展示协议，保持旧 Dashboard 分支', () => {
    expect(buildDisplayMetricsModel({ type: 'CODE', template: 'branch-overview-v1' }, { kpis: [] }))
      .toMatchObject({ enabled: false, components: [] });
  });

  it('按 visible、layoutRegion、order 和数组顺序稳定排序，并支持同类多实例', () => {
    const presentation = {
      displaySchemaVersion: 1,
      display: { components: [
        component('b', 'METRIC_CARD', { layoutRegion: 'RIGHT', order: 2, dataRefs: [ref(2, 'loan', '贷款余额')] }),
        component('hidden', 'METRIC_CARD', { visible: false }),
        component('a', 'METRIC_CARD', { layoutRegion: 'LEFT', order: 1 }),
        component('c', 'COMPLETION', { layoutRegion: 'LEFT', order: 1, dataRefs: [ref(3, 'rate', '完成率', 'RATIO')] })
      ] }
    };
    const result = buildDisplayMetricsModel(presentation, {
      kpis: [{ key: 'deposit', value: 123.456, unit: '亿元' }, { key: 'loan', value: 20, unit: '亿元' }],
      targets: [{ key: 'rate', rate: 1.25, unit: '%' }]
    });
    expect(result.enabled).toBe(true);
    expect(result.components.map(item => item.componentId)).toEqual(['a', 'c', 'b']);
    expect(result.components).toHaveLength(3);
  });

  it('标题优先使用 CUSTOM，其次名称快照；身份仍由 componentId/dataRef 保持', () => {
    const custom = component('custom-card', 'METRIC_CARD', {
      text: { titleMode: 'CUSTOM', title: '我的余额', subtitle: '自定义副标题', description: '说明' },
      dataRefs: [ref(8, 'deposit', '存款余额')]
    });
    const auto = component('auto-card', 'METRIC_CARD', {
      dataRefs: [ref(9, 'loan', '贷款余额')]
    });
    const { components } = buildDisplayMetricsModel({ displaySchemaVersion: 1, display: { components: [custom, auto] } }, {
      kpis: [{ key: 'deposit', value: 8, unit: '亿元' }, { key: 'loan', value: 9, unit: '亿元' }]
    });
    expect(components[0]).toMatchObject({ componentId: 'custom-card', title: '我的余额', subtitle: '自定义副标题', metricCode: 'deposit' });
    expect(components[1]).toMatchObject({ componentId: 'auto-card', title: '贷款余额', metricCode: 'loan' });
  });

  it('0 与 null 分开，保留负数/超100文本；完成图形进度才钳制到0～100', () => {
    const config = { displaySchemaVersion: 1, display: { components: [
      component('zero', 'METRIC_CARD', { dataRefs: [ref(1, 'deposit', '余额', 'YUAN')], format: { displayUnit: 'YUAN', decimals: 0, thousandsSeparator: true, emptyText: '暂无' } }),
      component('missing', 'METRIC_CARD', { dataRefs: [ref(2, 'unknown', '未知', 'YUAN')], format: { displayUnit: 'YUAN', decimals: 2, thousandsSeparator: true, emptyText: '待接入' } }),
      component('completion', 'COMPLETION', { dataRefs: [ref(3, 'rate', '完成率', 'RATIO')], format: { displayUnit: 'PERCENT', decimals: 1, thousandsSeparator: false, emptyText: '待接入' } })
    ] } };
    const result = buildDisplayMetricsModel(config, {
      kpis: [{ key: 'deposit', value: 0, unit: '亿元' }],
      targets: [{ key: 'rate', rate: 1.5, unit: 'RATIO' }]
    });
    expect(result.components[0]).toMatchObject({ value: 0, text: '0元', state: 'READY' });
    expect(result.components[1]).toMatchObject({ value: null, text: '待接入', state: 'NO_SOURCE' });
    expect(result.components[2]).toMatchObject({ value: 150, text: '150.0%', progress: 100, state: 'READY' });

    const negative = formatDisplayMetric(-12.5, { displayUnit: 'PERCENT', decimals: 1, thousandsSeparator: true });
    expect(negative.text).toBe('-12.5%');
  });

  it('主字段与 subFields 只读取现有 block 结果，不用标题猜值；来源替换会清除旧值', () => {
    const config = { displaySchemaVersion: 1, display: { components: [component('multi', 'METRIC_CARD', {
      content: { mainField: 'value', subFields: [{ field: 'change', label: '较上期', unit: 'PERCENT' }] },
      dataRefs: [ref(7, 'deposit', '存款余额')]
    })] } };
    const first = buildDisplayMetricsModel(config, { blockResults: { 7: { value: -2, change: 0 } } });
    expect(first.components[0]).toMatchObject({ value: -2, text: '-2.00元', subFields: [{ value: 0, text: '0.00%' }] });
    const second = buildDisplayMetricsModel(config, { blockResults: { 7: { value: null } } });
    expect(second.components[0]).toMatchObject({ value: null, text: '—', state: 'NO_VALUE' });
    expect(second.components[0].subFields[0]).toMatchObject({ value: null, text: '—' });
  });

  it('显式主字段缺失时不回退 block 的 value，按待接入处理；value 主字段仍读取兼容值', () => {
    const config = { displaySchemaVersion: 1, display: { components: [
      component('explicit-field', 'METRIC_CARD', {
        content: { mainField: '测试_对公贷款目标完成率', subFields: [] },
        format: { displayUnit: 'PERCENT', decimals: 2, emptyText: '待接入' },
        dataRefs: [ref(31, '', '对公贷款完成率', 'PERCENT')]
      }),
      component('value-field', 'METRIC_CARD', {
        content: { mainField: 'value', subFields: [] },
        format: { displayUnit: 'YUAN', decimals: 2 },
        dataRefs: [ref(32, '', '存款余额', 'YUAN')]
      })
    ] } };
    const result = buildDisplayMetricsModel(config, {
      blockResults: { 31: { value: 86.4, unit: 'PERCENT' }, 32: { value: 123.45, unit: 'YUAN' } }
    });

    expect(result.components[0]).toMatchObject({ value: null, text: '待接入', state: 'NO_SOURCE' });
    expect(result.components[1]).toMatchObject({ value: 123.45, text: '123.45元', state: 'READY' });
  });

  it('分组业务卡按当前数据日期计算上一个自然月月末差值，并区分金额与完成率百分点', () => {
    const amount = component('business-retail-deposit-balance', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'retailDeposit', '零售存款余额', 'HUNDRED_MILLION')],
      content: { mainField: 'retailDeposit', subFields: [] },
      format: { displayUnit: 'HUNDRED_MILLION', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED' }
    });
    const rate = component('business-retail-deposit-rate', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'retailDepositRate', '零售存款完成率', 'RATIO')],
      content: { mainField: 'retailDepositRate', subFields: [] },
      format: { displayUnit: 'PERCENT', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED' }
    });
    const result = buildDisplayMetricsModel({
      template: 'branch-overview-v1', displaySchemaVersion: 1,
      display: { components: [amount, rate] }
    }, {
      dataDate: '2027-01-15',
      blockResults: {
        31: { retailDeposit: 125, retailDepositRate: 0.9064, unit: 'HUNDRED_MILLION' },
        57: { rows: [
          { data_date: '2027-01-15', retailDeposit: 125, retailDepositRate: 0.9064 },
          { data_date: '2026-12-31', retailDeposit: 100, retailDepositRate: 0.8 }
        ] }
      }
    });

    expect(result.components[0].monthDelta).toMatchObject({
      state: 'READY', value: 25, text: '较上月 +25.00亿元'
    });
    expect(result.components[1].monthDelta).toMatchObject({
      state: 'READY', value: 10.64, text: '较上月 +10.64个百分点'
    });
  });

  it('金额单位选择只覆盖分组页头金额卡和营业收入，并同步换算较上月金额', () => {
    const amount = component('business-retail-deposit-balance', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'retailDeposit', '零售存款余额', 'YUAN')],
      content: { mainField: 'retailDeposit', subFields: [] },
      format: { displayUnit: 'TEN_THOUSAND', decimals: 2, thousandsSeparator: true }
    });
    const rate = component('business-retail-deposit-rate', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'retailDepositRate', '零售存款完成率', 'RATIO')],
      content: { mainField: 'retailDepositRate', subFields: [] },
      format: { displayUnit: 'PERCENT', decimals: 2, thousandsSeparator: true }
    });
    const revenue = component('business-revenue-operating', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'revenue', '营业收入', 'YUAN')],
      content: { mainField: 'revenue', subFields: [] },
      format: { displayUnit: 'TEN_THOUSAND', decimals: 2, thousandsSeparator: true }
    });
    const presentation = {
      template: 'branch-overview-v1', displaySchemaVersion: 1,
      display: { components: [amount, rate, revenue] }
    };
    const model = {
      dataDate: '2026-09-21',
      blockResults: {
        31: { retailDeposit: 1068201500, retailDepositRate: 0.9064, revenue: 200000000, unitByField: {
          retailDeposit: 'YUAN', retailDepositRate: 'RATIO', revenue: 'YUAN'
        } },
        57: { rows: [
          { date: '2026-09-21', retailDeposit: 1068201500, retailDepositRate: 0.9064, revenue: 200000000 },
          { date: '2026-08-31', retailDeposit: 1056201500, retailDepositRate: 0.8, revenue: 190000000 }
        ], unitByField: { retailDeposit: 'YUAN', retailDepositRate: 'RATIO', revenue: 'YUAN' } }
      }
    };

    const result = buildDisplayMetricsModel(presentation, model, { amountUnit: 'YUAN' });
    expect(result.components.find(item => item.componentId === 'business-retail-deposit-balance')).toMatchObject({
      text: '1,068,201,500.00元', unit: '元', sourceUnit: 'YUAN',
      monthDelta: { text: '较上月 +12,000,000.00元', unit: '元' }
    });
    expect(result.components.find(item => item.componentId === 'business-revenue-operating')).toMatchObject({
      text: '200,000,000.00元', unit: '元'
    });
    expect(result.components.find(item => item.componentId === 'business-retail-deposit-rate')).toMatchObject({
      text: '90.64%', monthDelta: { text: '较上月 +10.64个百分点' }
    });
  });

  it('金额较上月先按原值相减再换算，避免目标单位分别四舍五入丢失差值', () => {
    const card = component('business-corp-loan-balance', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'loan', '对公贷款余额', 'YUAN')],
      content: { mainField: 'loan', subFields: [] },
      format: { displayUnit: 'TEN_THOUSAND', decimals: 2, thousandsSeparator: true }
    });
    const result = buildDisplayMetricsModel({
      template: 'branch-overview-v1', displaySchemaVersion: 1,
      display: { components: [card] }
    }, {
      dataDate: '2026-09-21',
      blockResults: {
        31: { loan: 1000040, unit: 'YUAN' },
        57: { rows: [{ date: '2026-09-21', loan: 1000040 }, { date: '2026-08-31', loan: 999980 }], unitByField: { loan: 'YUAN' } }
      }
    }, { amountUnit: 'TEN_THOUSAND' });

    expect(result.components[0].monthDelta).toMatchObject({
      state: 'READY', value: 0.006, text: '较上月 +0.01万元'
    });
  });

  it('上月月末缺值、日期非法、重复日期或历史来源不唯一时不计算差值', () => {
    const card = component('business-corp-loan-balance', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'loan', '对公贷款余额', 'HUNDRED_MILLION')],
      content: { mainField: 'loan', subFields: [] },
      format: { displayUnit: 'HUNDRED_MILLION', decimals: 2 }
    });
    const presentation = { template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components: [card] } };
    const base = {
      dataDate: '2026-09-21',
      blockResults: {
        31: { loan: 8 },
        57: { rows: [
          { date: '2026-09-21', loan: 8 },
          { date: '2026-08-31', loan: 6 }
        ] }
      }
    };
    expect(buildDisplayMetricsModel(presentation, base).components[0].monthDelta.text).toBe('较上月 +2.00亿元');
    for (const model of [
      { ...base, dataDate: '2026-02-30' },
      { ...base, blockResults: { ...base.blockResults, 57: { rows: [
        { date: '2026-09-21', loan: 8 }, { date: '2026-08-31', loan: null }
      ] } } },
      { ...base, blockResults: { ...base.blockResults, 57: { rows: [
        { date: '2026-09-21', loan: 8 }, { date: '2026-08-31', loan: 6 }, { date: '2026-08-31', loan: 7 }
      ] } } },
      { ...base, blockResults: {
        ...base.blockResults,
        58: { rows: [{ date: '2026-09-21', loan: 8 }, { date: '2026-08-31', loan: 6 }] }
      } }
    ]) {
      expect(buildDisplayMetricsModel(presentation, model).components[0].monthDelta)
        .toMatchObject({ state: 'NO_VALUE', text: '较上月 暂无数据', value: null });
    }

    expect(buildDisplayMetricsModel(presentation, {
      ...base,
      blockResults: { ...base.blockResults, 57: {
        unitByField: { loan: 'YUAN' }, rows: base.blockResults[57].rows
      } }
    }).components[0].monthDelta).toMatchObject({ state: 'NO_VALUE', text: '较上月 暂无数据' });

    expect(buildDisplayMetricsModel(presentation, {
      ...base,
      blockResults: {
        31: { loan: 8, unitByField: { loan: 'HUNDRED_MILLION' } },
        57: {
          columnsMeta: [{ name: 'loan', unit: null, amountScale: 'HUNDRED_MILLION' }],
          unitByField: { loan: 'YUAN' }, rows: base.blockResults[57].rows
        }
      }
    }).components[0].monthDelta).toMatchObject({ state: 'NO_VALUE', text: '较上月 暂无数据' });
  });

  it('当前值和上月月末值为零时仍生成有效的零差值', () => {
    const card = component('business-revenue-fee', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'fee', '手续费收入', 'HUNDRED_MILLION')],
      content: { mainField: 'fee', subFields: [] },
      format: { displayUnit: 'HUNDRED_MILLION', decimals: 2 }
    });
    const result = buildDisplayMetricsModel({ displaySchemaVersion: 1, display: { components: [card] } }, {
      dataDate: '2026-09-21',
      blockResults: {
        31: { fee: 0 },
        57: { rows: [{ date: '2026-09-21', fee: 0 }, { date: '2026-08-31', fee: 0 }] }
      }
    });
    expect(result.components[0].monthDelta).toMatchObject({
      state: 'READY', value: 0, text: '较上月 0.00亿元'
    });
  });

  it('当前值低于上月月末值时显示金额负差和负百分点', () => {
    const amount = component('business-corp-loan-balance', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'loan', '对公贷款余额', 'HUNDRED_MILLION')],
      content: { mainField: 'loan', subFields: [] },
      format: { displayUnit: 'HUNDRED_MILLION', decimals: 2, thousandsSeparator: true }
    });
    const rate = component('business-corp-loan-rate', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'loanRate', '对公贷款完成率', 'RATIO')],
      content: { mainField: 'loanRate', subFields: [] },
      format: { displayUnit: 'PERCENT', decimals: 2, thousandsSeparator: true }
    });
    const result = buildDisplayMetricsModel({
      template: 'branch-overview-v1', displaySchemaVersion: 1,
      display: { components: [amount, rate] }
    }, {
      dataDate: '2026-09-21',
      blockResults: {
        31: { loan: 80, loanRate: 0.8 },
        57: { rows: [
          { date: '2026-09-21', loan: 80, loanRate: 0.8 },
          { date: '2026-08-31', loan: 100, loanRate: 0.9 }
        ] }
      }
    });

    expect(result.components[0].monthDelta).toMatchObject({
      state: 'READY', value: -20, text: '较上月 -20.00亿元'
    });
    expect(result.components[1].monthDelta).toMatchObject({
      state: 'READY', value: -10, text: '较上月 -10.00个百分点'
    });
  });

  it('比例卡 displayUnit 为 RATIO 时仍按百分比点计算差值', () => {
    const card = component('business-retail-deposit-rate-ratio', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'depositRate', '零售存款完成率', 'RATIO')],
      content: { mainField: 'depositRate', subFields: [] },
      format: { displayUnit: 'RATIO', decimals: 2, thousandsSeparator: true }
    });
    const result = buildDisplayMetricsModel({ displaySchemaVersion: 1, display: { components: [card] } }, {
      dataDate: '2026-09-21',
      blockResults: {
        31: { depositRate: 0.9064 },
        57: { rows: [
          { date: '2026-09-21', depositRate: 0.9064 },
          { date: '2026-08-31', depositRate: 0.8 }
        ] }
      }
    });
    expect(result.components[0].monthDelta).toMatchObject({
      state: 'READY', value: 10.64, text: '较上月 +10.64个百分点'
    });
  });

  it('通用卡不生成分组业务差值元数据', () => {
    const card = component('legacy-card', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'loan', '贷款余额', 'HUNDRED_MILLION')],
      content: { mainField: 'loan', subFields: [] }
    });
    const result = buildDisplayMetricsModel({ displaySchemaVersion: 1, display: { components: [card] } }, {
      dataDate: '2026-09-21', blockResults: { 31: { loan: 8 } }
    });
    expect(result.components[0]).not.toHaveProperty('monthDelta');
  });

  it('普通 KPI 保持来源单位优先于 dataRef 单位', () => {
    const card = component('legacy-unit-card', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(1, 'loan', '贷款余额', 'HUNDRED_MILLION')],
      content: { mainField: 'value', subFields: [] },
      format: { displayUnit: 'AUTO', decimals: 2 }
    });
    const result = buildDisplayMetricsModel({ displaySchemaVersion: 1, display: { components: [card] } }, {
      kpis: [{ key: 'loan', value: 8, unit: 'YUAN' }]
    });
    expect(result.components[0]).toMatchObject({ value: 8, text: '8.00元', unit: '元' });
  });

  it('draft 模式按上年末、上月末和上一自然日生成三维差值，保留日期口径与旧 monthDelta', () => {
    const card = component('business-corp-deposit-balance', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'deposit', '存款余额', 'YUAN')],
      content: { mainField: 'deposit', subFields: [] },
      format: { displayUnit: 'TEN_THOUSAND', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED' }
    });
    const rate = component('business-corp-deposit-rate', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'depositRate', '存款完成率', 'RATIO')],
      content: { mainField: 'depositRate', subFields: [] },
      format: { displayUnit: 'PERCENT', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED' }
    });
    const result = buildDisplayMetricsModel({
      template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components: [card, rate] }
    }, {
      dataDate: '2028-03-02',
      blockResults: {
        31: { deposit: 1200000000, depositRate: 0.9064, unitByField: { deposit: 'YUAN', depositRate: 'RATIO' } },
        57: { rows: [
          { date: '2028-03-02', deposit: 1200000000, depositRate: 0.9064 },
          { date: '2027-12-31', deposit: 900000000, depositRate: 0.6064 },
          { date: '2028-02-29', deposit: 1000000000, depositRate: 0.7064 },
          { date: '2028-03-01', deposit: 1100000000, depositRate: 0.8064 }
        ], unitByField: { deposit: 'YUAN', depositRate: 'RATIO' } }
      }
    }, { amountUnit: 'TEN_THOUSAND', draftOverview: true });

    expect(result.components[0].monthDelta).toMatchObject({ state: 'READY', value: 20000, text: '较上月 +20,000.00万元' });
    expect(result.components[0].comparisons).toMatchObject({
      year: { state: 'READY', value: 30000, text: '较上年 +30,000.00万元', referenceDate: '2027-12-31' },
      month: { state: 'READY', value: 20000, text: '较上月 +20,000.00万元', referenceDate: '2028-02-29' },
      day: { state: 'READY', value: 10000, text: '较上日 +10,000.00万元', referenceDate: '2028-03-01' }
    });
    expect(result.components[1].comparisons).toMatchObject({
      year: { state: 'READY', value: 30, text: '较上年 +30.00个百分点' },
      month: { state: 'READY', value: 20, text: '较上月 +20.00个百分点' },
      day: { state: 'READY', value: 10, text: '较上日 +10.00个百分点' }
    });
    const yuanResult = buildDisplayMetricsModel({
      template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components: [card] }
    }, {
      dataDate: '2028-03-02',
      blockResults: {
        31: { deposit: 1200000000, unitByField: { deposit: 'YUAN' } },
        57: { rows: [
          { date: '2028-03-02', deposit: 1200000000 },
          { date: '2027-12-31', deposit: 900000000 },
          { date: '2028-02-29', deposit: 1000000000 },
          { date: '2028-03-01', deposit: 1100000000 }
        ], unitByField: { deposit: 'YUAN' } }
      }
    }, { amountUnit: 'HUNDRED_MILLION', draftOverview: true });
    expect(yuanResult.components[0].comparisons).toMatchObject({
      year: { value: 3, text: '较上年 +3.00亿元' },
      month: { value: 2, text: '较上月 +2.00亿元' },
      day: { value: 1, text: '较上日 +1.00亿元' }
    });
  });

  it('三维差值接受0和负数，但缺失、重复、当前不一致或单位不匹配分别保持不可用', () => {
    const card = component('business-corp-loan-balance', 'METRIC_CARD', {
      layoutRegion: 'HEADER',
      dataRefs: [ref(31, 'loan', '贷款余额', 'YUAN')],
      content: { mainField: 'loan', subFields: [] },
      format: { displayUnit: 'YUAN', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED' }
    });
    const presentation = { template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components: [card] } };
    const base = {
      dataDate: '2028-03-02',
      blockResults: {
        31: { loan: 0, unitByField: { loan: 'YUAN' } },
        57: { rows: [
          { date: '2028-03-02', loan: 0 },
          { date: '2027-12-31', loan: -100 },
          { date: '2028-02-29', loan: -200 },
          { date: '2028-03-01', loan: -300 }
        ], unitByField: { loan: 'YUAN' } }
      }
    };
    const valid = buildDisplayMetricsModel(presentation, base, { draftOverview: true }).components[0];
    expect(valid.comparisons).toMatchObject({
      year: { state: 'READY', value: 100 }, month: { state: 'READY', value: 200 }, day: { state: 'READY', value: 300 }
    });

    const cases = [
      ['缺失上年末', { rows: base.blockResults[57].rows.filter(row => row.date !== '2027-12-31') }, 'year'],
      ['重复上月末', { rows: [...base.blockResults[57].rows, { date: '2028-02-29', loan: -201 }] }, 'month'],
      ['当前值不一致', { rows: base.blockResults[57].rows.map(row => row.date === '2028-03-02' ? { ...row, loan: 1 } : row) }, 'day'],
      ['历史单位不匹配', { rows: base.blockResults[57].rows, unitByField: { loan: 'TEN_THOUSAND' } }, 'year']
    ];
    for (const [, history, key] of cases) {
      const model = { ...base, blockResults: { ...base.blockResults, 57: history } };
      expect(buildDisplayMetricsModel(presentation, model, { draftOverview: true }).components[0].comparisons[key])
        .toMatchObject({ state: 'NO_VALUE', value: null });
    }
  });

  it('显式 comparisons 优先受控历史结果，关闭时不回退 monthDelta', () => {
    const card = component('business-corp-deposit-balance', 'METRIC_CARD', {
      layoutRegion: 'HEADER', dataRefs: [ref(31, 'deposit', '存款余额', 'YUAN')],
      content: { mainField: 'deposit', subFields: [] }, format: { displayUnit: 'TEN_THOUSAND', decimals: 2 }
    });
    const presentation = { template: 'branch-overview-v1', displaySchemaVersion: 1,
      display: { components: [card], comparisons: { 'business-corp-deposit-balance': { enabled: true, historyBlockId: 57, valueFields: ['deposit'], dateField: 'data_date', sourceUnit: 'YUAN' } } } };
    const model = { dataDate: '2028-03-02', blockResults: { 31: { deposit: 1200000000, unitByField: { deposit: 'YUAN' } } },
      comparisonResults: { 57: { rows: [
        { data_date: '2028-03-02', deposit: 1200000000 }, { data_date: '2028-02-29', deposit: 1000000000 }, { data_date: '2027-12-31', deposit: 900000000 }, { data_date: '2028-03-01', deposit: 1100000000 }
      ], unitByField: { deposit: 'YUAN' } } } };
    const result = buildDisplayMetricsModel(presentation, model, { amountUnit: 'TEN_THOUSAND' }).components[0];
    expect(result.comparisonConfigured).toBe(true);
    expect(result.comparisons.month).toMatchObject({ state: 'READY', value: 20000 });
    const disabled = buildDisplayMetricsModel({ ...presentation, display: { ...presentation.display, comparisons: { 'business-corp-deposit-balance': { enabled: false } } } }, model, { amountUnit: 'TEN_THOUSAND' }).components[0];
    expect(disabled.comparisonConfigured).toBe(true);
    expect(disabled.comparisons).toBeNull();
    expect(disabled.monthDelta).toBeNull();
  });
});
