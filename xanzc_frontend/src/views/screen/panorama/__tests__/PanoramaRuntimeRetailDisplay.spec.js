// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { ref } from 'vue';

const harness = vi.hoisted(() => ({ state: null }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }));
vi.mock('../usePanoramaData', () => ({ usePanoramaData: () => harness.state }));
vi.mock('../PanoramaDashboard.vue', () => ({ default: { template: '<main>分行内容</main>' } }));
vi.mock('../CorporateDashboard.vue', () => ({ default: { template: '<main>对公内容</main>' } }));
vi.mock('../RetailDashboard.vue', () => ({ default: {
  props: ['model', 'sourcePresentation', 'error'],
  template: '<main data-testid="retail-dashboard-probe">零售内容</main>'
} }));
import PanoramaRuntime from '../PanoramaRuntime.vue';

const issue = { code: 'DATE_MISMATCH', message: '零售槽位日期不一致，需核对统计期间' };
function mountRuntime(template) {
  return mount(PanoramaRuntime, { props: {
    view: { renderPackage: { canvasStyle: { presentation: { template } } } }
  } });
}

beforeEach(() => {
  harness.state = {
    model: ref({ kpis: [], issues: [issue] }),
    loading: ref(false), error: ref(''),
    slotIssues: ref({ retailRevenue: [issue] }),
    refresh: vi.fn(), selectBranch: vi.fn()
  };
});

describe('零售经营总览展示收敛', () => {
  it('有槽位异常时移除右下角提示，但继续向零售内容传递原始异常信息', () => {
    const wrapper = mountRuntime('retail-overview-v1');
    expect(wrapper.find('[data-testid="panorama-slot-issues"]').exists()).toBe(false);
    const dashboard = wrapper.findComponent({ name: 'RetailDashboard' });
    expect(dashboard.props('model').issues).toEqual([issue]);
    expect(dashboard.props('sourcePresentation').runtimeIssues.retailRevenue).toEqual([issue]);
    wrapper.unmount();
  });

  it.each(['branch-overview-v1', 'corporate-overview-v1'])('%s 继续显示槽位异常提示', template => {
    const wrapper = mountRuntime(template);
    expect(wrapper.get('[data-testid="panorama-slot-issues"]').text()).toContain(issue.message);
    wrapper.unmount();
  });

  it('零售页面仍向内容组件传递请求整体失败状态', () => {
    harness.state.error.value = '查询失败';
    const wrapper = mountRuntime('retail-overview-v1');
    expect(wrapper.findComponent({ name: 'RetailDashboard' }).props('error')).toBe('查询失败');
    wrapper.unmount();
  });
});
