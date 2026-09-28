// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: ['metricLabel', 'metricValues', 'metricNumericValues', 'metricColors', 'regionStates', 'labelLayout', 'cityDetails', 'cityDetailMode', 'showRegionMetrics', 'showProvincePoints', 'showProvincePointLabels'],
    emits: ['region-select', 'branch-select'],
    template: '<div class="panorama-map-stub" />'
  }
}));

vi.mock('../PanoramaInstitutionDirectory.vue', () => ({
  default: { name: 'PanoramaInstitutionDirectory', template: '<div data-testid="directory-stub" />' }
}));

vi.mock('../../presentation/layout/PresentationLayout.vue', () => ({
  default: {
    name: 'PresentationLayout',
    emits: ['branch-select'],
    template: '<div data-testid="presentation-layout-stub"><button type="button" data-action="layout-branch" @click="$emit(\'branch-select\', \'ORG-1\')">布局机构</button></div>'
  }
}));

vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div />' }
}));

import PanoramaDashboard from '../PanoramaDashboard.vue';

const model = {
  title: '分行经营总览',
  scopeLabel: '全辖机构',
  dataDate: '2026-09-22',
  kpis: [],
  trend: [],
  composition: [],
  rankings: [{ orgCode: 'ORG-1', name: '甲机构', cityCode: '610100', deposit: 100, increase: 2 }],
  attention: [],
  institutions: [{ orgCode: 'ORG-1', orgName: '甲机构', cityCode: '610100', cityName: '西安市', located: false }],
  issues: []
};

const configuredPresentation = {
  displayPresentation: {
    displaySchemaVersion: 1,
    display: {
      components: [{
        componentId: 'ranking',
        componentType: 'RANKING',
        layoutRegion: 'RIGHT',
        visible: true,
        title: '机构排名',
        content: { rankingMetrics: [{ metricKey: 'deposit', field: 'deposit', label: '存款余额', unit: '亿元' }] }
      }]
    }
  }
};

const mounted = [];
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

function mountDashboard(props = {}) {
  const wrapper = mount(PanoramaDashboard, {
    props: { model, loading: false, error: '', demo: false, ...props },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

describe('PanoramaDashboard institution ranking navigation', () => {
  it('configured PresentationLayout 的排名机构事件转发给运行时', async () => {
    const wrapper = mountDashboard({ sourcePresentation: configuredPresentation });
    await wrapper.get('[data-action="layout-branch"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['ORG-1']]);
  });

  it('正式 legacy 排名机构选择后只发支行事件，不打开城市弹层', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-testid="ranking-row"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['ORG-1']]);
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
  });

  it('demo legacy 排名机构仍保留城市弹层行为', async () => {
    const wrapper = mountDashboard({ demo: true });
    await wrapper.get('[data-testid="ranking-row"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['ORG-1']]);
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(true);
  });
});
