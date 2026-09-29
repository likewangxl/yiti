import { describe, expect, it } from 'vitest';
import { buildDisplayMetricsModel } from '../../presentation/model/displayMetricsModel';
import { buildBranchOverviewPresentation } from '../branchOverviewPresentation.js';

describe('branchOverviewPresentation', () => {
  it('无配置时提供完整十项分组顶栏、存贷款构成和中心/右侧占位，并优先读取模型单位', () => {
    const presentation = buildBranchOverviewPresentation(null);
    const components = presentation.display.components;
    const header = components.filter(item => item.layoutRegion === 'HEADER');
    expect(header).toHaveLength(10);
    expect(header.map(item => item.componentId)).toEqual([
      'business-retail-deposit-balance', 'business-retail-deposit-rate',
      'business-retail-loan-balance', 'business-retail-loan-rate',
      'business-corp-deposit-balance', 'business-corp-deposit-rate',
      'business-corp-loan-balance', 'business-corp-loan-rate',
      'business-revenue-operating', 'business-revenue-fee'
    ]);
    expect(header.filter(item => item.componentId.endsWith('-rate')).every(item => item.componentType === 'COMPLETION')).toBe(true);
    const composition = components.find(item => item.componentType === 'COMPOSITION_TABS' && item.layoutRegion === 'LEFT');
    expect(composition).toMatchObject({ componentId: 'branch-composition' });
    expect(composition.content.tabs.map(item => item.tabKey)).toEqual(['deposit', 'loan']);
    expect(components.filter(item => item.componentType === 'DETAIL_TABLE')).toHaveLength(0);
    expect(components.find(item => item.componentType === 'MAP' && item.layoutRegion === 'CENTER')).toBeTruthy();
    expect(components.find(item => item.componentType === 'RANKING' && item.layoutRegion === 'RIGHT')).toBeTruthy();
    const metrics = buildDisplayMetricsModel(presentation, { kpis: [{ key: 'corpDeposit', value: 18000, unit: '万元' }] });
    expect(metrics.components.find(item => item.componentId === 'business-corp-deposit-balance')).toMatchObject({ text: '18,000.00', sourceUnit: '万元', state: 'READY' });
    expect(metrics.components.find(item => item.componentId === 'business-retail-deposit-balance')).toMatchObject({ text: '—', state: 'NO_SOURCE' });
  });

  it('已有支行 display 配置只保留来源组件并补齐被替换的两个位置', () => {
    const source = { displaySchemaVersion: 1, type: 'CODE', template: 'branch-overview-v1', display: { components: [
      { componentId: 'configured-mix', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', visible: true, dataRefs: [{ blockId: 64 }] },
      { componentId: 'configured-map', componentType: 'MAP', layoutRegion: 'CENTER', visible: true, dataRefs: [{ blockId: 65 }] }
    ] } };
    const presentation = buildBranchOverviewPresentation(source);
    expect(presentation.display.components).toEqual(expect.arrayContaining([
      expect.objectContaining({ componentId: 'configured-mix' }),
      expect.objectContaining({ componentId: 'configured-map', dataRefs: [{ blockId: 65 }] }),
      expect.objectContaining({ componentType: 'RANKING', layoutRegion: 'RIGHT' })
    ]));
  });
});
