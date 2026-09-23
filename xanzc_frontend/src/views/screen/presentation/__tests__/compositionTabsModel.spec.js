import { describe, expect, it } from 'vitest';
import { buildCompositionTabsModel } from '../model/compositionTabsModel';

const tab = (tabKey, overrides = {}) => ({
  tabKey,
  label: tabKey === 'deposit' ? '存款' : '贷款',
  corporateField: 'corp',
  retailField: 'retail',
  totalField: 'total',
  unit: 'HUNDRED_MILLION',
  ...overrides
});

const config = (tabs, overrides = {}) => ({
  displaySchemaVersion: 1,
  display: { components: [{
    componentId: 'composition-main',
    componentType: 'COMPOSITION_TABS',
    layoutRegion: 'LEFT',
    order: 0,
    visible: true,
    content: { tabs },
    dataRefs: [{ blockId: 11, metricCode: 'COMPOSITION', metricName: '业务构成', unit: 'HUNDRED_MILLION', dimension: 'ORG' }],
    ...overrides
  }] }
});

describe('compositionTabsModel', () => {
  it('旧 presentation 不启用配置化结构页签', () => {
    expect(buildCompositionTabsModel({ type: 'CODE', template: 'branch-overview-v1' }, { composition: [] }))
      .toMatchObject({ enabled: false, components: [], tabs: [] });
  });

  it('每个 tab 独立读取 corporateField/retailField/totalField，可信 total 才计算占比并显示 other', () => {
    const result = buildCompositionTabsModel(config([
      tab('deposit'),
      tab('loan', { label: '贷款', corporateField: 'corpLoan', retailField: 'retailLoan', totalField: 'loanTotal' })
    ]), {
      blockResults: { 11: {
        corp: 714.26, retail: 572.16, total: 1400,
        corpLoan: 80, retailLoan: 20, loanTotal: 100,
        units: { corp: 'HUNDRED_MILLION', retail: 'HUNDRED_MILLION', total: 'HUNDRED_MILLION', corpLoan: 'HUNDRED_MILLION', retailLoan: 'HUNDRED_MILLION', loanTotal: 'HUNDRED_MILLION' }
      } }
    });
    expect(result.tabs).toHaveLength(2);
    expect(result.tabs[0]).toMatchObject({ tabKey: 'deposit', state: 'READY', total: { value: 1400 }, other: { value: 113.58 } });
    expect(result.tabs[0].corporate.share).toBeCloseTo(51.0186, 3);
    expect(result.tabs[0].retail.share).toBeCloseTo(40.8686, 3);
    expect(result.tabs[1]).toMatchObject({ tabKey: 'loan', state: 'READY' });
    expect(result.tabs[1].corporate.share).toBe(80);
    expect(result.tabs[1].retail.share).toBe(20);
    expect(result.components[0].sections.map(item => item.sectionKey)).toEqual(['corporate', 'retail', 'income']);
    expect(result.components[0].sections[0].items.map(item => item.tabKey)).toEqual(['deposit', 'loan']);
    expect(result.sections.map(item => item.sectionKey)).toEqual(['corporate', 'retail', 'income']);
    expect(result.sections[0].items[0]).toMatchObject({
      total: { value: 1400 }, other: { value: 113.58 }, otherShareText: '8.11285714%'
    });
  });

  it('total 缺失、缺一方、零分母和负值不计算100%，且返回明确状态', () => {
    const result = buildCompositionTabsModel(config([
      tab('no-total', { totalField: '' }),
      tab('missing-retail', { retailField: 'missingRetail' }),
      tab('zero', { totalField: 'zeroTotal' }),
      tab('negative', { totalField: 'negativeTotal' })
    ]), {
      blockResults: { 11: {
        corp: 40, retail: 60, total: 100, missingRetail: null, zeroTotal: 0, negativeTotal: -10,
        units: { corp: 'HUNDRED_MILLION', retail: 'HUNDRED_MILLION', missingRetail: 'HUNDRED_MILLION', zeroTotal: 'HUNDRED_MILLION', negativeTotal: 'HUNDRED_MILLION' }
      } }
    });
    expect(result.tabs.map(item => item.state)).toEqual(['NO_TOTAL', 'MISSING_SIDE', 'ZERO_DENOMINATOR', 'NEGATIVE_VALUE']);
    expect(result.tabs[1].corporate.share).toBeCloseTo(40);
    expect(result.tabs[1].retail.share).toBeNull();
    expect(result.tabs[0].corporate.share).toBeNull();
  });

  it('只在同类型金额单位间换算，异类型单位明确拒绝；不从未配置tab自动生成中收', () => {
    const result = buildCompositionTabsModel(config([
      tab('amount', { unit: 'HUNDRED_MILLION' }),
      tab('mixed', { corporateField: 'mixedCorp', retailField: 'mixedRetail', totalField: 'mixedTotal', unit: 'HUNDRED_MILLION' })
    ]), {
      blockResults: { 11: {
        corp: 71426, retail: 57216, total: 128642,
        mixedCorp: 60, mixedRetail: 40, mixedTotal: 100,
        units: { corp: 'TEN_THOUSAND', retail: 'TEN_THOUSAND', total: 'TEN_THOUSAND', mixedCorp: 'PERCENT', mixedRetail: 'HUNDRED_MILLION', mixedTotal: 'HUNDRED_MILLION' },
        revenue: 99
      } }
    });
    expect(result.tabs[0].corporate.value).toBeCloseTo(7.1426, 6);
    expect(result.tabs[0].retail.value).toBeCloseTo(5.7216, 6);
    expect(result.tabs[0].total.value).toBeCloseTo(12.8642, 6);
    expect(result.tabs[1]).toMatchObject({ state: 'UNIT_MISMATCH', corporate: { share: null }, retail: { share: 40 } });
    expect(result.tabs.some(item => item.tabKey === 'revenue')).toBe(false);
  });

  it('公司单位冲突时仍独立计算可比的零售占比', () => {
    const result = buildCompositionTabsModel(config([tab('deposit')]), {
      blockResults: { 11: {
        corp: 1, retail: 40, total: 100,
        units: { corp: 'PERCENT', retail: 'HUNDRED_MILLION', total: 'HUNDRED_MILLION' }
      } }
    });
    expect(result.tabs[0].state).toBe('UNIT_MISMATCH');
    expect(result.tabs[0].corporate.share).toBeNull();
    expect(result.tabs[0].retail.share).toBe(40);
  });

  it('模型只产出默认10秒或显式轮播间隔，不创建定时器', () => {
    const result = buildCompositionTabsModel(config([tab('deposit')]), { blockResults: { 11: {} } });
    const custom = buildCompositionTabsModel(config([tab('deposit')]), { blockResults: { 11: {} } }, { intervalMs: 15000 });
    expect(result).toMatchObject({ intervalMs: 10000, rotationEnabled: true, rotationState: 'READY' });
    expect(custom).toMatchObject({ intervalMs: 15000, rotationEnabled: true, rotationState: 'READY' });
  });

  it('按明确配置计算中间收入占营业收入比例，且不从公司/零售字段猜测', () => {
    const result = buildCompositionTabsModel(config([tab('deposit'), tab('loan', { label: '贷款' })], {
      content: {
        tabs: [tab('deposit'), tab('loan', { label: '贷款' })],
        incomeRatio: { numeratorField: 'intermediaryIncome', denominatorField: 'operatingIncome', unit: 'HUNDRED_MILLION' }
      }
    }), {
      blockResults: { 11: {
        corp: 40, retail: 60, total: 100,
        intermediaryIncome: 12, operatingIncome: 80,
        units: { corp: 'HUNDRED_MILLION', retail: 'HUNDRED_MILLION', total: 'HUNDRED_MILLION', intermediaryIncome: 'TEN_THOUSAND', operatingIncome: 'HUNDRED_MILLION' }
      } }
    });
    expect(result.components[0].intermediaryIncome).toMatchObject({ state: 'READY', ratio: 0.0015, ratioText: '0.0015%' });
    expect(result.components[0].intermediaryIncome.numerator.value).toBeCloseTo(0.0012, 8);
    expect(result.components[0].intermediaryIncome.denominator.value).toBe(80);
  });

  it('中间收入缺失、零分母、负值和单位类型错误均失败闭合，不造数', () => {
    const make = (fields, source) => buildCompositionTabsModel(config([tab('deposit')], {
      content: { tabs: [tab('deposit')], ...fields }
    }), { blockResults: { 11: source } }).components[0].intermediaryIncome;
    expect(make({ incomeRatio: { numeratorField: 'n', denominatorField: 'd', unit: 'YUAN' } }, {
      n: null, d: 10, units: { n: 'YUAN', d: 'YUAN' }
    })).toMatchObject({ state: 'MISSING' });
    expect(make({ incomeRatio: { numeratorField: 'n', denominatorField: 'd', unit: 'YUAN' } }, {
      n: 1, d: 0, units: { n: 'YUAN', d: 'YUAN' }
    })).toMatchObject({ state: 'ZERO_DENOMINATOR', ratio: null });
    expect(make({ incomeRatio: { numeratorField: 'n', denominatorField: 'd', unit: 'YUAN' } }, {
      n: -1, d: 10, units: { n: 'YUAN', d: 'YUAN' }
    })).toMatchObject({ state: 'NEGATIVE_VALUE', ratio: null });
    expect(make({ incomeRatio: { numeratorField: 'n', denominatorField: 'd', unit: 'YUAN' } }, {
      n: 1, d: 10, units: { n: 'PERCENT', d: 'YUAN' }
    })).toMatchObject({ state: 'UNIT_MISMATCH', ratio: null });
    expect(make({}, { corp: 5, retail: 5, total: 10 })).toMatchObject({ state: 'PENDING', ratio: null, ratioText: '待接入' });
  });
});
