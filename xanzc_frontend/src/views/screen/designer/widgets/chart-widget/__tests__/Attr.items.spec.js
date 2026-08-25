// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const mocks = vi.hoisted(() => ({
  listScreenDatasources: vi.fn(),
  listOrgGroups: vi.fn(),
  pushSnapshotDebounced: vi.fn()
}));

vi.mock('@/api/screen', () => ({
  listScreenDatasources: (...args) => mocks.listScreenDatasources(...args),
  listOrgGroups: (...args) => mocks.listOrgGroups(...args),
  probeScreenDatasourceColumns: vi.fn()
}));
vi.mock('@/stores/screenDesigner', () => ({
  useScreenDesignerStore: () => ({ pushSnapshotDebounced: mocks.pushSnapshotDebounced })
}));
vi.mock('@/views/screen/designer/widgets', () => ({
  chartMetas: [
    { innerType: 'METRIC_CARD', needTimeseries: false },
    { innerType: 'LINE_TREND', needTimeseries: true },
    { innerType: 'AREA_STACK', needTimeseries: true },
    { innerType: 'BAR_COMPARE', needTimeseries: false },
    { innerType: 'COMBO_CHART', needTimeseries: false },
    { innerType: 'PIE_SHARE', needTimeseries: false }
  ]
}));
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn() } }));

import Attr from '../Attr.vue';

const ElSelectStub = {
  props: { modelValue: null, multiple: Boolean },
  emits: ['update:modelValue', 'change'],
  template: `
    <select v-bind="$attrs" :multiple="multiple" :value="modelValue" @change="update">
      <slot />
    </select>
  `,
  methods: {
    update(event) {
      const value = this.multiple
        ? Array.from(event.target.selectedOptions).map(option => option.value)
        : event.target.value;
      this.$emit('update:modelValue', value);
      this.$emit('change', value);
    }
  }
};
const ElInputStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
};

const stubs = {
  CommonAttr: { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': ElSelectStub,
  'el-option': { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
  'el-input': ElInputStub,
  'el-input-number': ElInputStub,
  'el-button': { template: '<button><slot /></button>' },
  'el-switch': true,
  'el-radio-group': true,
  'el-radio-button': true
};

function datasource(id, dsName, config, dsType = 'SINGLE') {
  return {
    id, dsName, dsType, sourceKind: 'WIDE_TABLE',
    configJson: JSON.stringify(config)
  };
}

async function mountAttr(element, rows) {
  mocks.listScreenDatasources.mockResolvedValue(rows);
  mocks.listOrgGroups.mockResolvedValue([]);
  const wrapper = mount(Attr, { props: { element }, global: { stubs } });
  await flushPromises();
  return wrapper;
}

async function chooseMetricColumns(wrapper, values) {
  const select = wrapper.find('[data-testid="chart-metric-columns"]');
  for (const option of select.element.options) option.selected = values.includes(option.value);
  await select.trigger('change');
}

describe('ChartWidget Attr 指标列绑定', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('从 metrics 读取候选列，选中后写入 bind.items 且保留其他绑定字段', async () => {
    const element = {
      innerType: 'METRIC_CARD',
      bindJson: JSON.stringify({ dsId: 9010, period: 'LATEST', dsType: 'SINGLE', valueCol: 'keep' }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '全省存款聚合', {
      metrics: [
        { metricName: '一般性存款月均余额较上月-机构' },
        { metricName: '一般性存款月均余额-机构' }
      ]
    })]);

    const select = wrapper.find('[data-testid="chart-metric-columns"]');
    expect(select.exists()).toBe(true);
    expect(Array.from(select.element.options).map(option => option.textContent)).toEqual([
      '一般性存款月均余额较上月-机构', '一般性存款月均余额-机构'
    ]);

    await chooseMetricColumns(wrapper, ['一般性存款月均余额-机构']);
    expect(JSON.parse(element.bindJson)).toMatchObject({
      dsId: 9010,
      valueCol: 'keep',
      items: [{ col: '一般性存款月均余额-机构', label: '一般性存款月均余额-机构' }]
    });
  });

  it('fieldMeta 的 alias 只作为展示 label，col 保持原始列名', async () => {
    const element = {
      innerType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '聚合', {
      metrics: [{ metricName: 'raw_metric_should_not_win' }],
      fieldMeta: [
        { col: 'raw_metric', alias: '展示指标', role: 'METRIC' },
        { col: 'dimension_col', alias: '维度', role: 'DIM' }
      ]
    })]);

    const select = wrapper.find('[data-testid="chart-metric-columns"]');
    expect(Array.from(select.element.options).map(option => option.textContent)).toEqual(['展示指标']);
    await chooseMetricColumns(wrapper, ['raw_metric']);

    expect(JSON.parse(element.bindJson).items).toEqual([{ col: 'raw_metric', label: '展示指标' }]);
  });

  it('已有 items 回显并保留已有 label', async () => {
    const element = {
      innerType: 'METRIC_CARD',
      bindJson: JSON.stringify({
        dsId: 9010,
        items: [{ col: 'metric_a', label: '历史标题' }]
      }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '聚合', {
      metrics: [{ metricName: 'metric_a' }, { metricName: 'metric_b' }]
    })]);

    expect(wrapper.vm.metricItemCols).toEqual(['metric_a']);
    expect(wrapper.vm.metricColumnOptions).toEqual([
      { col: 'metric_a', label: '历史标题' },
      { col: 'metric_b', label: 'metric_b' }
    ]);
  });

  it('指标列编辑器覆盖五种支持显式 items 的图表类型', async () => {
    for (const innerType of ['METRIC_CARD', 'LINE_TREND', 'AREA_STACK', 'BAR_COMPARE', 'COMBO_CHART']) {
      const element = {
        innerType, bindJson: JSON.stringify({ dsId: 9010 }),
        styleJson: '{}', drillJson: '{}', propValue: {}
      };
      const dsType = ['LINE_TREND', 'AREA_STACK'].includes(innerType) ? 'TIMESERIES' : 'SINGLE';
      const wrapper = await mountAttr(element, [datasource(9010, '聚合', {
        metrics: [{ metricName: 'metric_a' }]
      }, dsType)]);
      expect(wrapper.find('[data-testid="chart-metric-columns"]').exists()).toBe(true);
      wrapper.unmount();
    }
  });

  it('切换数据源时清空旧 items，但保留其他 bind 字段', async () => {
    const element = {
      innerType: 'METRIC_CARD',
      bindJson: JSON.stringify({
        dsId: 9010, period: 'LATEST', valueCol: 'keep',
        items: [{ col: 'metric_a', label: '旧指标' }]
      }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [
      datasource(9010, '旧数据源', { metrics: [{ metricName: 'metric_a' }] }),
      datasource(9011, '新数据源', { metrics: [{ metricName: 'metric_b' }] })
    ]);

    wrapper.vm.bind.dsId = 9011;
    await flushPromises();

    expect(JSON.parse(element.bindJson)).toMatchObject({ dsId: 9011, period: 'LATEST', valueCol: 'keep' });
    expect(JSON.parse(element.bindJson).items).toEqual([]);
  });

  it('非指标列绑定图表不显示指标列编辑器', async () => {
    const element = {
      innerType: 'PIE_SHARE', bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '聚合', {
      metrics: [{ metricName: 'metric_a' }]
    })]);

    expect(wrapper.find('[data-testid="chart-metric-columns"]').exists()).toBe(false);
  });
});
