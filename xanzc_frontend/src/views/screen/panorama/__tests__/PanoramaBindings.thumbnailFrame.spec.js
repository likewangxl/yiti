// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
const api=vi.hoisted(()=>({listScreens:vi.fn(),getScreenCanvas:vi.fn(),listScreenDatasources:vi.fn(async()=>[]),listScreenMetricCatalog:vi.fn(async()=>[])}));
vi.mock('@/api/screen',()=>api);
vi.mock('@/stores/permission',()=>({usePermissionStore:()=>({loaded:true,canAccess:()=>true})}));
vi.mock('vue-router',()=>({useRoute:()=>({query:{}}),useRouter:()=>({push:vi.fn()})}));
vi.mock('../MetricBindingComposer.vue',()=>({default:{template:'<div />'}}));
vi.mock('../PanoramaDataVerification.vue',()=>({default:{template:'<div />'}}));
vi.mock('../PanoramaIntegrationReadiness.vue',()=>({default:{template:'<div />'}}));
vi.mock('../PanoramaSettings.vue',()=>({default:{template:'<div />'}}));
import PanoramaBindings from '../PanoramaBindings.vue';
const thumbnail={props:['presentation','canvasWidth','canvasHeight','context','template'],template:'<div data-testid="runtime-thumbnail-stub" />'};
describe('缩略图运行画布接线',()=>{
  it.each([undefined,1])('按草稿传递完整presentation及设计尺寸 schema=%s',async version=>{
    const presentation={type:'CODE',template:'branch-overview-v1',...(version?{displaySchemaVersion:version,display:{components:[]}}:{}),customSetting:'keep'};
    api.listScreens.mockResolvedValue([{id:1,screenCode:'SCR_TEST',screenName:'测试屏',bizLine:'ALL',viewLevel:'PROVINCE'}]);
    api.getScreenCanvas.mockResolvedValue({screenId:1,canvasVersion:1,canvasStyleJson:JSON.stringify({designWidth:1600,designHeight:1000,presentation}),canvasDraftJson:'{"components":[]}'});
    const wrapper=mount(PanoramaBindings,{props:{screenId:1},global:{directives:{'bp-overflow-tooltip':{}},stubs:{BranchConfigThumbnail:thumbnail,OperatingConfigThumbnail:thumbnail,'el-button':true,'el-dialog':true}}});
    await vi.waitFor(()=>expect(wrapper.find('[data-testid="runtime-thumbnail-stub"]').exists()).toBe(true));
    const child=wrapper.findComponent(thumbnail);
    expect(child.props('canvasWidth')).toBe(1600);expect(child.props('canvasHeight')).toBe(1000);
    expect(child.props('presentation')).toMatchObject({template:'branch-overview-v1',customSetting:'keep'});
    expect(child.props('context')).toBe('screen');wrapper.unmount();
  });
});
