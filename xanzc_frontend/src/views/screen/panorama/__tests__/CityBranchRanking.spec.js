// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import CityBranchRanking from '../CityBranchRanking.vue';

const model = {
  activeTabKey: 'retailDepositRate',
  tabs: [
    { key: 'retailDepositRate', label: '零售存款' }, { key: 'retailLoanRate', label: '零售贷款' },
    { key: 'retailNplRate', label: '零售贷款不良率' }, { key: 'corpDepositRate', label: '对公存款' },
    { key: 'corpLoanRate', label: '对公贷款' }, { key: 'corpNplRate', label: '对公贷款不良率' }
  ],
  rows: Array.from({ length: 7 }, (_, index) => ({ orgCode: `ORG-${index + 1}`, orgName: `支行${index + 1}`, value: 100 - index, unit: '%', rank: index + 1 })),
  missingRows: [], missingCount: 0, total: 7
};

describe('CityBranchRanking', () => {
  const mounted = [];
  const mountRanking = props => {
    const wrapper = mount(CityBranchRanking, { props });
    mounted.push(wrapper);
    return wrapper;
  };
  const unmountTracked = wrapper => {
    const index = mounted.indexOf(wrapper);
    if (index >= 0) mounted.splice(index, 1);
    wrapper.unmount();
  };
  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount());
    vi.useRealTimers();
  });

  it('每 5000ms 先轮播当前 tab 的分页，末页停留后才切换 tab', async () => {
    vi.useFakeTimers();
    const wrapper = mountRanking({ model, pageSize: 5 });
    expect(wrapper.findAll('[data-testid="city-branch-ranking-tab"]')).toHaveLength(6);
    expect(wrapper.find('[data-testid="branch-row"] [role="progressbar"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="branch-page-next"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="retailDepositRate"]').attributes('aria-selected')).toBe('true');
    vi.advanceTimersByTime(4999);
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="branch-page-next"]').element.disabled).toBe(false);
    expect(wrapper.emitted('page-change')).toBeUndefined();

    vi.advanceTimersByTime(1);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('page-change')).toContainEqual([2]);
    expect(wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="retailDepositRate"]').attributes('aria-selected')).toBe('true');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(2);

    vi.advanceTimersByTime(4999);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('tab-change')).toBeUndefined();
    vi.advanceTimersByTime(1);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('tab-change')).toContainEqual(['retailLoanRate']);

    await wrapper.setProps({ model: { ...model, activeTabKey: 'retailLoanRate' } });
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(5);
  });

  it('支持手动选择、暂停、hover、focus、visibility 和卸载清理', async () => {
    vi.useFakeTimers();
    const wrapper = mountRanking({ model, pageSize: 5 });
    await wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="corpLoanRate"]').trigger('click');
    expect(wrapper.emitted('tab-change')).toContainEqual(['corpLoanRate']);
    await wrapper.setProps({ model: { ...model, activeTabKey: 'corpLoanRate' } });
    await wrapper.get('[data-testid="city-branch-ranking-pause"]').trigger('click');
    expect(wrapper.get('[data-testid="city-branch-ranking-pause"]').text()).toContain('继续');
    const pausedCount = wrapper.emitted('tab-change').length;
    vi.advanceTimersByTime(60000);
    expect(wrapper.emitted('tab-change')).toHaveLength(pausedCount);
    await wrapper.get('[data-testid="city-branch-ranking-pause"]').trigger('click');
    await wrapper.get('[data-testid="branch-page-next"]').trigger('click');
    expect(wrapper.emitted('page-change').at(-1)).toEqual([2]);
    await wrapper.get('[data-testid="branch-page-prev"]').trigger('click');
    expect(wrapper.emitted('page-change').at(-1)).toEqual([1]);

    await wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="retailDepositRate"]').trigger('focus');
    const focusCount = wrapper.emitted('tab-change').length;
    vi.advanceTimersByTime(20000);
    expect(wrapper.emitted('tab-change')).toHaveLength(focusCount);
    await wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="retailDepositRate"]').trigger('blur');
    await wrapper.get('[data-testid="city-branch-ranking"]').trigger('mouseenter');
    const hoverCount = wrapper.emitted('tab-change').length;
    vi.advanceTimersByTime(20000);
    expect(wrapper.emitted('tab-change')).toHaveLength(hoverCount);
    await wrapper.get('[data-testid="city-branch-ranking"]').trigger('mouseleave');

    const originalVisibility = Object.getOwnPropertyDescriptor(document, 'visibilityState');
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });
    document.dispatchEvent(new Event('visibilitychange'));
    const hiddenCount = wrapper.emitted('tab-change').length;
    vi.advanceTimersByTime(20000);
    expect(wrapper.emitted('tab-change')).toHaveLength(hiddenCount);
    Object.defineProperty(document, 'visibilityState', originalVisibility || { configurable: true, value: 'visible' });
    document.dispatchEvent(new Event('visibilitychange'));

    const eventsBeforeUnmount = wrapper.emitted('tab-change');
    const beforeUnmount = eventsBeforeUnmount.length;
    unmountTracked(wrapper);
    vi.advanceTimersByTime(30000);
    expect(eventsBeforeUnmount).toHaveLength(beforeUnmount);
  });

  it('只有一个 tab 时仍轮播多页，末页后回到第一页', async () => {
    vi.useFakeTimers();
    const singleTabModel = { ...model, tabs: [model.tabs[0]], activeTabKey: model.tabs[0].key };
    const wrapper = mountRanking({ model: singleTabModel, pageSize: 5 });
    vi.advanceTimersByTime(5000);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('page-change')).toContainEqual([2]);
    vi.advanceTimersByTime(5000);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('page-change').at(-1)).toEqual([1]);
    expect(wrapper.emitted('tab-change')).toBeUndefined();
  });

  it('missingRows fallback 也按分页轮播', async () => {
    vi.useFakeTimers();
    const missingModel = {
      ...model,
      tabs: [model.tabs[0]],
      activeTabKey: model.tabs[0].key,
      rows: [],
      missingRows: Array.from({ length: 7 }, (_, index) => ({ orgCode: `M-${index + 1}`, orgName: `缺失支行${index + 1}`, rank: index + 1 })),
      missingCount: 7
    };
    const wrapper = mountRanking({ model: missingModel, pageSize: 5 });
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(5);
    vi.advanceTimersByTime(5000);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('page-change')).toContainEqual([2]);
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(2);
  });

  it('动态 interval 变化会从新间隔重新计时，手动 tab/page 仍保持受控', async () => {
    vi.useFakeTimers();
    const wrapper = mountRanking({ model, pageSize: 5, interval: 10000 });
    await wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="corpLoanRate"]').trigger('click');
    expect(wrapper.emitted('tab-change').at(-1)).toEqual(['corpLoanRate']);
    await wrapper.get('[data-testid="branch-page-next"]').trigger('click');
    expect(wrapper.emitted('page-change').at(-1)).toEqual([2]);

    await wrapper.setProps({ model: { ...model, activeTabKey: 'corpLoanRate' }, page: 2, interval: 10000 });
    await wrapper.setProps({ page: 1, interval: 3000 });
    await wrapper.vm.$nextTick();
    const beforeAutoPage = wrapper.emitted('page-change').length;
    expect(wrapper.get('.city-branch-ranking__pagination span').text()).toBe('1 / 2');
    vi.advanceTimersByTime(2999);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('page-change')).toHaveLength(beforeAutoPage);
    expect(wrapper.get('.city-branch-ranking__pagination span').text()).toBe('1 / 2');
    vi.advanceTimersByTime(1);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('page-change')).toHaveLength(beforeAutoPage + 1);
    expect(wrapper.get('.city-branch-ranking__pagination span').text()).toBe('2 / 2');
    expect(wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="corpLoanRate"]').attributes('aria-selected')).toBe('true');
  });

  it('初始受控页码超过总页数时立即夹紧到末页', async () => {
    const wrapper = mountRanking({ model, page: 99, pageSize: 5 });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('.city-branch-ranking__pagination span').text()).toBe('2 / 2');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(2);
    expect(wrapper.emitted('page-change').at(-1)).toEqual([2]);
    unmountTracked(wrapper);
  });

  it('搜索变化重置页码，模型重算保留已推进页，缩页时夹紧', async () => {
    vi.useFakeTimers();
    const wrapper = mountRanking({ model, pageSize: 5 });
    vi.advanceTimersByTime(5000);
    await wrapper.vm.$nextTick();
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(2);

    await wrapper.setProps({ model: { ...model, rows: model.rows.map(row => ({ ...row })) } });
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(2);

    await wrapper.setProps({ search: '支行1' });
    await wrapper.vm.$nextTick();
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(1);
    expect(wrapper.emitted('page-change').at(-1)).toEqual([1]);

    await wrapper.setProps({ page: 2, search: '' });
    await wrapper.vm.$nextTick();
    await wrapper.setProps({ model: { ...model, rows: model.rows.slice(0, 2) } });
    await wrapper.vm.$nextTick();
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(2);
    expect(wrapper.emitted('page-change').at(-1)).toEqual([1]);
  });
});
