// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { ref } from 'vue';
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('../PanoramaDashboard.vue', () => ({ default: { template: '<main>经营内容</main>' } }));
vi.mock('../CorporateDashboard.vue', () => ({ default: { template: '<main>对公内容</main>' } }));
vi.mock('../RetailDashboard.vue', () => ({ default: { template: '<main>零售内容</main>' } }));
vi.mock('../usePanoramaData', () => ({ usePanoramaData: () => ({
  model: ref({ kpis: [], quality: { status: 'STALE', dataClassification: 'TEST', dataDate: '2026-08-30',
    batchId: 'batch-evidence', selectedComplete: true, expectedSubjects: 4, receivedSubjects: 4 } }),
  loading: ref(false), error: ref(''), slotIssues: ref({}), refresh: vi.fn(), selectBranch: vi.fn()
}) }));
import PanoramaRuntime from '../PanoramaRuntime.vue';
describe('投屏数据状态', () => {
  it('首屏保留测试性质、日期和过期状态，溯源详情默认收起且可展开', async () => {
    const wrapper = mount(PanoramaRuntime, { props: { view: { renderPackage: { canvasStyle: { dataNotice: '来自测试库接口' } } } } });
    const summary = wrapper.get('[data-testid="runtime-source-summary"]');
    expect(summary.text()).toContain('TEST');
    expect(summary.text()).toContain('2026-08-30');
    expect(summary.text()).toContain('过期');
    expect(wrapper.get('[data-testid="runtime-source-details"]').attributes('open')).toBeUndefined();
    expect(wrapper.get('[data-testid="runtime-source-details"]').text()).toContain('batch-evidence');
    expect(wrapper.text()).toContain('来自测试库接口');
    wrapper.unmount();
  });
});
