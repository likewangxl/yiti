// @vitest-environment happy-dom
import { mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import BusinessGrowthWidget from '../widgets/BusinessGrowthWidget.vue';

vi.mock('../../panorama/PanoramaTrend.vue', () => ({ default: {
  props: ['rows', 'series', 'title', 'compact', 'amountFriendly', 'amountUnit', 'variant', 'stackedPalette', 'timeAxis'],
  template: '<div data-testid="panorama-trend-mock" :data-compact="compact ? \'true\' : \'false\'" :data-amount-friendly="amountFriendly ? \'true\' : \'false\'" :data-amount-unit="amountUnit" :data-variant="variant" :data-stacked-palette="stackedPalette" :data-time-axis="timeAxis ? JSON.stringify(timeAxis) : \'\'">{{ title }}|{{ series.map(item => item.label).join(\',\') }}|{{ rows.length }}</div>'
} }));

const presentation = {
  displaySchemaVersion: 1,
  template: 'branch-overview-v1',
  display: { components: [{
    componentId: 'trend-main',
    componentType: 'TREND',
    layoutRegion: 'CENTER',
    content: { series: [
      { seriesKey: 'retailDeposit', field: '测试_直营零售存款', label: '存款', unit: 'YUAN' },
      { seriesKey: 'retailLoan', field: '测试_直营零售贷款', label: '贷款', unit: 'YUAN' },
      { seriesKey: 'corpDeposit', field: '测试_直营对公存款', label: '存款', unit: 'YUAN' },
      { seriesKey: 'corpLoan', field: '测试_直营对公贷款', label: '贷款', unit: 'YUAN' }
    ] },
    dataRefs: [{ blockId: 57, unit: 'YUAN' }]
  }] }
};

const rows = [{
  date: '2026-09-28',
  测试_直营零售存款: 100,
  测试_直营零售贷款: 200,
  测试_直营对公存款: 300,
  测试_直营对公贷款: 400
}];

describe('BusinessGrowthWidget', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('显示业务增长曲线标题和同时可见的零售/对公两张存贷款图', () => {
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { blockResults: { 57: { rows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });

    expect(wrapper.get('[data-testid="business-growth-widget"] h2').text()).toBe('业务增长曲线');
    expect(wrapper.findAll('[data-business-line]')).toHaveLength(2);
    expect(wrapper.find('[data-business-line="RETAIL"] h3').text()).toBe('零售业务');
    expect(wrapper.find('[data-business-line="CORP"] h3').text()).toBe('对公业务');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]')).toHaveLength(2);
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').map(node => node.text()))
      .toEqual(['零售业务|存款,贷款|1', '对公业务|存款,贷款|1']);
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-compact') === 'true')).toBe(true);
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-friendly') === 'true')).toBe(true);
    expect(wrapper.get('.business-growth-widget__heading small').text()).toBe('单位：元');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-unit') === 'YUAN')).toBe(true);
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-variant') === 'STACKED_GRADIENT')).toBe(true);
    expect(wrapper.get('[data-business-line="RETAIL"] [data-testid="panorama-trend-mock"]').attributes('data-stacked-palette')).toBe('WARM');
    expect(wrapper.get('[data-business-line="CORP"] [data-testid="panorama-trend-mock"]').attributes('data-stacked-palette')).toBe('COOL');
  });

  it('顶部 tablist 关联同一个 tabpanel，默认近七日且两个业务线共用周期和堆叠变体', async () => {
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { dataDate: '2026-09-28', blockResults: { 57: { rows: [
          { date: '2026-09-22', 测试_直营零售存款: 1, 测试_直营零售贷款: 2, 测试_直营对公存款: 3, 测试_直营对公贷款: 4 },
          { date: '2026-09-28', 测试_直营零售存款: 5, 测试_直营零售贷款: 6, 测试_直营对公存款: 7, 测试_直营对公贷款: 8 }
        ], unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });

    expect(wrapper.get('[role="tablist"]').findAll('[role="tab"]').map(node => node.text()))
      .toEqual(['近七日', '近一月', '近一年']);
    const selected = wrapper.get('[role="tab"][aria-selected="true"]');
    const panel = wrapper.get('[role="tabpanel"]');
    expect(selected.text()).toBe('近七日');
    expect(selected.attributes('aria-controls')).toBe(panel.attributes('id'));
    expect(panel.attributes('aria-labelledby')).toBe(selected.attributes('id'));
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-variant') === 'STACKED_GRADIENT')).toBe(true);

    await wrapper.get('[role="tab"][data-period="MONTH"]').trigger('click');
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近一月');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.text().endsWith('|2'))).toBe(true);
  });

  it('两张图共用完整时间轴，切换周期时同步更新轴粒度和类别数量', async () => {
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { dataDate: '2024-02-29', blockResults: { 57: { rows: [{
          date: '2024-02-23', 测试_直营零售存款: 1, 测试_直营零售贷款: 2, 测试_直营对公存款: 3, 测试_直营对公贷款: 4
        }, {
          date: '2024-02-29', 测试_直营零售存款: 5, 测试_直营零售贷款: 6, 测试_直营对公存款: 7, 测试_直营对公贷款: 8
        }], unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });
    const axes = () => wrapper.findAll('[data-testid="panorama-trend-mock"]')
      .map(node => JSON.parse(node.attributes('data-time-axis')));

    expect(axes()).toHaveLength(2);
    expect(axes()[0]).toEqual(axes()[1]);
    expect(axes()[0]).toMatchObject({ granularity: 'DAY', categories: expect.any(Array) });
    expect(axes()[0].categories).toHaveLength(7);

    await wrapper.get('[role="tab"][data-period="MONTH"]').trigger('click');
    expect(axes()[0]).toEqual(axes()[1]);
    expect(axes()[0]).toMatchObject({ granularity: 'DAY' });
    expect(axes()[0].categories).toHaveLength(30);

    await wrapper.get('[role="tab"][data-period="YEAR"]').trigger('click');
    expect(axes()[0]).toEqual(axes()[1]);
    expect(axes()[0]).toMatchObject({ granularity: 'MONTH' });
    expect(axes()[0].categories).toHaveLength(12);
  });

  it('tab 支持方向键循环以及 Home/End，并同步焦点、选中态和手动计时重置', async () => {
    vi.useFakeTimers();
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { dataDate: '2026-09-28', blockResults: { 57: { rows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      },
      attachTo: document.body
    });
    const tab = period => wrapper.get(`[role="tab"][data-period="${period}"]`);

    await tab('WEEK').trigger('keydown', { key: 'ArrowRight' });
    await nextTick();
    expect(wrapper.get('[role="tab"][aria-selected="true"]').attributes('data-period')).toBe('MONTH');
    expect(document.activeElement).toBe(tab('MONTH').element);
    await vi.advanceTimersByTimeAsync(9999);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').attributes('data-period')).toBe('MONTH');
    await vi.advanceTimersByTimeAsync(1);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').attributes('data-period')).toBe('YEAR');

    await tab('YEAR').trigger('keydown', { key: 'End' });
    await nextTick();
    expect(document.activeElement).toBe(tab('YEAR').element);
    await tab('YEAR').trigger('keydown', { key: 'Home' });
    await nextTick();
    expect(wrapper.get('[role="tab"][aria-selected="true"]').attributes('data-period')).toBe('WEEK');
    expect(document.activeElement).toBe(tab('WEEK').element);
    await tab('WEEK').trigger('keydown', { key: 'ArrowLeft' });
    await nextTick();
    expect(wrapper.get('[role="tab"][aria-selected="true"]').attributes('data-period')).toBe('YEAR');
    wrapper.unmount();
  });

  it('多个业务增长组件使用不同 tabpanel 和 tab id', () => {
    const first = mount(BusinessGrowthWidget, { props: { presentation, model: { blockResults: {} } } });
    const second = mount(BusinessGrowthWidget, { props: { presentation, model: { blockResults: {} } } });

    expect(first.get('[role="tabpanel"]').attributes('id'))
      .not.toBe(second.get('[role="tabpanel"]').attributes('id'));
    expect(first.get('[role="tab"]').attributes('id'))
      .not.toBe(second.get('[role="tab"]').attributes('id'));
  });

  it('10 秒轮播近七日→近一月→近一年→近七日，未到时限不提前且手动选择重置计时', async () => {
    vi.useFakeTimers();
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { dataDate: '2026-09-28', blockResults: { 57: { rows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });

    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近七日');
    await vi.advanceTimersByTimeAsync(9999);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近七日');
    await vi.advanceTimersByTimeAsync(1);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近一月');
    await vi.advanceTimersByTimeAsync(10000);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近一年');
    await vi.advanceTimersByTimeAsync(10000);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近七日');

    await wrapper.get('[role="tab"][data-period="MONTH"]').trigger('click');
    await vi.advanceTimersByTimeAsync(9999);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近一月');
    await vi.advanceTimersByTimeAsync(1);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近一年');

    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(30000);
    expect(clearIntervalSpy).toHaveBeenCalled();
  });

  it('金额单位变化不会重置手动选择的时间周期', async () => {
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        amountUnit: 'YUAN',
        presentation,
        model: { blockResults: { 57: { rows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });
    await wrapper.get('[role="tab"][data-period="YEAR"]').trigger('click');
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近一年');
    await wrapper.setProps({ amountUnit: 'TEN_THOUSAND' });
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('近一年');
  });

  it('模型身份或数据锚点变化时回到默认近七日', async () => {
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { dataDate: '2026-09-28', blockResults: { 57: { rows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });
    await wrapper.get('[role="tab"][data-period="YEAR"]').trigger('click');
    await wrapper.setProps({ model: { dataDate: '2026-09-29', blockResults: { 57: { rows, unitByField: Object.fromEntries([
      ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
      ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
    ]) } } } });
    expect(wrapper.get('[role="tab"][aria-selected="true"]').attributes('data-period')).toBe('WEEK');
  });

  it('动态显示并传递有效金额单位，非法选择回退元', async () => {
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        amountUnit: 'TEN_THOUSAND',
        presentation,
        model: { blockResults: { 57: { rows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });

    expect(wrapper.get('.business-growth-widget__heading small').text()).toBe('单位：万元');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-unit') === 'TEN_THOUSAND')).toBe(true);

    await wrapper.setProps({ amountUnit: 'HUNDRED_MILLION' });
    expect(wrapper.get('.business-growth-widget__heading small').text()).toBe('单位：亿元');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-unit') === 'HUNDRED_MILLION')).toBe(true);

    await wrapper.setProps({ amountUnit: 'PERCENT' });
    expect(wrapper.get('.business-growth-widget__heading small').text()).toBe('单位：元');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-unit') === 'YUAN')).toBe(true);
  });

  it('历史四列全为空时分别显示业务线待接入空态，不渲染猜测曲线', () => {
    const emptyRows = [{
      data_date: '2026-09-28',
      测试_直营零售存款: null,
      测试_直营零售贷款: null,
      测试_直营对公存款: null,
      测试_直营对公贷款: null
    }];
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { blockResults: { 57: { rows: emptyRows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });

    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]')).toHaveLength(0);
    expect(wrapper.find('[data-business-line="RETAIL"] [data-testid="business-growth-empty"]').text()).toBe('零售业务历史存贷款数据待接入');
    expect(wrapper.find('[data-business-line="CORP"] [data-testid="business-growth-empty"]').text()).toBe('对公业务历史存贷款数据待接入');
  });
});
