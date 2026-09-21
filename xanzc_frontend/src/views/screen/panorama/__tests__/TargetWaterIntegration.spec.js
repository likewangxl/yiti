// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
vi.mock('vue-echarts', () => ({default:{template:'<div />'}}));
vi.mock('../PanoramaMap.vue', () => ({default:{template:'<div />'}}));
import PanoramaDashboard from '../PanoramaDashboard.vue';
describe('目标完成率水位图集成', () => {
  it('双水位分别表现存款和贷款完成率，保留存款超额真实数值与各自缺口', () => {
    const wrapper=mount(PanoramaDashboard,{props:{model:{dataDate:'2026-09-21',kpis:[
      {key:'depositRate',label:'零售存款目标完成率',value:125,unit:'%',date:'2026-09-20'},
      {key:'loanRate',label:'零售贷款目标完成率',value:96.4,unit:'%'}
    ],institutions:[],rankings:[]}}});
    expect(wrapper.findAll('[data-testid="completion-water-gauge"]')).toHaveLength(2);
    expect(wrapper.find('[data-target-key="deposit"] [data-testid="target-progress-value"]').text()).toContain('125');
    expect(wrapper.find('[data-target-key="deposit"] [data-testid="completion-water-gauge"]').attributes('data-level')).toBe('100');
    expect(wrapper.find('[data-target-key="deposit"] [data-testid="completion-water-gauge"]').attributes('data-variant')).toBe('deposit');
    expect(wrapper.find('[data-target-key="loan"] [data-testid="completion-water-gauge"]').attributes('data-variant')).toBe('loan');
    expect(wrapper.find('[data-target-key="deposit"] .panorama-target-distance').text()).toContain('25');
    expect(wrapper.find('[data-target-key="loan"] .panorama-target-distance').text()).toContain('距目标还差3.6个百分点');
    expect(wrapper.find('[data-testid="target-progress-bullet"]').exists()).toBe(false);
    expect(wrapper.get('.panorama-target-panel').text()).toContain('2026-09-20');
    wrapper.unmount();
  });

  it('贷款没有独立目标绑定时保留空水位并显示暂无目标数据，不复用存款完成率', () => {
    const wrapper=mount(PanoramaDashboard,{props:{model:{kpis:[
      {key:'rate',label:'旧存款完成率',value:88,unit:'%'},
      {key:'loan',label:'贷款余额',value:560,unit:'亿元'}
    ],institutions:[],rankings:[]}}});
    const loanCard = wrapper.get('[data-target-key="loan"]');
    expect(loanCard.text()).toContain('暂无目标数据');
    expect(loanCard.get('[data-testid="completion-water-gauge"]').attributes('data-level')).toBe('unknown');
    expect(loanCard.get('[data-testid="target-progress-value"]').text()).toBe('—');
    expect(loanCard.text()).not.toContain('88.00%');
    wrapper.unmount();
  });
});
