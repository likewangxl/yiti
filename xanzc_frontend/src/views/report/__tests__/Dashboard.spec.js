// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const { getDashboardPresidentMock } = vi.hoisted(() => ({
  getDashboardPresidentMock: vi.fn()
}));

vi.mock('@/api/report', () => ({ getDashboardPresident: getDashboardPresidentMock }));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}));
vi.mock('echarts/core', () => ({ use: vi.fn() }));
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }));
vi.mock('echarts/charts', () => ({ LineChart: {} }));
vi.mock('echarts/components', () => ({ AriaComponent: {}, GridComponent: {}, TooltipComponent: {}, LegendComponent: {} }));
vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    props: { option: { type: Object, default: () => ({}) }, autoresize: Boolean },
    template: '<div class="v-chart-stub" :data-autoresize="String(autoresize)" />'
  }
}));
vi.mock('html2canvas', () => ({ default: vi.fn() }));
vi.mock('jspdf', () => ({ jsPDF: vi.fn() }));

import Dashboard from '../Dashboard.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  PageTitle: { name: 'PageTitle', template: '<h1>行长报表仪表盘</h1>' },
  'el-button': {
    name: 'ElButton',
    props: ['loading', 'disabled'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-date-picker': {
    name: 'ElDatePicker',
    props: ['modelValue', 'disabledDate', 'disabled'],
    emits: ['change', 'update:modelValue'],
    template: '<div class="date-picker-stub" />'
  },
  'el-empty': {
    name: 'ElEmpty',
    props: ['description'],
    template: '<div class="el-empty">{{ description }}</div>'
  },
  'el-skeleton': passthrough('ElSkeleton'),
  'el-skeleton-item': { name: 'ElSkeletonItem', template: '<span class="skeleton-item" />' }
};

const stats = [
  { label: '存款日均', value: 1200, unit: '万', trend: '较月初 +2%', trendType: 'up' },
  { label: '贷款余额', value: 980, unit: '万', trend: '较月初 +1%', trendType: 'up' },
  { label: '不良贷款率', value: 1.2, unit: '%', trend: '较月初 -0.1%', trendType: 'down' },
  { label: '中间业务收入', value: 52, unit: '万', trend: '--', trendType: 'flat' },
  { label: '本月新增有效客户', value: 24, unit: '', trend: '较月初 +3%', trendType: 'up' }
];

function dashboardFixture(overrides = {}) {
  return {
    org: '西安分行',
    date: '2026-08-10',
    stats: stats.map(item => ({ ...item })),
    trend: {
      xAxis: ['2026-07', '2026-08'],
      series: [
        { name: '一般性存款月均', unit: '万元', data: [1100, 1200] },
        { name: '对公一般性贷款', unit: '万元', data: [900, 980] }
      ]
    },
    ranking: [{
      rank: 1,
      orgName: '城东支行',
      achievementRate: 108.6,
      target: 1000,
      actual: 1086,
      unit: '万元'
    }],
    ...overrides
  };
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function mountDashboard() {
  return mount(Dashboard, {
    global: {
      stubs,
      directives: { loading: { mounted: () => {}, updated: () => {} } }
    }
  });
}

let wrapper;

afterEach(() => wrapper?.unmount());

beforeEach(() => {
  getDashboardPresidentMock.mockReset();
});

describe('Dashboard.vue 行长报表仪表盘', () => {
  it('请求 pending 时维持 5 张 KPI 骨架并标记 aria-busy', async () => {
    const pending = deferred();
    getDashboardPresidentMock.mockReturnValue(pending.promise);

    wrapper = mountDashboard();
    await nextTick();

    expect(wrapper.attributes('aria-busy')).toBe('true');
    expect(wrapper.findAll('[data-testid="kpi-skeleton"]')).toHaveLength(5);
  });

  it('请求成功后渲染 5 项真实 KPI', async () => {
    getDashboardPresidentMock.mockResolvedValue(dashboardFixture());

    wrapper = mountDashboard();
    await settle();

    expect(wrapper.attributes('aria-busy')).toBe('false');
    expect(wrapper.findAll('[data-testid="kpi-card"]')).toHaveLength(5);
    expect(wrapper.get('[data-testid="kpi-card"]').text()).toContain('存款日均');
    expect(wrapper.find('[data-testid="dashboard-empty"]').exists()).toBe(false);
  });

  it('日期选择器禁用未来日期', async () => {
    getDashboardPresidentMock.mockResolvedValue(dashboardFixture());

    wrapper = mountDashboard();
    await settle();

    const picker = wrapper.findComponent({ name: 'ElDatePicker' });
    const disabledDate = picker.props('disabledDate');
    expect(disabledDate(new Date('2000-01-01'))).toBe(false);
    expect(disabledDate(new Date('2999-01-01'))).toBe(true);
  });

  it('快速切换日期时，后发请求的响应优先显示', async () => {
    const first = deferred();
    const second = deferred();
    getDashboardPresidentMock.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);

    wrapper = mountDashboard();
    await nextTick();
    await wrapper.findComponent({ name: 'ElDatePicker' }).vm.$emit('change', '2026-08-10');
    await nextTick();

    second.resolve(dashboardFixture({ org: '新日期数据', date: '2026-08-10' }));
    await settle();
    first.resolve(dashboardFixture({ org: '旧日期数据', date: '2026-08-09' }));
    await settle();

    expect(wrapper.text()).toContain('新日期数据');
    expect(wrapper.text()).not.toContain('旧日期数据');
  });

  it('全空数据展示全空态，而不是错误态', async () => {
    getDashboardPresidentMock.mockResolvedValue(dashboardFixture({
      stats: stats.map(item => ({ ...item, value: null })),
      trend: { xAxis: [], series: [] },
      ranking: []
    }));

    wrapper = mountDashboard();
    await settle();

    expect(wrapper.get('[data-testid="dashboard-empty"]').text()).toContain('暂无仪表盘数据');
    expect(wrapper.find('[data-testid="dashboard-error"]').exists()).toBe(false);
  });

  it('请求失败展示可重试错误态，重试后恢复报表', async () => {
    getDashboardPresidentMock
      .mockRejectedValueOnce(new Error('服务暂不可用'))
      .mockResolvedValueOnce(dashboardFixture());

    wrapper = mountDashboard();
    await settle();

    expect(wrapper.get('[data-testid="dashboard-error"]').text()).toContain('服务暂不可用');
    await wrapper.get('[data-testid="dashboard-retry"]').trigger('click');
    await settle();

    expect(getDashboardPresidentMock).toHaveBeenCalledTimes(2);
    expect(wrapper.findAll('[data-testid="kpi-card"]')).toHaveLength(5);
  });

  it('趋势和排名缺失时分别显示局部空态', async () => {
    getDashboardPresidentMock.mockResolvedValue(dashboardFixture({
      trend: { xAxis: [], series: [] },
      ranking: []
    }));

    wrapper = mountDashboard();
    await settle();

    expect(wrapper.find('[data-testid="dashboard-empty"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="trend-empty"]').text()).toContain('暂无趋势数据');
    expect(wrapper.get('[data-testid="ranking-empty"]').text()).toContain('暂无机构排名数据');
  });

  it('趋势图保留后台系列 name/unit，启用 ECharts aria 和 autoresize', async () => {
    getDashboardPresidentMock.mockResolvedValue(dashboardFixture());

    wrapper = mountDashboard();
    await settle();

    const chart = wrapper.findComponent({ name: 'VChart' });
    const option = chart.props('option');
    expect(chart.props('autoresize')).toBe(true);
    expect(option.aria.enabled).toBe(true);
    expect(option.series.map(item => item.name)).toEqual(['一般性存款月均', '对公一般性贷款']);
    expect(wrapper.get('[data-testid="trend-summary"]').text()).toContain('一般性存款月均（万元）');
  });

  it('排名以达成率为主值，目标和实际为辅助值且不臆造单位', async () => {
    getDashboardPresidentMock.mockResolvedValue(dashboardFixture({
      ranking: [{
        rank: 2,
        orgName: '城南支行',
        achievementRate: 91.2,
        target: 800,
        actual: 729.6,
        unit: '万元'
      }]
    }));

    wrapper = mountDashboard();
    await settle();

    const row = wrapper.get('[data-testid="rank-row"]');
    expect(row.get('[data-testid="achievement-rate"]').text()).toBe('91.2%');
    expect(row.get('[data-testid="rank-target"]').text()).toContain('800 万元');
    expect(row.get('[data-testid="rank-actual"]').text()).toContain('729.6 万元');
    expect(row.get('[data-testid="rank-actual"]').text()).not.toContain('%');
    expect(row.get('[data-testid="rank-status"]').text()).toBe('未达成');
    expect(row.get('[data-testid="rank-progress"]').attributes('style')).toContain('91.2%');
  });
});
