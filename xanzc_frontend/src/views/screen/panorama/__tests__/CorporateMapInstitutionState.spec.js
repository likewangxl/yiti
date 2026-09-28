// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: {
      geoJson: { type: Object, default: () => ({}) },
      points: { type: Array, default: () => [] },
      cityDetails: { type: Object, default: () => ({}) },
      metricValues: { type: Object, default: () => ({}) },
      metricColors: { type: Object, default: () => ({}) },
      regionStates: { type: Object, default: () => ({}) },
      colorByMetric: { type: Boolean, default: false },
      mode: { type: String, default: '' },
      appearance: { type: String, default: '' },
      labelLayout: { type: String, default: '' },
      selectedRegionCode: { type: [String, Number], default: '' },
      demo: { type: Boolean, default: false }
    },
    emits: ['region-select', 'branch-select'],
    template: '<div data-testid="corporate-map" :data-color-by-metric="String(colorByMetric)" :data-region-states="JSON.stringify(regionStates)" :data-metric-colors="JSON.stringify(metricColors)"><button type="button" data-action="map-branch" @click="$emit(\'branch-select\', \'CORP-ORG-1\')">地图机构</button></div>'
  }
}));

vi.mock('../CorporateTrend.vue', () => ({
  default: { name: 'CorporateTrend', props: ['trend', 'dataDate', 'scopeLabel'], template: '<div data-testid="corporate-trend" />' }
}));

describe('CorporateDashboard legacy map institution state', () => {
  it('机构目录完整时按城市机构占用着色，即使指标为空也不降级为指标缺失', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        model: {
          institutions: [
            { orgCode: 'CORP-ORG-1', orgName: '甲机构', cityCode: '610100' },
            { orgCode: 'CORP-ORG-2', orgName: '乙机构', cityCode: '610300' }
          ],
          rankings: []
        }
      }
    });

    const map = wrapper.get('[data-testid="corporate-map"]');
    const regionStates = JSON.parse(map.attributes('data-region-states'));
    const metricColors = JSON.parse(map.attributes('data-metric-colors'));
    expect(map.attributes('data-color-by-metric')).toBe('true');
    expect(regionStates['610100']).toBe('HAS_INSTITUTION');
    expect(regionStates['610300']).toBe('HAS_INSTITUTION');
    expect(metricColors['610100']).not.toBe('#65738a');
    expect(metricColors['610300']).not.toBe('#65738a');
    wrapper.unmount();
  });

  it('legacy 地图机构事件透传给运行时导航，不依赖排名指标', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        model: { institutions: [{ orgCode: 'CORP-ORG-1', orgName: '甲机构', cityCode: '610100' }], rankings: [] }
      }
    });

    await wrapper.get('[data-action="map-branch"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toContainEqual(['CORP-ORG-1']);
    wrapper.unmount();
  });
});
