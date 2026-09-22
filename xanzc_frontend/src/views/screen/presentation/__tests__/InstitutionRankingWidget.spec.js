// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import InstitutionRankingWidget from '../widgets/InstitutionRankingWidget.vue';

const model = {
  activeMetricKey: 'deposit',
  metrics: [
    {
      metricKey: 'deposit', label: '存款余额', unit: '亿元', direction: 'DESC',
      expected: [{ orgCode: 'A', name: '甲' }, { orgCode: 'B', name: '乙' }], received: [{ orgCode: 'A' }],
      rankable: [{ orgCode: 'A', name: '甲', value: 10, rank: 1, state: 'RANKABLE' }],
      missing: [{ orgCode: 'B', name: '乙', state: 'MISSING', reason: 'NO_VALUE' }], receivedCount: 1, expectedCount: 2, rankableCount: 1, missingCount: 1,
      incomplete: true, summary: '已获得 1/2 家授权机构，缺少 1 家'
    },
    {
      metricKey: 'increase', label: '较上月净增', unit: '亿元', direction: 'ASC',
      expected: [{ orgCode: 'A', name: '甲' }, { orgCode: 'B', name: '乙' }], received: [{ orgCode: 'A' }, { orgCode: 'B' }],
      rankable: [{ orgCode: 'B', name: '乙', value: -2, rank: 1, state: 'RANKABLE' }, { orgCode: 'A', name: '甲', value: 0, rank: 2, state: 'RANKABLE' }],
      missing: [], receivedCount: 2, expectedCount: 2, rankableCount: 2, missingCount: 0,
      incomplete: false, summary: '已获得 2/2 家授权机构'
    }
  ]
};

const mounted = [];
afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount());
  vi.useRealTimers();
});

function mountWidget(overrides = {}) {
  const wrapper = mount(InstitutionRankingWidget, { props: { model, ...overrides }, attachTo: document.body });
  mounted.push(wrapper);
  return wrapper;
}

describe('InstitutionRankingWidget', () => {
  it('将全部可排名机构和缺数机构放在同一可滚动列表，不截断为TOP10', () => {
    const wrapper = mountWidget();
    expect(wrapper.get('[data-testid="institution-ranking-list"]').attributes('tabindex')).toBe('0');
    expect(wrapper.findAll('[data-testid="institution-ranking-row"]')).toHaveLength(2);
    expect(wrapper.find('[data-org-code="A"]').text()).toContain('1');
    expect(wrapper.find('[data-org-code="B"]').attributes('data-state')).toBe('MISSING');
    expect(wrapper.find('[data-org-code="B"]').text()).toContain('未参与');
    expect(wrapper.get('[data-testid="institution-ranking-coverage"]').text()).toContain('1/2');
    expect(wrapper.get('[data-testid="institution-ranking-incomplete"]').text()).toContain('缺少');
  });

  it('指标页签可键盘访问，手动选择暂停并可恢复自动轮播', async () => {
    vi.useFakeTimers();
    const wrapper = mountWidget({ interval: 1000 });
    const buttons = wrapper.findAll('[data-ranking-metric]');
    expect(buttons).toHaveLength(2);
    expect(buttons[0].attributes('aria-selected')).toBe('true');
    await buttons[1].trigger('click');
    expect(wrapper.emitted('metric-change')).toContainEqual([{ metricKey: 'increase' }]);
    expect(wrapper.get('[data-ranking-metric="increase"]').attributes('aria-selected')).toBe('true');
    expect(wrapper.get('[data-action="toggle-ranking-carousel"]').text()).toContain('继续');
    await vi.advanceTimersByTimeAsync(3000);
    expect(wrapper.get('[data-ranking-metric="increase"]').attributes('aria-selected')).toBe('true');
    await wrapper.get('[data-action="toggle-ranking-carousel"]').trigger('click');
    expect(wrapper.get('[data-action="toggle-ranking-carousel"]').text()).toContain('暂停');
    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.get('[data-ranking-metric="deposit"]').attributes('aria-selected')).toBe('true');
  });

  it('自动轮播切换时发出固定 metric-change 事件', async () => {
    vi.useFakeTimers();
    const wrapper = mountWidget({ interval: 1000 });
    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.emitted('metric-change')).toContainEqual([{ metricKey: 'increase' }]);
  });

  it('hover和focus暂停轮播，离开后恢复，卸载清理定时器', async () => {
    vi.useFakeTimers();
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
    const wrapper = mountWidget({ interval: 1000 });
    const region = wrapper.get('[data-testid="institution-ranking-widget"]');
    await region.trigger('mouseenter');
    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.get('[data-ranking-metric="deposit"]').attributes('aria-selected')).toBe('true');
    await region.trigger('mouseleave');
    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.get('[data-ranking-metric="increase"]').attributes('aria-selected')).toBe('true');
    await region.trigger('focusin');
    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.get('[data-ranking-metric="increase"]').attributes('aria-selected')).toBe('true');
    wrapper.unmount();
    expect(clearIntervalSpy).toHaveBeenCalled();
  });

  it.each([0, 1])('只有%i个指标时不启动无意义轮播定时器', metricCount => {
    vi.useFakeTimers();
    const setIntervalSpy = vi.spyOn(window, 'setInterval');
    const wrapper = mountWidget({ model: { ...model, metrics: model.metrics.slice(0, metricCount) } });

    expect(setIntervalSpy).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('指标从多项变为空时清理已有轮播定时器', async () => {
    vi.useFakeTimers();
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
    const wrapper = mountWidget({ interval: 1000 });

    await wrapper.setProps({ model: { ...model, metrics: [] } });

    expect(clearIntervalSpy).toHaveBeenCalled();
  });

  it('页面隐藏时暂停轮播，重新可见后恢复', async () => {
    vi.useFakeTimers();
    const wrapper = mountWidget({ interval: 1000 });
    const visibility = value => Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value
    });

    visibility('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.get('[data-ranking-metric="deposit"]').attributes('aria-selected')).toBe('true');

    visibility('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.get('[data-ranking-metric="increase"]').attributes('aria-selected')).toBe('true');
  });

  it('滚轮只滚动列表，不被轮播逻辑抢回滚动位置，并保留200家机构', async () => {
    vi.useFakeTimers();
    const rows = Array.from({ length: 200 }, (_, index) => ({
      orgCode: `ORG-${index + 1}`,
      name: `机构${index + 1}`,
      value: index,
      rank: index + 1,
      state: 'RANKABLE'
    }));
    const largeModel = {
      activeMetricKey: 'deposit',
      metrics: [{
        metricKey: 'deposit', label: '存款余额', unit: '亿元',
        rankable: rows, missing: [], receivedCount: 200, expectedCount: 200,
        incomplete: false, summary: '已获得 200/200 家授权机构'
      }]
    };
    const wrapper = mountWidget({ model: largeModel });
    const list = wrapper.get('[data-testid="institution-ranking-list"]');
    list.element.scrollTop = 137;
    await list.trigger('wheel', { deltaY: 600 });

    expect(list.element.scrollTop).toBe(137);
    expect(wrapper.findAll('[data-testid="institution-ranking-row"]')).toHaveLength(200);
  });
});
