// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
vi.mock('../BranchThumbnailChart.vue', () => ({ default: { props: ['kind'], template: '<div class="branch-thumbnail-chart" :data-kind="kind" />' } }));
import { mount } from '@vue/test-utils';
import BranchConfigThumbnail from '../BranchConfigThumbnail.vue';

const entries = [
  { key: 'overview-deposit', label: '存款总额', kind: 'DERIVED_TOTAL' },
  { key: 'business-retail-deposit-balance', label: '存款余额', kind: 'DISPLAY_COMPONENT', sourceComponentId: 'business-retail-deposit-balance' },
  { key: 'business-revenue-operating', label: '营业收入', kind: 'DISPLAY_COMPONENT' },
  { key: 'business-revenue-fee', label: '中间业务收入', kind: 'DISPLAY_COMPONENT' },
  { key: 'institution-map', label: '经营机构分布', kind: 'INSTITUTION_MAP' },
  { key: 'institution-ranking', label: '机构排名', kind: 'INSTITUTION_RANKING' }
];

describe('BranchConfigThumbnail', () => {
  it('完成率采用环图，底部选中名称自动同步目录标题', async () => {
    const key = 'business-retail-deposit-rate';
    const wrapper = mount(BranchConfigThumbnail, { props: { entries: [...entries, { key, label: '存款完成率', kind: 'DISPLAY_COMPONENT' }], selectedKey: key } });
    expect(wrapper.get(`[data-config-key="${key}"] .branch-thumbnail-chart`).attributes('data-kind')).toBe('COMPLETION');
    expect(wrapper.get('.branch-config-thumbnail__caption').text()).toContain('当前选中：存款完成率');
    await wrapper.setProps({ entries: [{ key, label: '年度存款完成率', kind: 'DISPLAY_COMPONENT' }] });
    expect(wrapper.get('.branch-config-thumbnail__caption').text()).toContain('年度存款完成率');
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
    expect(wrapper.text()).toContain('陕西省分行经营总览');
    expect(wrapper.text()).toContain('当前选中：经营机构分布');
    expect(wrapper.find('.branch-config-thumbnail__business').attributes('data-layout')).toBe('2-2-1');
    expect(wrapper.find('.branch-config-thumbnail__group--revenue').attributes('data-columns')).toBe('1');
    expect(wrapper.get('[data-config-key="institution-ranking"] .branch-config-thumbnail__component-title').attributes('data-full-title')).toBe('机构排名');
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
});
