// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
vi.mock('../ScaledRuntimeFrame.vue', () => ({ default: { name:'ScaledRuntimeFrame', props:['width','height'], template:'<div><slot /></div>' } }));
vi.mock('../PanoramaDashboard.vue', () => ({ default: { name:'PanoramaDashboard', props:['model','sourcePresentation','draftOverview'], template:'<div class="panorama-dashboard" />' } }));
vi.mock('../CorporateDashboard.vue', () => ({ default: { name:'CorporateDashboard', template:'<div />' } }));
vi.mock('../RetailDashboard.vue', () => ({ default: { name:'RetailDashboard', template:'<div />' } }));
vi.mock('../CityPanorama.vue', () => ({ default: { name:'CityPanorama', template:'<div />' } }));
vi.mock('../BranchOperatingOverview.vue', () => ({ default: { name:'BranchOperatingOverview', template:'<div />' } }));
import RuntimeConfigThumbnail from '../RuntimeConfigThumbnail.vue';
const entries=[{key:'deposit',label:'存款余额'},{key:'trend',label:'主要指标趋势'}];
describe('运行页缩略图',()=>{
  it('使用真正的分行运行组件并传递设计尺寸，不复造页面布局',()=>{
    const w=mount(RuntimeConfigThumbnail,{props:{entries,template:'branch-overview-v1',canvasWidth:1600,canvasHeight:1000}});
    expect(w.findComponent({name:'PanoramaDashboard'}).exists()).toBe(true);
    expect(w.findComponent({name:'ScaledRuntimeFrame'}).props()).toMatchObject({width:1600,height:1000});w.unmount();
  });
  it('目录选择返回原始条目，选中状态和标题随草稿同步，禁用时不能切换',async()=>{
    const w=mount(RuntimeConfigThumbnail,{props:{entries,template:'branch-overview-v1',selectedKey:'deposit'}});
    expect(w.get('[data-config-key="deposit"]').attributes('aria-pressed')).toBe('true');
    await w.get('[data-config-key="trend"]').trigger('keydown.enter');
    expect(w.emitted('select').at(-1)[0]).toEqual(entries[1]);
    await w.setProps({entries:[{key:'deposit',label:'新存款标题'},entries[1]],disabled:true});
    expect(w.text()).toContain('新存款标题');
    await w.get('[data-config-key="trend"]').trigger('click');
    expect(w.emitted('select')).toHaveLength(1);w.unmount();
  });
  it.each([['city','CityPanorama'],['branch','BranchOperatingOverview']])('下钻上下文%s复用相应运行组件',(context,name)=>{
    const w=mount(RuntimeConfigThumbnail,{props:{entries,context,template:'branch-overview-v1'}});
    expect(w.findComponent({name}).exists()).toBe(true);w.unmount();
  });
});
