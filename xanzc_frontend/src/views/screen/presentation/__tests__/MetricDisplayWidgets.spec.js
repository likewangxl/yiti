// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import MetricDisplayWidgets from '../widgets/MetricDisplayWidgets.vue';

vi.mock('vue-echarts', () => ({
  default: { name: 'VChart', props: ['option'], template: '<div class="chart-stub" />' }
}));

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

  it('按金额显示单位标记元、万元和亿元卡片，供窄屏字号适配', () => {
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        components: [
          { componentId: 'yuan', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', title: '元', unit: '元', text: '1,068,201,500.00元', state: 'READY' },
          { componentId: 'ten-thousand', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', title: '万元', unit: '万元', text: '106,820.15万元', state: 'READY' },
          { componentId: 'hundred-million', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', title: '亿元', unit: '亿元', text: '10.68亿元', state: 'READY' },
          { componentId: 'rate', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', title: '完成率', unit: '%', text: '90.64%', state: 'READY' }
        ]
      }
    });

    expect(wrapper.find('[data-component-id="yuan"]').classes()).toContain('presentation-metric-widget--unit-yuan');
    expect(wrapper.find('[data-component-id="ten-thousand"]').classes()).toContain('presentation-metric-widget--unit-ten-thousand');
    expect(wrapper.find('[data-component-id="hundred-million"]').classes()).toContain('presentation-metric-widget--unit-hundred-million');
    expect(wrapper.find('[data-component-id="rate"]').classes().some(item => item.startsWith('presentation-metric-widget--unit-'))).toBe(false);
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

  it('draft 分组金额卡把唯一主值放在标题同行，三维比较仍留在下方；完成率卡不进入该布局', () => {
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        draftOverview: true,
        components: [{
          componentId: 'business-corp-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '对公存款余额', text: '120.00亿元', state: 'READY',
          comparisons: {
            year: { state: 'READY', text: '较上年 +30.00亿元' },
            month: { state: 'READY', text: '较上月 +20.00亿元' },
            day: { state: 'READY', text: '较上日 +10.00亿元' }
          },
          subFields: []
        }, {
          componentId: 'business-corp-deposit-rate', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '对公存款完成率', text: '86.40%', value: 86.4, state: 'READY', subFields: []
        }]
      }
    });
    const amount = wrapper.get('[data-component-id="business-corp-deposit-balance"]');
    expect(amount.find('[data-testid="presentation-metric-value"]').element.closest('header')).not.toBeNull();
    expect(amount.find('[data-testid="presentation-metric-value"]').text()).toBe('120.00亿元');
    expect(amount.find('[data-testid="presentation-metric-comparisons"]').text()).toContain('较上日 +10.00亿元');
    expect(amount.find('.presentation-metric-widget__value-line > [data-testid="presentation-metric-value"]').exists()).toBe(false);

    const rate = wrapper.get('[data-component-id="business-corp-deposit-rate"]');
    expect(rate.find('[data-testid="completion-ring-gauge"]').exists()).toBe(true);
    expect(rate.find('[data-testid="presentation-metric-value"]').exists()).toBe(false);
  });

  it('新模式将上年、上月、上日三维对比集中显示，并保留精确口径文案', () => {
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        components: [{
          componentId: 'business-corp-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '存款余额', text: '120.00亿元', state: 'READY',
          monthDelta: { state: 'READY', text: '较上月 +20.00亿元' },
          comparisons: {
            year: { state: 'READY', text: '较上年 +30.00亿元', referenceDate: '2027-12-31' },
            month: { state: 'READY', text: '较上月 +20.00亿元', referenceDate: '2028-02-29' },
            day: { state: 'READY', text: '较上日 +10.00亿元', referenceDate: '2028-03-01' }
          },
          subFields: []
        }]
      }
    });
    const comparison = wrapper.get('[data-testid="presentation-metric-comparisons"]');
    expect(comparison.text()).toContain('较上年 +30.00亿元');
    expect(comparison.text()).toContain('较上月 +20.00亿元');
    expect(comparison.text()).toContain('较上日 +10.00亿元');
    expect(comparison.find('[data-comparison="year"]').attributes('title')).toContain('2027-12-31');
  });

  it('仅分组的四张显式完成率指标卡使用仪表盘，并保留图标和较上月行', () => {
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
    expect(wrapper.find('[data-component-id="business-retail-deposit-rate"] [data-testid="completion-ring-gauge"]').attributes('data-mode')).toBe('dashboard');
    expect(wrapper.find('[data-component-id="business-retail-deposit-rate"] [data-testid="presentation-metric-icon"]').exists()).toBe(true);
    expect(wrapper.find('[data-component-id="business-retail-loan-rate"] [data-testid="presentation-metric-icon"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="presentation-metric-month-delta"]')).toHaveLength(7);
    expect(wrapper.find('[data-component-id="business-retail-deposit-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('90.64');
    expect(wrapper.find('[data-component-id="business-retail-loan-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('100');
    expect(wrapper.find('[data-component-id="business-corp-deposit-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('0');
    expect(wrapper.find('[data-component-id="business-corp-loan-rate"] [data-testid="completion-ring-gauge"]').attributes('data-state')).toBe('MISSING');
    expect(wrapper.find('[data-component-id="business-corp-loan-rate"] [data-testid="presentation-metric-status"]').text()).toContain('待接入');
    expect(wrapper.find('[data-component-id="business-retail-deposit-balance"] [data-testid="completion-ring-gauge"]').exists()).toBe(false);
    expect(wrapper.find('[data-component-id="legacy-card"] [data-testid="completion-ring-gauge"]').exists()).toBe(false);
    expect(wrapper.find('[data-component-id="legacy-completion"] .presentation-metric-widget__progress').exists()).toBe(true);
    expect(wrapper.find('[data-component-id="legacy-completion"] [data-testid="completion-ring-gauge"]').exists()).toBe(false);
  });

  it('按零售/对公与存款/贷款维度为四张完成率仪表盘保留互不重复的强调色', () => {
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

  it('draft 模式完成率卡使用紧凑圆环，显示真实百分比和三维对比', () => {
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        draftOverview: true,
        components: [{
          componentId: 'business-corp-deposit-rate', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
          title: '对公存款完成率', text: '86.40%', value: 86.4, state: 'READY',
          comparisons: {
            year: { state: 'READY', text: '较上年 +6.40个百分点' },
            month: { state: 'READY', text: '较上月 +2.40个百分点' },
            day: { state: 'READY', text: '较上日 +0.40个百分点' }
          },
          subFields: []
        }]
      }
    });
    const card = wrapper.get('[data-component-id="business-corp-deposit-rate"]');
    expect(card.find('[data-testid="completion-ring-gauge"]').attributes('data-mode')).toBe('compact');
    expect(card.find('[data-testid="completion-ring-value"]').text()).toBe('86.40%');
    expect(card.find('[data-testid="presentation-metric-value"]').exists()).toBe(false);
    expect(card.find('.presentation-metric-widget__draft-progress').exists()).toBe(false);
    expect(card.findAll('[data-testid="presentation-metric-comparisons"] .presentation-metric-widget__comparison')).toHaveLength(3);
    expect(card.find('[data-testid="presentation-metric-comparisons"]').text()).toContain('较上日 +0.40%');
  });

  it('draft 模式四张显式完成率卡都使用紧凑圆环，保留 125/0/负数/缺失边界和三维对比', () => {
    const rateCases = [
      { componentId: 'business-retail-deposit-rate', text: '125.00%', value: 125, state: 'READY' },
      { componentId: 'business-retail-loan-rate', text: '0.00%', value: 0, state: 'READY' },
      { componentId: 'business-corp-deposit-rate', text: '-4.00%', value: -4, state: 'READY' },
      { componentId: 'business-corp-loan-rate', text: '待接入', value: null, state: 'NO_SOURCE' }
    ];
    const wrapper = mount(MetricDisplayWidgets, {
      props: {
        grouped: true,
        draftOverview: true,
        components: rateCases.map((item, order) => ({
          ...item,
          componentType: 'METRIC_CARD',
          layoutRegion: 'HEADER',
          order,
          title: `${item.componentId} 标题`,
          comparisons: {
            year: { state: 'READY', text: '较上年 +1.00个百分点' },
            month: { state: 'READY', text: '较上月 +2.00个百分点' },
            day: { state: 'READY', text: '较上日 +3.00个百分点' }
          },
          subFields: []
        }))
      }
    });

    expect(wrapper.findAll('[data-testid="completion-ring-gauge"]')).toHaveLength(4);
    expect(wrapper.findAll('[data-testid="completion-ring-gauge"]').every(node => node.attributes('data-mode') === 'compact')).toBe(true);
    expect(wrapper.findAll('.presentation-metric-widget__draft-progress')).toHaveLength(0);
    expect(wrapper.findAll('[data-testid="presentation-metric-value"]')).toHaveLength(0);
    expect(wrapper.findAll('[data-testid="presentation-metric-comparisons"]')).toHaveLength(4);
    expect(wrapper.findAll('[data-testid="presentation-metric-comparisons"] .presentation-metric-widget__comparison')).toHaveLength(12);

    expect(wrapper.find('[data-component-id="business-retail-deposit-rate"] [data-testid="completion-ring-value"]').text()).toBe('125.00%');
    expect(wrapper.find('[data-component-id="business-retail-deposit-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('100');
    expect(wrapper.find('[data-component-id="business-retail-loan-rate"] [data-testid="completion-ring-value"]').text()).toBe('0.00%');
    expect(wrapper.find('[data-component-id="business-retail-loan-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('0');
    expect(wrapper.find('[data-component-id="business-corp-deposit-rate"] [data-testid="completion-ring-value"]').text()).toBe('-4.00%');
    expect(wrapper.find('[data-component-id="business-corp-deposit-rate"] [data-testid="completion-ring-gauge"]').attributes('data-progress')).toBe('0');
    expect(wrapper.find('[data-component-id="business-corp-loan-rate"] [data-testid="completion-ring-value"]').text()).toBe('—');
    expect(wrapper.find('[data-component-id="business-corp-loan-rate"] [data-testid="completion-ring-status"]').text()).toBe('待接入');
    expect(wrapper.find('[data-component-id="business-corp-loan-rate"] [data-testid="presentation-metric-status"]').exists()).toBe(false);
    expect(wrapper.find('[data-component-id="business-corp-loan-rate"] [data-testid="completion-ring-gauge"]').attributes('data-state')).toBe('MISSING');
  });

  it('完成率比较只有 draft 紧凑圆环改用百分号，发布态保留百分点口径', () => {
    const component = {
      componentId: 'business-corp-deposit-rate', componentType: 'METRIC_CARD', layoutRegion: 'HEADER',
      title: '对公存款完成率', text: '86.40%', value: 86.4, state: 'READY',
      comparisons: { month: { state: 'READY', text: '较上月 +2.40个百分点' } }, subFields: []
    };
    const draft = mount(MetricDisplayWidgets, { props: { grouped: true, draftOverview: true, components: [component] } });
    expect(draft.get('[data-testid="presentation-metric-comparisons"]').text()).toContain('较上月 +2.40%');
    expect(draft.get('[data-testid="presentation-metric-comparisons"]').text()).not.toContain('个百分点');

    const published = mount(MetricDisplayWidgets, { props: { grouped: true, draftOverview: false, components: [component] } });
    expect(published.get('[data-testid="presentation-metric-comparisons"]').text()).toContain('较上月 +2.40个百分点');
  });
});
