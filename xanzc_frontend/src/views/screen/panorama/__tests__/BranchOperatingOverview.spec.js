// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('../PanoramaDashboard.vue', () => ({ default: {
  name: 'PanoramaDashboard',
  props: ['model', 'sourcePresentation', 'showConfigure'],
  template: '<main data-testid="panorama-dashboard"><slot name="header-context" /><slot name="branch-map" /><slot name="branch-ranking" /></main>'
} }));

import BranchOperatingOverview from '../BranchOperatingOverview.vue';

describe('BranchOperatingOverview', () => {
  it('使用新版 PanoramaDashboard，并把中心与右下区域替换为支行专属内容', () => {
    const wrapper = mount(BranchOperatingOverview, {
      props: {
        model: {
          orgCode: '105', orgName: '延兴门西路支行', dataDate: '2026-09-20',
          institutions: [{ orgCode: '105', orgName: '延兴门西路支行' }],
          targets: [{ key: 'deposit', label: '存款', actual: 80, target: 100, unit: '万元' }]
        },
        performanceEnabled: false
      },
      global: { stubs: {
        BranchPerformancePanel: { template: '<div data-testid="branch-performance-panel" />' }
      } }
    });

    const dashboard = wrapper.getComponent({ name: 'PanoramaDashboard' });
    expect(dashboard.props('showConfigure')).toBe(false);
    expect(dashboard.props('model')).toMatchObject({ title: '延兴门西路支行', scopeLabel: '延兴门西路支行' });
    expect(wrapper.get('[data-testid="branch-incomplete-chart"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="branch-performance-panel"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="branch-operating-branch-select"]').element.value).toBe('105');
  });
});
