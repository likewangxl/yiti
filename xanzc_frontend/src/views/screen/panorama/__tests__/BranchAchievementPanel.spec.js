/* @vitest-environment happy-dom */
import { describe, expect, it } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import BranchAchievementPanel from '../BranchAchievementPanel.vue';

function target(index, overrides = {}) {
  return {
    key: `metric-${index}`,
    label: `指标 ${index}`,
    actual: 40 + index,
    target: 100,
    unit: '万元',
    date: '2026-09-20',
    owner: index % 2 ? '零售组' : '公司组',
    category: index % 2 ? '零售' : '公司',
    ...overrides
  };
}

function mountPanel(targets, props = {}) {
  return mount(BranchAchievementPanel, {
    props: { targets, ...props }
  });
}

describe('BranchAchievementPanel', () => {
  it('默认展示未完成指标，并分页覆盖全部 28 条而不丢失', async () => {
    const targets = Array.from({ length: 28 }, (_, index) => target(index + 1));
    const wrapper = mountPanel(targets);
    const seen = new Set();

    expect(wrapper.get('[data-testid="branch-achievement-summary"]').text()).toContain('28');
    expect(wrapper.findAll('[data-testid="branch-achievement-row"]')).toHaveLength(10);

    for (;;) {
      wrapper.findAll('[data-testid="branch-achievement-row"]')
        .forEach(row => seen.add(row.attributes('data-item-id')));
      const next = wrapper.get('[data-testid="branch-achievement-next"]');
      if (next.attributes('disabled') !== undefined) break;
      await next.trigger('click');
    }

    expect(seen.size).toBe(28);
    expect(wrapper.get('[data-testid="branch-achievement-page"]').text()).toContain('3 / 3');
  });

  it('支持名称或负责人搜索及真实分类筛选，并在筛选后回到第一页', async () => {
    const targets = [
      target(1, { label: '对公贷款投放', owner: '张三', category: '资产负债' }),
      target(2, { label: '零售客户增长', owner: '李四', category: '客户经营' }),
      target(3, { label: '中收计划', owner: '张三', category: '收入' })
    ];
    const wrapper = mountPanel(targets);
    await wrapper.get('[data-testid="branch-achievement-status"]').setValue('all');
    await wrapper.get('[data-testid="branch-achievement-search"]').setValue('张三');
    expect(wrapper.findAll('[data-testid="branch-achievement-row"]')).toHaveLength(2);
    expect(wrapper.text()).toContain('对公贷款投放');
    expect(wrapper.text()).toContain('中收计划');

    await wrapper.get('[data-testid="branch-achievement-category"]').setValue('收入');
    expect(wrapper.findAll('[data-testid="branch-achievement-row"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="branch-achievement-row"]').text()).toContain('中收计划');
    expect(wrapper.get('[data-testid="branch-achievement-page"]').text()).toContain('1 / 1');

    await wrapper.get('[data-testid="branch-achievement-search"]').setValue('');
    await wrapper.get('[data-testid="branch-achievement-category"]').setValue('');
    await wrapper.get('[data-testid="branch-achievement-sort"]').setValue('name');
    expect(wrapper.findAll('[data-testid="branch-achievement-row"]')[0].text()).toContain('对公贷款投放');
  });

  it('区分全部、缺失和已完成，达标率只计算有有效目标实际的指标', async () => {
    const wrapper = mountPanel([
      target(1, { label: '超额指标', actual: 120, target: 100 }),
      target(2, { label: '缺失实际', actual: null, target: 100 }),
      target(3, { label: '未完成指标', actual: 20, target: 100 })
    ]);

    expect(wrapper.get('[data-testid="branch-achievement-summary"]').text()).toContain('达标率');
    expect(wrapper.get('[data-testid="branch-achievement-summary"]').text()).toContain('50%');
    expect(wrapper.get('[data-testid="branch-achievement-status"]').element.value).toBe('incomplete');
    expect(wrapper.findAll('[data-testid="branch-achievement-row"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="branch-achievement-row"]').text()).toContain('未完成指标');

    await wrapper.get('[data-testid="branch-achievement-status"]').setValue('all');
    expect(wrapper.findAll('[data-testid="branch-achievement-row"]')).toHaveLength(3);
    expect(wrapper.text()).toContain('120%');
    expect(wrapper.text()).toContain('数据不足');
    expect(wrapper.get('[data-item-id^="metric-2-"] td:nth-child(3)').text()).toContain('—');
    expect(wrapper.get('[data-item-id^="metric-2-"] td:nth-child(3)').text()).not.toContain('0');

    await wrapper.get('[data-testid="branch-achievement-status"]').setValue('missing');
    expect(wrapper.findAll('[data-testid="branch-achievement-row"]')).toHaveLength(1);
    await wrapper.get('[data-testid="branch-achievement-status"]').setValue('completed');
    expect(wrapper.findAll('[data-testid="branch-achievement-row"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="branch-achievement-row"]').text()).toContain('超额指标');

    const completeWithMissing = mountPanel([
      target(11, { actual: 120, target: 100 }),
      target(12, { actual: null, target: 100 })
    ]);
    expect(completeWithMissing.text()).toContain('暂无已提供的未完成指标');
    expect(completeWithMissing.text()).toContain('仍有 1 项指标数据不足');
  });

  it('切换机构 targets 后清理旧筛选结果并重置页码', async () => {
    const wrapper = mountPanel(Array.from({ length: 12 }, (_, index) => target(index + 1)));
    await wrapper.get('[data-testid="branch-achievement-next"]').trigger('click');
    expect(wrapper.get('[data-testid="branch-achievement-page"]').text()).toContain('2 / 2');

    await wrapper.setProps({ targets: [target(101, { label: '新机构指标', owner: '新负责人', actual: 40 })] });
    await flushPromises();

    expect(wrapper.get('[data-testid="branch-achievement-page"]').text()).toContain('1 / 1');
    expect(wrapper.get('[data-testid="branch-achievement-row"]').text()).toContain('新机构指标');
  });

  it('按扣基期口径展示自定义实际表头，并补充原值与基础值说明', () => {
    const wrapper = mountPanel([
      target(20, { label: '机构KPI净值', actual: 60, target: 100, sourceActual: 80, base: 20 })
    ], { actualLabel: '考核实际（扣基期）' });
    const row = wrapper.get('[data-testid="branch-achievement-row"]');

    expect(wrapper.get('thead th:nth-child(3)').text()).toBe('考核实际（扣基期）');
    expect(row.get('td:nth-child(3)').text()).toContain('60');
    expect(row.get('td:nth-child(3) small.branch-achievement__source-note').text()).toBe('原值 80 · 基础值 20');
  });
});
