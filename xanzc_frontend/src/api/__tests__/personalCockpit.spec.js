import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ view: vi.fn(), data: vi.fn(), workspace: vi.fn() }));
vi.mock('@/api/screen', () => ({ getScreenView: api.view, queryScreenData: api.data }));
vi.mock('@/api/personalDashboard', () => ({ buildPersonalDashboardModel: (sources, identity) => ({ identity, metrics: { items: sources.workspace.value.metrics || [] } }), getPersonalWorkspace: api.workspace }));
import { loadPersonalCockpit } from '../personalCockpit';
const identity = { empId: 'E-1', mainOrgCode: 'O-1', displayName: '验收用户', mainOrgName: '验收机构' };
const kinds = ['current', 'history', 'organization', 'target'];
function view(version = 1, state = 'published') {
  const bindings = Object.fromEntries(kinds.map((kind, index) => [kind, { dsId: 21 + index, valueField: 'v', dateField: 'd', unit: '万元' }]));
  const settings = { version: 1, title: '个人', defaultRange: '6m', metrics: [{ metricCode: 'sales', metricName: '余额', unit: '万元', bindings }] };
  const components = [{ id: 'personal-cockpit-settings', component: 'TextLabel', propValue: { personalDashboard: settings } }, ...kinds.map((kind, index) => ({ id: kind, component: 'ChartWidget', blockId: 11 + index, propValue: { personalMetricCode: 'sales', personalSourceKind: kind }, bindJson: '{"dsId":999}' }))];
  const bindSnapshots = Object.fromEntries(kinds.map((kind, index) => [11 + index, { bind: { dsId: 21 + index, fieldMap: { value: 'v', date: 'd' } } }]));
  return { screenCode: 'PERSONAL_COCKPIT', runtimeSchemaVersion: version, state, renderPackageJson: JSON.stringify({ schemaVersion: version, components, bindSnapshots }) };
}
const row = (value, day = '2026-10-28') => ({ columns: ['d', 'v'], rows: [[day, value]] });
beforeEach(() => {
  vi.useFakeTimers(); vi.setSystemTime(new Date(2026, 9, 28, 0, 15));
  api.view.mockReset().mockResolvedValue(view());
  api.data.mockReset().mockImplementation(body => row(body.dsId === 23 || body.blockId === 13 ? 100 : body.dsId === 24 || body.blockId === 14 ? 50 : 20));
  api.workspace.mockReset().mockResolvedValue({ metrics: [{ metricCode: 'old', currentValue: 5 }] });
});
afterEach(() => vi.useRealTimers());
describe('个人驾驶舱真实运行契约', () => {
  it('读取后端二维行，schema1使用冻结来源且默认6月真实发送RANGE', async () => {
    const result = await loadPersonalCockpit({ identity });
    expect(result.range).toBe('6m');
    expect(api.data).toHaveBeenCalledWith(expect.objectContaining({ schemaVersion: 1, dsId: 22, period: 'RANGE', dateFrom: '2026-05-01', dateTo: '2026-10-28', contextParams: { empId: 'E-1', orgCode: 'O-1' } }));
    expect(api.data.mock.calls.some(([body]) => body.dsId === 999)).toBe(false);
    expect(result.metrics.items[0]).toMatchObject({ currentValue: 20, dataDate: '2026-10-28', orgValue: 100, orgUnit: '万元', targetValue: 50, achievementRate: 40 });
  });
  it('schema2只发blockId，草稿身份与服务端状态相符', async () => {
    api.view.mockResolvedValue(view(2, 'draft'));
    await loadPersonalCockpit({ identity, preview: 'draft', range: '1y' });
    expect(api.data).toHaveBeenCalledWith(expect.objectContaining({ blockId: 12, previewState: 'draft', dateFrom: '2025-11-01' }));
    expect(api.data.mock.calls.every(([body]) => !Object.hasOwn(body, 'dsId'))).toBe(true);
  });
  it.each(['RPT-43004', 'unpublished'])('仅明确未配置/%s允许保留工作台快照', async kind => {
    if (kind === 'unpublished') api.view.mockResolvedValue({ screenCode: 'PERSONAL_COCKPIT', state: 'published', renderPackageJson: null });
    else api.view.mockRejectedValue({ code: kind });
    const result = await loadPersonalCockpit({ identity });
    expect(result.metrics.items[0].metricCode).toBe('old'); expect(result.metrics.message).toContain('配置');
  });
  it.each([403, 401, 500])('HTTP %s不降级', async status => {
    api.view.mockRejectedValue({ response: { status } });
    await expect(loadPersonalCockpit({ identity })).rejects.toBeTruthy(); expect(api.workspace).not.toHaveBeenCalled();
  });
  it.each(['bad-json', 'bad-version', 'missing-snapshot', 'wrong-state', 'wrong-screen'])('坏包%s失败关闭', async kind => {
    const response = view(); const pkg = JSON.parse(response.renderPackageJson);
    if (kind === 'bad-json') response.renderPackageJson = 'broken';
    if (kind === 'bad-version') response.runtimeSchemaVersion = 2;
    if (kind === 'missing-snapshot') { delete pkg.bindSnapshots[11]; response.renderPackageJson = JSON.stringify(pkg); }
    if (kind === 'wrong-state') response.state = 'ACTIVE';
    if (kind === 'wrong-screen') response.screenCode = 'OTHER';
    api.view.mockResolvedValue(response);
    await expect(loadPersonalCockpit({ identity })).rejects.toBeTruthy(); expect(api.workspace).not.toHaveBeenCalled(); expect(api.data).not.toHaveBeenCalled();
  });
  it('空白值不变0、重复最新日期不任取、历史缺口保留null', async () => {
    api.data.mockImplementation(body => body.dsId === 21 ? { columns: ['d', 'v'], rows: [['2026-10-28', 1], ['2026-10-28', 2]] } : row('   '));
    const result = await loadPersonalCockpit({ identity, range: '7d' });
    expect(result.metrics.items[0].currentValue).toBeNull(); expect(result.metrics.items[0].history[0].value).toBeNull(); expect(result.metrics.message).toContain('失败');
  });
  it('目标日期不一致不可计算完成率', async () => {
    api.data.mockImplementation(body => row(body.dsId === 24 ? 50 : 20, body.dsId === 24 ? '2026-09-30' : '2026-10-28'));
    const result = await loadPersonalCockpit({ identity });
    expect(result.metrics.items[0].targetValue).toBeNull(); expect(result.metrics.items[0].achievementRate).toBeNull();
  });
  it('质量不完整/错误响应不能当正常数据', async () => {
    api.data.mockResolvedValue({ ...row(99), quality: { status: 'NO_COMPLETE_BATCH' } });
    const result = await loadPersonalCockpit({ identity });
    expect(result.metrics.items[0].currentValue).toBeNull(); expect(result.metrics.message).toContain('失败');
  });
  it('缺少fresh员工或主机构身份不发送取数', async () => {
    await expect(loadPersonalCockpit({ identity: { empId: 'E-1' } })).rejects.toThrow(); expect(api.data).not.toHaveBeenCalled();
  });
});
