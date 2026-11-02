// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
vi.mock('../BranchThumbnailChart.vue', () => ({ default: { props: ['kind'], template: '<div class="branch-thumbnail-chart" :data-kind="kind" />' } }));
import { mount } from '@vue/test-utils';
import BranchConfigThumbnail from '../BranchConfigThumbnail.vue';
import RuntimeConfigThumbnail from '../RuntimeConfigThumbnail.vue';

const allEntries = [
  ['overview-deposit', '存款总额', 'DERIVED_TOTAL'], ['overview-deposit-composition', '存款业务分布', 'COMPOSITION_TAB'],
  ['overview-loan', '贷款总额', 'DERIVED_TOTAL'], ['overview-loan-composition', '贷款业务分布', 'COMPOSITION_TAB'], ['overview-settlementDeposit', '结算性存款', 'SYSTEM_METRIC'],
  ...['retail-deposit', 'retail-deposit-rate', 'retail-loan', 'retail-loan-rate'].map((key) => [`business-${key}`, key, key.endsWith('rate') ? 'COMPLETION' : 'DISPLAY_COMPONENT']),
  ...['corp-deposit', 'corp-deposit-rate', 'corp-loan', 'corp-loan-rate'].map((key) => [`business-${key}`, key, key.endsWith('rate') ? 'COMPLETION' : 'DISPLAY_COMPONENT']),
  ['business-revenue-operating', '营业收入', 'DISPLAY_COMPONENT'], ['business-revenue-fee', '中间业务收入', 'DISPLAY_COMPONENT'],
  ['business-growth', '业务增长曲线', 'BUSINESS_GROWTH'], ['institution-map', '经营机构分布', 'INSTITUTION_MAP'], ['institution-ranking', '机构排名', 'INSTITUTION_RANKING']
].map(([key, label, kind]) => ({ key, label, kind, sourceComponentId: key }));

const entries = [
  { key: 'overview-deposit', label: '存款总额', kind: 'DERIVED_TOTAL' },
  { key: 'business-retail-deposit-balance', label: '存款余额', kind: 'DISPLAY_COMPONENT', sourceComponentId: 'business-retail-deposit-balance' },
  { key: 'business-revenue-operating', label: '营业收入', kind: 'DISPLAY_COMPONENT' },
  { key: 'business-revenue-fee', label: '中间业务收入', kind: 'DISPLAY_COMPONENT' },
  { key: 'institution-map', label: '经营机构分布', kind: 'INSTITUTION_MAP' },
  { key: 'institution-ranking', label: '机构排名', kind: 'INSTITUTION_RANKING' }
];

describe('BranchConfigThumbnail', () => {
  it('运行画面沿用真实组件，底部选中名称自动同步目录标题', async () => {
    const key = 'business-retail-deposit-rate';
    const wrapper = mount(BranchConfigThumbnail, { props: { entries: [...entries, { key, label: '存款完成率', kind: 'DISPLAY_COMPONENT' }], selectedKey: key } });
    expect(wrapper.getComponent(RuntimeConfigThumbnail).props('entries').find(item=>item.key===key).label).toBe('存款完成率');
    expect(wrapper.get('footer').text()).toContain('当前选中：存款完成率');
    await wrapper.setProps({ entries: [{ key, label: '年度存款完成率', kind: 'DISPLAY_COMPONENT' }] });
    expect(wrapper.get('footer').text()).toContain('年度存款完成率');
    wrapper.unmount();
  });
  it('展示真实目录标题、占位缩略图，并通过键盘/点击选择', async () => {
    const wrapper = mount(BranchConfigThumbnail, {
      props: { entries, selectedKey: 'overview-deposit', screenTitle: '陕西省分行经营总览', selectedLabel: '经营机构分布', footerText: '标题实时展示 · 数据仅占位', disabled: false }
    });
    expect(wrapper.find('[data-testid="branch-config-thumbnail"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-config-key]').length).toBe(entries.length);
    expect(wrapper.text()).toContain('存款总额');
    expect(wrapper.text()).toContain('机构排名');
    expect(wrapper.getComponent(RuntimeConfigThumbnail).props('screenTitle')).toBe('陕西省分行经营总览');
    expect(wrapper.text()).toContain('当前选中：经营机构分布');
    expect(wrapper.getComponent(RuntimeConfigThumbnail).props('template')).toBe('branch-overview-v1');
    expect(wrapper.get('[data-config-key="institution-ranking"] [data-full-title]').attributes('data-full-title')).toBe('机构排名');
    await wrapper.get('[data-config-key="institution-map"]').trigger('click');
    expect(wrapper.emitted('select')?.[0]?.[0].key).toBe('institution-map');
    await wrapper.get('[data-config-key="institution-ranking"]').trigger('keydown.enter');
    expect(wrapper.emitted('select')?.[1]?.[0].key).toBe('institution-ranking');
  });

  it('只读/写入时禁用选择并提供已选中状态', () => {
    const wrapper = mount(BranchConfigThumbnail, {
      props: { entries, selectedKey: 'institution-ranking', disabled: true }
    });
    const selected = wrapper.get('[data-config-key="institution-ranking"]');
    expect(selected.attributes('aria-pressed')).toBe('true');
    expect(selected.attributes('disabled')).toBeDefined();
    expect(wrapper.findAll('.is-active')).toHaveLength(1);
  });

  it('18项真实目录全部落在独立交互区域，且每项都有完整标题与占位层', () => {
    const wrapper = mount(BranchConfigThumbnail, { props: { entries: allEntries, selectedKey: 'institution-ranking' } });
    const tiles = wrapper.findAll('[data-config-key]');
    expect(tiles).toHaveLength(18);
    expect(new Set(tiles.map(tile => tile.attributes('data-config-key'))).size).toBe(18);
    expect(wrapper.findAll('[data-full-title]').length).toBe(18);
    expect(tiles.every(tile => tile.element.tagName === 'BUTTON')).toBe(true);
    expect(wrapper.text()).toContain('数据仅占位');
  });
});
