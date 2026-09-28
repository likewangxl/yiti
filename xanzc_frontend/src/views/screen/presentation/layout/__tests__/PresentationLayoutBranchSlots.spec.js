// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PresentationLayout from '../PresentationLayout.vue';

const presentation = {
  displaySchemaVersion: 1,
  template: 'branch-overview-v1',
  display: { components: [
    { componentId: 'header', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', visible: true, dataRefs: [{ metricCode: 'deposit', unit: 'HUNDRED_MILLION' }], content: { mainField: 'value' } },
    { componentId: 'map', componentType: 'MAP', layoutRegion: 'CENTER', visible: true, dataRefs: [{ blockId: 1 }] },
    { componentId: 'ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', visible: true, content: { rankingMetrics: [] } }
  ] }
};

describe('PresentationLayout 支行插槽', () => {
  it('支行命名插槽替换中心地图和右侧机构排名，保留新版分组与三栏', () => {
    const wrapper = mount(PresentationLayout, {
      props: { presentation, model: { kpis: [{ key: 'deposit', value: 1, unit: '亿元' }] } },
      slots: {
        'branch-map': '<div data-testid="branch-slot-map">未完成横向图</div>',
        'branch-ranking': '<div data-testid="branch-slot-ranking">当前支行员工排名</div>'
      },
      global: { stubs: { PresentationMapWidget: { template: '<div data-testid="default-map" />' }, InstitutionRankingWidget: { template: '<div data-testid="default-ranking" />' } } }
    });

    expect(wrapper.get('[data-layout-region="HEADER"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-layout-column]')).toHaveLength(3);
    expect(wrapper.get('[data-testid="branch-slot-map"]').text()).toContain('未完成');
    expect(wrapper.get('[data-testid="branch-slot-ranking"]').text()).toContain('员工');
    expect(wrapper.find('[data-testid="default-map"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="default-ranking"]').exists()).toBe(false);
  });
});
