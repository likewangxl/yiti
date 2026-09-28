// @vitest-environment happy-dom
import { describe, expect, it, vi, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
const api = vi.hoisted(() => ({ schemes: vi.fn(), results: vi.fn() }));
vi.mock('@/api/branchPerformance', () => ({ listBranchKpiSchemes: api.schemes, listBranchKpiResults: api.results }));
import BranchPerformancePanel from '../BranchPerformancePanel.vue';
const wrappers = [];
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.resetAllMocks(); });
function setup() {
  api.schemes.mockResolvedValue({ records: [{ schemeCode: 'S1', schemeName: '季度考核' }], total: 1 });
  api.results.mockImplementation(async p => ({ scopeOrgCode: p.orgCode, total: p.subjectType === 'EMP' ? 2 : 1, metrics: [{ metricCode: 'M1', metricName: '贷款净增' }], records: p.subjectType === 'EMP'
    ? ['A', 'B'].map(subjectId => ({ subjectId, subjectName: subjectId, totalScore: 80, subjectType: 'EMP', metrics: { M1: { actual: 20, base: 0, target: 25, completeRate: 80, score: 80 } } }))
    : [{ subjectId: p.orgCode, subjectType: 'ORG', metrics: { M1: { actual: 120, base: 100, target: 25, completeRate: 80, score: 80 } } }] }));
  const wrapper = mount(BranchPerformancePanel, { props: { orgCode: '105', dataDate: '2026-09-20', enabled: true } });
  wrappers.push(wrapper); return wrapper;
}
describe('支行KPI统计组件', () => {
  it('按同方案日期机构查询并显示并列名次，显示独立来源口径', async () => {
    const w = setup(); await flushPromises();
    expect(api.results).toHaveBeenCalledWith(expect.objectContaining({ orgCode: '105', dataDate: '2026-09-20', schemeCode: 'S1', subjectType: 'EMP' }));
    expect(w.findAll('[data-testid="branch-personal-kpi-row"]')).toHaveLength(2);
    expect(w.text()).toContain('同分并列');
    expect(w.text()).toContain('基础值');
    expect(w.text()).toContain('KPI计分系统');
  });
  it('切换机构立即清空旧排名，迟到响应不复活', async () => {
    const w = setup(); await flushPromises();
    let finish;
    api.results.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    await w.setProps({ orgCode: '451' });
    expect(w.findAll('[data-testid="branch-personal-kpi-row"]')).toHaveLength(0);
    await w.setProps({ enabled: false });
    finish?.({ records: [], metrics: [], total: 0 }); await flushPromises();
    expect(w.findAll('[data-testid="branch-personal-kpi-row"]')).toHaveLength(0);
  });
  it('权限失败明确提示，不能以零人数或旧排名兜底', async () => {
    const w = setup(); await flushPromises();
    api.results.mockRejectedValue(Object.assign(new Error('没有KPI权限'), { response: { status: 403 } }));
    await w.get('[data-testid="branch-kpi-refresh"]').trigger('click'); await flushPromises();
    expect(w.get('[role="alert"]').text()).toContain('权限');
    expect(w.findAll('[data-testid="branch-personal-kpi-row"]')).toHaveLength(0);
  });

  it('同一机构切换数据日期会清空旧排名并按新日期重新查询', async () => {
    const w = setup(); await flushPromises();
    api.results.mockImplementation(async p => ({ scopeOrgCode: p.orgCode, total: 1, metrics: [{ metricCode: 'M1', metricName: '贷款净增' }], records: p.subjectType === 'EMP'
      ? [{ subjectId: 'C', subjectName: p.dataDate, totalScore: 90, subjectType: 'EMP', metrics: { M1: { actual: 20, base: 0, target: 25, completeRate: 80, score: 90 } } }]
      : [{ subjectId: p.orgCode, subjectName: p.orgCode, totalScore: 80, subjectType: 'ORG', metrics: { M1: { actual: 120, base: 100, target: 25, completeRate: 80, score: 80 } } }] }));
    await w.setProps({ dataDate: '2026-09-21' });
    await flushPromises();
    expect(api.results).toHaveBeenLastCalledWith(expect.objectContaining({ orgCode: '105', dataDate: '2026-09-21', subjectType: 'EMP' }));
    expect(w.text()).toContain('2026-09-21');
  });

  it('紧凑员工排名保留四列、分页和不完整提示，表格自身滚动而不裁掉底部控件', async () => {
    api.schemes.mockResolvedValue({ records: [{ schemeCode: 'S1', schemeName: '季度考核' }], total: 1 });
    api.results.mockImplementation(async p => p.subjectType === 'EMP'
      ? { scopeOrgCode: p.orgCode, total: 12, metrics: [{ metricCode: 'M1', metricName: '贷款净增' }], records: Array.from({ length: 12 }, (_, index) => ({
        subjectId: `E${index}`, subjectName: `员工${index}`, totalScore: 100 - index, subjectType: 'EMP', metrics: { M1: { actual: 20, base: 0, target: 25, completeRate: 80, score: 100 - index } }
      })) }
      : { scopeOrgCode: p.orgCode, total: 1, metrics: [{ metricCode: 'M1', metricName: '贷款净增' }], records: [{ subjectId: p.orgCode, subjectType: 'ORG', metrics: { M1: { actual: 120, base: 100, target: 25, completeRate: 80, score: 80 } } }] });
    const w = mount(BranchPerformancePanel, { props: { orgCode: '105', dataDate: '2026-09-20', enabled: true, compact: true, rankingOnly: true } });
    wrappers.push(w);
    await flushPromises();
    expect(w.findAll('[data-testid="branch-personal-kpi-row"]')).toHaveLength(10);
    expect(w.get('[data-testid="branch-kpi-next"]').exists()).toBe(true);
    expect(w.get('table thead').findAll('th')).toHaveLength(4);
    expect(w.get('[data-testid="branch-performance-panel"]').classes()).toEqual(expect.arrayContaining(['branch-performance--compact', 'branch-performance--ranking-only']));
    await w.get('[data-testid="branch-kpi-next"]').trigger('click');
    expect(w.findAll('[data-testid="branch-personal-kpi-row"]')).toHaveLength(2);
    expect(w.get('[data-testid="branch-kpi-next"]').element.disabled).toBe(true);
    await w.get('[data-testid="branch-kpi-prev"]').trigger('click');
    expect(w.findAll('[data-testid="branch-personal-kpi-row"]')).toHaveLength(10);
  });
});
