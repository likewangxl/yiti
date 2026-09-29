// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaMap.vue', () => ({
  default: {
    name: 'PanoramaMap',
    props: ['metricLabel', 'metricValues', 'metricNumericValues', 'metricColors', 'regionStates', 'labelLayout', 'cityDetails', 'cityDetailMode', 'colorByCity', 'showRegionMetrics', 'showProvincePoints', 'showProvincePointLabels'],
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
    { key: 'customers', label: '营销有效归属客户数', value: 0, unit: '万户', change: 0 },
    { key: 'revenue', label: '手工测试收入', value: 32.68, unit: '亿元', change: 8.1 }
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

it('非生产且过期的省分行数据在页眉明确披露来源状态，不出现临时命名', () => {
  const wrapper = mountDashboard({ model: {
    ...model,
    sourceQualities: { composition: { dataClassification: 'TEST', status: 'STALE', dataDate: '2026-09-21' } }
  } });
  const notice = wrapper.get('.panorama-live-state');
  expect(notice.text()).toContain('非生产联调数据');
  expect(notice.text()).toContain('已过期');
  expect(notice.text()).not.toContain('测试');
});

it('配置化页头用金额单位下拉框替代来源状态，并把选择传给展示布局', async () => {
  const wrapper = mountDashboard({
    sourcePresentation: {
      displayPresentation: { displaySchemaVersion: 1, display: { components: [] } }
    }
  }, {
    stubs: {
      PresentationLayout: {
        props: ['amountUnit'],
        template: '<div data-testid="presentation-layout-stub" :data-amount-unit="amountUnit" />'
      }
    }
  });
  const select = wrapper.get('[data-testid="panorama-amount-unit"]');
  expect(select.element.value).toBe('TEN_THOUSAND');
  expect(wrapper.find('.panorama-live-state').exists()).toBe(false);
  await select.setValue('YUAN');
  expect(wrapper.get('[data-testid="presentation-layout-stub"]').attributes('data-amount-unit')).toBe('YUAN');
});

it('旧版固定布局保留来源状态，不显示不会生效的金额单位下拉框', () => {
  const wrapper = mountDashboard();
  expect(wrapper.find('[data-testid="panorama-amount-unit"]').exists()).toBe(false);
  expect(wrapper.get('.panorama-live-state').text()).toContain('经营监测');
});

describe('PanoramaDashboard 省级经营大屏', () => {
  beforeEach(() => {
    document.body.style.overflow = '';
  });

  it('新展示协议用组件实例替换固定KPI区，旧业务身份仍来自dataRef', () => {
    const wrapper = mountDashboard({
      sourcePresentation: { displayPresentation: {
        displaySchemaVersion: 1,
        display: { components: [{
          componentId: 'deposit-custom', componentType: 'METRIC_CARD', layoutRegion: 'LEFT', order: 0, visible: true,
          text: { titleMode: 'CUSTOM', title: '全行存款', subtitle: '', description: '' },
          format: { displayUnit: 'HUNDRED_MILLION', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED', emptyText: '—' },
          content: { mainField: 'value', subFields: [] }, interaction: { action: 'NONE' },
          dataRefs: [{ blockId: 1, role: 'PRIMARY', metricCode: 'deposit', metricName: '存款余额', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
        }] }
      } }
    });
    expect(wrapper.find('[data-testid="presentation-metric-widgets"]').exists()).toBe(true);
    expect(wrapper.find('[data-component-id="deposit-custom"]').text()).toContain('全行存款');
    expect(wrapper.find('[data-component-id="deposit-custom"]').text()).toContain('1,286.42亿元');
    expect(wrapper.find('.panorama-kpi-grid').exists()).toBe(false);
  });

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount());
    vi.useRealTimers();
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

  it('缺失可见指标就近显示字段级来源原因，且运行时 issue 优先', async () => {
    const wrapper = mountDashboard({
      model: { ...model, kpis: model.kpis.map(item => item.key === 'loan' ? { ...item, value: null } : item), composition: [{ name: '对公', value: 10, unit: '亿元' }], attention: [], rankings: [] },
      sourcePresentation: {
        sourceAvailability: {
          loan: { status: 'NO_SOURCE', message: '贷款来源暂无机构范围数据' },
          composition: { fields: { value: { status: 'PARTIAL', message: '最新周期构成项不完整' } } },
          attention: { status: 'NO_SOURCE', message: '尚无按当前机构范围聚合的流程与经营关注数据源' },
          ranking: { fields: { increase: { status: 'NO_VALUES', message: '最新周期无有效值' } } }
        },
        runtimeIssues: { loan: [{ field: 'value', message: '请求字段为空' }] }
      }
    });
    expect(wrapper.get('[data-testid="kpi-status-loan"]').text()).toContain('请求字段为空');
    expect(wrapper.get('[data-testid="attention-status"]').text()).toContain('尚无按当前机构范围');
    expect(wrapper.get('[data-testid="composition-breakdown"]').text()).toContain('最新周期构成项不完整');
    await wrapper.get('[data-ranking-mode="increase"]').trigger('click');
    expect(wrapper.get('[data-testid="ranking-status"]').text()).toContain('最新周期无有效值');
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

  it('极小非零月均值保留有效精度，不与0或缺失混淆', () => {
    const wrapper = mountDashboard({ model: { ...extendedModel, kpis: [...extendedModel.kpis.map(item => item.key === 'depositAverage' ? { ...item, value: 0.00043837 } : item)] } });
    expect(wrapper.findAll('[data-testid="deposit-operation-card"]')[1].text()).toContain('0.000438');
    expect(wrapper.findAll('[data-testid="deposit-operation-card"]')[1].text()).toContain('0.00043837');
  });

  it('分行重点完成情况使用明确完成率，不再展示净增、协调事项等重复摘要', () => {
    const wrapper = mountDashboard({
      model: {
        ...extendedModel,
        kpis: [
          ...extendedModel.kpis.filter(item => !['depositIncrease', 'rate'].includes(item.key)),
          { key: 'depositIncrease', value: -1.88, unit: '亿元' },
          { key: 'rate', value: 86.5, unit: '%' },
          { key: 'corporateDepositRate', label: '对公存款目标完成率', value: 86.5, unit: '%' }
        ]
      }
    });
    const summary = wrapper.get('[data-testid="leadership-diagnostics"]').text();
    expect(summary).toContain('对公存款目标完成率');
    expect(summary).toContain('86.5%');
    expect(summary).toContain('距目标还差13.5个百分点');
    expect(summary).toContain('辖内机构达标率');
    expect(summary).not.toContain('存款较上月净减');
    expect(summary).not.toContain('协调事项');
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

  it('真实运行缺少对公完成率时保持缺数态，不回退演示完成率', () => {
    const wrapper = mountDashboard({ demo: false, model: { ...extendedModel, kpis: extendedModel.kpis.filter(item => !String(item.key).toLowerCase().includes('corporate') && !String(item.key).toLowerCase().includes('corp')) } });
    const text = wrapper.get('[data-testid="leadership-diagnostics"]').text();
    expect(text).not.toContain('93.6%');
    expect(text).not.toContain('88.2%');
    expect(text).not.toContain('91.8%');
    expect(text).toContain('暂无目标数据');
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

  it('地图选中机构后保留高亮状态，不占用地图空间显示摘要', async () => {
    const wrapper = mountDashboard();
    await wrapper.get('.stub-select-branch').trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['ORG-1']]);
    expect(wrapper.find('[data-testid="selected-institution"]').exists()).toBe(false);
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

  it('排名表提供轮播暂停控件，切换排名指标时保持首行对齐', async () => {
    const wrapper = mountDashboard({ model: extendedModel });
    const toggle = wrapper.get('.panorama-carousel-toggle');
    expect(toggle.text()).toBe('暂停轮播');
    expect(toggle.attributes('aria-pressed')).toBe('false');
    await toggle.trigger('click');
    expect(toggle.text()).toBe('继续轮播');
    expect(toggle.attributes('aria-pressed')).toBe('true');
    await wrapper.get('[data-ranking-mode="increase"]').trigger('click');
    expect(wrapper.findAll('[data-testid="ranking-row"]')[0].text()).toContain('榆林市分行');
  });

  it('排名表每次只向上轮播一行，动画结束后将该行循环到队尾', async () => {
    vi.useFakeTimers();
    const wrapper = mountDashboard({ model: extendedModel });
    const viewport = wrapper.get('.panorama-ranking-carousel').element;
    const table = wrapper.get('.panorama-detail-table').element;
    const firstRow = wrapper.findAll('[data-testid="ranking-row"]')[0].element;
    const initialRows = wrapper.findAll('[data-testid="ranking-row"]').map(row => row.text());
    Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: 120 });
    Object.defineProperty(table, 'scrollHeight', { configurable: true, value: 480 });
    firstRow.getBoundingClientRect = () => ({ height: 48 });

    await vi.advanceTimersByTimeAsync(3200);
    await nextTick();
    const body = wrapper.get('.panorama-ranking-carousel tbody');
    expect(body.classes()).toContain('is-advancing');
    expect(body.attributes('style')).toContain('translate3d(0, -48px, 0)');

    await vi.advanceTimersByTimeAsync(560);
    await nextTick();
    const cycledRows = wrapper.findAll('[data-testid="ranking-row"]').map(row => row.text());
    expect(cycledRows[0]).toBe(initialRows[1]);
    expect(cycledRows.at(-1)).toBe(initialRows[0]);
  });

  it('流程与经营关注分两行显示事项和机构，并每次向上轮播一条', async () => {
    vi.useFakeTimers();
    const attention = [
      { label: '授信调查任务待处理', count: 2, orgCode: 'ORG-1', orgName: '西安市分行' },
      { label: '客户回访即将到期', count: 1, orgCode: 'ORG-2', orgName: '榆林市分行' },
      { label: '存款目标进度偏慢', count: 3, orgCode: 'ORG-3', orgName: '宝鸡市分行' }
    ];
    const wrapper = mountDashboard({ model: { ...extendedModel, attention } });
    const rows = () => wrapper.findAll('.panorama-attention-list li');
    expect(rows()[0].find('.panorama-attention-copy > span').text()).toBe('授信调查任务待处理');
    expect(rows()[0].find('.panorama-attention-copy small').text()).toBe('西安市分行');
    expect(rows()[0].find('strong').exists()).toBe(false);
    expect(wrapper.get('.panorama-attention-heading-meta').text()).toContain('事项明细');
    expect(wrapper.get('.panorama-attention-heading-meta').text()).not.toContain('项需跟进');

    const viewport = wrapper.get('.panorama-attention-carousel').element;
    Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: 80 });
    Object.defineProperty(viewport, 'scrollHeight', { configurable: true, value: 180 });
    rows()[0].element.getBoundingClientRect = () => ({ height: 48 });
    await vi.advanceTimersByTimeAsync(3600);
    await nextTick();
    expect(rows()[0].attributes('style')).toContain('translate3d(0, -48px, 0)');

    await vi.advanceTimersByTimeAsync(520);
    await nextTick();
    expect(rows()[0].find('.panorama-attention-copy > span').text()).toBe('客户回访即将到期');
  });

  it('只有机构汇总数量时不冒充具体事项', () => {
    const wrapper = mountDashboard({
      model: {
        ...extendedModel,
        attention: [{ label: 'ORG-1', count: 5, orgCode: 'ORG-1', orgName: '西安市分行' }]
      }
    });
    expect(wrapper.find('.panorama-attention-list').exists()).toBe(false);
    expect(wrapper.get('[data-testid="attention-status"]').text()).toContain('仅提供机构汇总数量');
  });

  it('排名值不同显示连续名次', () => {
    const rankings = [100, 90, 80, 70].map((deposit, index) => ({
      orgCode: `R-${index + 1}`, name: `排名机构${index + 1}`, deposit
    }));
    const institutions = rankings.map(item => ({ orgCode: item.orgCode, orgName: item.name, metrics: {}, trend: [], attention: [] }));
    const wrapper = mountDashboard({ model: { ...extendedModel, rankings, institutions } });
    const rows = wrapper.findAll('[data-testid="ranking-row"]');
    expect(rows.map(row => row.find('.panorama-matrix-rank').text())).toEqual(['1', '2', '3', '4']);
  });

  it('相同值并列且下一位采用竞赛排名', () => {
    const rankings = [100, 100, 90].map((deposit, index) => ({
      orgCode: `T-${index + 1}`, name: `并列机构${index + 1}`, deposit
    }));
    const institutions = rankings.map(item => ({ orgCode: item.orgCode, orgName: item.name, metrics: {}, trend: [], attention: [] }));
    const wrapper = mountDashboard({ model: { ...extendedModel, rankings, institutions } });
    const rows = wrapper.findAll('[data-testid="ranking-row"]');
    expect(rows.map(row => row.find('.panorama-matrix-rank').text())).toEqual(['1', '1', '3']);
  });

  it('目标率图形限制在 100%，文字保留实际超额完成率且范围不写死年度', () => {
    const wrapper = mountDashboard({
      model: {
        ...extendedModel,
        kpis: [...extendedModel.kpis, { key: 'rate', label: '目标完成率', value: 125, unit: '%', period: 'LAST_1M' }]
      }
    });
    expect(wrapper.get('[data-testid="target-progress-value"]').text()).toContain('125');
    expect(wrapper.get('[data-testid="completion-water-gauge"]').attributes('data-level')).toBe('100');
    expect(wrapper.find('[data-testid="target-progress-bullet"]').exists()).toBe(false);
    expect(wrapper.get('.panorama-target-panel').text()).not.toContain('年度目标');
  });

  it('零售目标只在下方目标进度展示，顶部对公完成率缺来源时显示明确标注的演示值', () => {
    const wrapper = mountDashboard({
      demo: true,
      model: {
        ...extendedModel,
        kpis: [...extendedModel.kpis, { key: 'rate', label: '已设目标机构完成率', value: 72, unit: '%', period: 'LATEST' }]
      }
    });

    expect(wrapper.get('.panorama-target-summary-label').text()).toBe('已设目标机构完成率');
    expect(wrapper.get('[data-diagnostic="corporateDepositCompletion"]').text()).toContain('对公存款目标完成率');
    expect(wrapper.get('[data-diagnostic="corporateDepositCompletion"]').text()).toContain('93.6%');
    expect(wrapper.get('[data-diagnostic="corporateDepositCompletion"]').text()).toContain('演示值·非业务数据');
    expect(wrapper.get('.panorama-target-panel').text()).toContain('零售贷款目标完成率');
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

  it('分行省级 fallback 地图恢复地市 callout 并隐藏支行名称', () => {
    const wrapper = mountDashboard();
    const map = wrapper.findComponent({ name: 'PanoramaMap' });
    expect(map.props('labelLayout')).toBe('callout');
    expect(map.props('showProvincePointLabels')).toBe(false);
    expect(map.props('cityDetailMode')).toBe('institutions');
    expect(map.props('colorByCity')).toBe(true);
  });

  it('向省级地图传入仅授权机构名称的城市详情', () => {
    const wrapper = mountDashboard({
      model: {
        ...extendedModel,
        institutions: [
          ...model.institutions,
          { orgCode: 'ORG-3', orgName: '宝鸡支行', cityCode: '610300', located: true, lng: 107.1, lat: 34.3 }
        ],
        citySummaries: {
          '610100': { dataDate: '2026-09-01', kpis: [{ key: 'deposit', label: '存款余额', value: 125, unit: '万元' }] }
        }
      }
    });
    const map = wrapper.findComponent({ name: 'PanoramaMap' });
    expect(map.props('cityDetails')['610100']).toMatchObject({
      institutionCount: 1,
      institutions: [{ orgCode: 'ORG-1', orgName: '西安市分行' }]
    });
    expect(map.props('cityDetails')['610100'].metrics).toBeUndefined();
    expect(map.props('cityDetails')['610100'].dataDate).toBeUndefined();
    expect(map.props('cityDetailMode')).toBe('institutions');
  });

  it('地图范围标题使用辖区机构分布', () => {
    const wrapper = mountDashboard({ model: extendedModel });
    expect(wrapper.get('.panorama-map-panel').text()).toContain('辖区机构分布');
    expect(wrapper.get('.panorama-map-panel').text()).not.toContain('陕西省分行机构分布');
  });

  it('分行重点完成情况区展示对公存款、对公贷款、对公营业收入和辖内机构达标率，地图保持机构口径', async () => {
    const wrapper = mountDashboard({
      model: {
        ...extendedModel,
        kpis: [
          ...extendedModel.kpis,
          { key: 'corporateDepositRate', label: '对公存款目标完成率', value: 93.6, unit: '%', date: '2026-09-06' },
          { key: 'corporateLoanRate', label: '对公贷款目标完成率', value: 88.2, unit: '%', date: '2026-09-06' },
          { key: 'corporateRevenueRate', label: '对公营业收入目标完成率', value: 91.8, unit: '%', date: '2026-09-06' }
        ],
        attention: [{ label: '宝鸡市分行', count: 2 }],
        citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 42.5, unit: '亿元' }] } }
      }
    });
    const diagnostics = wrapper.findAll('[data-testid="leadership-diagnostics"] [data-diagnostic]');
    expect(diagnostics.map(card => card.find('.panorama-diagnostic-label').text())).toEqual(['对公存款目标完成率', '对公贷款目标完成率', '对公营业收入目标完成率', '辖内机构达标率']);
    expect(diagnostics[0].text()).toContain('93.6%');
    expect(diagnostics[1].text()).toContain('88.2%');
    expect(diagnostics[2].text()).toContain('91.8%');
    expect(diagnostics[2].find('[role="progressbar"]').attributes('aria-valuenow')).toBe('91.8');
    expect(diagnostics[3].text()).toContain('0/1家');
    expect(diagnostics[3].find('[role="progressbar"]').attributes('aria-valuenow')).toBe('0');

    const map = wrapper.findComponent({ name: 'PanoramaMap' });
    expect(map.props('metricLabel')).toBe('');
    expect(map.props('metricValues')).toEqual({});
    expect(map.props('metricNumericValues')).toEqual({});
    expect(map.props('showRegionMetrics')).toBe(false);
    expect(map.props('showProvincePoints')).toBe(false);
    await wrapper.get('[data-ranking-mode="increase"]').trigger('click');
    expect(map.props('metricLabel')).toBe('');
    expect(map.props('metricValues')).toEqual({});
  });

  it('权限错误清除城市弹层与搜索缓存，恢复后不复用旧选择', async () => {
    const wrapper=mountDashboard({model:extendedModel});
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="branch-search"]').setValue('旧条件');
    await wrapper.setProps({error:'没有权限（403）',model:{institutions:[]}});
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
    await wrapper.setProps({error:'',model:extendedModel});
    await wrapper.get('.stub-select-region').trigger('click');await nextTick();
    expect(wrapper.get('[data-testid="branch-search"]').element.value).toBe('');
  });

  it('正式排名点击带 cityCode 的机构直接发支行导航，不打开城市画像', async () => {
    const wrapper = mountDashboard({ model: extendedModel });
    await wrapper.get('[data-testid="ranking-row"]').trigger('click');
    await nextTick();
    expect(wrapper.emitted('branch-select')).toEqual([['ORG-1']]);
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);
  });

  it('城市画像业务线事件沿原样转发给容器', async () => {
    const cityStub = {
      name: 'CityPanorama',
      props: ['model', 'cityCode', 'cityName', 'sourcePresentation'],
      emits: ['business-line-select'],
      template: '<div data-testid="city-panorama-forward-stub"><button type="button" data-action="city-business-line" @click="$emit(\'business-line-select\', { businessLine: \'CORP\', tabKey: \'deposit\', context: { cityCode: \'610100\', cityName: \'西安市\' } })">业务线</button></div>'
    };
    const wrapper = mountDashboard({}, { stubs: { CityPanorama: cityStub } });
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();

    await wrapper.get('[data-action="city-business-line"]').trigger('click');
    expect(wrapper.emitted('business-line-select')).toEqual([[{
      businessLine: 'CORP', tabKey: 'deposit', context: { cityCode: '610100', cityName: '西安市' }
    }]]);
  });

  it('城市画像关闭后恢复搜索、关注、分页和选中状态，且详情卡已移除', async () => {
    const wrapper = mountDashboard({
      model: {
        ...extendedModel,
        institutions: Array.from({ length: 7 }, (_, index) => ({
          orgCode: `CITY-${index + 1}`,
          orgName: `西安支行${index + 1}`,
          cityCode: '610100',
          located: true,
          lng: 108.8 + index / 100,
          lat: 34.1 + index / 100,
          metrics: { deposit: 10 - index, rate: 90 },
          attention: index === 0 ? [{ label: '待跟进', count: 1 }] : [],
          trend: []
        }))
      }
    });
    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    const modal = wrapper.get('[data-testid="city-panorama-modal"]');
    await modal.get('[data-testid="branch-search"]').setValue('西安支行');
    await modal.get('[data-testid="attention-filter"]').trigger('click');
    await modal.get('[data-testid="branch-page-next"]').trigger('click');
    await modal.get('[data-testid="branch-row"]').trigger('click');
    expect(modal.find('[data-testid="branch-detail"]').exists()).toBe(false);
    await modal.get('[data-action="city-close"]').trigger('click');
    expect(wrapper.find('[data-testid="city-panorama-modal"]').exists()).toBe(false);

    await wrapper.get('.stub-select-region').trigger('click');
    await nextTick();
    const reopened = wrapper.get('[data-testid="city-panorama-modal"]');
    expect(reopened.get('[data-testid="branch-search"]').element.value).toBe('西安支行');
    expect(reopened.get('[data-testid="attention-filter"]').classes()).toContain('active');
    expect(reopened.get('[data-testid="branch-page-next"]').element.disabled).toBe(true);
    expect(reopened.find('[data-testid="branch-detail"]').exists()).toBe(false);
  });
});
