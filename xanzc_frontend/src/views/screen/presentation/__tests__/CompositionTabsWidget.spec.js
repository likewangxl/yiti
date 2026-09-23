// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import CompositionTabsWidget from '../widgets/CompositionTabsWidget.vue';

const model = {
  enabled: true,
  intervalMs: 100,
  rotationEnabled: true,
  rotationState: 'READY',
  tabs: [
    { tabKey: 'deposit', label: '存款', unit: '亿元', state: 'READY', corporate: { value: 40, text: '40', share: 40, shareText: '40%' }, retail: { value: 60, text: '60', share: 60, shareText: '60%' }, total: { value: 100, text: '100' }, other: null, gap: null, statusMessage: '' },
    { tabKey: 'loan', label: '贷款', unit: '亿元', state: 'NO_TOTAL', corporate: { value: 2, text: '2', share: null, shareText: '占比不可计算' }, retail: { value: 3, text: '3', share: null, shareText: '占比不可计算' }, total: { value: null, text: '—' }, other: null, gap: null, statusMessage: '总量来源待接入，未计算占比' }
  ],
  components: [{ tabs: [], intermediaryIncome: { state: 'READY', ratio: 15, ratioText: '15%', numerator: { value: 12, text: '12', unit: '亿元' }, denominator: { value: 80, text: '80', unit: '亿元' } } }]
};

describe('CompositionTabsWidget', () => {
  afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); vi.restoreAllMocks(); });

  it('支持自动切换、手动暂停/恢复、hover/focus暂停，并在卸载时清理timer', async () => {
    vi.useFakeTimers();
    const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval');
    const wrapper = mount(CompositionTabsWidget, { props: { model } });
    expect(wrapper.find('[data-testid="composition-tab-deposit"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="composition-tab-loan"]').exists()).toBe(false);

    vi.advanceTimersByTime(100);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="composition-tab-loan"]').exists()).toBe(true);

    await wrapper.find('[data-testid="composition-tab-deposit-select"]').trigger('click');
    expect(wrapper.find('[data-testid="composition-tabs-pause"]').text()).toContain('继续');
    vi.advanceTimersByTime(200);
    expect(wrapper.find('[data-testid="composition-tab-deposit"]').exists()).toBe(true);

    await wrapper.find('[data-testid="composition-tabs-pause"]').trigger('click');
    expect(wrapper.find('[data-testid="composition-tabs-pause"]').text()).toContain('暂停');
    await wrapper.find('[data-testid="composition-tabs-root"]').trigger('mouseenter');
    vi.advanceTimersByTime(200);
    expect(wrapper.find('[data-testid="composition-tab-deposit"]').exists()).toBe(true);
    await wrapper.find('[data-testid="composition-tabs-root"]').trigger('mouseleave');
    await wrapper.find('[data-testid="composition-tabs-root"]').trigger('focusin');
    vi.advanceTimersByTime(200);
    expect(wrapper.find('[data-testid="composition-tab-deposit"]').exists()).toBe(true);
    await wrapper.find('[data-testid="composition-tabs-root"]').trigger('focusout');

    wrapper.unmount();
    expect(clearIntervalSpy).toHaveBeenCalled();
  });

  it('公司/零售点击只发出固定业务身份与tabKey，不拼接URL', async () => {
    const wrapper = mount(CompositionTabsWidget, { props: { model } });
    await wrapper.find('[data-testid="business-line-corp"]').trigger('click');
    await wrapper.find('[data-testid="business-line-retail"]').trigger('click');
    expect(wrapper.emitted('business-line-select')).toEqual([
      [{ businessLine: 'CORP', tabKey: 'deposit' }],
      [{ businessLine: 'RETAIL', tabKey: 'deposit' }]
    ]);
    expect(JSON.stringify(wrapper.emitted('business-line-select'))).not.toContain('http');
  });

  it('展示中间收入占营业收入比例第三部分，未接入时显示待接入', () => {
    const wrapper = mount(CompositionTabsWidget, { props: { model } });
    expect(wrapper.find('[data-testid="composition-intermediary-income"]').text()).toContain('中间收入占营业收入');
    expect(wrapper.find('[data-testid="composition-intermediary-income-ratio"]').text()).toContain('15%');
    const pending = mount(CompositionTabsWidget, { props: { model: { ...model, components: [{ intermediaryIncome: { state: 'PENDING', ratio: null, ratioText: '待接入' } }] } } });
    expect(pending.find('[data-testid="composition-intermediary-income-ratio"]').text()).toContain('待接入');
  });

  it('三部分公司/零售行显示核定总量与其他或缺口', () => {
    const wrapper = mount(CompositionTabsWidget, { props: { model: {
      sections: [{ sectionKey: 'corporate', label: '公司', businessLine: 'CORP', items: [{
        tabKey: 'deposit', label: '存款', value: { value: 40, text: '40', unit: '亿元' }, shareText: '40%',
        total: { value: 100, text: '100', unit: '亿元' }, other: { value: 10, text: '10', unit: '亿元' }, otherShareText: '10%'
      }] }]
    } } });
    expect(wrapper.find('[data-testid="composition-section-corporate"]').text()).toContain('核定总量 100 亿元');
    expect(wrapper.find('[data-testid="composition-section-corporate"]').text()).toContain('其他 10 亿元');
    expect(wrapper.find('[data-testid="composition-section-corporate"]').text()).toContain('10%');
  });
});
