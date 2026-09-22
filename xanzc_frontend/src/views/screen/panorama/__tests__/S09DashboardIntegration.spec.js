// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';

import PanoramaDashboard from '../PanoramaDashboard.vue';
import CorporateDashboard from '../CorporateDashboard.vue';
import RetailDashboard from '../RetailDashboard.vue';
import BranchOperatingDashboard from '../BranchOperatingDashboard.vue';

const compositionTabsStub = {
  name: 'CompositionTabsWidget',
  props: ['model'],
  emits: ['business-line-select'],
  template: `
    <section data-testid="composition-tabs-stub">
      <strong data-testid="composition-tabs-enabled">{{ model?.enabled }}</strong>
      <span data-testid="composition-tabs-title">{{ model?.components?.[0]?.title }}</span>
      <span data-testid="composition-tabs-count">{{ model?.tabs?.length }}</span>
      <button type="button" data-action="select-corp" @click="$emit('business-line-select', { businessLine: 'CORP', tabKey: model?.activeTabKey })">公司</button>
      <button type="button" data-action="select-retail" @click="$emit('business-line-select', { businessLine: 'RETAIL', tabKey: model?.activeTabKey })">零售</button>
    </section>
  `
};

const stubs = {
  CompositionTabsWidget: compositionTabsStub,
  CompositionBreakdown: { name: 'CompositionBreakdown', template: '<div data-testid="composition-breakdown"></div>' },
  MetricDisplayWidgets: true,
  SeriesTableWidgets: true,
  PanoramaMap: true,
  CityPanorama: true,
  PanoramaInstitutionDirectory: true,
  PanoramaTrend: true,
  CorporateTrend: true,
  RetailTrend: true,
  RetailAttentionDetails: true,
  CompletionWaterGauge: true,
  VChart: true
};

const sourcePresentation = {
  displaySchemaVersion: 1,
  display: {
    components: [{
      componentId: 'composition-main',
      componentType: 'COMPOSITION_TABS',
      layoutRegion: 'LEFT',
      order: 0,
      visible: true,
      dataRefs: [{ blockId: 'composition-block' }],
      text: { titleMode: 'CUSTOM', title: '三类业务结构' },
      content: {
        tabs: [
          { tabKey: 'deposit', label: '存款', corporateField: 'corpDeposit', retailField: 'retailDeposit', totalField: 'depositTotal', unit: 'YUAN' },
          { tabKey: 'loan', label: '贷款', corporateField: 'corpLoan', retailField: 'retailLoan', totalField: 'loanTotal', unit: 'YUAN' },
          { tabKey: 'revenue', label: '收入', corporateField: 'corpRevenue', retailField: 'retailRevenue', totalField: 'revenueTotal', unit: 'YUAN' }
        ]
      }
    }]
  }
};

const model = {
  title: '测试经营大屏',
  orgCode: 'ORG-42',
  orgName: '测试机构',
  cityCode: '610100',
  cityName: '西安市',
  scopeLabel: '授权机构',
  dataDate: '2026-09-22',
  kpis: [],
  trend: [],
  composition: [{ name: '旧结构', value: 12, unit: '亿元' }],
  rankings: [],
  institutions: [],
  attention: [],
  targets: [],
  segments: [],
  marketing: [],
  projects: [],
  teams: [],
  blockResults: {
    'composition-block': {
      corpDeposit: 40, retailDeposit: 60, depositTotal: 120,
      corpLoan: 30, retailLoan: 20, loanTotal: 60,
      corpRevenue: 12, retailRevenue: 8, revenueTotal: 25
    }
  }
};

const wrappers = [];

function mountDashboard(component, props = {}) {
  const runtimePresentation = component === BranchOperatingDashboard
    ? sourcePresentation
    : { displayPresentation: sourcePresentation };
  const wrapper = mount(component, {
    props: { model, sourcePresentation: runtimePresentation, loading: false, error: '', demo: true, ...props },
    global: { stubs },
    attachTo: document.body
  });
  wrappers.push(wrapper);
  return wrapper;
}

describe('S09 三类业务结构页签 Dashboard 集成', () => {
  afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()));

  it.each([
    ['综合', PanoramaDashboard],
    ['对公', CorporateDashboard],
    ['零售', RetailDashboard],
    ['支行', BranchOperatingDashboard]
  ])('%s Dashboard 在 COMPOSITION_TABS 下挂载配置模型并隐藏旧固定结构', (label, component) => {
    const wrapper = mountDashboard(component);
    const tabs = wrapper.get('[data-testid="composition-tabs-stub"]');

    expect(tabs.get('[data-testid="composition-tabs-enabled"]').text()).toBe('true');
    expect(tabs.get('[data-testid="composition-tabs-title"]').text()).toBe('三类业务结构');
    expect(tabs.get('[data-testid="composition-tabs-count"]').text()).toBe('3');
    expect(wrapper.find('[data-testid="composition-breakdown"]').exists()).toBe(false);
    expect(wrapper.text()).toContain(label === '对公' ? '对公经营' : label === '零售' ? '零售经营' : label === '支行' ? '支行经营' : '经营监测');
  });

  it('综合 Dashboard 的旧协议没有 COMPOSITION_TABS 时继续使用 CompositionBreakdown', () => {
    const wrapper = mountDashboard(PanoramaDashboard, { sourcePresentation: {} });

    expect(wrapper.find('[data-testid="composition-tabs-stub"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="composition-breakdown"]').exists()).toBe(true);
  });

  it.each([
    ['综合', PanoramaDashboard],
    ['对公', CorporateDashboard],
    ['零售', RetailDashboard],
    ['支行', BranchOperatingDashboard]
  ])('%s Dashboard 将结构页签动作补充当前机构上下文并向上冒泡', async (_label, component) => {
    const wrapper = mountDashboard(component);

    await wrapper.get('[data-action="select-corp"]').trigger('click');
    await wrapper.get('[data-action="select-retail"]').trigger('click');

    expect(wrapper.emitted('business-line-select')).toEqual([
      [{
        businessLine: 'CORP',
        tabKey: 'deposit',
        context: { orgCode: 'ORG-42', orgName: '测试机构', cityCode: '610100', cityName: '西安市' }
      }],
      [{
        businessLine: 'RETAIL',
        tabKey: 'deposit',
        context: { orgCode: 'ORG-42', orgName: '测试机构', cityCode: '610100', cityName: '西安市' }
      }]
    ]);
    expect(JSON.stringify(wrapper.emitted('business-line-select'))).not.toContain('http');
  });
});
