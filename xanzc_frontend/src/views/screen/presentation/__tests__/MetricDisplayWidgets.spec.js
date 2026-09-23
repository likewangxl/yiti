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

  it('按配置顺序为同类指标卡提供青紫蓝绿 accent 层级', () => {
    const wrapper = mount(MetricDisplayWidgets, { props: { components: [
      { componentId: 'metric-0', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, title: '指标一', text: '1', state: 'READY' },
      { componentId: 'metric-1', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 1, title: '指标二', text: '2', state: 'READY' },
      { componentId: 'metric-2', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 2, title: '指标三', text: '3', state: 'READY' },
      { componentId: 'metric-3', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 3, title: '指标四', text: '4', state: 'READY' }
    ] } });

    expect(wrapper.find('[data-component-id="metric-0"]').classes()).toContain('presentation-metric-widget--cyan');
    expect(wrapper.find('[data-component-id="metric-1"]').classes()).toContain('presentation-metric-widget--violet');
    expect(wrapper.find('[data-component-id="metric-2"]').classes()).toContain('presentation-metric-widget--blue');
    expect(wrapper.find('[data-component-id="metric-3"]').classes()).toContain('presentation-metric-widget--green');
  });
});
