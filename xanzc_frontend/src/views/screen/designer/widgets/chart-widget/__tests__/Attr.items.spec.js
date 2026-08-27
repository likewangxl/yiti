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
    { innerType: 'PIE_SHARE', needTimeseries: false },
    { innerType: 'RANK_LIST', needTimeseries: false }
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

function datasource(id, dsName, config, dsType = 'SINGLE', sourceKind = 'WIDE_TABLE') {
  return {
    id, dsName, dsType, sourceKind,
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

async function chooseCategoryColumn(wrapper, value, testId = 'chart-category-column') {
  const select = wrapper.find(`[data-testid="${testId}"]`);
  await select.setValue(value);
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

  it('fieldMeta 与 metrics 并存时合并候选，按 col 去重且 fieldMeta alias 优先', async () => {
    const element = {
      innerType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '聚合', {
      metrics: [
        { metricName: 'raw_metric' },
        { metricName: 'metric_from_metrics' },
        { metricName: 'duplicate_metric' },
        { metricName: '' },
        { metricName: '   ' }
      ],
      fieldMeta: [
        { col: 'raw_metric', alias: '元数据展示', role: 'METRIC' },
        { col: 'field_only', alias: '', role: 'METRIC' },
        { col: 'duplicate_metric', alias: '字段别名', role: 'METRIC' },
        { col: 'duplicate_metric', alias: '重复别名', role: 'METRIC' },
        { col: 'dimension_col', alias: '维度', role: 'DIM' },
        { col: '', alias: '空列', role: 'METRIC' }
      ]
    })]);

    expect(wrapper.vm.metricColumnOptions).toEqual([
      { col: 'raw_metric', label: '元数据展示' },
      { col: 'field_only', label: 'field_only' },
      { col: 'duplicate_metric', label: '字段别名' },
      { col: 'metric_from_metrics', label: 'metric_from_metrics' }
    ]);
  });

  it('fieldMeta 的 alias 作为展示 label，metrics 中未被覆盖的指标仍可选', async () => {
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
    expect(Array.from(select.element.options).map(option => option.textContent)).toEqual([
      '展示指标', 'raw_metric_should_not_win'
    ]);
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
      metrics: [{ metricName: 'metric_b' }]
    })]);

    expect(wrapper.vm.metricItemCols).toEqual(['metric_a']);
    expect(wrapper.vm.metricColumnOptions).toEqual([
      { col: 'metric_b', label: 'metric_b' },
      { col: 'metric_a', label: '历史标题' }
    ]);
  });

  it('选中两列后为每个数据项显示原始列名和可编辑 label', async () => {
    const element = {
      innerType: 'METRIC_CARD',
      bindJson: JSON.stringify({ dsId: 9010, period: 'LATEST', dsType: 'SINGLE' }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '全省存款聚合', {
      fieldMeta: [
        { col: 'metric_a', alias: '指标 A', role: 'METRIC' },
        { col: 'metric_b', alias: '指标 B', role: 'METRIC' }
      ]
    })]);

    await chooseMetricColumns(wrapper, ['metric_a', 'metric_b']);

    const rows = wrapper.findAll('[data-testid="chart-metric-item-row"]');
    expect(rows).toHaveLength(2);
    expect(rows.map(row => row.find('.metric-item-value').text())).toEqual([
      'metric_a', 'metric_b'
    ]);
    expect(rows.map(row => row.find('[data-testid="chart-metric-item-label"]').element.value)).toEqual([
      '指标 A', '指标 B'
    ]);
  });

  it('修改指标 label 不联动覆盖指标列候选的来源名称', async () => {
    const element = {
      innerType: 'METRIC_CARD',
      bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '聚合', {
      fieldMeta: [
        { col: 'core_balance', alias: '核心存款余额', role: 'METRIC' }
      ]
    })]);

    await chooseMetricColumns(wrapper, ['core_balance']);
    const row = wrapper.find('[data-testid="chart-metric-item-row"]');
    await row.find('[data-testid="chart-metric-item-label"]').setValue('自定义卡片标题');

    expect(wrapper.vm.metricColumnOptions).toEqual([
      { col: 'core_balance', label: '核心存款余额' }
    ]);
    expect(Array.from(wrapper.find('[data-testid="chart-metric-columns"]').element.options)
      .map(option => option.textContent)).toEqual(['核心存款余额']);
    expect(row.find('[data-testid="chart-metric-item-label"]').element.value).toBe('自定义卡片标题');
    expect(JSON.parse(element.bindJson).items).toEqual([
      { col: 'core_balance', label: '自定义卡片标题' }
    ]);
  });

  it('修改 label 时只更新对应 item，保留 col、其他 item 和绑定字段', async () => {
    const element = {
      innerType: 'METRIC_CARD',
      bindJson: JSON.stringify({
        dsId: 9010, period: 'LATEST', dsType: 'SINGLE', valueCol: 'keep',
        items: [
          { col: 'metric_a', label: '指标 A' },
          { col: 'metric_b', label: '指标 B' }
        ]
      }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '全省存款聚合', {
      fieldMeta: [
        { col: 'metric_a', alias: '指标 A', role: 'METRIC' },
        { col: 'metric_b', alias: '指标 B', role: 'METRIC' }
      ]
    })]);

    const rows = wrapper.findAll('[data-testid="chart-metric-item-row"]');
    await rows[0].find('[data-testid="chart-metric-item-label"]').setValue('自定义标题');

    expect(JSON.parse(element.bindJson)).toMatchObject({
      dsId: 9010, period: 'LATEST', dsType: 'SINGLE', valueCol: 'keep',
      items: [
        { col: 'metric_a', label: '自定义标题' },
        { col: 'metric_b', label: '指标 B' }
      ]
    });
  });

  it('已有自定义 label 在编辑器中回显，旧 item 无 label 时显示 alias 兜底', async () => {
    const element = {
      innerType: 'METRIC_CARD',
      bindJson: JSON.stringify({
        dsId: 9010,
        items: [
          { col: 'metric_a', label: '历史自定义标题' },
          { col: 'metric_b' }
        ]
      }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '全省存款聚合', {
      fieldMeta: [
        { col: 'metric_a', alias: '指标 A', role: 'METRIC' },
        { col: 'metric_b', alias: '指标 B', role: 'METRIC' }
      ]
    })]);

    const rows = wrapper.findAll('[data-testid="chart-metric-item-row"]');
    expect(rows).toHaveLength(2);
    expect(rows.map(row => row.find('[data-testid="chart-metric-item-label"]').element.value)).toEqual([
      '历史自定义标题', '指标 B'
    ]);
  });

  it('指标列编辑器覆盖六种支持显式 items 的图表类型（含占比饼图）', async () => {
    for (const innerType of ['METRIC_CARD', 'LINE_TREND', 'AREA_STACK', 'BAR_COMPARE', 'COMBO_CHART', 'PIE_SHARE']) {
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

  it('RANK_LIST 指标列改为多选 items，类目列单选并兼容历史 valueCol/nameCol', async () => {
    const element = {
      innerType: 'RANK_LIST',
      bindJson: JSON.stringify({ dsId: 9010, valueCol: 'metric_b', nameCol: 'org_name' }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '排行榜数据源', {
      fieldMeta: [
        { col: 'metric_a', alias: '指标 A', role: 'METRIC' },
        { col: 'metric_b', alias: '指标 B', role: 'METRIC' },
        { col: 'org_code', alias: '机构编码', role: 'DIM' },
        { col: 'org_name', alias: '机构', role: 'DIM' }
      ],
      metrics: [{ metricName: 'metric_c' }],
      table: 'ORG_INDEX_RESULT',
      aggregation: { groupBy: 'SUBJECT', agg: 'SUM' }
    })]);

    const metricSelect = wrapper.find('[data-testid="chart-metric-columns"]');
    const categorySelect = wrapper.find('[data-testid="rank-category-column"]');
    expect(metricSelect.exists()).toBe(true);
    expect(categorySelect.exists()).toBe(true);
    expect(wrapper.find('[data-testid="rank-metric-column"]').exists()).toBe(false);
    expect(wrapper.vm.metricColumnOptions).toEqual([
      { col: 'metric_a', label: '指标 A' },
      { col: 'metric_b', label: '指标 B' },
      { col: 'metric_c', label: 'metric_c' }
    ]);
    expect(wrapper.vm.metricItemCols).toEqual(['metric_b']);
    expect(categorySelect.element.value).toBe('org_name');

    await chooseMetricColumns(wrapper, ['metric_a', 'metric_c']);
    await chooseCategoryColumn(wrapper, 'org_code', 'rank-category-column');
    expect(JSON.parse(element.bindJson)).toMatchObject({
      dsId: 9010,
      categoryCol: 'org_code',
      items: [
        { col: 'metric_a', label: '指标 A' },
        { col: 'metric_c', label: 'metric_c' }
      ]
    });
    expect(JSON.parse(element.bindJson)).not.toHaveProperty('valueCol');
  });

  it('RANK_LIST 切换数据源时清空旧 valueCol', async () => {
    const element = {
      innerType: 'RANK_LIST',
      bindJson: JSON.stringify({
        dsId: 9010, valueCol: 'metric_a', categoryCol: 'org_name',
        nameCol: 'org_name', items: [{ col: 'metric_a', label: '旧指标' }]
      }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [
      datasource(9010, '旧排行榜数据源', { metrics: [{ metricName: 'metric_a' }] }),
      datasource(9011, '新排行榜数据源', { metrics: [{ metricName: 'metric_b' }] })
    ]);

    wrapper.vm.bind.dsId = 9011;
    await flushPromises();

    const saved = JSON.parse(element.bindJson);
    expect(saved).toMatchObject({ dsId: 9011 });
    expect(saved.items).toEqual([]);
    expect(saved).not.toHaveProperty('valueCol');
    expect(saved).not.toHaveProperty('categoryCol');
    expect(saved).not.toHaveProperty('nameCol');
  });

  it('TABLE_LIST 的机构主体聚合宽表候选自动增加 org_name，并可写回 items', async () => {
    const element = {
      innerType: 'TABLE_LIST',
      bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '机构聚合', {
      table: 'ORG_INDEX_RESULT',
      aggregation: { groupBy: 'SUBJECT', agg: 'SUM' },
      metrics: [{ metricName: 'balance' }]
    })]);

    expect(wrapper.find('[data-testid="chart-metric-columns"]').exists()).toBe(true);
    expect(wrapper.vm.metricColumnOptions).toEqual([
      { col: 'balance', label: 'balance' },
      { col: 'org_name', label: 'org_name' }
    ]);

    await chooseMetricColumns(wrapper, ['org_name']);
    expect(JSON.parse(element.bindJson).items).toEqual([
      { col: 'org_name', label: 'org_name' }
    ]);
  });

  it('TABLE_LIST 合并 DIM/METRIC 字段元数据，fieldMeta alias 优先且 DIM 可写回', async () => {
    const element = {
      innerType: 'TABLE_LIST',
      bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '机构聚合', {
      table: 'ORG_INDEX_RESULT',
      aggregation: { groupBy: 'SUBJECT', agg: 'SUM' },
      fieldMeta: [
        { col: 'org_name', alias: '机构名称', role: 'DIM' },
        { col: 'balance', alias: '余额', role: 'METRIC' },
        { col: 'dimension_only', alias: '区域', role: 'DIM' }
      ],
      metrics: [
        { metricName: 'balance' },
        { metricName: 'metric_only' },
        { metricName: 'org_name' }
      ]
    })]);

    expect(wrapper.vm.metricColumnOptions).toEqual([
      { col: 'org_name', label: '机构名称' },
      { col: 'balance', label: '余额' },
      { col: 'dimension_only', label: '区域' },
      { col: 'metric_only', label: 'metric_only' }
    ]);

    await chooseMetricColumns(wrapper, ['org_name', 'dimension_only']);
    expect(JSON.parse(element.bindJson).items).toEqual([
      { col: 'org_name', label: '机构名称' },
      { col: 'dimension_only', label: '区域' }
    ]);
  });

  it('数值图表指标列候选仍排除 DIM 字段元数据', async () => {
    const element = {
      innerType: 'METRIC_CARD',
      bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '聚合', {
      fieldMeta: [
        { col: 'org_name', alias: '机构名称', role: 'DIM' },
        { col: 'balance', alias: '余额', role: 'METRIC' }
      ],
      metrics: [{ metricName: 'metric_only' }]
    })]);

    expect(wrapper.vm.metricColumnOptions).toEqual([
      { col: 'balance', label: '余额' },
      { col: 'metric_only', label: 'metric_only' }
    ]);
  });

  it('BAR_COMPARE 类目轴候选合并字段元数据与 metrics，并补充机构主体运行时列', async () => {
    const element = {
      innerType: 'BAR_COMPARE',
      bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '机构聚合', {
      table: 'ORG_INDEX_RESULT',
      aggregation: { groupBy: 'SUBJECT', agg: 'SUM' },
      fieldMeta: [
        { col: 'org_code', alias: '机构编码', role: 'DIM' },
        { col: 'balance', alias: '余额', role: 'METRIC' }
      ],
      metrics: [
        { metricName: 'balance' },
        { metricName: 'growth' }
      ]
    })]);

    const select = wrapper.find('[data-testid="chart-category-column"]');
    expect(select.exists()).toBe(true);
    expect(wrapper.vm.categoryColumnOptions).toEqual([
      { col: 'org_code', label: '机构编码' },
      { col: 'balance', label: '余额' },
      { col: 'growth', label: 'growth' },
      { col: 'org_name', label: 'org_name' }
    ]);

    await chooseCategoryColumn(wrapper, 'org_name');
    expect(JSON.parse(element.bindJson).categoryCol).toBe('org_name');
  });

  it('BAR_COMPARE 类目轴允许不选择，并明确提示未选择时不显示类目信息', async () => {
    const element = {
      innerType: 'BAR_COMPARE',
      bindJson: JSON.stringify({ dsId: 9010, categoryCol: '' }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '机构聚合', {
      fieldMeta: [{ col: 'org_name', alias: '机构名称', role: 'DIM' }],
      metrics: [{ metricName: 'balance' }]
    })]);

    const select = wrapper.find('[data-testid="chart-category-column"]');
    expect(select.attributes('placeholder')).toContain('可不选择');
    expect(wrapper.findAll('.attr-hint').some(hint => hint.text().includes('未选择时不显示类目信息'))).toBe(true);
    await chooseCategoryColumn(wrapper, '');
    expect(JSON.parse(element.bindJson).categoryCol).toBe('');
  });

  it('BAR_COMPARE 类目轴按聚合口径补充列，NONE 不凭空增加维度', async () => {
    const cases = [
      {
        config: { table: 'ORG_INDEX_RESULT', aggregation: { groupBy: 'DATE', agg: 'SUM' } },
        expected: 'data_date'
      },
      { config: { table: 'ORG_INDEX_RESULT' }, expected: 'data_date' },
      {
        config: { table: 'ORG_INDEX_RESULT', aggregation: { groupBy: 'NONE', agg: 'SUM' } },
        expected: null
      },
      {
        config: { table: 'EMP_INDEX_RESULT', aggregation: { groupBy: 'SUBJECT', agg: 'SUM' } },
        expected: 'emp_id'
      }
    ];

    for (const [index, candidate] of cases.entries()) {
      const element = {
        innerType: 'BAR_COMPARE',
        bindJson: JSON.stringify({ dsId: 9200 + index }),
        styleJson: '{}', drillJson: '{}', propValue: {}
      };
      const wrapper = await mountAttr(element, [datasource(
        9200 + index,
        `柱状数据源${index}`,
        candidate.config
      )]);
      const columns = wrapper.vm.categoryColumnOptions.map(item => item.col);
      if (candidate.expected) expect(columns).toContain(candidate.expected);
      else expect(columns).not.toEqual(expect.arrayContaining(['data_date', 'org_code', 'emp_id', 'org_name']));
      wrapper.unmount();
    }
  });

  it('BAR_COMPARE 保留旧类目列回显，切换数据源时清空 categoryCol 与 items', async () => {
    const element = {
      innerType: 'BAR_COMPARE',
      bindJson: JSON.stringify({
        dsId: 9300, categoryCol: 'legacy_category', items: [{ col: 'metric_a', label: '旧指标' }], valueCol: 'keep'
      }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [
      datasource(9300, '旧数据源', { metrics: [{ metricName: 'metric_a' }] }),
      datasource(9301, '新数据源', { metrics: [{ metricName: 'metric_b' }] })
    ]);

    expect(wrapper.vm.categoryColumnOptions).toContainEqual({ col: 'legacy_category', label: 'legacy_category' });
    wrapper.vm.bind.dsId = 9301;
    await flushPromises();

    expect(JSON.parse(element.bindJson)).toMatchObject({ dsId: 9301, valueCol: 'keep', categoryCol: '' });
    expect(JSON.parse(element.bindJson).items).toEqual([]);
  });

  it('仅精确匹配机构主体聚合宽表时增加 org_name，其他配置不增加', async () => {
    const cases = [
      { table: 'ORG_INDEX_RESULT', aggregation: { groupBy: 'NONE', agg: 'SUM' } },
      { table: 'ORG_INDEX_RESULT', aggregation: { groupBy: 'DATE', agg: 'SUM' } },
      { table: 'ORG_INDEX_RESULT' },
      { table: 'OTHER_TABLE', aggregation: { groupBy: 'SUBJECT', agg: 'SUM' } },
      { table: 'ORG_INDEX_RESULT', aggregation: { groupBy: 'SUBJECT', agg: 'SUM' }, sourceKind: 'CUSTOM_SQL' }
    ];

    for (const [index, candidate] of cases.entries()) {
      const element = {
        innerType: 'TABLE_LIST',
        bindJson: JSON.stringify({ dsId: 9100 + index }),
        styleJson: '{}', drillJson: '{}', propValue: {}
      };
      const wrapper = await mountAttr(element, [datasource(
        9100 + index,
        `非目标数据源${index}`,
        { table: candidate.table, aggregation: candidate.aggregation },
        'SINGLE',
        candidate.sourceKind || 'WIDE_TABLE'
      )]);
      expect(wrapper.vm.metricColumnOptions.some(item => item.col === 'org_name')).toBe(false);
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
      innerType: 'FUNNEL_CHART', bindJson: JSON.stringify({ dsId: 9010 }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = await mountAttr(element, [datasource(9010, '聚合', {
      metrics: [{ metricName: 'metric_a' }]
    })]);

    expect(wrapper.find('[data-testid="chart-metric-columns"]').exists()).toBe(false);
  });
});
