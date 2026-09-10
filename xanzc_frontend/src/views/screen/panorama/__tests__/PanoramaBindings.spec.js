// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listScreens: vi.fn(),
  getScreenCanvas: vi.fn(),
  getScreenView: vi.fn(),
  listScreenDatasources: vi.fn(),
  saveScreenCanvas: vi.fn(),
  publishScreenCanvas: vi.fn(),
  discardScreenCanvas: vi.fn(),
  queryScreenData: vi.fn(),
  listOrgProfiles: vi.fn(),
  listOrgGroups: vi.fn()
}));
vi.mock('@/api/screen', () => api);

const router = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn() }));
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => router
}));

const { dashboardStub } = vi.hoisted(() => ({
  dashboardStub: {
    name: 'PanoramaDashboard',
    props: ['model', 'loading', 'error', 'demo'],
    emits: ['refresh', 'back', 'configure', 'branch-select'],
    template: '<div data-testid="runtime-dashboard">'
      + '<div data-testid="runtime-kpi">{{ model?.kpis?.[0]?.value }}</div>'
      + '<button data-action="runtime-configure" @click="$emit(\'configure\')">配置</button>'
      + '<button data-action="runtime-back" @click="$emit(\'back\')">返回</button>'
      + '</div>'
  }
}));
vi.mock('../PanoramaDashboard.vue', () => ({ default: dashboardStub }));

const { datasourcePickerStub } = vi.hoisted(() => ({
  datasourcePickerStub: {
    name: 'PanoramaDatasourcePicker',
    inheritAttrs: false,
    props: ['sources', 'modelValue', 'disabled', 'placeholder'],
    emits: ['update:modelValue', 'change'],
    methods: {
      sourceLabel(source = {}) {
        const label = source.dsName || source.ds_name || source.dsCode || `数据源 #${source.id}`;
        return source.__compositionColumnsUnsupported ? `${label}（当前双列模式不支持）` : label;
      },
      select(event) {
        const value = event.target.value;
        this.$emit('update:modelValue', value);
        this.$emit('change', value);
      }
    },
    template: '<select v-bind="$attrs" :value="modelValue" :disabled="disabled" @change="select">'
      + '<option value="">{{ placeholder }}</option>'
      + '<option v-for="source in sources" :key="source.id" :value="String(source.id)" :disabled="source.__compositionColumnsUnsupported">'
      + '{{ sourceLabel(source) }}</option>'
      + '</select>'
  }
}));
vi.mock('../PanoramaDatasourcePicker.vue', () => ({ default: datasourcePickerStub }));

import PanoramaBindings from '../PanoramaBindings.vue';
import PanoramaRuntime from '../PanoramaRuntime.vue';

const screen = { id: 9, screenCode: 'SCR_CODE', screenName: '经营总览', viewLevel: 'PROVINCE',
  bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT', status: 'ACTIVE' };
const canvas = {
  screenId: 9, screenCode: 'SCR_CODE', canvasVersion: 4,
  canvasStyleJson: JSON.stringify({ schemaVersion: 1, background: '#050e2b' }),
  canvasDraftJson: JSON.stringify({ components: [{ id: 'old', component: 'ChartWidget', blockId: 41,
    innerType: 'METRIC_CARD', style: { top: 1, left: 2 }, propValue: {}, bindJson: '{}', styleJson: '{}', drillJson: '{}' }] })
};
const datasource = { id: 77, dsName: '存款', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE', bizLine: 'COMMON', status: 'ACTIVE',
  configJson: JSON.stringify({ fieldMeta: [{ col: 'deposit_raw', alias: '存款原值', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' }] }) };
const compositionColumnsDatasource = {
  id: 79, dsName: '构成宽表', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE', bizLine: 'COMMON', status: 'ACTIVE',
  configJson: JSON.stringify({
    table: 'ORG_INDEX_RESULT',
    fieldMeta: [
      { col: 'name_raw', alias: '构成名称', role: 'DIM' },
      { col: 'value_raw', alias: '构成值', role: 'METRIC' },
      { col: 'corporate_raw', alias: '对公', role: 'METRIC' },
      { col: 'retail_raw', alias: '零售', role: 'METRIC' }
    ]
  })
};
const compositionUnsupportedDatasource = {
  id: 78, dsName: '自定义构成查询', dsType: 'SINGLE', sourceKind: 'CUSTOM_SQL', bizLine: 'COMMON', status: 'ACTIVE',
  configJson: JSON.stringify({
    fieldMeta: [
      { col: 'corporate_raw', alias: '对公', role: 'METRIC' },
      { col: 'retail_raw', alias: '零售', role: 'METRIC' }
    ]
  })
};
const autoDatasource = {
  id: 83, dsName: '机构指标汇总（明确指标目录）', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE', bizLine: 'COMMON', status: 'ACTIVE',
  configJson: JSON.stringify({
    table: 'ORG_INDEX_RESULT', scopeMode: 'GLOBAL', aggregation: { groupBy: 'NONE', agg: 'SUM' },
    fieldMeta: [{ col: 'manual_deposit', role: 'METRIC' }],
    metrics: [
      { metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 },
      { metricCode: 'M_0277', metricName: '对公一般性存款余额-机构', slot: 18 },
      { metricCode: 'M_0309', metricName: '零售一般性存款余额-机构', slot: 30 }
    ]
  })
};

describe('PanoramaBindings', () => {
  beforeEach(() => {
    router.push.mockReset();
    router.back.mockReset();
    api.queryScreenData.mockReset();
    api.listOrgProfiles.mockReset();
    api.listOrgGroups.mockReset();
  });

  afterEach(() => vi.restoreAllMocks());

  it('零售空草稿只展示零售指标，明确保存零售模板且不继承全行数据源', async () => {
    api.listScreens.mockResolvedValue([{ ...screen, bizLine: 'RETAIL', screenName: '零售经营总览' }]);
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValue([
      datasource, { ...datasource, id: 88, dsName: '零售客户资产', bizLine: 'RETAIL' }
    ]);
    api.saveScreenCanvas.mockResolvedValue({ canvasVersion: 5 });
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="slot-retailAum"]').exists()).toBe(true));
    expect(wrapper.find('[data-testid="slot-deposit"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="slot-datasource"] option[value="77"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="readiness-slot-retailAum"]').exists()).toBe(true);
    await wrapper.find('[data-testid="slot-datasource"]').setValue('88');
    await wrapper.find('[data-testid="field-option-retailAum-value"]').setValue('deposit_raw');
    await wrapper.find('[data-testid="unit-retailAum-value"]').setValue('HUNDRED_MILLION');
    await wrapper.find('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalled());
    expect(api.saveScreenCanvas.mock.lastCall[0].canvasStyle.presentation.template).toBe('retail-overview-v1');
    expect(api.saveScreenCanvas.mock.lastCall[0].components[0].propValue.bindingKey).toBe('retailAum');
    wrapper.unmount();
  });

  it('零售命名机构组排名只能用引擎机构号标识机构，不能选择机构名称', async () => {
    api.listScreens.mockResolvedValue([{ ...screen, bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP' }]);
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValue([{ ...datasource, id: 88, bizLine: 'RETAIL', configJson: JSON.stringify({
      table: 'ORG_INDEX_RESULT', subjectCol: 'org_code', aggregation: { groupBy: 'SUBJECT' },
      fieldMeta: [{ col: 'amount', role: 'METRIC' }, { col: 'org_name', role: 'DIM' }]
    }) }]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="slot-retailRanking"]').exists()).toBe(true));
    await wrapper.find('[data-testid="slot-retailRanking"]').trigger('click');
    await wrapper.find('[data-testid="slot-datasource"]').setValue('88');
    const identity = wrapper.get('[data-testid="field-option-retailRanking-orgCode"]');
    expect(identity.find('option[value="org_code"]').exists()).toBe(true);
    expect(identity.find('option[value="org_name"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('读取服务端草稿并显示转换提示，不把旧组件静默当作新绑定树', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue(canvas);
    api.listScreenDatasources.mockResolvedValue([datasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(9));
    expect(wrapper.find('[data-testid="legacy-conversion-warning"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="conversion-confirm"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="try-run"]').exists()).toBe(false);
  });

  it('加载当前屏后显示 14 槽静态接入检查，未点击前不读取机构管理目录', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValue([datasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(wrapper.find('[data-testid="integration-readiness"]').exists()).toBe(true));
    expect(wrapper.findAll('[data-testid^="readiness-slot-"]')).toHaveLength(14);
    expect(wrapper.text()).toContain('静态预检');
    expect(wrapper.text()).toContain('未发布任何版本');
    expect(api.listOrgProfiles).not.toHaveBeenCalled();
    expect(api.listOrgGroups).not.toHaveBeenCalled();
  });

  it('屏选择器切换到 B 时传递选中的 ID，并加载 B 画布', async () => {
    const screenB = { ...screen, id: 10, screenCode: 'SCR_OTHER', screenName: '另一张屏' };
    const canvasB = { ...canvas, screenId: 10, screenCode: 'SCR_OTHER', canvasVersion: 8 };
    api.listScreens.mockResolvedValue([screen, screenB]);
    api.listScreenDatasources.mockResolvedValue([datasource]);
    api.getScreenCanvas.mockImplementation(id => Promise.resolve(id === 10 ? canvasB : canvas));
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(9));
    await wrapper.find('[data-testid="screen-select"]').setValue('10');
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(10));
    expect(wrapper.find('[data-testid="screen-select"]').element.value).toBe('10');
    expect(wrapper.find('.panorama-bindings__screen-meta').text()).toContain('SCR_OTHER');
    await vi.waitFor(() => expect(wrapper.find('.panorama-bindings__footer').text()).toContain('当前版本 8'));
  });

  it('提供返回工作区和管理数据源入口，并使用路由守卫', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue(canvas);
    api.listScreenDatasources.mockResolvedValue([datasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(9));
    await wrapper.find('[data-testid="binding-back-workspace"]').trigger('click');
    await wrapper.find('[data-testid="binding-datasources"]').trigger('click');
    expect(router.push).toHaveBeenNthCalledWith(1, '/workspace');
    expect(router.push).toHaveBeenNthCalledWith(2, '/screen-admin/datasources');
  });

  it('字段候选来自 config fieldMeta，单位默认标记为原始元值，不调用试跑', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValue([datasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    await vi.waitFor(() => expect(wrapper.find('[data-testid="slot-datasource"] option[value="77"]').exists()).toBe(true));
    await wrapper.find('[data-testid="slot-datasource"]').setValue('77');
    await vi.waitFor(() => expect(wrapper.text()).toContain('存款原值'));
    expect(wrapper.text()).toContain('存款原值');
    expect(wrapper.text()).toContain('原始元值');
    expect(wrapper.find('[data-testid="field-option-deposit-value"] option[value="data_date"]').exists()).toBe(false);
    await wrapper.find('[data-testid="slot-trend"]').trigger('click');
    await wrapper.find('[data-testid="slot-datasource"]').setValue('77');
    expect(wrapper.find('[data-testid="field-option-trend-date"] option[value="data_date"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="field-option-trend-deposit"] option[value="data_date"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="try-run"]').exists()).toBe(false);
  });

  it('未选数据源时字段和单位禁用，picker change 传入新 ID 后恢复配置', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValue([datasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    expect(wrapper.find('[data-testid="datasource-option-count"]').text()).toContain('当前屏可选数据源：1 个');
    expect(wrapper.find('[data-testid="datasource-required-hint"]').text()).toContain('请先选择数据源');
    expect(wrapper.find('[data-testid="field-option-deposit-value"]').element.disabled).toBe(true);
    expect(wrapper.find('[data-testid="unit-deposit-value"]').element.disabled).toBe(true);

    wrapper.findComponent(datasourcePickerStub).vm.$emit('change', '77');
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="field-option-deposit-value"]').element.disabled).toBe(false);
    expect(wrapper.find('[data-testid="unit-deposit-value"]').element.disabled).toBe(false);
    expect(wrapper.find('[data-testid="datasource-required-hint"]').exists()).toBe(false);
  });

  it('选择展示内容时只对唯一明确候选自动填来源、字段和亿元单位，并显示待核验摘要', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({
      ...canvas,
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [] })
    });
    api.listScreenDatasources.mockResolvedValue([autoDatasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-auto-preview"]').exists()).toBe(true));
    await wrapper.find('[data-testid="slot-depositAverage"]').trigger('click');

    expect(wrapper.find('[data-testid="slot-datasource"]').element.value).toBe('83');
    expect(wrapper.find('[data-testid="field-option-depositAverage-value"]').element.value).toBe('一般性存款月均余额-机构');
    expect(wrapper.find('[data-testid="unit-depositAverage-value"]').element.value).toBe('HUNDRED_MILLION');
    expect(wrapper.find('[data-testid="binding-auto-notice"]').text()).toContain('待核验');
    expect(wrapper.find('[data-testid="binding-auto-applied"]').text()).toContain('机构指标汇总');
  });

  it('一键配置空白展示内容不覆盖已有手工字段，且来源切换按语义预填', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({
      ...canvas,
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [{
        id: 'deposit', component: 'ChartWidget', blockId: 41, innerType: 'METRIC_CARD',
        propValue: { bindingKey: 'deposit' },
        bindJson: JSON.stringify({ dsId: 83, period: 'LATEST', fields: { value: 'manual_deposit' }, units: { value: 'YUAN' } })
      }] })
    });
    const switchedSource = {
      ...autoDatasource,
      id: 84,
      dsName: '切换后的机构指标',
      configJson: JSON.stringify({
        table: 'ORG_INDEX_RESULT', scopeMode: 'GLOBAL', aggregation: { groupBy: 'NONE', agg: 'SUM' },
        fieldMeta: [{ col: 'manual_deposit', role: 'METRIC' }],
        metrics: [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }]
      })
    };
    api.listScreenDatasources.mockResolvedValue([autoDatasource, switchedSource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-auto-preview"]').exists()).toBe(true));

    await wrapper.find('[data-testid="binding-auto-empty"]').trigger('click');
    expect(wrapper.vm.collectValidBindings().bindings.deposit.fields.value).toBe('manual_deposit');
    await wrapper.find('[data-testid="slot-composition"]').trigger('click');
    expect(wrapper.find('[data-testid="composition-mode"]').element.value).toBe('columns');
    await wrapper.find('[data-testid="slot-depositAverage"]').trigger('click');
    await wrapper.find('[data-testid="slot-datasource"]').setValue('84');
    expect(wrapper.find('[data-testid="field-option-depositAverage-value"]').element.value).toBe('一般性存款月均余额-机构');
    expect(wrapper.find('[data-testid="unit-depositAverage-value"]').element.value).toBe('HUNDRED_MILLION');
  });

  it('已选择数据源但没有适用字段时明确提示，不猜测字段', async () => {
    const noMetricDatasource = {
      ...datasource,
      id: 82,
      dsName: '只有机构维度',
      configJson: JSON.stringify({ fieldMeta: [{ col: 'org_code', alias: '机构号', role: 'DIM' }] })
    };
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValue([noMetricDatasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    await wrapper.find('[data-testid="slot-datasource"]').setValue('82');
    expect(wrapper.find('[data-testid="datasource-fields-unavailable-hint"]').text()).toContain('没有适用字段候选');
    expect(wrapper.find('[data-testid="field-option-deposit-value"] option[value="org_code"]').exists()).toBe(false);
  });

  it('columns 模式的可选数据源计数排除保留的禁用旧来源', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({
      ...canvas,
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [] })
    });
    api.listScreenDatasources.mockResolvedValue([compositionUnsupportedDatasource, compositionColumnsDatasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    await wrapper.find('[data-testid="slot-composition"]').trigger('click');
    await wrapper.find('[data-testid="composition-mode"]').setValue('columns');
    expect(wrapper.find('[data-testid="datasource-option-count"]').text()).toContain('当前屏可选数据源：1 个');
    expect(wrapper.find('[data-testid="slot-datasource"] option[value="78"]').exists()).toBe(false);
  });

  it('NAMED_GROUP 城市汇总的机构号只显示内置 org_code，不把 org_name 当身份候选', async () => {
    const namedGroupScreen = { ...screen, orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'ORG_RETAIL' };
    const namedGroupDatasource = {
      ...datasource,
      sourceKind: 'WIDE_TABLE',
      configJson: JSON.stringify({
        table: 'ORG_INDEX_RESULT', subjectCol: 'org_code',
        aggregation: { groupBy: 'SUBJECT' },
        metrics: [{ metricName: 'deposit_raw' }],
        fieldMeta: [{ col: 'org_name', alias: '机构名称', role: 'DIM' }]
      })
    };
    api.listScreens.mockResolvedValue([namedGroupScreen]);
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValue([namedGroupDatasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    await wrapper.find('[data-testid="slot-citySummary"]').trigger('click');
    await wrapper.find('[data-testid="slot-datasource"]').setValue('77');
    expect(wrapper.find('[data-testid="field-option-citySummary-orgCode"] option[value="org_code"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="field-option-citySummary-orgCode"] option[value="org_name"]').exists()).toBe(false);
  });

  it('业务构成按 rows/columns 切换互斥字段，columns 只展示双列来源并保留数据源与周期', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({
      ...canvas,
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [] })
    });
    api.listScreenDatasources.mockResolvedValue([compositionUnsupportedDatasource, compositionColumnsDatasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    await wrapper.find('[data-testid="slot-composition"]').trigger('click');
    expect(wrapper.find('[data-testid="composition-mode"]').element.value).toBe('rows');
    expect(wrapper.find('.panorama-bindings__editor-head').text()).toContain('名称/构成值均需绑定');
    expect(wrapper.find('[data-testid="field-option-composition-name"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="field-option-composition-value"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="field-option-composition-corporate"]').exists()).toBe(false);

    await wrapper.find('[data-testid="slot-datasource"]').setValue('79');
    await wrapper.find('[data-testid="binding-period"]').setValue('LAST_1M');
    await wrapper.find('[data-testid="field-option-composition-name"]').setValue('name_raw');
    await wrapper.find('[data-testid="field-option-composition-value"]').setValue('value_raw');
    await wrapper.find('[data-testid="unit-composition-value"]').setValue('YUAN');
    await wrapper.find('[data-testid="composition-mode"]').setValue('columns');

    expect(wrapper.find('[data-testid="composition-mode"]').element.value).toBe('columns');
    expect(wrapper.find('[data-testid="field-option-composition-name"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="field-option-composition-value"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="field-option-composition-corporate"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="field-option-composition-retail"]').exists()).toBe(true);
    expect(wrapper.find('.panorama-bindings__editor-head').text()).toContain('对公金额/零售金额均需绑定');
    expect(wrapper.find('[data-testid="slot-datasource"]').element.value).toBe('79');
    expect(wrapper.find('[data-testid="binding-period"]').element.value).toBe('LAST_1M');
    expect(wrapper.find('[data-testid="slot-datasource"] option[value="78"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="composition-mode-hint"]').text()).toContain('恰好一行');
    expect(wrapper.find('[data-testid="composition-mode-hint"]').text()).toContain('对公/零售');

    await wrapper.find('[data-testid="field-option-composition-corporate"]').setValue('corporate_raw');
    await wrapper.find('[data-testid="field-option-composition-retail"]').setValue('retail_raw');
    await wrapper.find('[data-testid="unit-composition-corporate"]').setValue('YUAN');
    await wrapper.find('[data-testid="unit-composition-retail"]').setValue('YUAN');
    await wrapper.find('[data-testid="composition-mode"]').setValue('rows');
    expect(wrapper.find('[data-testid="composition-mode"]').element.value).toBe('rows');
    expect(wrapper.find('[data-testid="slot-datasource"]').element.value).toBe('79');
    expect(wrapper.find('[data-testid="binding-period"]').element.value).toBe('LAST_1M');
    expect(wrapper.find('[data-testid="field-option-composition-name"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="field-option-composition-corporate"]').exists()).toBe(false);
  });

  it('既有 columns 绑定若来源不满足后端固定宽表约束，保存前报告硬错误', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({
      ...canvas,
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [{
        id: 'composition', component: 'ChartWidget', blockId: 41, innerType: 'PIE_SHARE',
        propValue: { bindingKey: 'composition' },
        bindJson: JSON.stringify({
          dsId: 78, period: 'LATEST', fields: { corporate: 'corporate_raw', retail: 'retail_raw' },
          units: { corporate: 'YUAN', retail: 'YUAN' }
        })
      }] })
    });
    api.listScreenDatasources.mockResolvedValue([compositionUnsupportedDatasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });

    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    await wrapper.find('[data-testid="binding-save"]').trigger('click');
    expect(wrapper.find('.panorama-bindings__error').text()).toContain('仅允许 WIDE_TABLE 且表为 ORG_INDEX_RESULT');
    expect(api.saveScreenCanvas).not.toHaveBeenCalled();
  });

  it('保存代码草稿固定 presentation 和组件样式，发布必须填写 reason；冲突保留编辑态', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasDraftJson: JSON.stringify({ components: [] }) });
    api.listScreenDatasources.mockResolvedValue([datasource]);
    api.saveScreenCanvas.mockRejectedValue(Object.assign(new Error('版本冲突'), { code: 'RPT-43012' }));
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    await vi.waitFor(() => expect(wrapper.find('[data-testid="slot-datasource"] option[value="77"]').exists()).toBe(true));
    await wrapper.find('[data-testid="slot-datasource"]').setValue('77');
    await wrapper.find('[data-testid="field-option-deposit-value"]').setValue('deposit_raw');
    await wrapper.find('[data-testid="unit-deposit-value"]').setValue('HUNDRED_MILLION');
    await wrapper.find('[data-testid="binding-save"]').trigger('click');
    expect(api.saveScreenCanvas).toHaveBeenCalledWith(expect.objectContaining({
      screenId: 9, expectedVersion: 4,
      canvasStyle: expect.objectContaining({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      components: expect.any(Array)
    }));
    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-conflict"]').exists()).toBe(true));
    expect(wrapper.find('[data-testid="binding-conflict"]').text()).toContain('版本冲突');
    expect(api.publishScreenCanvas).not.toHaveBeenCalled();
    await wrapper.find('[data-testid="publish-reason"]').setValue('发布经营总览');
    await wrapper.find('[data-testid="binding-publish"]').trigger('click');
    expect(api.publishScreenCanvas).not.toHaveBeenCalled();
  });

  it('读取到列角色不匹配的旧绑定时，保存会拒绝而不把日期当作指标', async () => {
    api.listScreens.mockResolvedValue([screen]);
    api.getScreenCanvas.mockResolvedValue({
      ...canvas,
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [{
        id: 'bad', component: 'ChartWidget', blockId: 41, innerType: 'METRIC_CARD',
        propValue: { bindingKey: 'deposit' },
        bindJson: JSON.stringify({ dsId: 77, period: 'LATEST', fields: { value: 'data_date' }, units: { value: 'YUAN' } })
      }] })
    });
    api.listScreenDatasources.mockResolvedValue([datasource]);
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(api.listScreenDatasources).toHaveBeenCalled());
    await wrapper.find('[data-testid="binding-save"]').trigger('click');
    expect(wrapper.find('.panorama-bindings__error').text()).toContain('字段类型或元数据不匹配');
    expect(api.saveScreenCanvas).not.toHaveBeenCalled();
  });

  it('Runtime 挂载真实 Dashboard 并把适配后的 KPI 传给它，配置入口导航到当前屏', async () => {
    api.queryScreenData.mockResolvedValue({
      columns: ['deposit_raw'],
      rows: [[100000000]],
      columnsMeta: [{ col: 'deposit_raw', amountScale: 'YUAN' }]
    });
    const view = {
      screenId: 9,
      screenCode: 'SCR_CODE',
      runtimeSchemaVersion: 2,
      renderPackage: {
        canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
        components: [{ component: 'ChartWidget', blockId: 51, propValue: { bindingKey: 'deposit' } }],
        bindSnapshots: { 51: { bind: { dsId: 77, period: 'LATEST', fields: { value: 'deposit_raw' }, units: { value: 'YUAN' } } } }
      }
    };
    const wrapper = mount(PanoramaRuntime, { props: { view, context: { screenCode: 'SCR_CODE' } } });
    await vi.waitFor(() => expect(api.queryScreenData).toHaveBeenCalledWith(expect.objectContaining({ blockId: 51 })));
    await vi.waitFor(() => expect(wrapper.find('[data-testid="runtime-kpi"]').text()).toBe('1'));
    expect(wrapper.find('[data-testid="runtime-dashboard"]').exists()).toBe(true);

    await wrapper.find('[data-action="runtime-configure"]').trigger('click');
    expect(router.push).toHaveBeenCalledWith({ path: '/screen-admin/designer', query: { screenId: '9' } });
  });

  it('Runtime 带 backPath 时只导航一次并保留 back 事件', async () => {
    const view = { screenId: 9, screenCode: 'SCR_CODE', runtimeSchemaVersion: 2,
      renderPackage: { canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } }, components: [] } };
    const wrapper = mount(PanoramaRuntime, { props: { view, context: { screenCode: 'SCR_CODE' }, backPath: '/screens' } });
    await wrapper.get('[data-action="runtime-back"]').trigger('click');
    expect(router.push).toHaveBeenCalledTimes(1);
    expect(router.push).toHaveBeenCalledWith('/screens');
    expect(router.back).not.toHaveBeenCalled();
    expect(wrapper.emitted('back')).toHaveLength(1);
  });

  it('屏 A 保存未返回时切到屏 B，迟到的 A 响应不能覆盖 B 草稿', async () => {
    const screenB = { ...screen, id: 10, screenCode: 'SCR_OTHER', screenName: '另一张屏' };
    const canvasB = { ...canvas, screenId: 10, screenCode: 'SCR_OTHER', canvasVersion: 8 };
    api.listScreens.mockResolvedValue([screen, screenB]);
    api.listScreenDatasources.mockResolvedValue([datasource]);
    let resolveSave;
    let resolveB;
    let firstCanvas = true;
    api.getScreenCanvas.mockImplementation(id => {
      if (id === 9 && firstCanvas) {
        firstCanvas = false;
        return Promise.resolve(canvas);
      }
      if (id === 10) return new Promise(resolve => { resolveB = resolve; });
      return Promise.resolve(canvas);
    });
    api.saveScreenCanvas.mockImplementation(() => new Promise(resolve => { resolveSave = resolve; }));
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 } });
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(9));
    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-save"]').element.disabled).toBe(false));
    await wrapper.find('[data-testid="conversion-confirm"]').setValue(true);
    await wrapper.find('[data-testid="slot-datasource"]').setValue('77');
    await wrapper.find('[data-testid="field-option-deposit-value"]').setValue('deposit_raw');
    await wrapper.find('[data-testid="unit-deposit-value"]').setValue('HUNDRED_MILLION');

    await wrapper.find('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledWith(expect.objectContaining({ screenId: 9 })));
    expect(wrapper.find('[data-testid="screen-select"]').element.disabled).toBe(true);

    await wrapper.setProps({ screenId: 10 });
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(10));
    resolveB(canvasB);
    expect(wrapper.find('[data-testid="screen-select"]').element.disabled).toBe(true);
    resolveSave({ canvasVersion: 99, canvasDraftJson: JSON.stringify({ components: [] }) });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="screen-select"]').element.disabled).toBe(false));
    await vi.waitFor(() => expect(wrapper.find('.panorama-bindings__screen-meta').text()).toContain('SCR_OTHER'));

    expect(wrapper.find('.panorama-bindings__footer').text()).toContain('当前版本 8');
    expect(wrapper.find('[data-testid="binding-conflict"]').exists()).toBe(false);
  });
});
