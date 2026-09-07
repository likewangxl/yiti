// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    template: '<div class="panorama-map-stub"><button type="button" class="stub-select-region" @click="$emit(\'region-select\', { code: \'610100\', name: \'西安市\' })">选择西安</button><button type="button" class="stub-select-branch" @click="$emit(\'branch-select\', \'ORG-1\')">选择支行</button></div>',
    emits: ['region-select', 'branch-select']
  }
}));
vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)" />' }
}));

import PanoramaDashboard from '../PanoramaDashboard.vue';

const model = {
  title: '分行经营总览',
  dataDate: '2026-09-06',
  kpis: [
    { key: 'deposit', label: '存款余额', value: 1286.42, unit: '亿元', change: 6.8 },
    { key: 'loan', label: '贷款余额', value: null, unit: '亿元', change: null },
    { key: 'customers', label: '客户总量', value: 0, unit: '万户', change: 0 },
    { key: 'revenue', label: '营收', value: 32.68, unit: '亿元', change: 8.1 }
  ],
  trend: [
    { date: '2026-08', deposit: 1253, loan: 948 },
    { date: '2026-09', deposit: 1286, loan: 968 }
  ],
  composition: [
    { name: '对公业务', value: 568.48, unit: '亿元' },
    { name: '零售业务', value: 398.87, unit: '亿元' }
  ],
  rankings: [
    { orgCode: 'ORG-1', name: '西安市分行', deposit: 512.63, change: 6.2 },
    { orgCode: 'ORG-2', name: '榆林市分行', deposit: 188.74, change: 7.4 }
  ],
  attention: [{ label: '目标进度偏慢机构', count: 3 }],
  institutions: [
    {
      orgCode: 'ORG-1', orgName: '西安市分行', cityCode: '610100', lng: 108.94, lat: 34.34,
      located: true, metrics: { deposit: 512.63, loan: 392.18, customers: 73.28, target: 88.6, rate: 88.6 },
      trend: [{ date: '2026-09', deposit: 512.63, loan: 392.18 }], attention: []
    },
    {
      orgCode: 'ORG-2', orgName: '榆林市分行', cityCode: '610800', lng: null, lat: null,
      located: false, metrics: { deposit: null, loan: 0, customers: null, target: null, rate: null },
      trend: [], attention: []
    }
  ],
  issues: [],
  citySummaries: {}
};

const mounted = [];

function mountDashboard(overrides = {}, options = {}) {
  const wrapper = mount(PanoramaDashboard, {
    props: { model, loading: false, error: '', demo: false, ...overrides },
    global: { stubs: options.stubs || {} },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

describe('PanoramaDashboard 省级经营大屏', () => {
  beforeEach(() => {
    document.body.style.overflow = '';
  });

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount());
    document.body.style.overflow = '';
  });

  it('先展示四个 KPI 的绑定值，null 为空态且 0 不被当成缺失', () => {
    const wrapper = mountDashboard();
    const cards = wrapper.findAll('[data-testid="panorama-kpi"]');
    expect(cards).toHaveLength(4);
    expect(cards[0].text()).toContain('1,286.42');
    expect(cards[1].text()).toContain('—');
    expect(cards[1].text()).not.toContain('NaN');
    expect(cards[2].text()).toContain('0');
    expect(wrapper.find('[data-testid="panorama-demo-badge"]').exists()).toBe(false);
  });

  it('演示模式明确标识，真实错误和加载态有可访问反馈', () => {
    const wrapper = mountDashboard({ demo: true, loading: true, error: '取数失败' });
    expect(wrapper.find('[data-testid="panorama-demo-badge"]').text()).toContain('演示数据');
    expect(wrapper.find('[role="status"]').text()).toContain('加载中');
    expect(wrapper.find('[role="alert"]').text()).toContain('取数失败');
  });

  it('刷新、返回、配置入口通过事件交给容器', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="refresh"]').trigger('click');
    await wrapper.get('[data-action="back"]').trigger('click');
    await wrapper.get('[data-action="configure"]').trigger('click');
    expect(wrapper.emitted('refresh')).toHaveLength(1);
    expect(wrapper.emitted('back')).toHaveLength(1);
    expect(wrapper.emitted('configure')).toHaveLength(1);
  });

  it('省级地图选择行政区后打开市级 modal，市汇总不存在时显示未绑定而不累加下级机构', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="city-panorama-modal"]').text()).toContain('未绑定');
    expect(wrapper.find('[data-testid="city-kpi-deposit"]').text()).not.toContain('512.63');
    expect(document.body.style.overflow).toBe('hidden');
  });

  it('市级 modal 支持 close、Escape 和 backdrop，并把焦点恢复到打开前控件', async () => {
    const wrapper = mountDashboard();
    const regionButton = wrapper.get('.stub-select-region').element;
    regionButton.focus();
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    const modal = wrapper.get('[data-testid="city-panorama-modal"]');
    await modal.get('[data-action="city-close"]').trigger('click');
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
    expect(document.body.style.overflow).toBe('');
    expect(document.activeElement).toBe(regionButton);

    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="city-panorama-modal"]').trigger('keydown.esc');
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);

    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="city-panorama-modal"]').trigger('click');
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
  });

  it('分页控件变为 disabled 使焦点落到 body 后，Escape 仍关闭城市', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await nextTick();
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
    expect(document.body.style.overflow).toBe('');
  });

  it('地图选中机构会显示机构详情，不从名称猜 cityCode', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('.stub-select-branch').trigger('click');
    expect(wrapper.find('[data-testid="selected-institution"]').text()).toContain('西安市分行');
  });
});
