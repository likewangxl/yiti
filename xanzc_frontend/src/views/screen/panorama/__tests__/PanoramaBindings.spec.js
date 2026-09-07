// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listScreens: vi.fn(),
  getScreenCanvas: vi.fn(),
  listScreenDatasources: vi.fn(),
  saveScreenCanvas: vi.fn(),
  publishScreenCanvas: vi.fn(),
  discardScreenCanvas: vi.fn(),
  queryScreenData: vi.fn()
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
      + '</div>'
  }
}));
vi.mock('../PanoramaDashboard.vue', () => ({ default: dashboardStub }));

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

describe('PanoramaBindings', () => {
  beforeEach(() => {
    router.push.mockReset();
    router.back.mockReset();
    api.queryScreenData.mockReset();
  });

  afterEach(() => vi.restoreAllMocks());

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
