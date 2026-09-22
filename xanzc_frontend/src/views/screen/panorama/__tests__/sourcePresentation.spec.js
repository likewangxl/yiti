// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import {
  applyMetricLabels,
  resolveSourcePresentation,
  resolveDataStatus
} from '../sourcePresentation';

const { dashboardStub, retailDashboardStub, router, runtimeIssues } = vi.hoisted(() => ({
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
  router: { push: vi.fn(), back: vi.fn() },
  runtimeIssues: { value: {} }
}));

vi.mock('../PanoramaDashboard.vue', () => ({ default: dashboardStub }));
vi.mock('../RetailDashboard.vue', () => ({ default: retailDashboardStub }));
vi.mock('vue-router', () => ({ useRouter: () => router }));
vi.mock('../usePanoramaData', () => ({
  usePanoramaData: () => ({
    model: { value: { title: '总览', kpis: [{ key: 'deposit', label: '原标签', value: 12, unit: '亿元' }] } },
    loading: { value: false },
    error: { value: '' },
    slotIssues: runtimeIssues,
    refresh: vi.fn(),
    selectBranch: vi.fn()
  })
}));

import PanoramaRuntime from '../PanoramaRuntime.vue';

describe('source presentation', () => {
  it('不同合法来源日期作为溯源提示，不能伪装成取数失败遮住页面', () => {
    runtimeIssues.value={corpRevenue:[{code:'MIXED_DATES',message:'不同来源统计期间不同'}]};
    const wrapper=mount(PanoramaRuntime,{props:{view:{},context:{}}});
    expect(wrapper.get('[data-testid="panorama-slot-issues"]').text()).toContain('不同来源统计期间不同');
    expect(wrapper.find('[data-testid="runtime-source-summary"]').exists()).toBe(false);
    wrapper.unmount();runtimeIssues.value={};
  });
  it('右下诊断保留真正请求错误，过滤已就近解释的 NO_VALUES', () => {
    runtimeIssues.value = {
      deposit: [{ code: 'NO_VALUES', field: 'value', message: '字段 value 当前无有效值' }],
      loan: [{ code: 'REQUEST_FAILED', field: 'request', message: '贷款请求失败' }]
    };
    const wrapper = mount(PanoramaRuntime, { props: { view: {}, context: {} } });
    expect(wrapper.find('[data-testid="panorama-slot-issues"]').text()).toContain('贷款请求失败');
    expect(wrapper.find('[data-testid="panorama-slot-issues"]').text()).not.toContain('字段 value');
    runtimeIssues.value = {};
  });

  it('dataNotice 保留121到240字符，sourceAvailability message 超过120字符拒绝', () => {
    const notice = 'n'.repeat(121);
    const longMessage = 'm'.repeat(121);
    const result = resolveSourcePresentation({ renderPackage: { canvasStyle: {
      dataNotice: notice,
      sourceAvailability: {
        attention: { status: 'NO_SOURCE', message: longMessage },
        rate: { message: '缺少合法状态，不应保留' }
      }
    } } });
    expect(result.dataNotice).toBe(notice);
    expect(result.sourceAvailability).toEqual({ attention: { status: 'NO_SOURCE', message: '' } });
  });

  it('保留白名单槽位及字段级状态，并拒绝非法状态、markup 和日期', () => {
    const result = resolveSourcePresentation({ renderPackage: { canvasStyle: {
      sourceAvailability: {
        attention: { status: 'NO_SOURCE', message: '尚无按当前机构范围聚合的流程与经营关注数据源' },
        composition: { status: 'PARTIAL', fields: { corporate: { status: 'NO_VALUES', message: '最新周期无有效值' } } },
        trend: { status: 'PARTIAL', fields: { depositIncrease: { status: 'NO_VALUES', message: '<bad>' } } },
        unknown: { status: 'NO_ROWS', message: '不应进入模型' },
        loan: { fields: { madeUp: { status: 'NO_VALUES', message: '不应进入模型' } } },
        ranking: { status: 'AVAILABLE', fields: { increase: { status: 'AVAILABLE', message: '不应展示', dataDate: '2026-99-99' } } }
      }
    } } });
    expect(result.sourceAvailability).toEqual({
      attention: { status: 'NO_SOURCE', message: '尚无按当前机构范围聚合的流程与经营关注数据源' },
      composition: { status: 'PARTIAL', message: '', fields: { corporate: { status: 'NO_VALUES', message: '最新周期无有效值' } } },
      trend: { status: 'PARTIAL', message: '', fields: { depositIncrease: { status: 'NO_VALUES', message: '' } } },
      ranking: { status: 'AVAILABLE', message: '', fields: { increase: { status: 'AVAILABLE', message: '不应展示' } } }
    });
  });

  it('保留 loanRate 独立标签和来源字段状态，不映射为旧 rate', () => {
    const result = resolveSourcePresentation({ renderPackage: { canvasStyle: {
      metricLabels: { rate: '存款目标完成率', loanRate: '零售贷款目标完成率' },
      sourceAvailability: {
        loanRate: { status: 'NO_SOURCE', message: '贷款目标来源尚未接入', fields: {
          value: { status: 'NO_VALUES', message: '当前无有效值' }
        } }
      }
    } } });
    expect(result.metricLabels).toEqual({ rate: '存款目标完成率', loanRate: '零售贷款目标完成率' });
    expect(result.sourceAvailability.loanRate).toEqual({
      status: 'NO_SOURCE', message: '贷款目标来源尚未接入',
      fields: { value: { status: 'NO_VALUES', message: '当前无有效值' } }
    });
    expect(resolveDataStatus(result, [], 'loanRate', 'value')).toEqual({
      status: 'NO_VALUES', message: '当前无有效值'
    });
    const model = { kpis: [
      { key: 'loanRate', label: '旧标签', value: null, unit: '%' },
      { key: 'rate', label: '旧存款标签', value: 88, unit: '%' }
    ] };
    expect(applyMetricLabels(model, result.metricLabels).kpis).toEqual([
      { key: 'loanRate', label: '零售贷款目标完成率', value: null, unit: '%' },
      { key: 'rate', label: '存款目标完成率', value: 88, unit: '%' }
    ]);
  });

  it('经营关注允许按机构号声明字段并保留中文字段语义', () => {
    const result = resolveSourcePresentation({ renderPackage: { canvasStyle: {
      sourceAvailability: {
        attention: { status: 'AVAILABLE', fields: {
          orgCode: { status: 'AVAILABLE', message: '机构号' }
        } }
      }
    } } });
    expect(result.sourceAvailability.attention.fields.orgCode).toEqual({ status: 'AVAILABLE', message: '机构号' });
  });

  it('运行时字段 issue 优先于静态声明，未声明时返回明确泛化空态', () => {
    const presentation = resolveSourcePresentation({ renderPackage: { canvasStyle: {
      sourceAvailability: { ranking: { status: 'PARTIAL', fields: { increase: { status: 'NO_VALUES', message: '静态说明' } } } }
    } } });
    expect(resolveDataStatus(presentation, { ranking: [{ field: 'increase', message: '请求字段为空' }] }, 'ranking', 'increase')).toEqual({ status: 'RUNTIME', message: '请求字段为空' });
    expect(resolveDataStatus(presentation, [{ slot: 'loan', field: 'request', message: '查询失败' }, { slot: 'ranking', field: 'increase', message: '别的错误' }], 'loan', 'value')).toEqual({ status: 'RUNTIME', message: '查询失败' });
    expect(resolveDataStatus(presentation, {}, 'citySummary', 'customers')).toEqual({ status: 'UNAVAILABLE', message: '暂无有效数据' });
    expect(resolveDataStatus(presentation, {}, 'ranking', 'increase')).toEqual({ status: 'NO_VALUES', message: '静态说明' });
  });

  it('运行时 Quality 的 PARTIAL/NO_COMPLETE_BATCH 优先静态 AVAILABLE', () => {
    const presentation = {
      sourceAvailability: { deposit: { status: 'AVAILABLE', message: '' } },
      runtimeQuality: {
        status: 'NO_COMPLETE_BATCH',
        message: '授权机构范围内没有完整批次'
      }
    };
    expect(resolveDataStatus(presentation, [], 'deposit', 'value')).toEqual({
      status: 'NO_COMPLETE_BATCH', message: '授权机构范围内没有完整批次'
    });
  });

  it('NO_VALUES 运行诊断优先使用静态中文说明，真正错误仍优先，缺静态时不泄漏 semantic', () => {
    const presentation = resolveSourcePresentation({ renderPackage: { canvasStyle: {
      sourceAvailability: {
        ranking: { status: 'PARTIAL', fields: { increase: { status: 'NO_VALUES', message: '最新周期无有效值' } } }
      }
    } } });
    expect(resolveDataStatus(presentation, { ranking: [{ field: 'increase', code: 'NO_VALUES', message: '字段 increase 当前无有效值' }] }, 'ranking', 'increase')).toEqual({ status: 'NO_VALUES', message: '最新周期无有效值' });
    expect(resolveDataStatus(presentation, { ranking: [{ field: 'increase', code: 'REQUEST_FAILED', message: '请求失败' }] }, 'ranking', 'increase')).toEqual({ status: 'RUNTIME', message: '请求失败' });
    const generic = resolveDataStatus(presentation, { trend: [{ field: 'depositIncrease', code: 'NO_VALUES', message: '字段 depositIncrease 当前无有效值' }] }, 'trend', 'depositIncrease');
    expect(generic.message).toContain('存款较上月净增');
    expect(generic.message).not.toContain('depositIncrease');
  });

  it('没有配置时不产生来源提示或额外标签', () => {
    const view = { renderPackage: { canvasStyle: {} } };
    expect(resolveSourcePresentation(view)).toEqual({ dataNotice: '', metricLabels: {}, sourceAvailability: {} });
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
      metricLabels: { deposit: '一般性存款余额' },
      sourceAvailability: {}
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

  it('运行时不显示顶部来源说明，但标签覆盖仍限制在展示模型', () => {
    const view = {
      renderPackage: {
        canvasStyle: {
          dataNotice: '系统联调数据：<测试计算>',
          metricLabels: { deposit: '一般性存款余额' }
        }
      }
    };
    const wrapper = mount(PanoramaRuntime, { props: { view, context: {} } });

    expect(wrapper.find('[data-testid="panorama-data-notice"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('系统联调数据');
    expect(wrapper.find('[data-testid="source-dashboard"]').text()).toContain('一般性存款余额|12|亿元');
    expect(wrapper.html()).not.toContain('<testing');
  });

  it('运行时根保留沉浸式深色视觉类名，不再渲染来源条', () => {
    const view = {
      renderPackage: {
        canvasStyle: { dataNotice: '系统联调数据' }
      }
    };
    const wrapper = mount(PanoramaRuntime, { props: { view, context: {} } });

    expect(wrapper.get('[data-testid="panorama-runtime"]').classes()).toContain('panorama-runtime--immersive');
    expect(wrapper.find('[data-testid="panorama-data-notice"]').exists()).toBe(false);
  });
});
