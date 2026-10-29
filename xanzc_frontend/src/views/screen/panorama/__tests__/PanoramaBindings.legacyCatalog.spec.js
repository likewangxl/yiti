// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({ listScreens: vi.fn(), getScreenCanvas: vi.fn(), listScreenDatasources: vi.fn(), saveScreenCanvas: vi.fn(), listScreenMetricCatalog: vi.fn(async () => []) }));
vi.mock('@/api/screen', () => ({ ...api }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => ({ loaded: true, canAccess: () => true }) }));
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }));
vi.mock('../PanoramaDatasourcePicker.vue', () => ({ default: { props: ['sources', 'modelValue', 'disabled'], emits: ['update:modelValue', 'change'], template: '<select v-bind="$attrs" :value="modelValue" />' } }));
vi.mock('../PanoramaDataVerification.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PanoramaIntegrationReadiness.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PanoramaSettings.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../../presentation/editor/PresentationEditor.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../MetricBindingComposer.vue', () => ({ default: {
  name: 'MetricBindingComposer', props: ['fieldSpecs'], emits: ['apply'],
  template: `<button data-testid="emit-plan" @click="$emit('apply',{ ok:true, binding:{ period:'LATEST', fields:{ value:'new_value' }, units:{ value:'YUAN' } }, existingDatasourceId:7, datasourceRequest:null })">apply</button>`
} }));
import PanoramaBindings from '../PanoramaBindings.vue';

const canvas = {
  screenId: 1, screenCode: 'SCR_CORP', canvasVersion: 3,
  canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'corporate-overview-v1' }, metricLabels: {} }),
  canvasDraftJson: JSON.stringify({ components: [
    { id: 'corp-deposit', component: 'ChartWidget', blockId: 11, propValue: { bindingKey: 'corpDeposit' }, bindJson: JSON.stringify({ dsId: 7, period: 'LATEST', fields: { value: 'old_value', change: 'old_change', date: 'data_date' }, units: { value: 'YUAN', change: 'PERCENT' } }) },
    { id: 'corp-segments', component: 'ChartWidget', blockId: 12, propValue: { bindingKey: 'corpSegments' }, bindJson: JSON.stringify({ dsId: 7, period: 'LATEST', fields: { name: 'segment_name', customers: 'customers', loan: 'loan' }, units: { customers: 'COUNT', loan: 'YUAN' } }) }
  ] })
};

describe('PanoramaBindings legacy catalog', () => {
  it('机构KPI快照的指标筛选项不是输出列，不能混进字段候选', async () => {
    api.listScreens.mockResolvedValue([{id:1,screenCode:'SCR_CORP',screenName:'对公',bizLine:'CORP',viewLevel:'PROVINCE'}]);
    api.getScreenCanvas.mockResolvedValue({...canvas,canvasDraftJson:JSON.stringify({components:[{id:'target',component:'ChartWidget',blockId:11,innerType:'TABLE_LIST',propValue:{bindingKey:'corpTargets'},bindJson:JSON.stringify({dsId:7,period:'LATEST',fields:{name:'org_name',actual:'actual_value',target:'target_value'},units:{actual:'YUAN',target:'YUAN'}})}]})});
    api.listScreenDatasources.mockResolvedValue([{id:7,dsName:'机构快照',status:'ACTIVE',bizLine:'CORP',sourceKind:'KPI_DETAIL',dsType:'SINGLE',configJson:JSON.stringify({schemaVersion:2,scopeMode:'NAMED_GROUP',subjectType:'ORG',mode:'SNAPSHOT',schemeCode:'QA',metrics:[{metricCode:'M_1',metricName:'不是返回列'}]})}]);
    api.listScreenMetricCatalog.mockResolvedValue([]);
    const wrapper=mount(PanoramaBindings,{props:{screenId:1},global:{directives:{'bp-overflow-tooltip':{}},stubs:{'el-button':true,'el-dialog':true}}});
    await vi.waitFor(()=>expect(wrapper.find('[data-config-key="corpTargets"]').exists()).toBe(true));await wrapper.get('[data-config-key="corpTargets"]').trigger('click');
    const field=wrapper.get('[data-testid="field-option-corpTargets-actual"]');
    expect(field.find('option[value="actual_value"]').exists()).toBe(true);expect(field.find('option[value="不是返回列"]').exists()).toBe(false);wrapper.unmount();
  });
  it.each([['元','YUAN','corpDeposit'],['万元','TEN_THOUSAND','corpDeposit'],['亿元','HUNDRED_MILLION','corpDeposit'],['%','PERCENT','corpNplRate'],['比例','RATIO','corpNplRate']])('来源单位 %s 与绑定编码一致时可回显并通过保存校验', async (alias,unit,slot) => {
    api.listScreens.mockResolvedValue([{id:1,screenCode:'SCR_CORP',screenName:'对公',bizLine:'CORP',viewLevel:'PROVINCE'}]);
    api.getScreenCanvas.mockResolvedValue({...canvas,canvasDraftJson:JSON.stringify({components:[{id:'metric',component:'ChartWidget',blockId:11,innerType:'METRIC_CARD',propValue:{bindingKey:slot},bindJson:JSON.stringify({dsId:7,period:'LATEST',fields:{value:'amount'},units:{value:unit}})}]})});
    api.listScreenDatasources.mockResolvedValue([{id:7,dsName:'来源',status:'ACTIVE',bizLine:'CORP',sourceKind:'WIDE_TABLE',configJson:JSON.stringify({table:'ORG_INDEX_RESULT',fieldMeta:[{col:'amount',role:'METRIC',unit:alias}]})}]);
    api.listScreenMetricCatalog.mockResolvedValue([]);
    const wrapper=mount(PanoramaBindings,{props:{screenId:1},global:{directives:{'bp-overflow-tooltip':{}},stubs:{'el-button':true,'el-dialog':true}}});
    await vi.waitFor(()=>expect(wrapper.find(`[data-config-key="${slot}"]`).exists()).toBe(true));await wrapper.get(`[data-config-key="${slot}"]`).trigger('click');
    expect(wrapper.get(`[data-testid="field-option-${slot}-value"] option[value="amount"]`).attributes('disabled')).toBeUndefined();
    expect(wrapper.vm.collectValidBindings().problems).toEqual([]);wrapper.unmount();
  });
  it('仅声明指标编码的宽表来源也按已确认目录列名回显已有绑定', async () => {
    api.listScreens.mockResolvedValue([{id:1,screenCode:'SCR_CORP',screenName:'对公',bizLine:'CORP',viewLevel:'PROVINCE'}]);
    api.getScreenCanvas.mockResolvedValue(canvas);
    api.listScreenDatasources.mockResolvedValue([{id:7,dsName:'既有指标来源',status:'ACTIVE',bizLine:'CORP',sourceKind:'WIDE_TABLE',configJson:JSON.stringify({table:'ORG_INDEX_RESULT',metrics:[{metricCode:'M_AMOUNT'}]})}]);
    api.listScreenMetricCatalog.mockResolvedValue([{metricCode:'M_AMOUNT',metricName:'存款',outputColumn:'old_value',unit:'YUAN',baseDim:'ORG',bindable:true}]);
    const wrapper=mount(PanoramaBindings,{props:{screenId:1},global:{directives:{'bp-overflow-tooltip':{}},stubs:{'el-button':true,'el-dialog':true}}});
    await vi.waitFor(()=>expect(wrapper.find('[data-testid="field-option-corpDeposit-value"]').exists()).toBe(true));
    expect(wrapper.get('[data-testid="field-option-corpDeposit-value"]').element.value).toBe('old_value');
    expect(wrapper.get('[data-testid="field-option-corpDeposit-value"] option[value="old_value"]').attributes('disabled')).toBeUndefined();
    wrapper.unmount();api.listScreenMetricCatalog.mockResolvedValue([]);
  });
  it('渲染对公实际目录，应用计划合并旧字段并保存后可回读', async () => {
    api.listScreens.mockResolvedValue([{ id: 1, screenCode: 'SCR_CORP', screenName: '对公', bizLine: 'CORP', viewLevel: 'PROVINCE' },
      { id: 2, screenCode: 'SCR_OTHER', screenName: '另一张对公屏', bizLine: 'CORP', viewLevel: 'PROVINCE' }]);
    api.getScreenCanvas.mockResolvedValue(canvas);
    api.listScreenDatasources.mockResolvedValue([{ id: 7, dsName: 'corp', status: 'ACTIVE', bizLine: 'CORP', sourceKind: 'WIDE_TABLE', configJson: JSON.stringify({ fieldMeta: [
      { col: 'old_value', role: 'METRIC', unit: 'YUAN' }, { col: 'new_value', role: 'METRIC', unit: 'YUAN' }, { col: 'old_change', role: 'METRIC', unit: 'PERCENT' }, { col: 'segment_name', role: 'DIM' }, { col: 'customers', role: 'METRIC', unit: 'COUNT' }, { col: 'loan', role: 'METRIC', unit: 'YUAN' }
    ] }) }]);
    api.saveScreenCanvas.mockResolvedValue({ canvasVersion: 4 });
    const wrapper = mount(PanoramaBindings, { props: { screenId: 1 }, global: {
      stubs: { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' } },
      directives: { 'bp-overflow-tooltip': {} }
    } });
    await vi.waitFor(() => expect(wrapper.find('[data-config-key="corpDeposit"]').exists()).toBe(true));
    expect(wrapper.find('[data-testid="operating-config-thumbnail"]').exists()).toBe(true);
    expect(wrapper.find('.panorama-bindings__slots').exists()).toBe(false);
    expect(wrapper.find('[data-config-key="corpSegments"]').exists()).toBe(true);
    await wrapper.get('[data-config-key="corpDeposit"]').trigger('click');
    expect(wrapper.find('[data-testid="field-option-corpDeposit-value"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="field-option-corpDeposit-value"]').find('option[value="old_change"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="field-option-corpDeposit-change"]').find('option[value="old_value"]').exists()).toBe(false);
    await wrapper.get('[data-testid="emit-plan"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.vm.saving).toBe(false));
    await wrapper.get('[data-testid="component-title"]').setValue('自定义存款标题');
    expect(wrapper.get('[data-config-key="corpDeposit"]').text()).toContain('自定义存款标题');
    await wrapper.get('[data-testid="screen-select"]').setValue('2');
    expect(wrapper.get('[data-testid="screen-select"]').element.value).toBe('1');
    expect(api.getScreenCanvas).not.toHaveBeenCalledWith(2);
    await wrapper.vm.saveDraft();
    expect(wrapper.vm.error).toBe('');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalled());
    const payload = api.saveScreenCanvas.mock.lastCall[0];
    expect(payload.screenId).toBe(1);
    const saved = JSON.parse(payload.components.find(item => item.propValue?.bindingKey === 'corpDeposit').bindJson);
    expect(saved.fields).toMatchObject({ value: 'new_value', change: 'old_change', date: 'data_date' });
    wrapper.unmount();
    api.getScreenCanvas.mockResolvedValue({ ...canvas, canvasStyleJson: JSON.stringify(payload.canvasStyle), canvasDraftJson: JSON.stringify({ components: payload.components }) });
    const reloaded = mount(PanoramaBindings, { props: { screenId: 1 }, global: {
      stubs: { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' } },
      directives: { 'bp-overflow-tooltip': {} }
    } });
    await vi.waitFor(() => expect(reloaded.find('[data-config-key="corpDeposit"]').exists()).toBe(true));
    expect(reloaded.vm.bindingState.corpDeposit.fields).toMatchObject({ value: 'new_value', change: 'old_change', date: 'data_date' });
    expect(reloaded.get('[data-config-key="corpDeposit"]').text()).toContain('自定义存款标题');
    reloaded.unmount();
  });
});
