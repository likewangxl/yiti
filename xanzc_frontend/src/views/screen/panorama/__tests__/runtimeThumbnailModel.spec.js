import { describe, expect, it } from 'vitest';
import { buildRuntimeThumbnailModel, runtimeThumbnailTargets } from '../runtimeThumbnailModel';

const entries = [
  { key: 'deposit', label: '存款余额' },
  { key: 'composition', label: '业务构成与占比' },
  { key: 'branches', label: '辖区机构分布' },
  { key: 'trend', label: '主要指标趋势' },
  { key: 'ranking', label: '全辖机构对比' }
];

describe('runtimeThumbnailModel', () => {
  it('为旧版运行大屏提供与运行页一致的空数据模型', () => {
    const model = buildRuntimeThumbnailModel({ entries, template: 'branch-overview-v1', screenTitle: '支行经营总览' });
    expect(model.title).toBe('支行经营总览');
    expect(model.kpis.map(item => item.key)).toEqual(['deposit', 'loan', 'customers', 'revenue']);
    expect(model.kpis.every(item => item.value === null && item.change === null)).toBe(true);
    expect(model.composition).toEqual([]);
    expect(model.institutions).toEqual([]);
    expect(model.trend).toEqual([]);
  });

  it('只为真实运行 DOM 区域生成选择目标，无法定位项保留 null', () => {
    const targets = runtimeThumbnailTargets({ entries, template: 'branch-overview-v1' });
    expect(targets.find(item => item.entryKey === 'deposit').selector).toBe('.panorama-kpi:nth-of-type(1)');
    expect(targets.find(item => item.entryKey === 'composition').selector).toBe('.panorama-composition-panel');
    expect(targets.find(item => item.entryKey === 'branches').selector).toBe('.panorama-map-panel');
    expect(targets.find(item => item.entryKey === 'trend').selector).toBe('.panorama-trend-panel');
    expect(targets.find(item => item.entryKey === 'ranking').selector).toBe('.panorama-ranking-panel');
  });

  it('schema1 使用 presentation 组件 key，而不是猜测布局', () => {
    const presentation = { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: [
      { id: 'metric-1', componentType: 'METRIC_CARD', configKey: 'overview-deposit', layoutRegion: 'HEADER', order: 1 },
      { id: 'map-1', componentType: 'MAP', configKey: 'institution-map', layoutRegion: 'CENTER', order: 2 }
    ] } };
    const model = buildRuntimeThumbnailModel({ entries, template: 'branch-overview-v1', presentation });
    expect(model.kpis).toEqual(expect.arrayContaining([{ key: 'overview-deposit', label: '存款余额', value: null, change: null }]));
    const targets = runtimeThumbnailTargets({ entries: [{ key: 'overview-deposit', label: '存款余额' }, { key: 'institution-map', label: '机构地图' }], template: 'branch-overview-v1', presentation });
    expect(targets).toEqual(expect.arrayContaining([
      expect.objectContaining({ entryKey: 'overview-deposit', selector: '[data-config-key="overview-deposit"]' }),
      expect.objectContaining({ entryKey: 'institution-map', selector: '[data-config-key="institution-map"]' })
    ]));
  });

  it('标题优先使用可编辑展示标题，并对未知 entry 返回不可定位目标', () => {
    const model = buildRuntimeThumbnailModel({ entries: [{ key: 'unknown', label: '默认', displayLabel: '已编辑标题' }], template: 'retail-overview-v1' });
    expect(model.title).toBe('经营大屏');
    expect(model.kpis).toEqual([]);
    expect(runtimeThumbnailTargets({ entries: [{ key: 'unknown', label: '默认' }], template: 'retail-overview-v1' })).toEqual([
      { entryKey: 'unknown', selector: null, occurrence: 0 }
    ]);
  });

  it('目标选择器必须对应各运行模板真实 DOM，并保留共享组件的多个位置', () => {
    const corp = runtimeThumbnailTargets({ entries: [
      { key: 'corpDeposit', label: '存款' }, { key: 'corpSegments', label: '客群' }, { key: 'corpAttention', label: '关注' }, { key: 'corpRanking', label: '排名' }, { key: 'corpTargets', label: '目标' }
    ], template: 'corporate-overview-v1' });
    expect(corp.filter(item => item.entryKey === 'corpDeposit').map(item => item.selector)).toEqual(['.corporate-kpi:nth-of-type(1)', '[data-testid="corporate-deposit-balance"]']);
    expect(corp.find(item => item.entryKey === 'corpSegments').selector).toBe('[data-testid="corporate-segments"]');
    expect(corp.find(item => item.entryKey === 'corpAttention').selector).toBe('[data-testid="corporate-attention"]');
    expect(corp.find(item => item.entryKey === 'corpRanking').selector).toBe('.corporate-ranking-panel');
    expect(corp.find(item => item.entryKey === 'corpTargets').selector).toBe('.corporate-target-panel');
    const retail = runtimeThumbnailTargets({ entries: [{ key: 'retailDeposit' }, { key: 'retailSegments' }, { key: 'retailRanking' }], template: 'retail-overview-v1' });
    expect(retail.filter(item => item.entryKey === 'retailDeposit').map(item => item.selector)).toEqual(['.retail-kpi:nth-of-type(2)', '[data-testid="retail-deposit-balance"]']);
    expect(retail.find(item => item.entryKey === 'retailSegments').selector).toBe('[data-testid="retail-segments"]');
    expect(retail.find(item => item.entryKey === 'retailRanking').selector).toBe('.retail-ranking-panel');
  });

  it('分行完成率与上下钻页面选择器对应真实区域', () => {
    const branch = runtimeThumbnailTargets({ entries: [{ key: 'leadership-corporateDeposit' }, { key: 'rate' }, { key: 'loanRate' }], template: 'branch-overview-v1' });
    expect(branch.map(item => item.selector)).toEqual([
      '[data-diagnostic="corporateDepositCompletion"]',
      '[data-target-key="deposit"]',
      '[data-target-key="loan"]'
    ]);
    const city = runtimeThumbnailTargets({ entries: [{ key: 'city-deposit-total' }, { key: 'city-branch-ranking' }], template: 'branch-overview-v1', context: 'city', presentation: { displaySchemaVersion:1 } });
    expect(city.map(item => item.selector)).toEqual(['[data-config-key="overview-deposit"]', '.city-list-panel']);
    const branchPage = runtimeThumbnailTargets({ entries: [{ key: 'branch-overview-deposit', parentKey: 'overview-deposit' }, { key: 'branch-employee-ranking' }], template: 'branch-overview-v1', context: 'branch', presentation:{displaySchemaVersion:1} });
    expect(branchPage.map(item => item.selector)).toEqual(['[data-config-key="overview-deposit"]', '[data-config-key="institution-ranking"]']);
  });
});
