// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { ref } from 'vue';

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }));
vi.mock('../PanoramaDashboard.vue', () => ({ default: { template: '<main data-testid="legacy-dashboard">经营内容</main>' } }));
vi.mock('../CorporateDashboard.vue', () => ({ default: { template: '<main>对公内容</main>' } }));
vi.mock('../RetailDashboard.vue', () => ({ default: { template: '<main>零售内容</main>' } }));
vi.mock('../usePanoramaData', () => ({ usePanoramaData: () => ({
  model: ref({
    kpis: [{ key: 'deposit', value: 1 }],
    configuredSlots: ['deposit', 'customers'],
    sourceQualities: { deposit: { batchId: 'B-OLD', dataDate: '2026-09-20', status: 'STALE' } },
    sourceDates: { customers: '2026-09-19' },
    sourceMetadata: {},
    issues: [{ slot: 'customers', code: 'NO_VALUES', message: '客户当前无有效值' }],
    quality: { batchId: 'B-OLD', dataDate: '2026-09-20', status: 'STALE' },
    qualityGuard: null,
    queriedAt: '2026-09-22T09:00:00Z'
  }),
  loading: ref(false),
  error: ref(''),
  lastQueriedAt: ref('2026-09-22T09:00:00Z'),
  slotIssues: ref({ customers: [{ code: 'NO_VALUES', message: '客户当前无有效值' }] }),
  refresh: vi.fn(),
  selectBranch: vi.fn()
}) }));

import PanoramaRuntime from '../PanoramaRuntime.vue';

describe('PanoramaRuntime S14', () => {
  it('新展示协议挂载统一质量状态，并将来源日期逐槽传给状态条', () => {
    const view = {
      screenCode: 'SCR_CODE',
      renderPackage: { canvasStyle: { presentation: {
        type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1,
        display: { components: [] }
      } } }
    };
    const wrapper = mount(PanoramaRuntime, { props: { view, context: {} } });

    expect(wrapper.get('[data-testid="presentation-runtime-status"]').attributes('data-state')).toBe('STALE');
    expect(wrapper.get('[data-testid="presentation-runtime-batch"]').text()).toContain('B-OLD');
    expect(wrapper.findAll('[data-testid="presentation-runtime-source-date"]').length).not.toBe(0);
    expect(wrapper.text()).toContain('客户当前无有效值');
    wrapper.unmount();
  });
});
