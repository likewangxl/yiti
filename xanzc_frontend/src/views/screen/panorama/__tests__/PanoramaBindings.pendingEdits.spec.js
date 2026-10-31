// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listScreens: vi.fn(async () => [{ id: 9, screenCode: 'SCR', screenName: '经营总览', bizLine: 'COMMON', viewLevel: 'PROVINCE', orgScopeMode: 'LEGACY_CONTEXT' }]),
  getScreenCanvas: vi.fn(async () => ({ screenId: 9, screenCode: 'SCR', canvasVersion: 1,
    canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
    canvasDraftJson: JSON.stringify({ components: [{ id: 'deposit', component: 'ChartWidget', blockId: 1, innerType: 'METRIC_CARD', propValue: { bindingKey: 'deposit' }, bindJson: '{}' }] }) })),
  listScreenDatasources: vi.fn(async () => []),
  listScreenMetricCatalog: vi.fn(async () => []),
  saveScreenCanvas: vi.fn(), publishScreenCanvas: vi.fn(), saveScreenDatasource: vi.fn()
}));
vi.mock('@/api/screen', () => api);
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => ({ loaded: true, canAccess: () => true }) }));
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }));
vi.mock('../PanoramaDatasourcePicker.vue', () => ({ default: { name: 'PanoramaDatasourcePicker', props: ['sources', 'modelValue', 'disabled'], emits:['change'], template: '<select :value="modelValue" :disabled="disabled" @change="$emit(\'change\',$event.target.value)"><option value="">清空</option><option v-for="s in sources" :value="s.id">{{s.dsName}}</option></select>' } }));
vi.mock('../MetricBindingComposer.vue', () => ({ default: {
  name: 'MetricBindingComposer', emits: ['dirty-change','apply'], template: '<div><button data-testid="emit-pending" @click="$emit(\'dirty-change\', true)">edit</button><button data-testid="emit-revert" @click="$emit(\'dirty-change\', false)">revert</button></div>'
} }));
vi.mock('../PanoramaDataVerification.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PanoramaSettings.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PresentationEditor.vue', () => ({ default: { template: '<div />' } }));

import PanoramaBindings from '../PanoramaBindings.vue';
import Composer from '../MetricBindingComposer.vue';
const mounted=[];
afterEach(()=>{mounted.splice(0).forEach(w=>w.unmount());vi.clearAllMocks();});
async function open(props={screenId:9}) {
 const w=mount(PanoramaBindings,{props,global:{directives:{'bp-overflow-tooltip':{}},stubs:{'el-button':{template:'<button><slot /></button>'},'el-dialog':{props:['modelValue'],template:'<div v-if="modelValue"><slot/><slot name="footer"/></div>'},BranchThumbnailChart:true}}});
 mounted.push(w);await flushPromises();return w;
}

describe('PanoramaBindings pending metric edits', () => {
  it('撤回指标修改恢复非pending，顶部保存函数也不能绕过保护',async()=>{
    const w=await open();await w.get('[data-testid="emit-pending"]').trigger('click');
    expect(w.get('[data-testid="binding-period"]').attributes('disabled')).toBeDefined();
    expect(await w.vm.saveDraft()).toBe(false);expect(api.saveScreenCanvas).not.toHaveBeenCalled();
    await w.get('[data-testid="emit-revert"]').trigger('click');
    expect(w.find('[data-testid="pending-metric-edits"]').exists()).toBe(false);
    expect(w.find('[data-testid="binding-conflict"]').exists()).toBe(false);
  });
  it('放弃未应用编辑保留已修改标题，紧凑选择器被拒绝后回显原组件',async()=>{
    const w=await open();await w.get('[data-testid="component-title"]').setValue('本地标题');
    await w.get('[data-testid="emit-pending"]').trigger('click');
    await w.get('#compact-component-select').setValue('loan');
    expect(w.get('#compact-component-select').element.value).toBe('deposit');
    await w.get('[data-testid="binding-discard-pending"]').trigger('click');
    expect(w.get('[data-testid="component-title"]').element.value).toBe('本地标题');
  });
  it('应用失败保留未应用编辑，成功应用已有来源后才清除',async()=>{
    const w=await open();const composer=w.findComponent(Composer);
    await w.get('[data-testid="emit-pending"]').trigger('click');
    composer.vm.$emit('apply',{ok:true,binding:{period:'LATEST',fields:{value:'x'},units:{value:'YUAN'}}});await flushPromises();
    expect(w.find('[data-testid="pending-metric-edits"]').exists()).toBe(true);
    composer.vm.$emit('apply',{ok:true,existingDatasourceId:7,binding:{period:'LATEST',fields:{value:'x'},units:{value:'YUAN'}}});await flushPromises();
    expect(w.find('[data-testid="pending-metric-edits"]').exists()).toBe(false);
  });
  it('城市共享来源列出15入口，取消不清字段，确认清空后才修改并保留窄屏辅助入口',async()=>{
    api.getScreenCanvas.mockResolvedValueOnce({screenId:9,canvasVersion:1,canvasStyleJson:JSON.stringify({presentation:{type:'CODE',template:'branch-overview-v1',displaySchemaVersion:1,display:{components:[]}}}),canvasDraftJson:JSON.stringify({components:[{id:'city',component:'ChartWidget',blockId:60,propValue:{bindingKey:'citySummary'},bindJson:JSON.stringify({dsId:902,period:'LATEST',fields:{cityCode:'city_code',deposit:'dep',loan:'loan'},units:{deposit:'YUAN',loan:'YUAN'}})},{id:'trend',component:'ChartWidget',blockId:61,propValue:{bindingKey:'branchTrend'},bindJson:'{}'}]})});
    api.listScreenDatasources.mockResolvedValueOnce([{id:902,dsName:'城市来源',sourceKind:'WIDE_TABLE',status:'ACTIVE',configJson:JSON.stringify({fieldMeta:[{col:'city_code',role:'DIM'},{col:'dep',role:'METRIC',unit:'YUAN'},{col:'loan',role:'METRIC',unit:'YUAN'}]})}]);
    const w=await open();
    expect(w.findAll('#compact-component-select option').some(o=>o.element.value==='drill-branchTrend')).toBe(true);
    await w.get('[data-testid="config-context-city"]').trigger('click');
    await w.get('[data-testid="slot-datasource"]').setValue('');
    expect(w.get('[data-testid="shared-source-confirm"]').text()).toContain('15');
    expect(w.get('[data-testid="shared-source-confirm"]').text()).toContain('零售存款余额');
    expect(w.get('[data-testid="screen-select"]').attributes('disabled')).toBeDefined();
    await w.get('[data-testid="shared-source-cancel"]').trigger('click');
    expect(w.get('[data-testid="slot-datasource"]').element.value).toBe('902');
    expect(w.get('[data-testid="field-option-citySummary-deposit"]').element.value).toBe('dep');
    expect(w.find('[data-testid="binding-cancel"]').exists()).toBe(false);
    await w.get('[data-testid="slot-datasource"]').setValue('');
    await w.get('[data-testid="shared-source-confirm-button"]').trigger('click');
    expect(w.get('[data-testid="slot-datasource"]').element.value).toBe('');
    expect(w.get('[data-testid="field-option-citySummary-deposit"]').element.value).toBe('');
  });
  it('未应用指标编辑显示保护并阻止保存预览发布，放弃后恢复', async () => {
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 }, global: { directives: { 'bp-overflow-tooltip': {} } } });
    await vi.waitFor(() => expect(wrapper.get('[data-testid="emit-pending"]').exists()).toBe(true));
    await wrapper.get('[data-testid="emit-pending"]').trigger('click');
    expect(wrapper.text()).toContain('未应用');
    expect(wrapper.get('[data-testid="binding-save"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="binding-preview"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="binding-cancel"]').trigger('click');
    expect(wrapper.text()).not.toContain('指标绑定尚未应用');
  });

  it('未应用时切换组件和页面范围保持原选择，并且放弃未应用不丢失标题修改', async () => {
    const wrapper = mount(PanoramaBindings, { props: { screenId: 9 }, global: { directives: { 'bp-overflow-tooltip': {} } } });
    await vi.waitFor(() => expect(wrapper.get('[data-testid="emit-pending"]').exists()).toBe(true));
    const before = wrapper.get('[data-testid="slot-deposit"]').classes();
    await wrapper.get('[data-testid="emit-pending"]').trigger('click');
    await wrapper.get('[data-testid="slot-loan"]').trigger('click');
    expect(wrapper.get('[data-testid="slot-deposit"]').classes()).toEqual(before);
    await wrapper.get('[data-testid="binding-discard-pending"]').trigger('click');
    expect(wrapper.find('[data-testid="pending-metric-edits"]').exists()).toBe(false);
  });
});
