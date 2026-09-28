// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: ['geoJson', 'points', 'selectedRegionCode', 'mode', 'demo'],
    template: '<div class="panorama-map-stub" />'
  }
}));

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, default: () => ({}) } },
    template: '<div data-testid="retail-chart" />'
  }
}));

import RetailDashboard from '../RetailDashboard.vue';

const model = {
  title: '零售经营总览',
  dataDate: '2026-09-08',
  kpis: [],
  segments: [
    { name: '私行客户', customers: 2, aum: 8 },
    { name: '大众客户', customers: 8, aum: 2 },
    { name: '资料不完整', customers: null, aum: 10 }
  ],
  rankings: [
    { orgCode: 'A', name: '甲支行', aum: 10, increase: -2, rate: 101, nplRate: 1.2 },
    { orgCode: 'B', name: '乙支行', aum: 10, increase: null, rate: null, nplRate: null }
  ],
  targets: [
    { name: 'AUM', actual: 12, target: 10 },
    { name: '存款', actual: 8, target: 10 }
  ],
  institutions: [
    { orgCode: 'A', name: '甲支行', cityCode: '610100' },
    { orgCode: 'B', name: '乙支行', cityCode: '610100' }
  ],
  trend: [],
  attention: [],
  issues: []
};

const mounted = [];

function mountDashboard(overrides = {}) {
  const wrapper = mount(RetailDashboard, {
    props: { model, loading: false, error: '', demo: false, ...overrides },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount());
  document.body.style.overflow = '';
});

describe('RetailDashboard 零售密度表达', () => {
  it('展示同一有效分层样本内的客户/资产占比与户均AUM，并声明非全客群', () => {
    const wrapper = mountDashboard();
    const panel = wrapper.get('[data-testid="retail-segments"]');
    expect(panel.get('[data-testid="retail-segment-scope-note"]').text()).toContain('分层内占比 · 2组有效，非全客群');
    expect(panel.findAll('[data-testid="retail-segment-row"]')).toHaveLength(2);
    expect(panel.findAll('.retail-share')).toHaveLength(4);
    expect(panel.findAll('.retail-segment-row__average')[0].text()).toBe('4.00');
    expect(panel.text()).toContain('20.00%');
    expect(panel.text()).toContain('80.00%');
  });

  it('历史 AUM 数据形态也不再展示经营观察条', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      rankings: model.rankings.map(row => ({ ...row, increase: null }))
    } });
    expect(wrapper.find('[data-testid="retail-leadership-insights"]').exists()).toBe(false);
    expect(wrapper.find('.retail-main-grid').exists()).toBe(true);
  });

  it('同屏显示排名四项指标，并对同值机构保留并列排名', async () => {
    const wrapper = mountDashboard();
    expect(wrapper.get('[data-testid="retail-ranking-matrix-head"]').text()).toContain('AUM亿元');
    expect(wrapper.findAll('[data-testid="retail-ranking-matrix"]')).toHaveLength(2);
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')[0].find('.retail-ranking-row__number').text()).toBe('1');
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')[1].find('.retail-ranking-row__number').text()).toBe('1');
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')[0].text()).toContain('1.20');
    await wrapper.get('[data-testid="retail-ranking-row"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-directory-ranking-context"]').text()).toContain('排名1/2');
    expect(wrapper.get('[data-testid="retail-directory-ranking-context"]').text()).toContain('可比样本 2 家');
  });
});
