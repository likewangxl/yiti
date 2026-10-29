// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import OperatingConfigThumbnail from '../OperatingConfigThumbnail.vue';

const entries = ['corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate', 'corpTrend', 'corpSegments', 'corpRanking', 'corpAttention', 'corpTargets', 'branches']
  .map(key => ({ key, label: key, kind: 'LEGACY_COMPONENT' }));

describe('OperatingConfigThumbnail', () => {
  it('按旧对公真实布局渲染所有目录，点击共享槽发出原始 entry', async () => {
    const wrapper = mount(OperatingConfigThumbnail, { props: { entries, template: 'corporate-overview-v1', screenTitle: '对公经营总览', selectedKey: 'corpRanking' } });
    expect(wrapper.find('[data-testid="operating-config-thumbnail"]').exists()).toBe(true);
    expect(wrapper.find('[data-region="top"]').exists()).toBe(true);
    expect(wrapper.find('[data-region="left"]').exists()).toBe(true);
    expect(wrapper.find('[data-region="center"]').exists()).toBe(true);
    expect(wrapper.find('[data-region="right"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-config-key="corpDeposit"]')).toHaveLength(1);
    expect(wrapper.get('[data-config-key="branches"]').classes()).toContain('is-analysis');
    expect(wrapper.text()).toContain('对公经营总览');
    expect(wrapper.findAll('[data-config-key]').length).toBeGreaterThanOrEqual(entries.length);
    expect(wrapper.find('[data-linked-config-key="corpDeposit"]').exists()).toBe(true);
    await wrapper.get('[data-config-key="corpRanking"]').trigger('click');
    expect(wrapper.emitted('select')?.at(-1)?.[0].key).toBe('corpRanking');
  });
});
