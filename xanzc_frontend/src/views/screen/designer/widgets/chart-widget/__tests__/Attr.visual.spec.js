// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const listScreenDatasources = vi.fn();
const listOrgGroups = vi.fn();
const pushSnapshotDebounced = vi.fn();

vi.mock('@/api/screen', () => ({
  listScreenDatasources: (...args) => listScreenDatasources(...args),
  listOrgGroups: (...args) => listOrgGroups(...args),
  probeScreenDatasourceColumns: vi.fn()
}));
vi.mock('@/stores/screenDesigner', () => ({
  useScreenDesignerStore: () => ({ pushSnapshotDebounced })
}));
vi.mock('@/views/screen/designer/widgets', () => ({
  chartMetas: [
    { innerType: 'LINE_TREND', needTimeseries: true },
    { innerType: 'PIE_SHARE', needTimeseries: false },
    { innerType: 'METRIC_CARD', needTimeseries: false },
    { innerType: 'BAR_COMPARE', needTimeseries: false },
    { innerType: 'AREA_STACK', needTimeseries: true }
  ]
}));
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn() } }));

import Attr from '../Attr.vue';

const ElSelectStub = {
  props: ['modelValue'], emits: ['update:modelValue', 'change'],
  methods: {
    update(event) {
      this.$emit('update:modelValue', event.target.value);
      this.$emit('change', event.target.value);
    }
  },
  template: '<select v-bind="$attrs" :value="modelValue" @change="update"><slot /></select>'
};
const ElInputStub = {
  props: ['modelValue'], emits: ['update:modelValue'],
  template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
};
const ElSwitchStub = {
  props: ['modelValue'], emits: ['update:modelValue', 'change'],
  template: '<input v-bind="$attrs" type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked); $emit(\'change\', $event.target.checked)" />'
};
const stubs = {
  CommonAttr: { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': ElSelectStub,
  'el-option': { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
  'el-input': ElInputStub,
  'el-input-number': ElInputStub,
  'el-button': { template: '<button><slot /></button>' },
  'el-switch': ElSwitchStub,
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { props: ['label'], template: '<button type="button">{{ label }}</button>' }
};

describe('ChartWidget Attr 视觉预设', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listScreenDatasources.mockResolvedValue([]);
    listOrgGroups.mockResolvedValue([]);
  });

  it('视觉预设与折线开关写入 styleJson，旧节点缺省值不覆盖既有字段', async () => {
    const element = {
      innerType: 'LINE_TREND', bindJson: '{}',
      styleJson: JSON.stringify({ title: '已有标题', showLegend: false }), drillJson: '{}', propValue: {}
    };
    const wrapper = mount(Attr, { props: { element }, global: { stubs } });
    await flushPromises();

    expect(wrapper.vm.styleCfg.title).toBe('已有标题');
    expect(wrapper.vm.styleCfg.showLegend).toBe(false);
    expect(wrapper.vm.styleCfg.visualPreset).toBe('aurora');
    await wrapper.find('[data-testid="chart-visual-preset"]').setValue('graphite');
    expect(JSON.parse(element.styleJson)).toMatchObject({ visualPreset: 'graphite', title: '已有标题', showLegend: false });

    await wrapper.find('[data-testid="chart-show-labels"]').setValue(true);
    expect(JSON.parse(element.styleJson).showLabels).toBe(true);
    expect(pushSnapshotDebounced).toHaveBeenCalled();
  });

  it('按图表类型展示形态配置并持久化饼图与指标卡 variant', async () => {
    const pie = { innerType: 'PIE_SHARE', bindJson: '{}', styleJson: '{}', drillJson: '{}', propValue: {} };
    const pieWrapper = mount(Attr, { props: { element: pie }, global: { stubs } });
    await flushPromises();
    expect(pieWrapper.find('[data-testid="pie-shape"]').exists()).toBe(true);
    pieWrapper.vm.styleCfg.pieShape = 'rose';
    pieWrapper.vm.syncStyle();
    expect(JSON.parse(pie.styleJson).pieShape).toBe('rose');

    const card = { innerType: 'METRIC_CARD', bindJson: '{}', styleJson: '{}', drillJson: '{}', propValue: {} };
    const cardWrapper = mount(Attr, { props: { element: card }, global: { stubs } });
    await flushPromises();
    expect(cardWrapper.find('[data-testid="card-variant"]').exists()).toBe(true);
    expect(cardWrapper.find('[data-testid="chart-show-trend"]').exists()).toBe(true);
    expect(cardWrapper.find('[data-testid="chart-show-trend"]').element.checked).toBe(false);
    cardWrapper.vm.styleCfg.cardVariant = 'glass';
    cardWrapper.vm.syncStyle();
    expect(JSON.parse(card.styleJson).cardVariant).toBe('glass');
    await cardWrapper.find('[data-testid="chart-show-trend"]').setValue(true);
    expect(JSON.parse(card.styleJson).showTrend).toBe(true);
  });
});
