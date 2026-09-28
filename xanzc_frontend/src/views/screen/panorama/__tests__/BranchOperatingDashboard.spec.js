// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, default: () => ({}) } },
    template: '<div data-testid="branch-operating-chart" :data-option="JSON.stringify(option)" />'
  }
}));

import BranchOperatingDashboard from '../BranchOperatingDashboard.vue';

const model = {
  orgCode: 'BR-001',
  orgName: '高新支行',
  dataDate: '2026-09-20',
  sourceLabel: '经营分析平台',
  institutions: [
    { orgCode: 'BR-001', orgName: '高新支行' },
    { orgCode: 'BR-002', orgName: '城南支行' }
  ],
  kpis: [
    { key: 'deposit', label: '存款余额', value: 128.6, unit: '亿元', yoy: 6.2, mom: -0.4, status: '稳中有升' },
    { key: 'loan', label: '贷款余额', value: 86.2, unit: '亿元', yoy: 4.8, mom: 1.1, status: '投放平稳' },
    { key: 'customer', label: '客户总量', value: 18420, unit: '户', yoy: null, mom: null, status: '' },
    { key: 'profit', label: '营业利润', value: 3.08, unit: '亿元', yoy: 8.3, mom: 2.2, status: '增长' },
    { key: 'marketing', label: '营销转化', value: 72, unit: '%', yoy: -1.4, mom: 0, status: '需关注' },
    { key: 'quality', label: '资产质量', value: 0.92, unit: '%', yoy: null, mom: null, status: '优良' }
  ],
  targets: [
    { key: 'deposit', label: '存款净增', actual: 6.2, target: 10, unit: '亿元', rate: 62, gap: -3.8 },
    { key: 'loan', label: '贷款投放', actual: -1.2, target: 8, unit: '亿元', rate: -15, gap: 9.2 }
  ],
  trend: [
    { date: '2026-07', deposit: 124, loan: 82 },
    { date: '2026-08', deposit: Number.NaN, loan: 84 },
    { date: '2026-09', deposit: 128.6, loan: Infinity }
  ],
  composition: [
    { name: '公司客户', value: 58, unit: '%' },
    { name: '个人客户', value: 42, unit: '%' }
  ],
  marketing: [{ label: '本月触达', count: 86 }, { label: '有效转化', count: 24 }],
  projects: [{ name: '园区综合授信', status: '推进中', amount: 12.5, unit: '亿元', owner: '公司金融部', days: 9 }],
  attention: [{ label: '押品资料待补', count: 3 }],
  teams: [{ name: '公司金融部', rate: 86, increase: 4.2, pending: 2 }],
  gaps: { composition: '', marketing: '', projects: '', teams: '' },
  metricLabels: { deposit: '存款余额', loan: '贷款余额' },
  sources: [{ label: '经营分析平台', detail: '支行经营日报' }]
};

function chartOption(wrapper) {
  const chart = wrapper.find('[data-testid="branch-operating-chart"]');
  return chart.exists() ? JSON.parse(chart.attributes('data-option')) : null;
}

describe('BranchOperatingDashboard 单支行经营大屏', () => {
  it('新版主区包含六项经营指标、完成情况与个人KPI排名，保留经营明细折叠入口', () => {
    const wrapper = mount(BranchOperatingDashboard, { props: { model } });
    expect(wrapper.findAll('[data-testid="branch-core-metric"]')).toHaveLength(6);
    expect(wrapper.get('[data-testid="branch-achievement-panel"]').text()).toContain('未完成');
    expect(wrapper.get('[data-testid="branch-performance-panel"]').text()).toContain('个人排名');
    expect(wrapper.find('details[data-testid="branch-operating-details"]').exists()).toBe(true);
    wrapper.unmount();
  });
  it('单目标不重复展示明细，单点余额明确不能判断趋势', () => {
    const wrapper = mount(BranchOperatingDashboard, { props: { model: {
      targets: [{ key: 'deposit', label: '存款目标', actual: 0, target: 500, unit: '万元' }],
      trend: [{ date: '2026-06-30', deposit: null }, { date: '2026-07-22', deposit: 120.8 }]
    } } });
    expect(wrapper.findAll('.branch-operating-target-row')).toHaveLength(0);
    expect(wrapper.text()).toContain('仅有1期余额');
    wrapper.unmount();
  });
  it('支行金额按万元标注图轴，展示独立考核日期，任务数保留整数', () => {
    const wrapper = mount(BranchOperatingDashboard, { props: { model: {
      trendUnit: '万元', trend: [{ date: '2026-07-22', deposit: 120.7989, loan: null }],
      targetDate: '2026-09-18', targets: [{ key: 'deposit', label: '存款目标', actual: 0, target: 500, gap: 500, unit: '万元' }],
      kpis: [{ key: 'touchTotal', label: '触达任务', value: 2, unit: '项' }]
    } } });
    expect(chartOption(wrapper).yAxis.name).toBe('万元');
    expect(wrapper.text()).toContain('考核截至 2026-09-18');
    expect(wrapper.get('.branch-operating-kpi__value-line strong').text()).toBe('2');
    wrapper.unmount();
  });
  it('四种任务状态全为零时不绘制均分环图，预警不产生扇区', () => {
    const wrapper = mount(BranchOperatingDashboard, { props: { model: { marketing: [
      { label: '待触达', count: 0 }, { label: '进行中', count: 0 }, { label: '已完成', count: 0 },
      { label: '已取消', count: 0 }, { label: 'SLA预警', count: 4 }
    ] } } });
    expect(wrapper.find('[data-testid="branch-operating-marketing-chart"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="branch-operating-marketing-bars"]').text()).toContain('4');
    wrapper.unmount();
  });
  let wrappers = [];

  afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount());
  });

  function mountDashboard(overrides = {}) {
    const wrapper = mount(BranchOperatingDashboard, {
      props: { model, loading: false, error: '', ...overrides },
      attachTo: document.body
    });
    wrappers.push(wrapper);
    return wrapper;
  }

  it('展示顶部支行上下文、六项 KPI 和三列经营区域，不包含地图', () => {
    const wrapper = mountDashboard();

    expect(wrapper.get('[data-testid="branch-operating-title"]').text()).toContain('高新支行');
    expect(wrapper.get('[data-testid="branch-operating-date"]').text()).toContain('2026-09-20');
    expect(wrapper.findAll('[data-testid="branch-operating-kpi"]')).toHaveLength(6);
    expect(wrapper.get('[data-kpi-key="deposit"]').text()).toContain('128.60');
    expect(wrapper.get('[data-kpi-key="marketing"]').text()).toContain('72');
    expect(wrapper.get('[data-kpi-key="deposit"]').text()).toContain('数据日期 2026-09-20');
    expect(wrapper.find('[data-testid="branch-operating-map"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="branch-operating-composition"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="branch-operating-targets"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="branch-operating-targets"] .branch-operating-target-body').exists()).toBe(true);
    expect(wrapper.get('[data-testid="branch-operating-targets"] .branch-operating-target-focus-column').exists()).toBe(true);
    expect(wrapper.get('[data-testid="branch-operating-targets"] .branch-operating-target-list').exists()).toBe(true);
    expect(wrapper.find('[data-testid="branch-operating-projects"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="branch-operating-project-table"]').text()).toContain('园区综合授信');
    expect(wrapper.find('[data-testid="branch-operating-teams"]').exists()).toBe(true);
  });

  it('刷新、返回和选择支行产生明确事件，全屏按钮可用', async () => {
    const wrapper = mountDashboard();

    await wrapper.get('[data-action="refresh"]').trigger('click');
    await wrapper.get('[data-action="back"]').trigger('click');
    const select = wrapper.get('[data-testid="branch-operating-branch-select"]');
    await select.setValue('BR-002');

    expect(wrapper.emitted('refresh')).toHaveLength(1);
    expect(wrapper.emitted('back')).toHaveLength(1);
    expect(wrapper.emitted('branch-select')).toEqual([['BR-002']]);
    expect(wrapper.get('[data-action="fullscreen"]').attributes('aria-label')).toContain('全屏');
  });

  it('同比环比缺失显示历史数据不足，空值不被渲染为 0，状态说明保留', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      kpis: [
        { key: 'deposit', label: '存款余额', value: null, unit: '亿元', yoy: null, mom: undefined },
        { key: 'loan', label: '贷款余额', value: 'not-a-number', unit: '亿元', yoy: NaN, mom: Infinity, status: null }
      ]
    } });

    expect(wrapper.get('[data-kpi-key="deposit"]').text()).toContain('—');
    expect(wrapper.get('[data-kpi-key="deposit"]').text()).not.toMatch(/\b0(?:\.00)?\b/);
    expect(wrapper.get('[data-kpi-key="deposit"]').text()).toContain('历史数据不足');
    expect(wrapper.get('[data-kpi-key="loan"]').text()).toContain('历史数据不足');
    expect(wrapper.get('[data-kpi-key="loan"]').text()).toContain('状态未提供');
  });

  it('趋势只绘制有限值，存款贷款切换时目标高亮同步变化', async () => {
    const wrapper = mountDashboard();
    const firstOption = chartOption(wrapper);

    expect(firstOption.series.map(item => item.name)).toEqual(['存款余额', '贷款余额']);
    expect(firstOption.series[0].data).toEqual([124, null, 128.6]);
    expect(firstOption.series[1].data).toEqual([82, 84, null]);
    expect(firstOption.yAxis.name).toBe('亿元');
    expect(firstOption.grid.top).toBe(28);
    expect(wrapper.get('[data-target-key="deposit"]').classes()).toContain('is-active');
    expect(wrapper.get('[data-target-key="loan"]').classes()).not.toContain('is-active');

    await wrapper.get('[data-trend-key="loan"]').trigger('click');
    const loanOption = chartOption(wrapper);
    expect(loanOption.series.map(item => item.name)).toEqual(['贷款余额']);
    expect(wrapper.get('[data-target-key="loan"]').classes()).toContain('is-active');
    expect(wrapper.get('[data-target-key="deposit"]').classes()).not.toContain('is-active');
  });

  it('真实零和负数保持原值，非有限目标金额显示缺失状态', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      trend: [{ date: '2026-09', deposit: 0, loan: -4.2 }],
      targets: [
        { key: 'deposit', label: '存款净增', actual: 0, target: 0, unit: '亿元', rate: 0, gap: 0 },
        { key: 'loan', label: '贷款投放', actual: -4.2, target: Infinity, unit: '亿元', rate: -12, gap: null }
      ]
    } });

    expect(chartOption(wrapper).series[0].data).toEqual([0]);
    expect(chartOption(wrapper).series[1].data).toEqual([-4.2]);
    expect(wrapper.get('[data-target-key="deposit"]').text()).toContain('0.00');
    expect(wrapper.get('[data-target-key="loan"]').text()).toContain('目标数据不足');
    expect(wrapper.get('[data-target-key="loan"]').text()).toContain('-4.20');
  });

  it('贷款目标未接入时只切换趋势并给出匹配缺失说明，不伪造贷款目标', async () => {
    const wrapper = mountDashboard({ model: { ...model, targets: [model.targets[0]] } });

    await wrapper.get('[data-trend-key="loan"]').trigger('click');
    expect(wrapper.get('[data-testid="branch-operating-target-match-empty"]').text()).toContain('暂无贷款目标');
    expect(wrapper.find('[data-target-key="loan"]').exists()).toBe(false);
    expect(chartOption(wrapper).series.map(item => item.name)).toEqual(['贷款余额']);
  });

  it('各业务区按来源数据展示，完全缺失时使用简洁空态，不填充虚构项目', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      composition: [],
      marketing: [],
      projects: [],
      attention: [],
      teams: []
    } });

    expect(wrapper.get('[data-testid="branch-operating-composition-empty"]').text()).toContain('暂无业务结构数据');
    expect(wrapper.get('[data-testid="branch-operating-marketing-empty"]').text()).toContain('暂无客户营销数据');
    expect(wrapper.get('[data-testid="branch-operating-projects-empty"]').text()).toContain('暂无重点项目数据');
    expect(wrapper.get('[data-testid="branch-operating-attention-empty"]').text()).toContain('经营关注数据未接入/暂不可用');
    expect(wrapper.get('[data-testid="branch-operating-teams-empty"]').text()).toContain('暂无团队贡献数据');
    expect(wrapper.findAll('[data-testid="branch-operating-project"]')).toHaveLength(0);
    expect(wrapper.text()).not.toContain('示例项目');
  });

  it('新经营驾驶舱使用多样化视觉区块，并把余额趋势与目标口径分开', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      kpis: [
        { ...model.kpis[0], key: 'corpDeposit', label: '对公存款余额' },
        ...model.kpis.slice(1),
        { key: 'corpDepositAverage', label: '对公存款月均', value: 126.4, unit: '亿元' }
      ],
      marketing: [
        { key: 'PENDING', label: '待处理', count: 8 },
        { key: 'IN_PROGRESS', label: '进行中', count: 5 },
        { key: 'SUCCESS', label: '已完成', count: 13 },
        { key: 'CANCELLED', label: '已取消', count: 2 },
        { key: 'SLA_WARNING', label: 'SLA预警', count: 1 }
      ],
      projects: [],
      teams: []
    } });

    expect(wrapper.get('[data-testid="branch-operating-target-focus"]').classes()).toContain('branch-operating-target-focus');
    expect(wrapper.get('[data-testid="branch-operating-target-ring"]').attributes('aria-label')).toContain('完成率');
    expect(wrapper.get('[data-testid="branch-operating-period-compare"]').text()).toContain('月均');
    expect(wrapper.get('[data-testid="branch-operating-marketing-chart"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="branch-operating-marketing-bars"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="branch-operating-projects-empty"]').classes()).toContain('branch-operating-empty--compact');
    expect(wrapper.get('[data-testid="branch-operating-teams-empty"]').classes()).toContain('branch-operating-empty--compact');

    const options = wrapper.findAll('[data-testid="branch-operating-chart"]')
      .map(chart => JSON.parse(chart.attributes('data-option')));
    expect(options.some(option => option.series?.some(series => series.type === 'bar'))).toBe(true);
    expect(options.some(option => option.series?.some(series => series.type === 'pie'))).toBe(true);
  });

  it('业务构成有来源数据时优先占据右上区块，月均对照作为辅助信息保留', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      kpis: [
        ...model.kpis,
        { key: 'corpDeposit', label: '对公存款余额', value: 58, unit: '万元', dataDate: '2026-09-20' },
        { key: 'corpDepositAverage', label: '对公存款月均', value: 55, unit: '万元', dataDate: '2026-09-20' }
      ],
      composition: [
        { name: '对公存款', value: 58, unit: '万元' },
        { name: '零售存款', value: 42, unit: '万元' }
      ]
    } });

    expect(wrapper.get('[data-testid="branch-operating-composition-title"]').text()).toBe('业务构成');
    expect(wrapper.findAll('.branch-operating-composition-row')).toHaveLength(2);
    expect(wrapper.get('[data-testid="branch-operating-composition"]').text()).toContain('对公存款');
    expect(wrapper.get('[data-testid="branch-operating-period-compare"]').text()).toContain('月均');
  });

  it('标准存款时点与月均成对且同口径时展示存款对照，禁止混配单位或日期', () => {
    const pairKpis = (average) => [
      { key: 'deposit', label: '存款余额', value: 128.6, unit: '万元', dataDate: '2026-09-20' },
      average
    ];
    const pairWrapper = mountDashboard({ model: {
      ...model,
      composition: [],
      kpis: pairKpis({ key: 'depositAverage', label: '存款月均', value: 120, unit: '万元', dataDate: '2026-09-20' })
    } });

    expect(pairWrapper.get('[data-testid="branch-operating-period-compare"]').text()).toContain('存款');
    expect(pairWrapper.get('[data-testid="branch-operating-period-compare"]').text()).toContain('万元');

    const mismatchedUnitWrapper = mountDashboard({ model: {
      ...model,
      composition: [],
      kpis: pairKpis({ key: 'depositAverage', label: '存款月均', value: 120, unit: '亿元', dataDate: '2026-09-20' })
    } });
    expect(mismatchedUnitWrapper.find('[data-testid="branch-operating-period-compare"]').exists()).toBe(false);

    const mismatchedDateWrapper = mountDashboard({ model: {
      ...model,
      composition: [],
      kpis: pairKpis({ key: 'depositAverage', label: '存款月均', value: 120, unit: '万元', dataDate: '2026-09-19' })
    } });
    expect(mismatchedDateWrapper.find('[data-testid="branch-operating-period-compare"]').exists()).toBe(false);
  });

  it('团队 increaseUnit 为万元时显示存款增量金额，缺少单位时兼容百分比', () => {
    const amountWrapper = mountDashboard({ model: {
      ...model,
      teams: [{ name: '公司金融部', rate: 86, increase: 12.5, increaseUnit: '万元', pending: 2 }]
    } });
    const amountTable = amountWrapper.get('[data-testid="branch-operating-teams"]');

    expect(amountTable.find('thead').text()).toContain('存款增量');
    expect(amountTable.find('tbody td:nth-child(3)').text()).toBe('+12.50 万元');

    const percentWrapper = mountDashboard({ model: {
      ...model,
      teams: [{ name: '公司金融部', rate: 86, increase: 4.2, pending: 2 }]
    } });
    const percentTable = percentWrapper.get('[data-testid="branch-operating-teams"]');

    expect(percentTable.find('thead').text()).toContain('增幅');
    expect(percentTable.find('tbody td:nth-child(3)').text()).toBe('+4.20%');
  });

  it('TEST 来源缺少 KPI 状态时明确标记为测试数据', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      sourceLabel: '经营分析 TEST 数据源',
      kpis: [{ ...model.kpis[0], status: '' }]
    } });

    expect(wrapper.get('[data-kpi-key="deposit"] .branch-operating-kpi__status').text()).toBe('测试数据');
  });

  it('完整来源数据在紧凑布局中保留全部目标、项目和团队行', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      targets: [
        ...model.targets,
        { key: 'customer', label: '客户增长', actual: 420, target: 500, unit: '户', rate: 84, gap: 80 }
      ],
      projects: [
        { name: '园区综合授信', status: '推进中', amount: 12.5, unit: '亿元', owner: '公司金融部', days: 9 },
        { name: '科创企业池', status: '待审批', amount: 8.2, unit: '亿元', owner: '普惠金融部', days: 12 },
        { name: '供应链融资', status: '推进中', amount: 6.4, unit: '亿元', owner: '交易银行部', days: 18 },
        { name: '重点客户回访', status: '已完成', amount: 2.1, unit: '万元', owner: '零售金融部', days: 0 }
      ],
      teams: [
        { name: '公司金融部', rate: 86, increase: 12.5, increaseUnit: '万元', pending: 2 },
        { name: '普惠金融部', rate: 74, increase: 8.4, increaseUnit: '万元', pending: 1 },
        { name: '零售金融部', rate: 91, increase: 5.2, increaseUnit: '万元', pending: 0 }
      ]
    } });

    expect(wrapper.findAll('[data-testid="branch-operating-kpi"]')).toHaveLength(6);
    expect(wrapper.findAll('.branch-operating-target-row')).toHaveLength(3);
    expect(wrapper.findAll('[data-testid="branch-operating-project"]')).toHaveLength(4);
    expect(wrapper.findAll('[data-testid="branch-operating-team"]')).toHaveLength(3);
  });

  it('单一真实存款余额只绘制一条带面积的折线，月均缺一值时对照板收起', () => {
    const wrapper = mountDashboard({ model: {
      ...model,
      composition: [],
      marketing: [],
      trend: [{ date: '2026-09', deposit: 128.6 }],
      kpis: model.kpis.filter(kpi => kpi.key !== 'loan')
    } });

    const option = chartOption(wrapper);
    expect(option.series).toHaveLength(1);
    expect(option.series[0].name).toBe('存款余额');
    expect(option.series[0].type).toBe('line');
    expect(option.series[0].areaStyle).toBeTruthy();
    expect(wrapper.find('[data-testid="branch-operating-period-compare"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="branch-operating-composition-empty"]').classes()).toContain('branch-operating-empty--compact');
  });
});
