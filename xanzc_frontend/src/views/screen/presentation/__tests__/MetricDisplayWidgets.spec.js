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

  it('为十个分行业务指标使用互不重复的显式 Element Plus 图标，旧组件不补图标', () => {
    const ids = [
      'business-retail-deposit-balance', 'business-retail-deposit-rate',
      'business-retail-loan-balance', 'business-retail-loan-rate',
      'business-corp-deposit-balance', 'business-corp-deposit-rate',
      'business-corp-loan-balance', 'business-corp-loan-rate',
      'business-revenue-operating', 'business-revenue-fee'
    ];
    const wrapper = mount(MetricDisplayWidgets, { props: { components: [
      ...ids.map((componentId, order) => ({
        componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order,
        title: componentId, text: '1.00万元', state: 'READY', subFields: []
      })),
      { componentId: 'legacy-card', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 10, title: '旧卡片', text: '1', state: 'READY', subFields: [] }
    ] } });

    const icons = wrapper.findAll('[data-testid="presentation-metric-icon"]');
    expect(icons).toHaveLength(10);
    expect(icons.map(node => node.attributes('data-icon'))).toEqual([
      'Wallet', 'TrendCharts', 'Coin', 'DataAnalysis', 'OfficeBuilding',
      'Histogram', 'Money', 'PieChart', 'Tickets', 'CreditCard'
    ]);
    expect(new Set(icons.map(node => node.attributes('data-icon'))).size).toBe(10);
    expect(wrapper.find('[data-component-id="legacy-card"] [data-testid="presentation-metric-icon"]').exists()).toBe(false);
  });

  it('分组卡隐藏原始来源字段，完整展示业务标题并保留来源 title；通用卡仍显示 metricName', () => {
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        components: [{
          componentId: 'business-retail-deposit-rate', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '存款完成率', metricName: '测试_零售存款目标完成率', text: '86.40%', state: 'READY', subFields: []
        }]
      }
    });
    const groupedCard = wrapper.find('[data-component-id="business-retail-deposit-rate"]');
    expect(groupedCard.classes()).toContain('presentation-metric-widget--grouped');
    expect(groupedCard.find('h2').text()).toBe('存款完成率');
    expect(groupedCard.find('.presentation-metric-widget__header small').exists()).toBe(false);
    expect(groupedCard.attributes('title')).toBe('测试_零售存款目标完成率');

    const genericWrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: false,
        components: [{
          componentId: 'legacy-card', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '存款余额', metricName: '测试_直营存款余额', text: '1.00万元', state: 'READY', subFields: []
        }]
      }
    });
    expect(genericWrapper.find('.presentation-metric-widget__header small').text()).toBe('测试_直营存款余额');
  });

  it('分组卡在主值后显示较上月差值，通用卡不显示该文案', () => {
    const groupedWrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        components: [{
          componentId: 'business-retail-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '存款余额', text: '125.00亿元', state: 'READY',
          monthDelta: { state: 'READY', text: '较上月 +25.00亿元' }, subFields: []
        }]
      }
    });
    expect(groupedWrapper.find('[data-testid="presentation-metric-month-delta"]').text()).toBe('较上月 +25.00亿元');
    expect(groupedWrapper.find('.presentation-metric-widget__value-line').text()).toContain('125.00亿元较上月 +25.00亿元');

    const genericWrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: false,
        components: [{
          componentId: 'legacy-card', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '存款余额', text: '125.00亿元', state: 'READY',
          monthDelta: { state: 'READY', text: '较上月 +25.00亿元' }, subFields: []
        }]
      }
    });
    expect(genericWrapper.find('[data-testid="presentation-metric-month-delta"]').exists()).toBe(false);
  });

  it('仅分组的四张显式完成率指标卡使用环形图，并保留图标和较上月行', () => {
    const rateIds = [
      'business-retail-deposit-rate',
      'business-retail-loan-rate',
      'business-corp-deposit-rate',
      'business-corp-loan-rate'
    ];
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        components: [
          ...rateIds.map((componentId, order) => ({
            componentId,
            componentType: 'METRIC_CARD',
            layoutRegion: 'HEADER',
            order,
            title: componentId,
            text: order === 0 ? '90.64%' : order === 1 ? '125.00%' : order === 2 ? '-4.00%' : '待接入',
            value: order === 0 ? 90.64 : order === 1 ? 125 : order === 2 ? -4 : null,
            state: order === 3 ? 'NO_SOURCE' : 'READY',
            monthDelta: { state: 'READY', text: '较上月 +1.00个百分点' },
            subFields: []
          })),
          {
            componentId: 'business-retail-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
            title: '存款余额', text: '125.00亿元', value: 125, state: 'READY', subFields: []
          },
          {
            componentId: 'legacy-card', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
            title: '旧卡片', text: '1.00亿元', value: 1, state: 'READY', subFields: []
          },
          {
            componentId: 'legacy-completion', componentType: 'COMPLETION', layoutRegion: 'RIGHT',
            title: '旧完成情况', text: '80.00%', progress: 80, value: 80, state: 'READY', subFields: []
          }
        ]
      }
    });

    expect(wrapper.findAll('[data-testid="completion-ring-gauge"]')).toHaveLength(4);
    expect(wrapper.find('[data-component-id="business-retail-deposit-rate"] [data-testid="presentation-metric-icon"]').exists()).toBe(true);
    expect(wrapper.find('[data-component-id="business-retail-loan-rate"] [data-testid="presentation-metric-icon"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="presentation-metric-month-delta"]')).toHaveLength(7);
    expect(wrapper.find('[data-component-id="business-retail-deposit-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('90.64');
    expect(wrapper.find('[data-component-id="business-retail-loan-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('100');
    expect(wrapper.find('[data-component-id="business-corp-deposit-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('0');
    expect(wrapper.find('[data-component-id="business-corp-loan-rate"] [data-testid="completion-ring-gauge"]').attributes('data-state')).toBe('MISSING');
    expect(wrapper.find('[data-component-id="business-retail-deposit-balance"] [data-testid="completion-ring-gauge"]').exists()).toBe(false);
    expect(wrapper.find('[data-component-id="legacy-card"] [data-testid="completion-ring-gauge"]').exists()).toBe(false);
    expect(wrapper.find('[data-component-id="legacy-completion"] .presentation-metric-widget__progress').exists()).toBe(true);
    expect(wrapper.find('[data-component-id="legacy-completion"] [data-testid="completion-ring-gauge"]').exists()).toBe(false);
  });

  it('按零售/对公与存款/贷款维度为四张完成率圆环使用互不重复的颜色', () => {
    const accentsById = {
      'business-retail-deposit-rate': 'var(--panorama-cyan, #4de8ef)',
      'business-retail-loan-rate': 'var(--panorama-blue, #5896ff)',
      'business-corp-deposit-rate': 'var(--panorama-violet, #a979ff)',
      'business-corp-loan-rate': 'var(--panorama-up, #58e4b5)'
    };
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        components: Object.keys(accentsById).map((componentId, order) => ({
          componentId,
          componentType: 'METRIC_CARD',
          layoutRegion: 'HEADER',
          order,
          title: componentId,
          text: '80.00%',
          value: 80,
          state: 'READY',
          subFields: []
        }))
      }
    });

    const actualAccents = Object.keys(accentsById).map(componentId => {
      const style = wrapper.find(`[data-component-id="${componentId}"] [data-testid="completion-ring-gauge"]`).attributes('style');
      return style.match(/--completion-ring-accent:\s*([^;]+)/)?.[1]?.trim();
    });

    expect(actualAccents).toEqual(Object.values(accentsById));
    expect(new Set(actualAccents).size).toBe(4);
  });

  it('非分组时即使组件ID匹配也保留普通指标卡展示', () => {
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: false,
        components: [{
          componentId: 'business-retail-deposit-rate', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '存款完成率', text: '90.64%', value: 90.64, state: 'READY', subFields: []
        }]
      }
    });

    expect(wrapper.find('[data-testid="completion-ring-gauge"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="presentation-metric-value"]').text()).toBe('90.64%');
  });
});
