// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { provinceGeo } from '../geography';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: {
      geoJson: { type: Object, default: () => ({}) },
      points: { type: Array, default: () => [] },
      selectedRegionCode: { type: [String, Number], default: null },
      mode: { type: String, default: '' },
      demo: { type: Boolean, default: false },
      appearance: { type: String, default: '' },
      labelLayout: { type: String, default: '' },
      metricValues: { type: Object, default: () => ({}) },
      metricNumericValues: { type: Object, default: () => ({}) },
      metricColors: { type: Object, default: () => ({}) },
      regionStates: { type: Object, default: () => ({}) },
      colorByMetric: { type: Boolean, default: false }
    },
    emits: ['region-select', 'branch-select'],
    template: '<div data-testid="retail-map" :data-mode="mode" :data-appearance="appearance" :data-label-layout="labelLayout" :data-color-by-metric="String(colorByMetric)" :data-region-states="JSON.stringify(regionStates)" :data-metric-colors="JSON.stringify(metricColors)"></div>'
  }
}));

vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div />' }
}));

vi.mock('element-plus', () => ({
  ElDialog: {
    name: 'ElDialog',
    props: { modelValue: Boolean, title: String },
    template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>'
  }
}));

import RetailDashboard from '../RetailDashboard.vue';

const baseModel = {
  title: '零售经营总览',
  scopeLabel: '授权范围',
  kpis: [],
  trend: [],
  segments: [],
  rankings: [],
  attention: [],
  targets: [],
  issues: [],
  institutions: [
    { orgCode: 'ZERO', name: '零值机构', cityCode: '610100', cityName: '西安市' },
    { orgCode: 'EMPTY', name: '无指标机构', cityCode: '610300', cityName: '宝鸡市' }
  ]
};

const mounted = [];
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

describe('RetailDashboard legacy map institution state', () => {
  it('保留 callout 与 relief，并把机构占用状态传给 PanoramaMap，不由零值/缺指标变成无机构', () => {
    const wrapper = mount(RetailDashboard, {
      props: {
        model: {
          ...baseModel,
          rankings: [{ orgCode: 'ZERO', cityCode: '610100', deposit: 0, average: null }]
        },
        loading: false,
        error: '',
        demo: false
      },
      attachTo: document.body
    });
    mounted.push(wrapper);

    const map = wrapper.get('[data-testid="retail-map"]');
    expect(map.attributes('data-mode')).toBe('province');
    expect(map.attributes('data-appearance')).toBe('relief');
    expect(map.attributes('data-label-layout')).toBe('callout');
    expect(map.attributes('data-color-by-metric')).toBe('true');
    expect(JSON.parse(map.attributes('data-region-states'))).toMatchObject({
      '610100': 'HAS_INSTITUTION',
      '610300': 'HAS_INSTITUTION',
      '610200': 'NO_INSTITUTION'
    });
  });

  it('刷新机构目录后地图状态随新目录更新', async () => {
    const wrapper = mount(RetailDashboard, {
      props: { model: baseModel, loading: false, error: '', demo: false },
      attachTo: document.body
    });
    mounted.push(wrapper);

    await wrapper.setProps({ model: {
      ...baseModel,
      institutions: [{ orgCode: 'NEW', name: '新机构', cityCode: '610200', cityName: '铜川市' }]
    } });
    const states = JSON.parse(wrapper.get('[data-testid="retail-map"]').attributes('data-region-states'));
    expect(states).toMatchObject({
      '610100': 'NO_INSTITUTION',
      '610200': 'HAS_INSTITUTION',
      '610300': 'NO_INSTITUTION'
    });
  });
});
