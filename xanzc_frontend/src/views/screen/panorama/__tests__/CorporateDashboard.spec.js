// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    props: ['geoJson', 'points', 'demo', 'mode', 'selectedRegionCode'],
    template: '<div data-testid="corporate-map" :data-mode="mode" />'
  }
}));

vi.mock('../CorporateTrend.vue', () => ({
  default: { props: ['trend', 'dataDate', 'scopeLabel'], template: '<div data-testid="corporate-trend" />' }
}));

describe('CorporateDashboard 对公经营总览', () => {
  it('以对公业务语义呈现 KPI、重点客群信贷、经营关注、排名和目标', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        demo: true,
        model: {
          title: '对公经营总览', scopeLabel: '省分行授权范围', dataDate: '2026-09-08',
          kpis: [
            { key: 'corpDeposit', value: 1200, unit: '亿元', change: 2.5 },
            { key: 'corpDepositAverage', value: 1180, unit: '亿元', change: null },
            { key: 'corpLoan', value: 980, unit: '亿元', change: -1 },
            { key: 'corpRevenue', value: 24.5, unit: '亿元', change: 4 },
            { key: 'corpCustomers', value: 8.6, unit: '万户', change: null },
            { key: 'corpNplRate', value: 1.2, unit: '%', change: -0.1 }
          ],
          trend: [{ date: '2026-09', deposit: 1200, loan: 980 }],
          segments: [{ name: '制造业', customers: 2, loan: 310 }],
          rankings: [{ orgCode: 'A', name: '西安分行', deposit: 420, increase: -3, rate: 88, nplRate: 1.1, cityCode: null }],
          attention: [{ label: '重点客户授信逾期', count: 0, owner: '公司部', deadline: '2026-09-30' }],
          targets: [{ name: '对公存款', actual: 1200, target: 1300 }],
          institutions: []
        }
      }
    });
    expect(wrapper.attributes('aria-label')).toBe('对公经营总览');
    expect(wrapper.findAll('[data-testid="corporate-kpi"]')).toHaveLength(6);
    expect(wrapper.text()).toContain('对公存款经营');
    expect(wrapper.text()).toContain('重点客群信贷');
    expect(wrapper.text()).toContain('需要协调的事项');
    expect(wrapper.text()).toContain('对公存款排名');
    expect(wrapper.text()).toContain('对公经营目标');
    expect(wrapper.find('[data-testid="corporate-demo-badge"]').text()).toContain('非业务数据');
    expect(wrapper.find('[data-testid="corporate-ranking-row"]').text()).toContain('西安分行');
  });

  it('排名可切换存款、净增和完成率，负数仍可排序', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      props: {
        model: {
          rankings: [
            { orgCode: 'A', name: '甲', deposit: 100, increase: -10, rate: 80 },
            { orgCode: 'B', name: '乙', deposit: 80, increase: 5, rate: 70 }
          ]
        }
      }
    });
    await wrapper.find('[data-ranking-metric="increase"]').trigger('click');
    expect(wrapper.findAll('[data-testid="corporate-ranking-row"]')[0].text()).toContain('乙');
    await wrapper.find('[data-ranking-order="lagging"]').trigger('click');
    expect(wrapper.findAll('[data-testid="corporate-ranking-row"]')[0].text()).toContain('甲');
  });

  it('事项详情和机构目录使用原生键盘焦点、Escape 与背景滚动锁', async () => {
    const { default: CorporateDashboard } = await import('../CorporateDashboard.vue');
    const wrapper = mount(CorporateDashboard, {
      attachTo: document.body,
      props: {
        demo: true,
        model: {
          scopeLabel: '全辖（示例）', dataDate: '2026-09-08',
          attention: [{ label: '重点项目', count: 0, owner: null, deadline: null }],
          institutions: [{ orgCode: 'A', name: '甲支行', cityCode: '610100', cityName: '西安市', located: false }],
          rankings: [{ orgCode: 'A', name: '甲支行', deposit: 1, increase: 0, rate: 80, nplRate: null }]
        }
      }
    });
    const attentionRow = wrapper.get('[data-testid="corporate-attention-row"]');
    await attentionRow.trigger('click');
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="corporate-attention-detail"]')).toBeTruthy();
    expect(document.body.style.overflow).toBe('hidden');
    const attentionDialog = wrapper.get('[data-testid="corporate-attention-detail"] .corporate-attention-dialog');
    await attentionDialog.trigger('keydown', { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(wrapper.findAll('[data-action="close-corporate-attention"]').at(-1).element);
    await wrapper.get('[data-action="close-corporate-attention"]').trigger('click');
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="corporate-attention-detail"]').exists()).toBe(false);
    expect(document.body.style.overflow).toBe('');

    await wrapper.get('[data-action="open-corporate-directory"]').trigger('click');
    await wrapper.vm.$nextTick();
    expect(document.activeElement).toBe(wrapper.get('[data-testid="corporate-directory-search"]').element);
    await wrapper.get('[data-testid="corporate-directory-dialog"]').trigger('keydown', { key: 'Escape' });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="corporate-directory-dialog"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
