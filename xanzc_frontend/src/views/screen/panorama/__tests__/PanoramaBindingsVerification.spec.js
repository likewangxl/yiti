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

describe('大屏管理的真实草稿核验入口', () => {
  it('把当前屏、服务端草稿版本及实时编辑绑定交给核验组件', async () => {
    const wrapper = mount(PanoramaBindings, { global: { stubs: {
      PanoramaIntegrationReadiness: true, PanoramaSettings: true, PanoramaDatasourcePicker: true
    } } });
    await flushPromises();
    const verification = wrapper.findComponent({ name: 'PanoramaDataVerification' });
    expect(verification.exists()).toBe(true);
    expect(verification.props('screen').screenCode).toBe('SCR_VERIFY');
    expect(verification.props('canvas').canvasVersion).toBe(4);
    expect(verification.props('disabled')).toBe(false);
    expect(verification.props('slotOrder')).toContain('branchTrend');
    expect(verification.props('datasources')).toHaveLength(1);
    expect(verification.props('bindingState').deposit).toEqual(fixture.binding);
    await wrapper.get('[data-testid="field-option-deposit-value"]').setValue('');
    expect(verification.props('bindingState').deposit.fields.value).toBeUndefined();
    wrapper.unmount();
  });
});
