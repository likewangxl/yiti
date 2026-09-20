// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
vi.mock('vue-echarts',()=>({default:{name:'ChartSurface',props:['option'],template:'<div />'}}));
import PanoramaTrend from '../PanoramaTrend.vue';
import CorporateTrend from '../CorporateTrend.vue';
import RetailTrend from '../RetailTrend.vue';
describe('趋势末端日期可读性',()=>{
  it.each([PanoramaTrend,CorporateTrend,RetailTrend])('为完整的末端日期预留空间',component=>{
    const wrapper=mount(component,{props:{compact:true,trend:[{date:'2026-08-29',deposit:1,loan:2,aum:3},{date:'2026-08-30',deposit:2,loan:3,aum:4}]}});
    const option=wrapper.getComponent({name:'ChartSurface'}).props('option');
    expect(option.grid.right).toBeGreaterThanOrEqual(44);
    expect(option.grid.containLabel).toBe(true);
    wrapper.unmount();
  });
});
