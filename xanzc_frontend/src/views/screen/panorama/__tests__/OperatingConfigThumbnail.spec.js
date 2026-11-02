// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import OperatingConfigThumbnail from '../OperatingConfigThumbnail.vue';
import RuntimeConfigThumbnail from '../RuntimeConfigThumbnail.vue';

const entries = ['corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate', 'corpTrend', 'corpSegments', 'corpRanking', 'corpAttention', 'corpTargets', 'branches']
  .map(key => ({ key, label: key, kind: 'LEGACY_COMPONENT' }));

describe('OperatingConfigThumbnail', () => {
  it('按旧对公真实布局渲染所有目录，点击共享槽发出原始 entry', async () => {
    const wrapper = mount(OperatingConfigThumbnail, { props: { entries, template: 'corporate-overview-v1', screenTitle: '对公经营总览', selectedKey: 'corpRanking' } });
    expect(wrapper.find('[data-testid="operating-config-thumbnail"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-config-key="corpDeposit"]')).toHaveLength(1);
    expect(wrapper.getComponent(RuntimeConfigThumbnail).props('template')).toBe('corporate-overview-v1');
    expect(wrapper.getComponent(RuntimeConfigThumbnail).props('screenTitle')).toBe('对公经营总览');
    expect(wrapper.findAll('[data-config-key]').length).toBeGreaterThanOrEqual(entries.length);
    expect(wrapper.getComponent(RuntimeConfigThumbnail).props('entries')).toEqual(entries);
    await wrapper.get('[data-config-key="corpRanking"]').trigger('click');
    expect(wrapper.emitted('select')?.at(-1)?.[0].key).toBe('corpRanking');
    wrapper.unmount();
  });
});
