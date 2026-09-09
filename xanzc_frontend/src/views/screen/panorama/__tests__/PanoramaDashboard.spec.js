// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    template: '<div class="panorama-map-stub"><button type="button" class="stub-select-region" @click="$emit(\'region-select\', { code: \'610100\', name: \'西安市\' })">选择西安</button><button type="button" class="stub-select-branch" @click="$emit(\'branch-select\', \'ORG-1\')">选择支行</button></div>',
    emits: ['region-select', 'branch-select']
  }
}));
vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)" />' }
}));
vi.mock('../PanoramaInstitutionDirectory.vue', () => ({
  default: {
    name: 'PanoramaInstitutionDirectory',
    props: ['model'],
    emits: ['close', 'branch-select'],
    template: '<div data-testid="institution-directory-stub"><button type="button" data-action="directory-close" @click="$emit(\'close\')">关闭目录</button><button type="button" data-action="directory-select" @click="$emit(\'branch-select\', \'ORG-2\')">选择机构</button></div>'
  }
}));

import PanoramaDashboard from '../PanoramaDashboard.vue';

const model = {
  title: '分行经营总览',
  dataDate: '2026-09-06',
  kpis: [
    { key: 'deposit', label: '存款余额', value: 1286.42, unit: '亿元', change: 6.8 },
    { key: 'loan', label: '贷款余额', value: null, unit: '亿元', change: null },
    { key: 'customers', label: '客户总量', value: 0, unit: '万户', change: 0 },
    { key: 'revenue', label: '营收', value: 32.68, unit: '亿元', change: 8.1 }
  ],
  trend: [
    { date: '2026-08', deposit: 1253, loan: 948 },
    { date: '2026-09', deposit: 1286, loan: 968 }
  ],
  composition: [
    { name: '对公业务', value: 568.48, unit: '亿元' },
    { name: '零售业务', value: 398.87, unit: '亿元' }
  ],
  rankings: [
    { orgCode: 'ORG-1', name: '西安市分行', deposit: 512.63, change: 6.2 },
    { orgCode: 'ORG-2', name: '榆林市分行', deposit: 188.74, change: 7.4 }
  ],
  attention: [{ label: '目标进度偏慢机构', count: 3 }],
  institutions: [
    {
      orgCode: 'ORG-1', orgName: '西安市分行', cityCode: '610100', lng: 108.94, lat: 34.34,
      located: true, metrics: { deposit: 512.63, loan: 392.18, customers: 73.28, target: 88.6, rate: 88.6 },
      trend: [{ date: '2026-09', deposit: 512.63, loan: 392.18 }], attention: []
    },
    {
      orgCode: 'ORG-2', orgName: '榆林市分行', cityCode: '610800', lng: null, lat: null,
      located: false, metrics: { deposit: null, loan: 0, customers: null, target: null, rate: null },
      trend: [], attention: []
    }
  ],
  issues: [],
  citySummaries: {}
};

const extendedModel = {
  ...model,
  kpis: [
    ...model.kpis,
    { key: 'depositIncrease', label: '存款较上月净增', value: 0, unit: '亿元', change: null },
    { key: 'depositAverage', label: '月均余额', value: null, unit: '亿元', change: null }
  ],
  rankings: [
    { orgCode: 'ORG-1', name: '西安市分行', deposit: 100, increase: -2, average: null, change: -2, cityCode: '610100' },
    { orgCode: 'ORG-2', name: '榆林市分行', deposit: 90, increase: 5, average: 7, change: 5, cityCode: '610800' },
    { orgCode: 'ORG-3', name: '宝鸡市分行', deposit: 80, increase: null, average: 10, change: null, cityCode: '610300' }
  ],
  trend: [
    { date: '2026-08', deposit: 1253, loan: 948, depositIncrease: -3 },
    { date: '2026-09', deposit: 1286, loan: 968, depositIncrease: 0 }
  ]
};

const mounted = [];

function mountDashboard(overrides = {}, options = {}) {
  const wrapper = mount(PanoramaDashboard, {
    props: { model, loading: false, error: '', demo: false, ...overrides },
    global: { stubs: options.stubs || {} },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

describe('PanoramaDashboard 省级经营大屏', () => {
  beforeEach(() => {
    document.body.style.overflow = '';
  });

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount());
    document.body.style.overflow = '';
  });

  it('先展示四个 KPI 的绑定值，null 为空态且 0 不被当成缺失', () => {
    const wrapper = mountDashboard();
    const cards = wrapper.findAll('[data-testid="panorama-kpi"]');
    expect(cards).toHaveLength(4);
    expect(cards[0].text()).toContain('1,286.42');
    expect(cards[1].text()).toContain('—');
    expect(cards[1].text()).not.toContain('NaN');
    expect(cards[2].text()).toContain('0');
    expect(wrapper.find('[data-testid="panorama-demo-badge"]').exists()).toBe(false);
  });

  it('核心 KPI 不截断新绑定，存款经营小卡单独显示净增 0 与未绑定月均', () => {
    const wrapper = mountDashboard({ model: extendedModel });
    expect(wrapper.findAll('[data-testid="panorama-kpi"]')).toHaveLength(4);
    const operationCards = wrapper.findAll('[data-testid="deposit-operation-card"]');
    expect(operationCards).toHaveLength(2);
    expect(operationCards[0].text()).toContain('0');
    expect(operationCards[1].text()).toContain('—');
    expect(operationCards[1].text()).toContain('未绑定');
  });

  it('分行主营摘要使用直接业务文案，移除技术覆盖和模糊下降指标', () => {
    const wrapper = mountDashboard({
      model: {
        ...extendedModel,
        kpis: [
          ...extendedModel.kpis.filter(item => !['depositIncrease', 'rate'].includes(item.key)),
          { key: 'depositIncrease', value: -1.88, unit: '亿元' },
          { key: 'rate', value: 86.5, unit: '%' }
        ]
      }
    });
    const summary = wrapper.get('[data-testid="leadership-diagnostics"]').text();
    expect(summary).toContain('存款较上月净减1.88亿元');
    expect(summary).toContain('距目标还差13.5个百分点');
    expect(summary).toContain('未完成目标机构1家');
    expect(summary).not.toContain('可比指标覆盖');
    expect(summary).not.toContain('下降机构数');
  });

  it('业务构成使用独立占比组件，每项金额只显示一次', () => {
    const wrapper = mountDashboard({ model: { ...model, composition: [
      { name: '对公业务', value: 714.26, unit: '亿元' },
      { name: '零售业务', value: 572.16, unit: '亿元' }
    ] } });
    const component = wrapper.findComponent({ name: 'CompositionBreakdown' });
    expect(component.exists()).toBe(true);
    expect(component.props('items')).toHaveLength(2);
    const text = component.text();
    expect(text).toContain('55.5%');
    expect(text).toContain('44.5%');
    expect(text.match(/714\.26/g)).toHaveLength(1);
    expect(text.match(/572\.16/g)).toHaveLength(1);
  });

  it('演示模式明确标识，真实错误和加载态有可访问反馈', () => {
    const wrapper = mountDashboard({ demo: true, loading: true, error: '取数失败' });
    expect(wrapper.find('[data-testid="panorama-demo-badge"]').text()).toContain('演示数据');
    expect(wrapper.find('[role="status"]').text()).toContain('加载中');
    expect(wrapper.find('[role="alert"]').text()).toContain('取数失败');
  });

  it('刷新、返回、配置入口通过事件交给容器', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="refresh"]').trigger('click');
    await wrapper.get('[data-action="back"]').trigger('click');
    await wrapper.get('[data-action="configure"]').trigger('click');
    expect(wrapper.emitted('refresh')).toHaveLength(1);
    expect(wrapper.emitted('back')).toHaveLength(1);
    expect(wrapper.emitted('configure')).toHaveLength(1);
  });

  it('机构目录入口只把 model.institutions 交给独立面板，并转发机构选择', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('[data-action="open-institution-directory"]').trigger('click');
    expect(wrapper.find('[data-testid="institution-directory-stub"]').exists()).toBe(true);
    await wrapper.get('[data-action="directory-select"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toContainEqual(['ORG-2']);
    await wrapper.get('[data-action="directory-close"]').trigger('click');
    expect(wrapper.find('[data-testid="institution-directory-stub"]').exists()).toBe(false);
  });

  it('省级地图选择行政区后打开市级 modal，市汇总不存在时显示未绑定而不累加下级机构', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="city-panorama-modal"]').text()).toContain('未绑定');
    expect(wrapper.find('[data-testid="city-kpi-deposit"]').text()).not.toContain('512.63');
    expect(document.body.style.overflow).toBe('hidden');
  });

  it('市级 modal 支持 close、Escape 和 backdrop，并把焦点恢复到打开前控件', async () => {
    const wrapper = mountDashboard();
    const regionButton = wrapper.get('.stub-select-region').element;
    regionButton.focus();
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    const modal = wrapper.get('[data-testid="city-panorama-modal"]');
    await modal.get('[data-action="city-close"]').trigger('click');
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
    expect(document.body.style.overflow).toBe('');
    expect(document.activeElement).toBe(regionButton);

    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="city-panorama-modal"]').trigger('keydown.esc');
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);

    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="city-panorama-modal"]').trigger('click');
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
  });

  it('分页控件变为 disabled 使焦点落到 body 后，Escape 仍关闭城市', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await nextTick();
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
    expect(document.body.style.overflow).toBe('');
  });

  it('地图选中机构会显示机构详情，不从名称猜 cityCode', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('.stub-select-branch').trigger('click');
    expect(wrapper.find('[data-testid="selected-institution"]').text()).toContain('西安市分行');
  });

  it('排名支持净增切换：负数参与排序，null 不进入当前指标前十矩阵', async () => {
    const wrapper = mountDashboard({ model: extendedModel });
    const increaseButton = wrapper.get('[data-ranking-mode="increase"]');
    await increaseButton.trigger('click');
    const topRows = wrapper.findAll('[data-testid="ranking-row"]');
    expect(topRows[0].text()).toContain('榆林市分行');
    expect(topRows[1].text()).toContain('西安市分行');
    expect(topRows.some(row => row.text().includes('宝鸡市分行'))).toBe(false);
    expect(wrapper.find('[data-testid="ranking-detail-ORG-3"]').exists()).toBe(false);
    expect(wrapper.findAll('.panorama-detail-table thead th')).toHaveLength(3);
  });

  it('机构矩阵只渲染当前指标前十，切换指标后仍按当前指标重排', async () => {
    const matrixRankings = Array.from({ length: 12 }, (_, index) => ({
      orgCode: `M-${index + 1}`,
      name: `矩阵机构${index + 1}`,
      deposit: 120 - index,
      increase: index === 0 ? -1 : 13 - index,
      average: 100 - index
    }));
    const wrapper = mountDashboard({ model: { ...extendedModel, rankings: matrixRankings } });
    const visibleRows = () => wrapper.findAll('.panorama-detail-table tbody tr');
    expect(visibleRows()).toHaveLength(10);
    expect(visibleRows()[0].text()).toContain('矩阵机构1');
    expect(visibleRows().at(-1).text()).toContain('矩阵机构10');
    await wrapper.get('[data-ranking-mode="increase"]').trigger('click');
    expect(visibleRows()).toHaveLength(10);
    expect(visibleRows()[0].text()).toContain('矩阵机构2');
    expect(visibleRows().at(-1).text()).toContain('矩阵机构11');
    expect(visibleRows().some(row => row.text().includes('矩阵机构12'))).toBe(false);
  });

  it('目标率图形限制在 100%，文字保留实际超额完成率且范围不写死年度', () => {
    const wrapper = mountDashboard({
      model: {
        ...extendedModel,
        kpis: [...extendedModel.kpis, { key: 'rate', label: '目标完成率', value: 125, unit: '%', period: 'LAST_1M' }]
      }
    });
    expect(wrapper.get('[data-testid="target-progress-value"]').text()).toContain('125');
    expect(wrapper.get('[data-testid="target-progress-ring"]').attributes('style')).toContain('--target-progress: 100%');
    expect(wrapper.get('.panorama-target-panel').text()).not.toContain('年度目标');
  });

  it('趋势默认净增并可切换回余额，采用真实绑定数据', async () => {
    const wrapper = mountDashboard({ model: extendedModel });
    const chart = () => JSON.parse(wrapper.findAll('[data-testid="chart-option"]').at(-1).attributes('data-option'));
    expect(chart().series[0].name).toContain('净增');
    expect(chart().series[0].data).toEqual([-3, 0]);
    expect(wrapper.get('[data-testid="panorama-trend"]').classes()).toContain('is-compact');
    await wrapper.get('[data-trend-mode="deposit"]').trigger('click');
    expect(chart().series[0].name).toContain('余额');
    expect(chart().series[0].data).toEqual([1253, 1286]);
  });

  it('地图范围标题使用全辖机构分布', () => {
    const wrapper = mountDashboard({ model: extendedModel });
    expect(wrapper.get('.panorama-map-panel').text()).toContain('全辖机构分布');
    expect(wrapper.get('.panorama-map-panel').text()).not.toContain('陕西省分行机构分布');
  });
});
