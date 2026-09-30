import { describe, expect, it } from 'vitest';
import { buildRuntimeComponentCatalog } from '../runtimeComponentCatalog';

const presentation = {
  type: 'CODE',
  template: 'branch-overview-v1',
  displaySchemaVersion: 1,
  metricLabels: { deposit: '全行存款总额', loan: '全行贷款总额' },
  display: { components: [
    ...['business-retail-deposit-balance', 'business-retail-deposit-rate', 'business-retail-loan-balance', 'business-retail-loan-rate',
      'business-corp-deposit-balance', 'business-corp-deposit-rate', 'business-corp-loan-balance', 'business-corp-loan-rate',
      'business-revenue-operating', 'business-revenue-fee'].map((componentId, order) => ({
      componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order, visible: true,
      text: { titleMode: 'CUSTOM', title: componentId }, content: {}, dataRefs: [{ blockId: 31 }]
    })),
    { componentId: 'legacy-trend-57', componentType: 'TREND', layoutRegion: 'CENTER', visible: true,
      text: { titleMode: 'AUTO', title: '' }, content: { series: [] }, dataRefs: [{ blockId: 57, role: 'PRIMARY' }] },
    { componentId: 'legacy-ranking-58', componentType: 'RANKING', layoutRegion: 'RIGHT', visible: true,
      text: { titleMode: 'AUTO', title: '' }, content: { rankingMetrics: [] }, dataRefs: [{ blockId: 58 }] },
    { componentId: 'legacy-composition-64', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', visible: true,
      text: { titleMode: 'AUTO', title: '' }, content: { tabs: [{ tabKey: 'business-structure', label: '业务结构', corporateField: '测试_直营对公存款', retailField: '测试_直营零售存款', totalField: '' }] }, dataRefs: [{ blockId: 64 }] },
    { componentId: 'legacy-map-58', componentType: 'MAP', layoutRegion: 'CENTER', visible: true,
      text: { titleMode: 'AUTO', title: '' }, content: { mainField: 'value' }, dataRefs: [{ blockId: 58 }] }
  ] }
};

describe('runtimeComponentCatalog', () => {
  it('把分行全辖 schema1 组件映射为真实运行态的18个配置区域', () => {
    const catalog = buildRuntimeComponentCatalog(presentation);
    expect(catalog.mode).toBe('BRANCH_PROVINCE');
    expect(catalog.entries).toHaveLength(18);
    expect(catalog.entries.every(item => item.dataConfigKey === item.key)).toBe(true);
    expect(catalog.entries.map(item => item.key)).toEqual(expect.arrayContaining([
      'overview-deposit', 'overview-deposit-composition', 'overview-loan', 'overview-loan-composition', 'overview-settlementDeposit',
      'business-retail-deposit-balance', 'business-retail-deposit-rate', 'business-retail-loan-balance', 'business-retail-loan-rate',
      'business-corp-deposit-balance', 'business-corp-deposit-rate', 'business-corp-loan-balance', 'business-corp-loan-rate',
      'business-revenue-operating', 'business-revenue-fee', 'business-growth', 'institution-map', 'institution-ranking'
    ]));
    expect(catalog.entries.find(item => item.key === 'overview-deposit')).toMatchObject({
      kind: 'DERIVED_TOTAL', slot: 'deposit', label: '全行存款总额', editable: true
    });
    expect(catalog.entries.find(item => item.key === 'overview-settlementDeposit')).toMatchObject({
      kind: 'SYSTEM_METRIC', editable: false, readonlyReason: expect.stringContaining('独立')
    });
    expect(catalog.entries.find(item => item.key === 'overview-deposit-composition')).toMatchObject({
      kind: 'COMPOSITION_TAB', sourceComponentId: 'legacy-composition-64', tabKey: 'deposit'
    });
    expect(catalog.entries.find(item => item.key === 'institution-map')).toMatchObject({ label: '经营机构分布', sourceComponentId: 'legacy-map-58' });
  });

  it('非分行全辖模式保留真实 display components，不按屏名称推断派生区域', () => {
    const source = { ...presentation, template: 'retail-overview-v1' };
    const catalog = buildRuntimeComponentCatalog(source);
    expect(catalog.mode).toBe('DISPLAY_COMPONENTS');
    expect(catalog.entries.map(item => item.key)).toEqual(presentation.display.components.map(item => item.componentId));
  });

  it('把历史默认 KPI 标签视为自动标题，只有真正自定义标签覆盖总额标题', () => {
    const source = { ...presentation, metricLabels: { deposit: '存款余额', loan: '贷款总额（自定义）' } };
    const catalog = buildRuntimeComponentCatalog(source);
    expect(catalog.entries.find(item => item.key === 'overview-deposit').label).toBe('存款总额');
    expect(catalog.entries.find(item => item.key === 'overview-loan').label).toBe('贷款总额（自定义）');
  });

  it('没有可见左侧构成组件时不伪造两张业务分布目录项', () => {
    const source = {
      ...presentation,
      display: { components: presentation.display.components.filter(item => item.componentType !== 'COMPOSITION_TABS') }
    };
    const catalog = buildRuntimeComponentCatalog(source);
    expect(catalog.entries.some(item => item.key === 'overview-deposit-composition')).toBe(false);
    expect(catalog.entries.some(item => item.key === 'overview-loan-composition')).toBe(false);
  });
});
