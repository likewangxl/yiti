// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
vi.mock('vue-echarts', () => ({default:{template:'<div />'}}));
vi.mock('../PanoramaMap.vue', () => ({default:{template:'<div />'}}));
import PanoramaDashboard from '../PanoramaDashboard.vue';
describe('目标完成率水位图集成', () => {
  it('只用一个水位图表现完成率，保留真实超额数值和目标差距', () => {
    const wrapper=mount(PanoramaDashboard,{props:{model:{kpis:[{key:'rate',label:'目标完成率',value:125,unit:'%'}],institutions:[],rankings:[]}}});
    expect(wrapper.findAll('[data-testid="completion-water-gauge"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="target-progress-value"]').text()).toContain('125');
    expect(wrapper.get('[data-testid="completion-water-gauge"]').attributes('data-level')).toBe('100');
    expect(wrapper.find('[data-testid="target-progress-bullet"]').exists()).toBe(false);
    expect(wrapper.get('.panorama-target-summary').text()).toContain('25');
    wrapper.unmount();
  });
});
