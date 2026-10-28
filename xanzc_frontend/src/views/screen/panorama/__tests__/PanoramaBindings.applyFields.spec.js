// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
const api = vi.hoisted(() => ({ listScreens: vi.fn(), getScreenCanvas: vi.fn(), listScreenDatasources: vi.fn(),
  listScreenMetricCatalog: vi.fn(async () => []), saveScreenCanvas: vi.fn(), saveScreenDatasource: vi.fn(), publishScreenCanvas: vi.fn() }));
vi.mock('@/api/screen', () => api);
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => ({ loaded: true, canAccess: () => true }) }));
vi.mock('../PanoramaDataVerification.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../MetricBindingComposer.vue', () => ({ default: { name: 'MetricBindingComposer', props: ['fieldSpecs', 'initialFields'], emits: ['apply'], template: '<div />' } }));
vi.mock('../PanoramaDatasourcePicker.vue', () => ({ default: { template: '<div />' } }));
import PanoramaBindings from '../PanoramaBindings.vue';

const wrappers = [];
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.clearAllMocks(); });

async function prepare(type, content, slot, fields, units, referenceBlockId = 10) {
  const component = { componentId: 'actual-component', componentType: type, visible: true, order: 0, layoutRegion: 'LEFT',
    text: { titleMode: 'CUSTOM', title: '实际组件' }, format: { displayUnit: 'AUTO', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED', emptyText: '—' },
    interaction: { action: 'NONE' }, dataRefs: [{ blockId: referenceBlockId, role: 'PRIMARY', metricCode: 'M_EXISTING', metricName: '既有指标', unit: 'YUAN', dimension: 'ORG' }],
    content: { mainField: '', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [], ...content } };
  const screen = { id: 9, screenCode: 'CORP', screenName: '对公', bizLine: 'CORP', orgScopeMode: 'LEGACY_CONTEXT', status: 'ACTIVE' };
  let canvas = { canvasVersion: 4, canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'corporate-overview-v1', displaySchemaVersion: 1,
    institutionRules: { allowedOperatingLevels: ['PRIMARY'], allowedOrgNatures: ['SECONDARY_BRANCH'] }, display: { components: [component] } } }),
    canvasDraftJson: JSON.stringify({ components: [{ id: 'block', blockId: 10, component: 'ChartWidget', innerType: 'METRIC_CARD', propValue: { bindingKey: slot },
      bindJson: JSON.stringify({ dsId: 77, period: 'LATEST', fields, units }) }] }) };
  const fieldMeta = Object.entries({ ...fields, NEW_AMOUNT: 'new_amount', NEW_OTHER: 'new_other', NEW_NAME: 'new_name' }).map(([semantic, col]) => ({ col,
    role: ['name', 'orgCode', 'date', 'NEW_NAME'].includes(semantic) ? 'DIM' : 'METRIC', unit: ['name', 'orgCode', 'date', 'NEW_NAME'].includes(semantic) ? undefined : 'YUAN' }));
  api.listScreens.mockResolvedValue([screen]); api.getScreenCanvas.mockImplementation(async () => canvas);
  api.saveScreenDatasource.mockResolvedValue(77);
  api.listScreenDatasources.mockResolvedValue([{ id: 77, dsName: '既有来源', sourceKind: 'WIDE_TABLE', dsType: 'SINGLE', bizLine: 'CORP', status: 'ACTIVE', configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', fieldMeta }) }]);
  api.saveScreenCanvas.mockImplementation(async payload => { canvas = { canvasVersion: 5, canvasStyleJson: JSON.stringify(payload.canvasStyle), canvasDraftJson: JSON.stringify({ components: payload.components }) }; return canvas; });
  const create = async () => { const w = mount(PanoramaBindings, { props: { screenId: 9 }, global: { directives: { 'bp-overflow-tooltip': {} }, stubs: {
    'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' }, 'el-dialog': true
  } } }); wrappers.push(w); await flushPromises(); return w; };
  return { wrapper: await create(), create };
}

async function applyAndSave(wrapper, fields, units = {}) {
  wrapper.findComponent({ name: 'MetricBindingComposer' }).vm.$emit('apply', { ok: true, existingDatasourceId: 77, binding: { period: 'LATEST', fields, units } });
  await flushPromises();
  expect(wrapper.find('.panorama-bindings__error').exists(), wrapper.text()).toBe(false);
  await wrapper.get('[data-testid="binding-save"]').trigger('click'); await flushPromises();
  expect(api.saveScreenCanvas, wrapper.text()).toHaveBeenCalled();
  return api.saveScreenCanvas.mock.lastCall[0];
}

describe('实际配置页应用字段并保存重载', () => {
  it('旧布局创建替代来源时保留同一组件尚未编辑的字段元数据', async () => {
    const state = await prepare('METRIC_CARD', { mainField: 'old_amount' }, 'corpDeposit', { value: 'old_amount', change: 'old_other' }, { value: 'YUAN', change: 'PERCENT' });
    state.wrapper.unmount();
    const canvas = await api.getScreenCanvas();
    canvas.canvasStyleJson = JSON.stringify({ presentation: { type: 'CODE', template: 'corporate-overview-v1' } });
    const wrapper = await state.create();
    wrapper.findComponent({ name: 'MetricBindingComposer' }).vm.$emit('apply', { ok: true,
      datasourceRequest: { sourceKind: 'WIDE_TABLE', configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', fieldMeta: [{ col: 'new_amount', role: 'METRIC', unit: 'YUAN' }] }) },
      binding: { period: 'LATEST', fields: { value: 'new_amount' }, units: { value: 'YUAN' } } });
    await flushPromises();
    expect(api.saveScreenDatasource).toHaveBeenCalled();
    expect(JSON.parse(api.saveScreenDatasource.mock.lastCall[0].configJson).fieldMeta.map(item => item.col)).toContain('old_other');
  });
  it('展示组件引用区块缺失时禁止错用上一个组件的绑定', async () => {
    const { wrapper } = await prepare('METRIC_CARD', { mainField: 'old_amount' }, 'corpDeposit', { value: 'old_amount' }, { value: 'YUAN' }, 999);
    expect(wrapper.findComponent({ name: 'MetricBindingComposer' }).exists()).toBe(false);
    expect(wrapper.text()).toContain('来源区块');
  });
  it('对公地图沿用授权机构目录和排名，不提供未被运行态消费的指标绑定', async () => {
    const { wrapper } = await prepare('MAP', { mainField: 'deposit' }, 'branches', { orgCode: 'org_code' }, {});
    expect(wrapper.findComponent({ name: 'MetricBindingComposer' }).exists()).toBe(false);
    expect(wrapper.get('[data-testid="binding-guidance"]').text()).toContain('机构');
  });
  it('对公排名应用原始来源列，同时保留运行态归一化字段与单位', async () => {
    const { wrapper } = await prepare('RANKING', { rankingMetrics: [{ metricKey: 'deposit', field: 'deposit', label: '存款', unit: 'HUNDRED_MILLION', direction: 'DESC' }] },
      'corpRanking', { orgCode: 'org_code', name: 'org_name', deposit: 'old_amount' }, { deposit: 'YUAN' });
    const payload = await applyAndSave(wrapper, { deposit: 'new_amount' }, { deposit: 'YUAN' });
    expect(JSON.parse(payload.components[0].bindJson).fields.deposit).toBe('new_amount');
    expect(payload.canvasStyle.presentation.display.components[0].content.rankingMetrics[0]).toMatchObject({ field: 'deposit', unit: 'HUNDRED_MILLION' });
  });
  it('普通构成逐页签回写并保留未编辑字段和展示单位', async () => {
    const { wrapper, create } = await prepare('COMPOSITION_TABS', { tabs: [{ tabKey: 'deposit', label: '存款', corporateField: 'old_amount', retailField: 'old_other', totalField: 'old_total', unit: 'HUNDRED_MILLION' }] },
      'corpDeposit', { value: 'old_amount' }, { value: 'YUAN' });
    const payload = await applyAndSave(wrapper, { 'tab:deposit:corporateField': 'new_amount', 'tab:deposit:retailField': 'new_other' });
    expect(payload.canvasStyle.presentation.display.components[0].content.tabs[0]).toMatchObject({ corporateField: 'new_amount', retailField: 'new_other', totalField: 'old_total', unit: 'HUNDRED_MILLION' });
    const reloaded = await create();
    expect(reloaded.findComponent({ name: 'MetricBindingComposer' }).props('initialFields')['tab:deposit:corporateField']).toBe('new_amount');
  });
  it('指标副字段与主字段独立回写，兼容字符串副字段', async () => {
    const { wrapper } = await prepare('METRIC_CARD', { mainField: 'old_amount', subFields: ['old_other'] }, 'corpDeposit', { value: 'old_amount' }, { value: 'YUAN' });
    const payload = await applyAndSave(wrapper, { 'sub:old_other': 'new_other' }, { 'sub:old_other': 'YUAN' });
    const content = payload.canvasStyle.presentation.display.components[0].content;
    expect(content.mainField).toBe('old_amount'); expect(content.subFields[0].field || content.subFields[0]).toBe('new_other');
  });
  it('明细维度依据真实来源role选择并回写，隐藏列保持原样', async () => {
    const { wrapper } = await prepare('DETAIL_TABLE', { columns: [
      { columnKey: 'businessLabel', field: 'old_name', label: '名称', unit: 'AUTO', visible: true },
      { columnKey: 'hidden', field: 'old_hidden', label: '隐藏', unit: 'YUAN', visible: false }
    ] }, 'corpTargets', { name: 'old_name', actual: 'old_amount', target: 'old_target' }, { actual: 'YUAN', target: 'YUAN' });
    expect(wrapper.findComponent({ name: 'MetricBindingComposer' }).props('fieldSpecs').find(f => f.semantic === 'column:businessLabel')?.kind).toBe('dimension');
    expect(wrapper.find('[data-testid="component-column-field-hidden"]').exists()).toBe(false);
    const payload = await applyAndSave(wrapper, { 'column:businessLabel': 'new_name' });
    expect(payload.canvasStyle.presentation.display.components[0].content.columns.map(c => c.field)).toEqual(['new_name', 'old_hidden']);
  });
});
