// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listScreens: vi.fn(),
  getScreenCanvas: vi.fn(),
  listScreenDatasources: vi.fn(),
  saveScreenCanvas: vi.fn()
}));
vi.mock('@/api/screen', () => ({
  listScreens: api.listScreens,
  getScreenCanvas: api.getScreenCanvas,
  listScreenDatasources: api.listScreenDatasources,
  saveScreenCanvas: api.saveScreenCanvas
}));

const router = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => router
}));

vi.mock('../PanoramaDatasourcePicker.vue', () => ({ default: {
  name: 'PanoramaDatasourcePicker',
  inheritAttrs: false,
  props: ['sources', 'modelValue', 'disabled', 'placeholder'],
  emits: ['update:modelValue', 'change'],
  template: '<select v-bind="$attrs" :value="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)">' +
    '<option value="">{{ placeholder || "请选择数据来源" }}</option>' +
    '<option v-for="source in sources" :key="source.id" :value="String(source.id)">{{ source.dsName }}</option>' +
    '</select>'
} }));

vi.mock('../PanoramaSettings.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PanoramaIntegrationReadiness.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PanoramaDataVerification.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../../presentation/editor/PresentationEditor.vue', () => ({ default: { template: '<div />' } }));

import PanoramaBindings from '../PanoramaBindings.vue';
import { resolveSourcePresentation } from '../sourcePresentation';
import { buildInstitutionRankingModel } from '../../presentation/model/institutionRankingModel';

const screen = { id: 9, screenCode: 'SCR_SIMPLE', screenName: '经营总览', bizLine: 'COMMON', viewLevel: 'PROVINCE', orgScopeMode: 'LEGACY_CONTEXT' };
const source = {
  id: 77, dsName: '指标来源', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE', bizLine: 'COMMON', status: 'ACTIVE',
  configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', fieldMeta: [
    { col: 'deposit_raw', alias: '存款原值', role: 'METRIC' },
    { col: 'sales_raw', alias: '营业收入', role: 'METRIC' },
    { col: 'sales_wan', alias: '营业收入（万元）', role: 'METRIC', unit: 'TEN_THOUSAND' },
    { col: 'percent_value', alias: '完成率', role: 'METRIC', unit: 'PERCENT' },
    { col: 'data_date', alias: '数据日期', role: 'DIM' },
    { col: '测试_直营零售存款', alias: '零售存款', role: 'METRIC' },
    { col: '测试_直营零售贷款', alias: '零售贷款', role: 'METRIC' },
    { col: '测试_直营对公存款', alias: '对公存款', role: 'METRIC' },
    { col: '测试_直营对公贷款', alias: '对公贷款', role: 'METRIC' },
    { col: 'org_code', alias: '机构号', role: 'DIM' },
    { col: 'org_name', alias: '机构名称', role: 'DIM' },
    { col: 'rank_value', alias: '排名值', role: 'METRIC', unit: 'YUAN' },
    { col: 'rank_increase', alias: '排名净增', role: 'METRIC', unit: 'YUAN' },
    { col: 'rank_average', alias: '排名日均', role: 'METRIC', unit: 'YUAN' },
    { col: 'org_name', alias: '机构名称', role: 'DIM' },
    { col: 'opaque_value', alias: '未声明字段', role: 'UNKNOWN' }
  ] })
};

function legacyCanvas() {
  return {
    screenId: 9, screenCode: 'SCR_SIMPLE', canvasVersion: 4,
    canvasStyleJson: JSON.stringify({
      schemaVersion: 1,
      background: '#050e2b',
      metricLabels: { deposit: '旧标题' },
      presentation: { type: 'CODE', template: 'branch-overview-v1' }
    }),
    canvasDraftJson: JSON.stringify({ components: [{
      id: 'deposit', component: 'ChartWidget', blockId: 41, innerType: 'METRIC_CARD',
      propValue: { bindingKey: 'deposit' },
      bindJson: JSON.stringify({ dsId: 77, period: 'LATEST', fields: { value: 'deposit_raw' }, units: { value: 'YUAN' } })
    }] })
  };
}

function schemaCanvas() {
  const components = [
    { componentId: 'card-a', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true,
      text: { titleMode: 'CUSTOM', title: '卡片 A', subtitle: '', description: '' },
      format: { displayUnit: 'YUAN', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED', emptyText: '—' },
      content: { mainField: 'deposit_raw', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
      interaction: { action: 'NONE' }, dataRefs: [{ blockId: 41, role: 'PRIMARY', metricCode: 'M_A', metricName: '存款', unit: 'YUAN', dimension: 'ORG' }] },
    { componentId: 'card-b', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 1, visible: true,
      text: { titleMode: 'CUSTOM', title: '卡片 B', subtitle: '', description: '' },
      format: { displayUnit: 'YUAN', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED', emptyText: '—' },
      content: { mainField: 'sales_raw', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
      interaction: { action: 'NONE' }, dataRefs: [{ blockId: 41, role: 'PRIMARY', metricCode: 'M_B', metricName: '收入', unit: 'YUAN', dimension: 'ORG' }] }
  ];
  return {
    screenId: 9, screenCode: 'SCR_SIMPLE', canvasVersion: 4,
    canvasStyleJson: JSON.stringify({ presentation: {
      type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1,
      institutionRules: { allowedOperatingLevels: ['PRIMARY'], allowedOrgNatures: ['SECONDARY_BRANCH'] },
      display: { components }
    } }),
    canvasDraftJson: JSON.stringify({ components: [
      { id: 'source-a', component: 'ChartWidget', blockId: 41, propValue: { bindingKey: 'deposit' },
        bindJson: JSON.stringify({ dsId: 77, period: 'LATEST', fields: { value: 'deposit_raw' }, units: { value: 'YUAN' } }) },
      { id: 'source-b', component: 'ChartWidget', blockId: 41, propValue: { bindingKey: 'deposit' },
        bindJson: JSON.stringify({ dsId: 77, period: 'LATEST', fields: { value: 'sales_raw' }, units: { value: 'YUAN' } }) }
    ] })
  };
}

function schemaCompositionCanvas() {
  const component = {
    componentId: 'composition-card', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', order: 0, visible: true,
    text: { titleMode: 'CUSTOM', title: '业务构成', subtitle: '', description: '' },
    format: { displayUnit: 'YUAN', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED', emptyText: '—' },
    content: { mainField: '', subFields: [], series: [], columns: [],
      tabs: [{ tabKey: 'business-structure', label: '业务结构', corporateField: 'deposit_raw', retailField: 'sales_raw', totalField: '', unit: 'YUAN' }], rankingMetrics: [] },
    interaction: { action: 'NONE' }, dataRefs: [{ blockId: 41, role: 'PRIMARY', metricCode: 'M_COMP', metricName: '业务构成', unit: 'YUAN', dimension: 'ORG' }]
  };
  return {
    screenId: 9, screenCode: 'SCR_SIMPLE', canvasVersion: 4,
    canvasStyleJson: JSON.stringify({ presentation: {
      type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1,
      display: { components: [component] }
    } }),
    canvasDraftJson: JSON.stringify({ components: [{ id: 'source-comp', component: 'ChartWidget', blockId: 41,
      propValue: { bindingKey: 'composition' }, bindJson: JSON.stringify({ dsId: 77, period: 'LATEST',
        fields: { corporate: 'deposit_raw', retail: 'sales_raw' }, units: { corporate: 'YUAN', retail: 'YUAN' } }) }] })
  };
}

function legacyProvinceCompositionCanvas() {
  const canvas = schemaCompositionCanvas();
  const style = JSON.parse(canvas.canvasStyleJson);
  const component = style.presentation.display.components[0];
  component.componentId = 'legacy-composition-64';
  component.dataRefs = [{ blockId: 64, role: 'PRIMARY', unit: 'YUAN' }];
  component.content.tabs = [{ tabKey: 'business-structure', label: '业务结构',
    corporateField: '测试_直营对公存款', retailField: '测试_直营零售存款', totalField: '', unit: 'YUAN' }];
  const draft = JSON.parse(canvas.canvasDraftJson);
  draft.components[0].blockId = 64;
  return { ...canvas, canvasStyleJson: JSON.stringify(style), canvasDraftJson: JSON.stringify(draft) };
}

function schemaGrowthCanvas() {
  const canvas = schemaCompositionCanvas();
  const style = JSON.parse(canvas.canvasStyleJson);
  const component = style.presentation.display.components[0];
  component.componentId = 'legacy-trend-57';
  component.componentType = 'TREND';
  component.layoutRegion = 'CENTER';
  component.dataRefs = [{ blockId: 57, role: 'PRIMARY', unit: 'YUAN' }];
  component.content = { mainField: '', subFields: [], columns: [], tabs: [], rankingMetrics: [], series: [
    { seriesKey: 'deposit', field: '总存款', label: '旧存款', unit: 'YUAN' },
    { seriesKey: 'loan', field: '总贷款', label: '旧贷款', unit: 'YUAN' },
    { seriesKey: 'depositIncrease', field: '存款净增', label: '旧净增', unit: 'YUAN' }
  ] };
  const draft = JSON.parse(canvas.canvasDraftJson);
  draft.components[0].blockId = 57;
  draft.components[0].propValue.bindingKey = 'trend';
  draft.components[0].bindJson = JSON.stringify({ dsId: 77, period: 'LAST_6M_EOM', fields: { date: 'data_date', deposit: '测试_直营零售存款' }, units: { date: '', deposit: 'YUAN' } });
  return { ...canvas, canvasStyleJson: JSON.stringify(style), canvasDraftJson: JSON.stringify(draft) };
}

function schemaRankingCanvas() {
  const canvas = schemaCompositionCanvas();
  const style = JSON.parse(canvas.canvasStyleJson);
  const component = style.presentation.display.components[0];
  component.componentId = 'legacy-ranking-58';
  component.componentType = 'RANKING';
  component.layoutRegion = 'RIGHT';
  component.dataRefs = [{ blockId: 58, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }];
  component.content = { mainField: '', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [
    { metricKey: 'value', field: 'deposit', label: '存款余额', unit: 'HUNDRED_MILLION', direction: 'DESC' },
    { metricKey: 'increase', field: 'increase', label: '净增', unit: 'HUNDRED_MILLION', direction: 'DESC' },
    { metricKey: 'average', field: 'average', label: '日均', unit: 'HUNDRED_MILLION', direction: 'DESC' }
  ] };
  const draft = JSON.parse(canvas.canvasDraftJson);
  draft.components[0].blockId = 58;
  draft.components[0].propValue.bindingKey = 'ranking';
  draft.components[0].bindJson = JSON.stringify({ dsId: 77, period: 'LATEST', fields: {
    orgCode: 'org_code', name: 'org_name', value: 'rank_value', increase: 'rank_increase', average: 'rank_average'
  }, units: { value: 'YUAN', increase: 'YUAN', average: 'YUAN' } });
  return { ...canvas, canvasStyleJson: JSON.stringify(style), canvasDraftJson: JSON.stringify(draft) };
}

function prepare(canvas) {
  api.listScreens.mockResolvedValue([screen]);
  api.getScreenCanvas.mockResolvedValue(canvas);
  api.listScreenDatasources.mockResolvedValue([source]);
  api.saveScreenCanvas.mockResolvedValue({ canvasVersion: 5 });
}

describe('PanoramaBindings simplified designer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    router.push.mockReset();
  });

  afterEach(() => vi.restoreAllMocks());

  it('只保留选屏、组件绑定、预览和保存入口', async () => {
    prepare(legacyCanvas());
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="screen-select"]').exists()).toBe(true));

    expect(wrapper.find('[data-testid="binding-preview"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="binding-save"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="binding-new-screen"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="binding-settings"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="binding-datasources"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="binding-auto-empty"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="binding-publish"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="binding-discard"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="binding-auto-preview"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="integration-readiness"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="verification-connected"]').exists()).toBe(false);
    expect(wrapper.find('.presentation-editor').exists()).toBe(false);
    expect(wrapper.find('[data-testid="legacy-migration-preview"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('legacy 标题写入 metricLabels，清空后保存并重载恢复默认标题', async () => {
    prepare(legacyCanvas());
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="component-title"]').exists()).toBe(true));
    await wrapper.get('[data-testid="component-title"]').setValue('全行存款余额');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));
    expect(api.saveScreenCanvas.mock.lastCall[0].canvasStyle.metricLabels).toEqual({ deposit: '全行存款余额' });
    expect(resolveSourcePresentation({ renderPackage: { canvasStyle: api.saveScreenCanvas.mock.lastCall[0].canvasStyle } }).metricLabels.deposit)
      .toBe('全行存款余额');

    await wrapper.get('[data-testid="component-title"]').setValue('');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(2));
    expect(api.saveScreenCanvas.mock.lastCall[0].canvasStyle.metricLabels).toEqual({});
    wrapper.unmount();
  });

  it('schema1 可按实际组件独立改标题和展示字段并保留协议字段与 blockId', async () => {
    prepare(schemaCanvas());
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-component-id="card-a"]').exists()).toBe(true));
    await wrapper.get('[data-component-id="card-a"]').trigger('click');
    expect(wrapper.get('[data-testid="component-main-field"]').find('option[value="org_name"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="component-main-field"]').find('option[value="opaque_value"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="component-main-field"]').find('option[value="percent_value"]').exists()).toBe(false);
    await wrapper.get('[data-testid="component-title"]').setValue('存款余额');
    await wrapper.get('[data-testid="component-main-field"]').setValue('sales_wan');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));

    const saved = api.saveScreenCanvas.mock.lastCall[0].canvasStyle.presentation;
    expect(saved.institutionRules).toEqual({ allowedOperatingLevels: ['PRIMARY'], allowedOrgNatures: ['SECONDARY_BRANCH'] });
    expect(saved.display.components[0]).toMatchObject({
      componentId: 'card-a',
      text: { titleMode: 'CUSTOM', title: '存款余额' },
      content: { mainField: 'sales_wan' },
      dataRefs: [{ blockId: 41, unit: 'TEN_THOUSAND' }]
    });
    expect(saved.display.components[1]).toMatchObject({ componentId: 'card-b', text: { title: '卡片 B' }, dataRefs: [{ blockId: 41 }] });

    await wrapper.get('[data-component-id="card-b"]').trigger('click');
    expect(wrapper.get('[data-testid="component-title"]').element.value).toBe('卡片 B');
    await wrapper.get('[data-testid="component-title"]').setValue('收入余额');
    expect(wrapper.vm.displaySession.presentation.display.components[0].text.title).toBe('存款余额');
    wrapper.unmount();
  });

  it('保存冲突时保留当前标题和绑定编辑态', async () => {
    prepare(legacyCanvas());
    api.saveScreenCanvas.mockRejectedValue(Object.assign(new Error('版本冲突'), { code: 'RPT-43012' }));
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="component-title"]').exists()).toBe(true));
    await wrapper.get('[data-testid="component-title"]').setValue('冲突后仍保留');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-conflict"]').exists()).toBe(true));
    expect(wrapper.get('[data-testid="component-title"]').element.value).toBe('冲突后仍保留');
    expect(wrapper.get('[data-testid="binding-conflict"]').text()).toContain('版本冲突');
    wrapper.unmount();
  });

  it('COMPOSITION_TABS 的 tab 源单位约束不被单列编辑改写', async () => {
    prepare(schemaCompositionCanvas());
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-component-id="composition-card"]').exists()).toBe(true));
    await wrapper.get('[data-component-id="composition-card"]').trigger('click');
    const corporate = wrapper.get('[data-testid="component-tab-field-business-structure-corporateField"]');
    expect(corporate.find('option[value="sales_wan"]').exists()).toBe(true);
    await corporate.setValue('sales_raw');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));
    const saved = api.saveScreenCanvas.mock.lastCall[0].canvasStyle.presentation.display.components[0];
    expect(saved.content.tabs[0]).toMatchObject({ corporateField: 'sales_raw', unit: 'YUAN' });
    wrapper.unmount();
  });

  it('分行有效存款/贷款分布各自可改标题并写入原组件 tabs，保留原 componentId 和 blockId', async () => {
    prepare(legacyProvinceCompositionCanvas());
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-config-key="overview-deposit-composition"]').exists()).toBe(true));
    expect(wrapper.find('[data-config-key="overview-loan-composition"]').exists()).toBe(true);
    await wrapper.get('[data-config-key="overview-deposit-composition"]').trigger('click');
    await wrapper.get('[data-testid="component-title"]').setValue('存款业务分布（自定义）');
    await wrapper.get('[data-testid="component-tab-field-deposit-corporateField"]').setValue('deposit_raw');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));
    const payload = api.saveScreenCanvas.mock.lastCall[0];
    const savedPresentation = payload.canvasStyle.presentation;
    expect(savedPresentation.display.components[0].componentId).toBe('legacy-composition-64');
    expect(savedPresentation.display.components[0].content.tabs.map(tab => tab.tabKey)).toEqual(['deposit', 'loan']);
    expect(savedPresentation.display.components[0].content.tabs[0]).toMatchObject({ label: '存款业务分布（自定义）', corporateField: 'deposit_raw' });
    expect(payload.components).toEqual(expect.arrayContaining([expect.objectContaining({ blockId: 64, propValue: { bindingKey: 'composition' } })]));
    wrapper.unmount();
  });

  it('增长曲线配置展示四个稳定 semantic 字段，新增单项写入对应 seriesKey 并保留旧 series', async () => {
    prepare(schemaGrowthCanvas());
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-config-key="business-growth"]').exists()).toBe(true));
    await wrapper.get('[data-config-key="business-growth"]').trigger('click');
    for (const semantic of ['retailDeposit', 'retailLoan', 'corpDeposit', 'corpLoan']) {
      expect(wrapper.find(`[data-testid="component-series-field-${semantic}"]`).exists()).toBe(true);
    }
    await wrapper.get('[data-testid="component-series-field-retailDeposit"]').setValue('测试_直营零售存款');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    expect(wrapper.find('.panorama-bindings__error').text()).toContain('单位');
    expect(api.saveScreenCanvas).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="component-series-unit-retailDeposit"]').setValue('YUAN');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));
    const savedSeries = api.saveScreenCanvas.mock.lastCall[0].canvasStyle.presentation.display.components[0].content.series;
    expect(savedSeries).toEqual(expect.arrayContaining([
      expect.objectContaining({ seriesKey: 'deposit', field: '总存款' }),
      expect.objectContaining({ seriesKey: 'retailDeposit', field: '测试_直营零售存款' })
    ]));
    wrapper.unmount();
  });

  it('机构排名只编辑 runtime 归一化字段，切换指标不写原 SQL 列名且保留其他指标', async () => {
    prepare(schemaRankingCanvas());
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-config-key="institution-ranking"]').exists()).toBe(true));
    await wrapper.get('[data-config-key="institution-ranking"]').trigger('click');
    const value = wrapper.get('[data-testid="component-ranking-field-value"]');
    expect(value.find('option[value="deposit"]').exists()).toBe(true);
    expect(value.element.value).toBe('deposit');
    expect(value.find('option[value="rank_value"]').exists()).toBe(false);
    await value.setValue('increase');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));
    const metrics = api.saveScreenCanvas.mock.lastCall[0].canvasStyle.presentation.display.components[0].content.rankingMetrics;
    expect(metrics[0]).toMatchObject({ field: 'increase', unit: 'HUNDRED_MILLION' });
    expect(metrics[1]).toMatchObject({ field: 'increase' });
    expect(metrics[2]).toMatchObject({ field: 'average' });
    const ranking = buildInstitutionRankingModel({
      institutions: [{ orgCode: 'A', orgName: '机构A', active: true, authorized: true }],
      rows: [{ orgCode: 'A', deposit: 100, increase: 7, average: 20 }],
      rankingMetrics: metrics
    });
    expect(ranking.rankable[0].value).toBe(7);
    wrapper.unmount();
  });

  it('卡片比较配置保存明确历史块、字段、日期与源单位', async () => {
    const canvas = schemaCanvas();
    const style = JSON.parse(canvas.canvasStyleJson);
    style.presentation.display.components.push({ componentId: 'legacy-trend-57', componentType: 'TREND', layoutRegion: 'CENTER', order: 0, visible: true,
      text: { titleMode: 'AUTO', title: '' }, format: { displayUnit: 'YUAN', decimals: 2 }, content: { series: [{ seriesKey: 'deposit', field: 'deposit_raw', label: '存款', unit: 'YUAN' }] }, interaction: { action: 'NONE' }, dataRefs: [{ blockId: 57, role: 'PRIMARY', unit: 'YUAN' }] });
    const draft = JSON.parse(canvas.canvasDraftJson);
    draft.components.push({ id: 'trend-source', component: 'ChartWidget', blockId: 57, propValue: { bindingKey: 'trend' },
      bindJson: JSON.stringify({ dsId: 77, period: 'LAST_6M_EOM', fields: { date: 'data_date', deposit: 'deposit_raw' }, units: { deposit: 'YUAN' } }) });
    prepare({ ...canvas, canvasStyleJson: JSON.stringify(style), canvasDraftJson: JSON.stringify(draft) });
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-component-id="card-a"]').exists()).toBe(true));
    await wrapper.get('[data-component-id="card-a"]').trigger('click');
    expect(wrapper.find('[data-testid="comparison-panel"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="comparison-mode"]').classes()).toContain('panorama-bindings__comparison-select');
    await wrapper.get('[data-testid="comparison-mode"]').setValue('custom');
    await wrapper.get('[data-testid="comparison-history-block"]').setValue('57');
    expect(wrapper.get('[data-testid="comparison-history-block"] option[value="57"]').text()).toBe('业务增长曲线');
    expect(wrapper.get('[data-testid="comparison-value-fields"]').attributes('multiple')).toBeUndefined();
    expect(wrapper.get('[data-testid="comparison-value-fields"]').classes()).toContain('panorama-bindings__comparison-select');
    await wrapper.get('[data-testid="comparison-value-fields"]').setValue(['deposit_raw']);
    await wrapper.get('[data-testid="comparison-date-field"]').setValue('data_date');
    await wrapper.get('[data-testid="comparison-source-unit"]').setValue('YUAN');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));
    expect(api.saveScreenCanvas.mock.lastCall[0].canvasStyle.presentation.display.comparisons['card-a']).toEqual({
      enabled: true, historyBlockId: 57, valueFields: ['deposit_raw'], dateField: 'data_date', sourceUnit: 'YUAN'
    });
    wrapper.unmount();
  });

  it('总览派生项允许多选比较字段，系统生成结算性存款也开放显式比较但标题只读', async () => {
    const canvas = schemaCanvas();
    const style = JSON.parse(canvas.canvasStyleJson);
    style.presentation.display.components.push({ componentId: 'legacy-trend-57', componentType: 'TREND', layoutRegion: 'CENTER', order: 0, visible: true,
      text: { titleMode: 'AUTO', title: '' }, format: { displayUnit: 'YUAN', decimals: 2 }, content: { series: [{ seriesKey: 'deposit', field: 'deposit_raw', label: '存款', unit: 'YUAN' }] }, interaction: { action: 'NONE' }, dataRefs: [{ blockId: 57, role: 'PRIMARY', unit: 'YUAN' }] });
    const draft = JSON.parse(canvas.canvasDraftJson);
    draft.components.push({ id: 'trend-source', component: 'ChartWidget', blockId: 57, propValue: { bindingKey: 'trend' },
      bindJson: JSON.stringify({ dsId: 77, period: 'LAST_6M_EOM', fields: { date: 'data_date', deposit: 'deposit_raw' }, units: { deposit: 'YUAN' } }) });
    prepare({ ...canvas, canvasStyleJson: JSON.stringify(style), canvasDraftJson: JSON.stringify(draft) });
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-config-key="overview-deposit"]').exists()).toBe(true));
    await wrapper.get('[data-config-key="overview-deposit"]').trigger('click');
    await wrapper.get('[data-testid="comparison-mode"]').setValue('custom');
    expect(wrapper.get('[data-testid="comparison-value-fields"]').attributes('multiple')).toBe('');
    expect(wrapper.get('[data-testid="comparison-value-fields"]').classes()).toContain('panorama-bindings__comparison-select--multiple');
    await wrapper.get('[data-config-key="overview-settlementDeposit"]').trigger('click');
    expect(wrapper.find('[data-testid="comparison-panel"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="component-title"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });
});
