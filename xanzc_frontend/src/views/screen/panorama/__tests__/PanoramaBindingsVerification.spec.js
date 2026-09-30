// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const fixture = vi.hoisted(() => ({
  screen: { id: 19, screenCode: 'SCR_VERIFY', screenName: '核验屏', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT', status: 'ACTIVE' },
  binding: { dsId: 77, period: 'LATEST', fields: { value: 'balance' }, units: { value: 'YUAN' } }
}));
vi.mock('@/api/screen', () => ({
  listScreens: vi.fn(async () => [fixture.screen]),
  getScreenCanvas: vi.fn(async () => ({
    screenId: 19, screenCode: 'SCR_VERIFY', canvasVersion: 4,
    canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
    canvasDraftJson: JSON.stringify({ components: [{ id: 'deposit', component: 'ChartWidget', blockId: 41,
      propValue: { bindingKey: 'deposit' }, bindJson: JSON.stringify(fixture.binding) }] })
  })),
  listScreenDatasources: vi.fn(async () => [{ id: 77, dsName: '余额来源', status: 'ACTIVE', bizLine: 'COMMON', sourceKind: 'WIDE_TABLE',
    configJson: JSON.stringify({ fieldMeta: [{ col: 'balance', role: 'METRIC', unit: '元' }] }) }]),
  getScreenView: vi.fn(), queryScreenData: vi.fn(), saveScreenCanvas: vi.fn(),
  publishScreenCanvas: vi.fn(), discardScreenCanvas: vi.fn(), listOrgProfiles: vi.fn(), listOrgGroups: vi.fn()
}));
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }));
vi.mock('../PanoramaDataVerification.vue', () => ({ default: {
  name: 'PanoramaDataVerification', props: ['screen', 'canvas', 'datasources', 'slotOrder', 'bindingState', 'disabled'],
  template: '<div data-testid="verification-connected">{{ bindingState.deposit?.fields?.value }}</div>'
} }));
import PanoramaBindings from '../PanoramaBindings.vue';

describe('大屏管理的简化配置页', () => {
  it('不挂载接入核验、目录和试跑入口', async () => {
    const wrapper = mount(PanoramaBindings, { global: { stubs: {
      PanoramaIntegrationReadiness: true, PanoramaSettings: true, PanoramaDatasourcePicker: true
    } } });
    await flushPromises();
    const verification = wrapper.findComponent({ name: 'PanoramaDataVerification' });
    expect(verification.exists()).toBe(false);
    expect(wrapper.find('[data-testid="verification-connected"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="integration-readiness"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="try-run"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="binding-datasources"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
