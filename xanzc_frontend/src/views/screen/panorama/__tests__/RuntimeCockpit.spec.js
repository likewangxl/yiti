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
  it('首屏不再显示环境、日期、批次和来源口径状态条', () => {
    const wrapper = mount(PanoramaRuntime, { props: { view: { renderPackage: { canvasStyle: { dataNotice: '来自测试库接口' } } } } });
    expect(wrapper.find('[data-testid="runtime-source-summary"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="runtime-source-details"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('来自测试库接口');
    expect(wrapper.text()).toContain('经营内容');
    wrapper.unmount();
  });
});
