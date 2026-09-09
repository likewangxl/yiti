// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: ['geoJson', 'points', 'selectedRegionCode', 'mode', 'demo'],
    emits: ['region-select', 'branch-select'],
    template: '<div class="panorama-map-stub" data-testid="retail-map"><button type="button" data-action="select-xian" @click="$emit(\'region-select\', { code: \'610100\', name: \'西安市\' })">选择西安</button></div>'
  }
}));

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, default: () => ({}) } },
    template: '<div data-testid="retail-chart" :data-option="JSON.stringify(option)" />'
  }
}));

import RetailDashboard from '../RetailDashboard.vue';

const model = {
  title: '零售经营总览',
  dataDate: '2026-09-06',
  kpis: [
    { key: 'retailAum', label: '零售AUM', value: 1824.6, unit: '亿元', change: 3.2 },
    { key: 'retailDeposit', label: '储蓄余额', value: 1286.42, unit: '亿元', change: -0.1 },
    { key: 'retailRevenue', label: '零售营业收入', value: 32.68, unit: '亿元', change: 8.1 },
    { key: 'retailValueCustomers', label: '价值客户', value: 86.24, unit: '万户', change: 4.1 },
    { key: 'retailLoan', label: '个人贷款', value: 968.35, unit: '亿元', change: 5.4 },
    { key: 'retailNplRate', label: '个贷不良率', value: 1.8, unit: '%', change: 0.2 },
    { key: 'retailDepositAverage', label: '储蓄月日均', value: 1287.26, unit: '亿元', change: null }
  ],
  trend: [
    { date: '2026-04', aum: 1762.3, deposit: 1268.12 },
    { date: '2026-05', aum: 1781.4, deposit: 1261.4 },
    { date: '2026-06', aum: 1802.8, deposit: 1277.86 }
  ],
  segments: [
    { name: '私行客户', customers: 8.6, aum: 386.2 },
    { name: '代发客户', customers: 42.1, aum: 517.8 }
  ],
  rankings: [
    { orgCode: 'ORG-XIAN', name: '西安市分行', aum: 520.6, increase: 12.4, rate: 98.2, nplRate: 1.4, cityCode: '610100' },
    { orgCode: 'ORG-WEINAN', name: '渭南市分行', aum: 184.2, increase: -4.2, rate: 76.3, nplRate: 2.3, cityCode: '610500' },
    { orgCode: 'ORG-UNKNOWN', name: '零售数据服务中心', aum: null, increase: 0, rate: null, nplRate: null, cityCode: null }
  ],
  attention: [
    { label: '重点客户维护', count: 4, owner: '零售金融部', deadline: '2026-09-15' },
    { label: '风险数据待核验', count: 0, owner: null, deadline: null }
  ],
  targets: [
    { name: '零售AUM增长', actual: 12.5, target: 10 },
    { name: '储蓄余额净增', actual: -3.6, target: 12 },
    { name: '价值客户增量', actual: 0, target: 0 }
  ],
  institutions: [
    { orgCode: 'ORG-XIAN', name: '西安市分行', cityCode: '610100', cityName: '西安市' },
    { orgCode: 'ORG-WEINAN', name: '渭南市分行', cityCode: '610500', cityName: '渭南市' },
    { orgCode: 'ORG-UNKNOWN', name: '零售数据服务中心', cityCode: null, cityName: null }
  ],
  issues: []
};

const mounted = [];

function mountDashboard(overrides = {}) {
  const wrapper = mount(RetailDashboard, {
    props: { model, loading: false, error: '', demo: false, ...overrides },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount());
  document.body.style.overflow = '';
});

describe('RetailDashboard 零售经营总览', () => {
  it('固定渲染六项零售 KPI，零值保留且 AUM 不由存款与贷款相加', () => {
    const wrapper = mountDashboard();
    expect(wrapper.findAll('[data-testid="retail-kpi"]')).toHaveLength(6);
    expect(wrapper.get('[data-kpi-key="retailAum"]').text()).toContain('1,824.60');
    expect(wrapper.get('[data-kpi-key="retailAum"]').text()).not.toContain('2,254.77');
    expect(wrapper.get('[data-kpi-key="retailNplRate"]').text()).toContain('↑ 0.20pp');
    expect(wrapper.get('[data-kpi-key="retailNplRate"] .retail-kpi__change').classes()).toContain('is-risk');
  });

  it('储蓄经营使用单独的月日均字段，客户分层不求和重叠客户', () => {
    const wrapper = mountDashboard();
    expect(wrapper.get('[data-testid="retail-deposit-balance"]').text()).toContain('1,286.42');
    expect(wrapper.get('[data-testid="retail-deposit-average"]').text()).toContain('1,287.26');
    expect(wrapper.get('[data-testid="retail-deposit-change"]').text()).toContain('↓ 0.10%');
    expect(wrapper.get('[data-testid="retail-segments"]').text()).toContain('分层口径以业务定义为准');
    expect(wrapper.findAll('[data-testid="retail-segment-row"]')).toHaveLength(2);
  });

  it('地图选择城市只过滤机构排名，辖内 KPI 与趋势仍使用全辖模型', async () => {
    const wrapper = mountDashboard();
    expect(wrapper.get('[data-testid="retail-scope-note"]').text()).toContain('KPI 与趋势不随城市筛选变化');
    await wrapper.get('[data-action="select-xian"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-selected-city"]').text()).toContain('西安市');
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="retail-ranking-row"]').text()).toContain('西安市分行');
    expect(wrapper.get('[data-testid="retail-trend"]').attributes('data-point-count')).toBe('3');
    await wrapper.get('[data-action="clear-city"]').trigger('click');
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')).toHaveLength(3);
  });

  it('排名可切换指标与领先/短板顺序，null 保留为缺失且不参与首位', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-ranking-metric="increase"]').trigger('click');
    await wrapper.get('[data-ranking-order="lagging"]').trigger('click');
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')[0].text()).toContain('渭南市分行');
    expect(wrapper.get('[data-testid="retail-ranking-row"]').text()).toContain('-4.20');
    await wrapper.get('[data-ranking-metric="rate"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-ranking-unit"]').text()).toContain('%');
  });

  it('目标完成率保留真实超额或负数，进度条限制视觉宽度且无效目标明确提示', () => {
    const wrapper = mountDashboard();
    expect(wrapper.get('[data-target-name="零售AUM增长"]').text()).toContain('125.00%');
    expect(wrapper.get('[data-target-name="零售AUM增长"] .retail-target-bar i').attributes('style')).toContain('width: 100%');
    expect(wrapper.get('[data-target-name="储蓄余额净增"]').text()).toContain('-30.00%');
    expect(wrapper.get('[data-target-name="价值客户增量"]').text()).toContain('无有效目标');
  });

  it('经营关注只展示来源字段，不自动生成逾期任务', () => {
    const wrapper = mountDashboard();
    const attention = wrapper.get('[data-testid="retail-attention"]');
    expect(attention.text()).toContain('责任归属与跟进时限');
    expect(attention.text()).toContain('零售金融部');
    expect(attention.text()).toContain('2026-09-15');
    expect(attention.findAll('li')).toHaveLength(2);
  });

  it('长内容保留在独立可滚动区域，并提供键盘语义而不引入下拉筛选', () => {
    const longModel = {
      ...model,
      segments: Array.from({ length: 12 }, (_, index) => ({
        name: `客户分层${index + 1}`,
        customers: index + 1,
        aum: index + 2
      })),
      rankings: Array.from({ length: 14 }, (_, index) => ({
        orgCode: `ORG-${index + 1}`,
        name: `机构${index + 1}`,
        aum: index + 1,
        increase: index,
        rate: 80 + index,
        nplRate: 1 + index / 10
      })),
      attention: Array.from({ length: 10 }, (_, index) => ({
        label: `待协调事项${index + 1}`,
        count: index,
        owner: '零售金融部',
        deadline: '2026-09-15'
      })),
      targets: Array.from({ length: 9 }, (_, index) => ({
        name: `经营目标${index + 1}`,
        actual: index + 1,
        target: index + 2
      }))
    };
    const wrapper = mountDashboard({ model: longModel });
    const regions = [
      ['.retail-savings__body', '储蓄核心指标内容'],
      ['.retail-segment-list', '客户分层列表'],
      ['.retail-attention-list', '经营关注事项'],
      ['.retail-ranking-list', '机构排名列表'],
      ['.retail-target-list', '零售经营目标列表']
    ];

    regions.forEach(([selector, label]) => {
      const region = wrapper.get(selector);
      expect(region.attributes('tabindex')).toBe('0');
      expect(region.attributes('aria-label')).toBe(label);
    });
    expect(wrapper.findAll('[data-testid="retail-segment-row"]')).toHaveLength(12);
    expect(wrapper.findAll('[data-testid="retail-ranking-row"]')).toHaveLength(14);
    expect(wrapper.get('[data-testid="retail-attention"]').findAll('li')).toHaveLength(10);
    expect(wrapper.findAll('.retail-target-row')).toHaveLength(9);
    expect(wrapper.findAll('select')).toHaveLength(0);
  });

  it('机构行打开本地详情，详情仅展示目录身份和对应排名指标，Escape 可关闭', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-testid="retail-ranking-row"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).toContain('西安市分行');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).toContain('AUM');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).not.toContain('趋势');
    await wrapper.get('[data-testid="retail-institution-dialog"]').trigger('keydown.esc');
    expect(wrapper.find('[data-testid="retail-institution-dialog"]').exists()).toBe(false);
  });

  it('机构目录支持搜索与键盘选择，并能看到没有 cityCode 的机构', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="open-retail-directory"]').trigger('click');
    expect(wrapper.get('[data-testid="retail-directory-search"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="retail-directory-row"]')).toHaveLength(3);
    await wrapper.get('[data-testid="retail-directory-search"]').setValue('数据服务');
    expect(wrapper.findAll('[data-testid="retail-directory-row"]')).toHaveLength(1);
    await wrapper.get('[data-testid="retail-directory-row"]').trigger('keydown.enter');
    expect(wrapper.get('[data-testid="retail-institution-dialog"]').text()).toContain('零售数据服务中心');
  });

  it('模型被拒绝或换屏清空时，目录、机构选择和城市筛选一起清除', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="select-xian"]').trigger('click');
    await wrapper.get('[data-testid="retail-ranking-row"]').trigger('click');
    expect(wrapper.find('[data-testid="retail-institution-dialog"]').exists()).toBe(true);
    await wrapper.setProps({ error: '403 Forbidden', model: { ...model, kpis: [], rankings: [], institutions: [] } });
    expect(wrapper.find('[data-testid="retail-institution-dialog"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="retail-selected-city"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="retail-scope-note"]').text()).toContain('当前大屏授权范围');
  });
  it('区分目标未配置和实际值待更新，并直说距目标或超目标金额', () => {
    const wrapper = mountDashboard({ model: { ...model, targets: [
      { name: '收入待更新', actual: null, target: 10 },
      { name: '收入缺口', actual: 8, target: 10 },
      { name: '资产超额', actual: 12, target: 10 }
    ] } });
    expect(wrapper.get('[data-target-name="收入待更新"]').text()).toContain('实际待更新');
    expect(wrapper.get('[data-target-name="收入待更新"]').text()).not.toContain('无有效目标');
    expect(wrapper.get('[data-target-name="收入缺口"]').text()).toContain('距目标 2.00 亿元');
    expect(wrapper.get('[data-target-name="资产超额"]').text()).toContain('超目标 2.00 亿元');
  });

});
