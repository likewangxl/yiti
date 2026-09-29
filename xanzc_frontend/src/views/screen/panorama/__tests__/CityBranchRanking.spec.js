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
  afterEach(() => vi.useRealTimers());

  it('始终展示六个 tab、横向率条、搜索分页，并支持手动选择、暂停和受控自动轮播', async () => {
    vi.useFakeTimers();
    const wrapper = mount(CityBranchRanking, { props: { model, pageSize: 5 } });
    expect(wrapper.findAll('[data-testid="city-branch-ranking-tab"]')).toHaveLength(6);
    expect(wrapper.find('[data-testid="branch-row"] [role="progressbar"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="branch-page-next"]').exists()).toBe(true);
    await wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="corpLoanRate"]').trigger('click');
    expect(wrapper.emitted('tab-change')).toContainEqual(['corpLoanRate']);
    await wrapper.setProps({ model: { ...model, activeTabKey: 'corpLoanRate' } });
    await wrapper.get('[data-testid="city-branch-ranking-pause"]').trigger('click');
    expect(wrapper.get('[data-testid="city-branch-ranking-pause"]').text()).toContain('继续');
    const pausedCount = wrapper.emitted('tab-change').length;
    vi.advanceTimersByTime(60000);
    expect(wrapper.emitted('tab-change')).toHaveLength(pausedCount);
    await wrapper.get('[data-testid="city-branch-ranking-pause"]').trigger('click');
    const expected = ['corpNplRate', 'retailDepositRate', 'retailLoanRate', 'retailNplRate', 'corpDepositRate', 'corpLoanRate'];
    const sequence = [];
    for (const key of expected) {
      vi.advanceTimersByTime(10000);
      await wrapper.vm.$nextTick();
      expect(wrapper.emitted('tab-change').at(-1)).toEqual([key]);
      sequence.push(wrapper.emitted('tab-change').at(-1)[0]);
      await wrapper.setProps({ model: { ...model, activeTabKey: key } });
    }
    expect(sequence).toEqual(expected);
    await wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="retailDepositRate"]').trigger('focus');
    const focusCount = wrapper.emitted('tab-change').length;
    vi.advanceTimersByTime(20000);
    expect(wrapper.emitted('tab-change')).toHaveLength(focusCount);
    await wrapper.get('[data-testid="city-branch-ranking-tab"][data-tab-key="retailDepositRate"]').trigger('blur');
    await wrapper.get('[data-testid="branch-page-next"]').trigger('click');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(2);
    await wrapper.get('[data-testid="branch-page-prev"]').trigger('click');
    expect(wrapper.findAll('[data-testid="branch-row"]')).toHaveLength(5);
    const eventsBeforeUnmount = wrapper.emitted('tab-change');
    const beforeUnmount = eventsBeforeUnmount.length;
    wrapper.unmount();
    vi.advanceTimersByTime(30000);
    expect(eventsBeforeUnmount).toHaveLength(beforeUnmount);
  });
});
