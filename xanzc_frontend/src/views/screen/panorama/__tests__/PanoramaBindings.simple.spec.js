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

const screen = { id: 9, screenCode: 'SCR_SIMPLE', screenName: '经营总览', bizLine: 'COMMON', viewLevel: 'PROVINCE', orgScopeMode: 'LEGACY_CONTEXT' };
const source = {
  id: 77, dsName: '指标来源', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE', bizLine: 'COMMON', status: 'ACTIVE',
  configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', fieldMeta: [
    { col: 'deposit_raw', alias: '存款原值', role: 'METRIC' },
    { col: 'sales_raw', alias: '营业收入', role: 'METRIC' },
    { col: 'sales_wan', alias: '营业收入（万元）', role: 'METRIC', unit: 'TEN_THOUSAND' },
    { col: 'percent_value', alias: '完成率', role: 'METRIC', unit: 'PERCENT' },
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
    expect(corporate.find('option[value="sales_wan"]').exists()).toBe(false);
    await corporate.setValue('sales_raw');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));
    const saved = api.saveScreenCanvas.mock.lastCall[0].canvasStyle.presentation.display.components[0];
    expect(saved.content.tabs[0]).toMatchObject({ corporateField: 'sales_raw', unit: 'YUAN' });
    wrapper.unmount();
  });
});
