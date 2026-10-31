import { describe, expect, it } from 'vitest';
import { buildOperatingThumbnailModel } from '../operatingThumbnailModel';
import { RETAIL_RUNTIME_KPI_ORDER } from '../operatingRuntimeLayout';

it('任意schema组件ID按真实组件类型选择图形，不能把趋势与表格画成数值卡', () => {
  const model=buildOperatingThumbnailModel({template:'retail-overview-v1',presentation:{displaySchemaVersion:1},entries:[
    {key:'custom-1',kind:'DISPLAY_COMPONENT',component:{componentType:'TREND',layoutRegion:'CENTER'}},
    {key:'custom-2',kind:'DISPLAY_COMPONENT',component:{componentType:'DETAIL_TABLE',layoutRegion:'RIGHT'}}
  ]});
  expect(model.entries.map(e=>e.visualKind)).toEqual(['BUSINESS_GROWTH','LIST']);
});

const corpKeys = ['corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate', 'corpTrend', 'corpSegments', 'corpRanking', 'corpAttention', 'corpTargets', 'branches'];
const retailKeys = ['retailAum', 'retailDeposit', 'retailDepositAverage', 'retailRevenue', 'retailValueCustomers', 'retailLoan', 'retailNplRate', 'retailTrend', 'retailSegments', 'retailRanking', 'retailAttention', 'retailTargets', 'branches'];
const entries = keys => keys.map(key => ({ key, slot: key, label: key, kind: 'LEGACY_COMPONENT' }));

describe('operatingThumbnailModel', () => {
  it('零售趋势与机构对比使用实际运行区域名称', () => {
    const model = buildOperatingThumbnailModel({ entries: entries(retailKeys), template: 'retail-overview-v1' });
    expect(model.entries.find(item => item.key === 'retailTrend').label).toBe('零售存款余额与月日均趋势');
    expect(model.entries.find(item => item.key === 'retailRanking').label).toBe('机构存款对比');
  });
  it('旧对公/零售目录全部可达，使用真实运行布局标题和视觉类型', () => {
    const corp = buildOperatingThumbnailModel({ entries: entries(corpKeys), template: 'corporate-overview-v1' });
    const retail = buildOperatingThumbnailModel({ entries: entries(retailKeys), template: 'retail-overview-v1' });
    expect(corp.entries.map(item => item.key)).toEqual(corpKeys);
    expect(retail.entries.map(item => item.key)).toEqual(retailKeys);
    expect(corp.entries.find(item => item.key === 'corpSegments').displayLabel).toBe('重点客群信贷');
    expect(corp.entries.find(item => item.key === 'corpTrend').visualKind).toBe('BUSINESS_GROWTH');
    expect(retail.entries.find(item => item.key === 'retailRanking').displayLabel).toBe('机构存款对比');
    expect(corp.groups.some(group => group.key === 'analysis')).toBe(true);
  });

  it('schema1 按 visible、layoutRegion、order 排序，不凭空生成输入外组件', () => {
    const input = [
      { key: 'late', label: '后', kind: 'DISPLAY_COMPONENT', component: { visible: true, layoutRegion: 'RIGHT', order: 9 } },
      { key: 'hidden', label: '隐藏', kind: 'DISPLAY_COMPONENT', component: { visible: false, layoutRegion: 'TOP', order: 0 } },
      { key: 'first', label: '先', kind: 'DISPLAY_COMPONENT', component: { visible: true, layoutRegion: 'TOP', order: 1 } },
      { key: 'second', label: '次', kind: 'DISPLAY_COMPONENT', component: { visible: true, layoutRegion: 'TOP', order: 2 } }
    ];
    const model = buildOperatingThumbnailModel({ entries: input, template: 'corporate-overview-v1', presentation: { displaySchemaVersion: 1 } });
    expect(model.entries.map(item => item.key)).toEqual(['first', 'second', 'late']);
    expect(model.entries.some(item => item.key === 'hidden')).toBe(false);
    expect(model.regions.map(region => region.key)).toEqual(expect.arrayContaining(['top', 'right']));
  });

  it('schema1 HEADER 归入 top，同一区域不同组件保持原 key，重复 source 才建立关联 key', () => {
    const input = [
      { key: 'a', label: 'A', kind: 'DISPLAY_COMPONENT', component: { visible: true, layoutRegion: 'HEADER', order: 1 } },
      { key: 'b', label: 'B', kind: 'DISPLAY_COMPONENT', component: { visible: true, layoutRegion: 'HEADER', order: 2 } },
      { key: 'map', label: '机构', kind: 'DISPLAY_COMPONENT', component: { visible: true, layoutRegion: 'CENTER', order: 1 }, visualKind: 'INSTITUTION_MAP' }
    ];
    const model = buildOperatingThumbnailModel({ entries: input, template: 'corporate-overview-v1', presentation: { displaySchemaVersion: 1 } });
    expect(model.regions.find(region => region.key === 'top').items.map(item => item.key)).toEqual(['a', 'b']);
    expect(model.regions.find(region => region.key === 'top').items.every(item => !item.key.includes('__'))).toBe(true);
    expect(model.regions.find(region => region.key === 'center').items[0].visualKind).toBe('INSTITUTION_MAP');
  });

  it('branch旧slot使用真实 slot 布局并保留静态标题', () => {
    const branchEntries = ['deposit', 'composition', 'loan', 'loanRate', 'branches', 'trend', 'ranking'].map(key => ({ key, slot: key, label: key, kind: 'LEGACY_COMPONENT' }));
    const model = buildOperatingThumbnailModel({ entries: branchEntries, template: 'branch-overview-v1', viewLevel: 'PROVINCE' });
    expect(model.regions.some(region => region.key === 'top' && region.items.length)).toBe(true);
    expect(model.entries.find(item => item.key === 'branches').visualKind).toBe('INSTITUTION_MAP');
    expect(model.entries.find(item => item.key === 'branches').displayLabel).toBe('辖区机构分布');
  });

  it('零售 KPI 缩略图顺序与运行页实际定义一致，月日均不提前', () => {
    const retail = buildOperatingThumbnailModel({ entries: entries(RETAIL_RUNTIME_KPI_ORDER), template: 'retail-overview-v1' });
    expect(retail.regions.find(region => region.key === 'top').items.map(item => item.key)).toEqual(RETAIL_RUNTIME_KPI_ORDER);
    expect(RETAIL_RUNTIME_KPI_ORDER).toEqual([
      'retailAum', 'retailDeposit', 'retailRevenue', 'retailValueCustomers',
      'retailLoan', 'retailNplRate', 'retailDepositAverage'
    ]);
  });

  it('旧分行右栏按运行页先显示完成率/贷款率，再显示机构对照', () => {
    const model = buildOperatingThumbnailModel({
      entries: ['ranking', 'rate', 'loanRate'].map(key => ({ key, slot: key, label: key, kind: 'LEGACY_COMPONENT' })),
      template: 'branch-overview-v1', viewLevel: 'PROVINCE'
    });
    expect(model.regions.find(region => region.key === 'right').items.map(item => item.key)).toEqual(['rate', 'loanRate', 'ranking']);
  });
});
