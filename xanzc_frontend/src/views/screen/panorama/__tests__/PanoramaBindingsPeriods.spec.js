// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listScreens: vi.fn(),
  getScreenCanvas: vi.fn(),
  listScreenDatasources: vi.fn(),
  saveScreenCanvas: vi.fn(),
  publishScreenCanvas: vi.fn(),
  listScreenMetricCatalog: vi.fn(async () => []),
  saveScreenDatasource: vi.fn(),
  tryRunScreenDatasource: vi.fn()
}));
vi.mock('@/api/screen', () => ({
  listScreens: api.listScreens,
  getScreenCanvas: api.getScreenCanvas,
  listScreenDatasources: api.listScreenDatasources,
  saveScreenCanvas: api.saveScreenCanvas,
  publishScreenCanvas: api.publishScreenCanvas,
  listScreenMetricCatalog: api.listScreenMetricCatalog,
  saveScreenDatasource: api.saveScreenDatasource,
  tryRunScreenDatasource: api.tryRunScreenDatasource
}));

const { permissionStore } = vi.hoisted(() => ({ permissionStore: {
  loaded: true, canAccess: vi.fn(() => true), load: vi.fn()
} }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => permissionStore }));

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
vi.mock('../MetricBindingComposer.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../../presentation/editor/PresentationEditor.vue', () => ({ default: { template: '<div />' } }));

import PanoramaBindings from '../PanoramaBindings.vue';

const screen = {
  id: 9, screenCode: 'SCR_PERIODS', screenName: '分行经营总览',
  bizLine: 'COMMON', viewLevel: 'PROVINCE', orgScopeMode: 'LEGACY_CONTEXT'
};
const supportedPeriods = ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'];

function canvas(periods, bindingPeriod = 'LATEST') {
  return {
    screenId: 9,
    screenCode: 'SCR_PERIODS',
    canvasVersion: 4,
    canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
    canvasDraftJson: JSON.stringify({ components: [{
      id: 'deposit', component: 'ChartWidget', blockId: 31, innerType: 'METRIC_CARD',
      propValue: { bindingKey: 'deposit' },
      bindJson: JSON.stringify({ dsId: 9032, period: bindingPeriod,
        fields: { value: 'deposit_raw' }, units: { value: 'HUNDRED_MILLION' } })
    }] })
  };
}

let wrapper;

async function mountWithPeriods(timeParamJson, bindingPeriod = 'LATEST') {
  api.listScreens.mockResolvedValue([screen]);
  api.getScreenCanvas.mockResolvedValue(canvas(timeParamJson, bindingPeriod));
  api.listScreenDatasources.mockResolvedValue([{
    id: 9032, dsName: '分行汇总来源', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE',
    bizLine: 'COMMON', status: 'ACTIVE', timeParamJson,
    configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', fieldMeta: [
      { col: 'deposit_raw', alias: '存款余额', role: 'METRIC', unit: 'HUNDRED_MILLION' }
    ] })
  }]);
  wrapper = mount(PanoramaBindings, {
    props: { screenId: 9 },
    global: {
      stubs: { 'el-button': true, 'el-dialog': true },
      directives: { 'bp-overflow-tooltip': { mounted: () => {} } }
    }
  });
  await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-period"]').exists()).toBe(true));
  await flushPromises();
  return wrapper;
}

function periodOptionValues(target) {
  return [...target.get('[data-testid="binding-period"]').element.options].map(option => option.value);
}

describe('PanoramaBindings datasource periods', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => wrapper?.unmount());

  it('解析 JSON 字符串形式的支持周期列表', async () => {
    const target = await mountWithPeriods(JSON.stringify(supportedPeriods));

    expect(periodOptionValues(target)).toEqual(supportedPeriods);
    expect(target.find('[role="alert"]').exists()).toBe(false);
  });

  it('直接接受接口返回的周期数组', async () => {
    const target = await mountWithPeriods(supportedPeriods);

    expect(periodOptionValues(target)).toEqual(supportedPeriods);
    expect(target.find('[role="alert"]').exists()).toBe(false);
  });

  it.each(['not-json', JSON.stringify({ period: 'LATEST' }), 17])('非法周期值 %j 不被当成已声明能力', async value => {
    const target = await mountWithPeriods(value);

    expect(periodOptionValues(target)).toEqual(['LATEST']);
    expect(target.find('[role="alert"]').text()).toContain('来源未声明');
  });

  it.each([null, ''])('来源未提供周期时沿用兼容默认值（%j）', async value => {
    const target = await mountWithPeriods(value);

    expect(periodOptionValues(target)).toEqual(supportedPeriods);
    expect(target.find('[role="alert"]').exists()).toBe(false);
  });
});
