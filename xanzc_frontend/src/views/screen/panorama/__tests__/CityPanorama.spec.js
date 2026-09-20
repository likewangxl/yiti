// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    template: '<div class="city-map-stub"><button type="button" class="map-branch-1" @click="$emit(\'branch-select\', \'ORG-1\')">地图支行1</button><button type="button" class="map-branch-2" @click="$emit(\'branch-select\', \'ORG-7\')">地图支行7</button></div>',
    emits: ['branch-select']
  }
}));
vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)" />' }
}));

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
    global: { stubs: {} }
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
    expect(wrapper.findAll('[data-testid="branch-row"]').some(row => row.text().includes('—'))).toBe(true);
  });

  it('支持搜索、经营关注筛选、按存款排序和五条分页', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-testid="branch-search"]').setValue('高新');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="branch-row"]').text()).toContain('高新科技路支行');

    await wrapper.get('[data-testid="branch-search"]').setValue('');
    await wrapper.get('[data-testid="attention-filter"]').trigger('click');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="branch-row"]').text()).toContain('高新科技路支行');

    await wrapper.get('[data-testid="attention-filter"]').trigger('click');
    await wrapper.get('[data-testid="deposit-sort"]').trigger('click');
    const rows = wrapper.findAll('[data-testid="branch-row"]');
    expect(rows[0].text()).not.toContain('高新科技路支行');
    expect(wrapper.find('[data-testid="branch-page-next"]').exists()).toBe(true);
  });

  it('地图和列表互选，选中机构展示趋势与经营关注明细', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-testid="branch-row"]').trigger('click');
    expect(wrapper.find('[data-testid="branch-detail"]').text()).toContain('高新科技路支行');
    expect(wrapper.find('[data-testid="branch-detail-trend"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="branch-detail"]').text()).toContain('审批超时');
    await wrapper.get('.map-branch-1').trigger('click');
    expect(wrapper.find('[data-testid="selected-org-code"]').text()).toContain('ORG-1');
  });

  it('支行详情可展开和收起，返回省级、刷新、全屏都由事件或能力交给容器', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-testid="branch-row"]').trigger('click');
    const detail = wrapper.get('[data-testid="branch-detail"]');
    await detail.get('[data-action="toggle-detail"]').trigger('click');
    expect(detail.attributes('aria-expanded')).toBe('false');
    await detail.get('[data-action="toggle-detail"]').trigger('click');
    expect(detail.attributes('aria-expanded')).toBe('true');
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

  it('市级摘要只呈现全市目标状态和目标距离，选中支行不改变摘要，位次只在详情出现', async () => {
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
    const summary = wrapper.get('[data-testid="city-leadership-diagnostics"]');
    const before = summary.text();
    expect(before).toContain('已完成目标0家');
    expect(before).toContain('未完成目标6家');
    expect(before).toContain('未提供1家');
    expect(before).toContain('距目标还差9个百分点');
    expect(before).not.toContain('同城展示观察');
    await wrapper.get('[data-testid="branch-row"]').trigger('click');
    expect(summary.text()).toBe(before);
    expect(wrapper.get('[data-testid="branch-observation"]').text()).toContain('存款余额位次');
  });

  it('接收父层快照后恢复搜索、关注、排序、页码、选中机构和详情展开状态', () => {
    const wrapper = mountCity({
      initialState: {
        search: '测试支行',
        attentionOnly: false,
        sortDescending: false,
        page: 2,
        selectedOrgCode: 'ORG-6',
        detailExpanded: false
      }
    });
    expect(wrapper.get('[data-testid="branch-search"]').element.value).toBe('测试支行');
    expect(wrapper.get('[data-testid="deposit-sort"]').text()).toContain('↑');
    expect(wrapper.get('[data-testid="branch-page-prev"]').element.disabled).toBe(false);
    expect(wrapper.get('[data-testid="selected-org-code"]').text()).toContain('ORG-6');
    expect(wrapper.get('[data-testid="branch-detail"]').attributes('aria-expanded')).toBe('false');
  });

  it('每次可见筛选状态变化都向父层发出完整快照', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-testid="branch-search"]').setValue('高新');
    await wrapper.get('[data-testid="attention-filter"]').trigger('click');
    const snapshots = wrapper.emitted('state-change') || [];
    expect(snapshots.at(-1)?.[0]).toMatchObject({ search: '高新', attentionOnly: true, sortDescending: true, page: 1 });
  });
});
