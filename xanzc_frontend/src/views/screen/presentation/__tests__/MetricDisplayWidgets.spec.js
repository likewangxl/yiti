// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import MetricDisplayWidgets from '../widgets/MetricDisplayWidgets.vue';

describe('MetricDisplayWidgets', () => {
  it('渲染多实例身份、布局区域、完成进度及空值状态', () => {
    const wrapper = mount(MetricDisplayWidgets, {
      props: { components: [
        { componentId: 'metric-a', componentType: 'METRIC_CARD', layoutRegion: 'LEFT', title: '存款', text: '0亿元', state: 'READY', subFields: [] },
        { componentId: 'completion-a', componentType: 'COMPLETION', layoutRegion: 'RIGHT', title: '完成率', text: '150.0%', progress: 100, state: 'READY', subFields: [] },
        { componentId: 'missing-a', componentType: 'METRIC_CARD', layoutRegion: 'BOTTOM', title: '中收', text: '待接入', state: 'NO_SOURCE', subFields: [] }
      ] }
    });
    expect(wrapper.findAll('[data-testid="presentation-metric-value"]').map(node => node.text()))
      .toEqual(['0亿元', '150.0%', '待接入']);
    expect(wrapper.find('[data-component-id="completion-a"]').attributes('data-layout-region')).toBe('RIGHT');
    expect(wrapper.find('[data-component-id="completion-a"] .presentation-metric-widget__progress i').element.style.width).toBe('100%');
    expect(wrapper.find('[data-component-id="missing-a"] [data-testid="presentation-metric-status"]').text()).toContain('待接入');
  });
});
