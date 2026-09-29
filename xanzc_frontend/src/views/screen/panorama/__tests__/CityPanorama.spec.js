// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: ['pointLabelLayout'],
    template: '<div class="city-map-stub" :data-point-label-layout="pointLabelLayout"><button type="button" class="map-branch-1" @click="$emit(\'branch-select\', \'ORG-1\')">地图支行1</button><button type="button" class="map-branch-2" @click="$emit(\'branch-select\', \'ORG-7\')">地图支行7</button></div>',
    emits: ['branch-select']
  }
}));
vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)" />' }
}));

const cityOperatingHeaderStub = {
  name: 'CityOperatingHeader',
  props: ['citySummary', 'amountUnit'],
  emits: ['business-line-select'],
  template: `
    <section data-testid="city-operating-header">
      <span data-testid="city-header-deposit">{{ citySummary?.kpis?.find(item => item.key === 'deposit')?.value ?? '—' }}</span>
      <span data-testid="city-header-date">{{ citySummary?.dataDate || '' }}</span>
      <span data-testid="city-header-amount-unit">{{ amountUnit }}</span>
      <button type="button" data-testid="city-header-business-line" @click="$emit('business-line-select', { businessLine: 'CORP', tabKey: 'deposit' })">业务线</button>
    </section>
  `
};

import CityPanorama from '../CityPanorama.vue';

const institutions = Array.from({ length: 7 }, (_, index) => ({
  orgCode: `ORG-${index + 1}`,
  orgName: index === 0 ? '高新科技路支行' : `测试支行${index + 1}`,
  cityCode: '610100',
  located: index !== 5,
  lng: index === 5 ? null : 108.8 + index / 10,
  lat: index === 5 ? null : 34.1 + index / 10,
  metrics: {
    deposit: index === 2 ? null : 38.62 - index,
    loan: 26.48 - index,
    customers: 4.26,
    target: index === 4 ? null : 92.4 - index,
    rate: index === 4 ? null : 92.4 - index
  },
  trend: [{ date: '2026-09', deposit: index === 2 ? null : 38.62 - index, loan: 26.48 - index }],
  attention: index === 0 ? [{ label: '审批超时', count: 2 }] : []
}));

const model = {
  title: '西安市·支行经营全景',
  dataDate: '2026-09-06',
  kpis: [
    { key: 'deposit', label: '存款余额', value: null, unit: '亿元', change: 6.2 },
    { key: 'loan', label: '贷款余额', value: 392.18, unit: '亿元', change: null },
    { key: 'customers', label: '营销有效归属客户数', value: 73.28, unit: '万户', change: 3.8 },
    { key: 'target', label: '目标完成率', value: 88.6, unit: '%', change: null }
  ],
  trend: [], composition: [], rankings: [], attention: [], issues: [], citySummaries: {}, institutions
};

const mounted = [];

function mountCity(overrides = {}) {
  const wrapper = mount(CityPanorama, {
    props: { model, cityCode: '610100', ...overrides },
    global: { stubs: { CityOperatingHeader: cityOperatingHeaderStub } }
  });
  mounted.push(wrapper);
  return wrapper;
}

describe('CityPanorama 市级支行全景', () => {
  afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

  it('只按显式 cityCode 筛选机构，缺失值显示 — 且不出现 NaN', () => {
    const wrapper = mountCity();
    expect(wrapper.find('[data-testid="city-kpi-deposit"]').text()).toContain('暂无有效数据');
    expect(wrapper.text()).not.toContain('NaN');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(5);
    expect(wrapper.find('[data-testid="branch-missing-coordinates"]').text()).toContain('无坐标');
    expect(wrapper.get('[data-testid="city-branch-ranking-missing"]').text()).toContain('暂无该指标有效数据');
  });

  it('下钻标题只展示业务标题，剥离末尾测试修饰且不出现领导视图文案', () => {
    const wrapper = mountCity({ model: { ...model, title: '西安市支行经营全景（测试）' } });
    expect(wrapper.get('.city-title-block h1').text()).toBe('西安市支行经营全景');
    expect(wrapper.text()).not.toContain('领导视图');
  });

  it('支持搜索、经营关注筛选、六指标切换和五条分页', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-testid="branch-search"]').setValue('高新');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="branch-row"]').text()).toContain('高新科技路支行');

    await wrapper.get('[data-testid="branch-search"]').setValue('');
    await wrapper.get('[data-testid="attention-filter"]').trigger('click');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="branch-row"]').text()).toContain('高新科技路支行');

    await wrapper.get('[data-testid="attention-filter"]').trigger('click');
    await wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="corpLoanRate"]').trigger('click');
    expect(wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="corpLoanRate"]').attributes('aria-selected')).toBe('true');
    expect(wrapper.find('[data-testid="branch-page-next"]').exists()).toBe(true);
  });

  it('排名轮播由父层受控：当前 tab 先完成分页，5000ms 后才切换下一个 tab', async () => {
    vi.useFakeTimers();
    const wrapper = mountCity();
    const firstTab = '[data-testid="city-branch-ranking-tab"][data-tab-key="retailDepositRate"]';
    const nextTab = '[data-testid="city-branch-ranking-tab"][data-tab-key="retailLoanRate"]';
    expect(wrapper.get(firstTab).attributes('aria-selected')).toBe('true');
    expect(wrapper.get('.city-branch-ranking__pagination span').text()).toBe('1 / 2');

    vi.advanceTimersByTime(4999);
    await wrapper.vm.$nextTick();
    expect(wrapper.get(firstTab).attributes('aria-selected')).toBe('true');
    expect(wrapper.get('.city-branch-ranking__pagination span').text()).toBe('1 / 2');

    vi.advanceTimersByTime(1);
    await wrapper.vm.$nextTick();
    expect(wrapper.get(firstTab).attributes('aria-selected')).toBe('true');
    expect(wrapper.get('.city-branch-ranking__pagination span').text()).toBe('2 / 2');

    vi.advanceTimersByTime(4999);
    await wrapper.vm.$nextTick();
    expect(wrapper.get(firstTab).attributes('aria-selected')).toBe('true');
    vi.advanceTimersByTime(1);
    await wrapper.vm.$nextTick();
    expect(wrapper.get(nextTab).attributes('aria-selected')).toBe('true');
    expect(wrapper.get('.city-branch-ranking__pagination span').text()).toBe('1 / 2');
  });

  it('地图和列表互选，选中机构保持同步且不再渲染详情卡', async () => {
    const wrapper = mountCity({ sourcePresentation: { screenCode: 'SCR_PROVINCE_MAP_V2' } });
    expect(wrapper.get('.city-map-stub').attributes('data-point-label-layout')).toBe('callout');
    await wrapper.get('[data-testid="branch-row"]').trigger('click');
    expect(wrapper.find('[data-testid="branch-detail"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="selected-org-code"]').text()).toContain('ORG-1');
    await wrapper.get('.map-branch-1').trigger('click');
    expect(wrapper.find('[data-testid="selected-org-code"]').text()).toContain('ORG-1');
  });

  it('旧屏的市级地图同样使用网点连线标注', () => {
    const wrapper = mountCity({ sourcePresentation: { screenCode: 'SCR_PROVINCE' } });
    expect(wrapper.get('.city-map-stub').attributes('data-point-label-layout')).toBe('callout');
  });

  it('移除支行详情卡，返回省级、刷新、全屏都由事件或能力交给容器', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-testid="branch-row"]').trigger('click');
    expect(wrapper.find('[data-testid="branch-detail"]').exists()).toBe(false);
    await wrapper.get('[data-action="city-back"]').trigger('click');
    await wrapper.get('[data-action="city-refresh"]').trigger('click');
    await wrapper.get('[data-action="city-fullscreen"]').trigger('click');
    expect(wrapper.emitted('back')).toHaveLength(1);
    expect(wrapper.emitted('refresh')).toHaveLength(1);
    expect(wrapper.emitted('fullscreen')).toHaveLength(1);
  });

  it('无 citySummaries 时市级 KPI 显示未绑定，禁止把下级存款加总成市级值', () => {
    const wrapper = mountCity({ model: { ...model, citySummaries: {} } });
    expect(wrapper.find('[data-testid="city-summary-unbound"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="city-kpi-deposit"]').text()).toContain('暂无有效数据');
    expect(wrapper.find('[data-testid="city-kpi-deposit"]').text()).not.toContain('200');
  });

  it('市级固定保留存款、贷款、营销客户、手工测试收入四张卡，并就近显示字段状态', () => {
    const wrapper = mountCity({
      model: { ...model, citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 12, unit: '亿元' }, { key: 'loan', value: 8, unit: '亿元' }] } } },
      sourcePresentation: {
        sourceAvailability: { citySummary: { fields: { customers: { status: 'NO_SOURCE', message: '客户主数据缺少机构归属' }, revenue: { status: 'NO_SOURCE', message: '现有收入来源关键金额字段为空' } } } }
      }
    });
    expect(wrapper.findAll('[data-testid^="city-kpi-"]').filter(item => item.attributes('data-testid').match(/^city-kpi-(deposit|loan|customers|revenue)$/))).toHaveLength(4);
    expect(wrapper.get('[data-testid="city-kpi-status-customers"]').text()).toContain('客户主数据缺少机构归属');
    expect(wrapper.get('[data-testid="city-kpi-status-revenue"]').text()).toContain('现有收入来源');
  });

  it('市级摘要只呈现全市目标状态和目标距离，选中支行不改变摘要且不渲染详情卡', async () => {
    const cityModel = {
      ...model,
      citySummaries: {
        '610100': {
          kpis: [{ key: 'rate', value: 91, unit: '%' }],
          dataDate: '2026-09-06'
        }
      }
    };
    const wrapper = mountCity({ model: cityModel });
    expect(wrapper.find('[data-testid="city-leadership-diagnostics"]').exists()).toBe(false);
    await wrapper.get('[data-testid="branch-row"]').trigger('click');
    expect(wrapper.find('[data-testid="branch-detail"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="selected-org-code"]').text()).toContain('ORG-1');
  });

  it('接收父层快照后恢复搜索、关注、排序、页码、选中机构和详情展开状态', () => {
    const wrapper = mountCity({
      initialState: {
        search: '测试支行',
        attentionOnly: false,
        sortDescending: false,
        page: 2,
        selectedOrgCode: 'ORG-6'
      }
    });
    expect(wrapper.get('[data-testid="branch-search"]').element.value).toBe('测试支行');
    expect(wrapper.findAll('[data-testid="city-branch-ranking-tab"]')).toHaveLength(6);
    expect(wrapper.get('[data-testid="branch-page-prev"]').element.disabled).toBe(false);
    expect(wrapper.get('[data-testid="selected-org-code"]').text()).toContain('ORG-6');
    expect(wrapper.find('[data-testid="branch-detail"]').exists()).toBe(false);
  });

  it('每次可见筛选状态变化都向父层发出完整快照', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-testid="branch-search"]').setValue('高新');
    await wrapper.get('[data-testid="attention-filter"]').trigger('click');
    const snapshots = wrapper.emitted('state-change') || [];
    expect(snapshots.at(-1)?.[0]).toMatchObject({ search: '高新', attentionOnly: true, sortDescending: true, page: 1 });
  });

  it('配置页使用当前 citySummary 顶部数据，切换城市即时更新且不泄漏父层 KPI', async () => {
    const wrapper = mountCity({
      sourcePresentation: { displayPresentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: [] } } },
      model: {
        ...model,
        kpis: [{ key: 'deposit', value: 999, unit: '亿元' }],
        citySummaries: {
          '610100': { dataDate: '2026-09-20', kpis: [{ key: 'deposit', value: 12, unit: '亿元' }] },
          '610200': { dataDate: '', kpis: [{ key: 'deposit', value: 34, unit: '亿元' }] }
        }
      }
    });

    expect(wrapper.find('.city-kpi-grid').exists()).toBe(false);
    expect(wrapper.get('[data-testid="city-header-amount-unit"]').element.value).toBe('TEN_THOUSAND');
    expect(wrapper.get('[data-testid="city-operating-header"]').get('[data-testid="city-header-deposit"]').text()).toBe('12');
    expect(wrapper.get('[data-testid="city-operating-header"]').text()).not.toContain('999');
    await wrapper.setProps({ cityCode: '610200' });
    expect(wrapper.get('[data-testid="city-operating-header"]').get('[data-testid="city-header-deposit"]').text()).toBe('34');
    expect(wrapper.get('[data-testid="city-header-date"]').text()).toBe('');
    await wrapper.setProps({ cityCode: '610300' });
    expect(wrapper.get('[data-testid="city-operating-header"]').get('[data-testid="city-header-deposit"]').text()).toBe('—');
  });

  it('城市顶部业务线事件附带当前城市上下文且不夹带选中支行', async () => {
    const wrapper = mountCity({
      cityName: '西安市',
      sourcePresentation: { displayPresentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: [] } } },
      initialOrgCode: 'ORG-1',
      model: { ...model, citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 12, unit: '亿元' }] } } }
    });

    await wrapper.get('[data-testid="city-header-business-line"]').trigger('click');
    expect(wrapper.emitted('business-line-select')).toEqual([[{
      businessLine: 'CORP', tabKey: 'deposit', context: { cityCode: '610100', cityName: '西安市' }
    }]]);
    expect(wrapper.emitted('business-line-select')[0][0]).not.toHaveProperty('context.selectedOrgCode');
  });
});
