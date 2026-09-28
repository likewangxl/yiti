// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: ['geoJson', 'points', 'selectedRegionCode', 'mode', 'demo'],
    emits: ['region-select', 'branch-select'],
    template: '<div data-testid="retail-map"><button type="button" data-action="map-branch" @click="$emit(\'branch-select\', \'RETAIL-ORG-1\')">地图机构</button></div>'
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

const model = {
  title: '零售经营总览',
  scopeLabel: '授权范围',
  kpis: [],
  trend: [],
  segments: [],
  rankings: [],
  attention: [],
  targets: [],
  issues: [],
  institutions: [{ orgCode: 'RETAIL-ORG-1', orgName: '零售机构', cityCode: '610100' }]
};

const mounted = [];
afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount());
  document.body.style.overflow = '';
});

describe('RetailDashboard legacy map institution navigation', () => {
  it('legacy PanoramaMap 透传 branch-select，交由既有 Runtime 门禁处理', async () => {
    const wrapper = mount(RetailDashboard, {
      props: { model, loading: false, error: '', demo: false },
      attachTo: document.body
    });
    mounted.push(wrapper);

    await wrapper.get('[data-action="map-branch"]').trigger('click');

    expect(wrapper.emitted('branch-select')).toContainEqual(['RETAIL-ORG-1']);
  });

  it('地图机构点击只透传导航事件，不打开机构目录弹层', async () => {
    const wrapper = mount(RetailDashboard, {
      props: { model, loading: false, error: '', demo: false },
      attachTo: document.body
    });
    mounted.push(wrapper);

    await wrapper.get('[data-action="map-branch"]').trigger('click');

    expect(wrapper.emitted('branch-select')).toContainEqual(['RETAIL-ORG-1']);
    expect(wrapper.find('[data-testid="retail-institution-dialog"]').exists()).toBe(false);
  });
});
