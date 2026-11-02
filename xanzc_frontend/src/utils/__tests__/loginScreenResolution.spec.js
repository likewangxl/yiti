import { describe, expect, it, vi } from 'vitest';
import { resolveLoginScreen } from '../loginScreen';

function fixture(target = 'PERSONAL') {
  const view = { screenCode: 'SCR_PROVINCE', state: 'published', runtimeSchemaVersion: 2,
    orgScopeMode: 'NAMED_GROUP', institutionRules: { allowedOperatingLevels: ['SUB_BRANCH'] },
    panoramaInstitutions: [{ orgCode: 'B01', cityCode: 'CITY1', operatingLevel: 'SUB_BRANCH' }],
    renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {},
      canvasStyle: { dataClassification: 'TEST', presentation: { type: 'CODE', template: 'branch-overview-v1' } } }) };
  return { user: { empId: 'U1', mainOrgCode: 'B01', roles: [{ roleId: 'R1', primary: true }, { roleId: 'R2' }] },
    payload: { rules: [{ roleId: 'R1', target, priority: 100 }] },
    menuStore: { hasUrl: vi.fn().mockReturnValue(true), resolveLandingPath: () => '/workspace' },
    permissionStore: { canAccess: vi.fn().mockReturnValue(true) },
    listAvailableScreens: vi.fn().mockResolvedValue([{ screenCode: 'SCR_PROVINCE', template: 'branch-overview-v1', dataMode: 'TEST' }]),
    getScreenView: vi.fn().mockResolvedValue(view), isCurrent: () => true };
}
describe('登录首屏授权解析', () => {
  it('个人大屏沿用工作台门禁且不请求机构目录', async () => {
    const f = fixture();
    expect(await resolveLoginScreen(f)).toEqual({ name: 'PersonalDashboard', query: { from: 'login' } });
    expect(f.listAvailableScreens).not.toHaveBeenCalled();
  });
  it('主角色屏无授权时尝试其他已分配角色，不以第一角色顺序决定', async () => {
    const f = fixture('RETAIL');
    f.payload.rules.push({ roleId: 'R2', target: 'PERSONAL', priority: 1 });
    expect((await resolveLoginScreen(f)).name).toBe('PersonalDashboard');
  });
  it('支行使用已发布父屏、主机构及返回的城市，不取目录首个机构', async () => {
    const f = fixture('BRANCH');
    expect(await resolveLoginScreen(f)).toEqual({ name: 'BranchOperatingPage', query: {
      from: 'login', orgCode: 'B01', cityCode: 'CITY1', sourceScreenCode: 'SCR_PROVINCE', businessLine: 'COMMON' } });
  });
  it.each(['missingOrg', 'unknownLayer', 'missingCity', 'draft'])('支行 %s 时回退工作台', async kind => {
    const f = fixture('BRANCH'); const view = await f.getScreenView();
    if (kind === 'missingOrg') f.user.mainOrgCode = 'B99';
    if (kind === 'unknownLayer') view.institutionRules = {};
    if (kind === 'missingCity') view.panoramaInstitutions[0].cityCode = '';
    if (kind === 'draft') view.state = 'draft';
    expect(await resolveLoginScreen(f)).toBe('/workspace');
  });
  it('401不得被备用大屏或工作台覆盖', async () => {
    const f = fixture('PROVINCE'); const error = { response: { status: 401 } };
    f.listAvailableScreens.mockRejectedValue(error);
    await expect(resolveLoginScreen(f)).rejects.toBe(error);
  });
  it('身份已变化时丢弃结果', async () => {
    const f = fixture('PROVINCE'); f.getScreenView.mockImplementation(async () => {
      f.isCurrent = () => false; return null;
    });
    expect(await resolveLoginScreen({ ...f, isCurrent: () => f.isCurrent() })).toBeNull();
  });
  it('显式WORKSPACE停止备用规则，其他用户的规则不能参与', async () => {
    const f = fixture('WORKSPACE');
    f.payload.rules.push({ roleId: 'R2', target: 'PERSONAL', priority: 0 }, { roleId: 'FOREIGN', target: 'RETAIL', priority: 0 });
    expect(await resolveLoginScreen(f)).toBe('/workspace');
    expect(f.listAvailableScreens).not.toHaveBeenCalled();
  });
  it('网络故障拒绝展示任何候选', async () => {
    const f = fixture('PROVINCE'); f.payload.rules.push({ roleId: 'R2', target: 'PERSONAL', priority: 1 });
    f.listAvailableScreens.mockRejectedValue(new Error('network'));
    await expect(resolveLoginScreen(f)).rejects.toThrow('network');
  });
});
