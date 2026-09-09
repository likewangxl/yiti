// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import {
  applyMetricLabels,
  resolveSourcePresentation
} from '../sourcePresentation';

const { dashboardStub, retailDashboardStub, router } = vi.hoisted(() => ({
  dashboardStub: {
    name: 'PanoramaDashboard',
    props: ['model', 'loading', 'error', 'demo'],
    template: '<div data-testid="source-dashboard"><span v-for="item in model.kpis" :key="item.key">{{ item.label }}|{{ item.value }}|{{ item.unit }}</span></div>'
  },
  retailDashboardStub: {
    name: 'RetailDashboard',
    props: ['model', 'loading', 'error', 'demo'],
    template: '<div data-testid="retail-source-dashboard"></div>'
  },
  router: { push: vi.fn(), back: vi.fn() }
}));

vi.mock('../PanoramaDashboard.vue', () => ({ default: dashboardStub }));
vi.mock('../RetailDashboard.vue', () => ({ default: retailDashboardStub }));
vi.mock('vue-router', () => ({ useRouter: () => router }));
vi.mock('../usePanoramaData', () => ({
  usePanoramaData: () => ({
    model: { value: { title: '总览', kpis: [{ key: 'deposit', label: '原标签', value: 12, unit: '亿元' }] } },
    loading: { value: false },
    error: { value: '' },
    slotIssues: { value: {} },
    refresh: vi.fn(),
    selectBranch: vi.fn()
  })
}));

import PanoramaRuntime from '../PanoramaRuntime.vue';

describe('source presentation', () => {
  it('没有配置时不产生来源提示或额外标签', () => {
    const view = { renderPackage: { canvasStyle: {} } };
    expect(resolveSourcePresentation(view)).toEqual({ dataNotice: '', metricLabels: {} });
    const wrapper = mount(PanoramaRuntime, { props: { view, context: {} } });
    expect(wrapper.find('[data-testid="panorama-data-notice"]').exists()).toBe(false);
  });

  it('读取 canvasStyle 中的来源提示并保持为文本节点', () => {
    const view = {
      renderPackage: {
        canvasStyle: {
          dataNotice: '系统联调数据：当前指标结果含测试计算',
          metricLabels: { deposit: '一般性存款余额' }
        }
      }
    };
    expect(resolveSourcePresentation(view)).toEqual({
      dataNotice: '系统联调数据：当前指标结果含测试计算',
      metricLabels: { deposit: '一般性存款余额' }
    });
  });

  it('只覆盖已有 KPI 白名单的有效短纯文本 label，不接受对象或 HTML', () => {
    const source = {
      title: '总览',
      kpis: [
        { key: 'deposit', label: '原存款', value: 12, unit: '亿元', values: [12] },
        { key: 'unknown', label: '原未知', value: 5, unit: '个' },
        { key: 'loan', label: '原贷款', value: 8, unit: '亿元' }
      ]
    };
    const result = applyMetricLabels(source, {
      deposit: '一般性存款余额',
      loan: { text: '不应执行' },
      customers: '<img src=x onerror=alert(1)>',
      unknown: '不应覆盖'
    });

    expect(result).not.toBe(source);
    expect(result.kpis).not.toBe(source.kpis);
    expect(result.kpis).toEqual([
      { key: 'deposit', label: '一般性存款余额', value: 12, unit: '亿元', values: [12] },
      { key: 'unknown', label: '原未知', value: 5, unit: '个' },
      { key: 'loan', label: '原贷款', value: 8, unit: '亿元' }
    ]);
    expect(source.kpis[0].label).toBe('原存款');
    expect(source.kpis[0].values).toEqual([12]);
  });

  it('运行时在大屏上方显示来源说明，并把标签覆盖限制在展示模型', () => {
    const view = {
      renderPackage: {
        canvasStyle: {
          dataNotice: '系统联调数据：<测试计算>',
          metricLabels: { deposit: '一般性存款余额' }
        }
      }
    };
    const wrapper = mount(PanoramaRuntime, { props: { view, context: {} } });

    expect(wrapper.find('[data-testid="panorama-data-notice"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="panorama-data-notice"]').text()).toBe('系统联调数据：<测试计算>');
    expect(wrapper.find('[data-testid="source-dashboard"]').text()).toContain('一般性存款余额|12|亿元');
    expect(wrapper.html()).not.toContain('<testing');
  });
});
