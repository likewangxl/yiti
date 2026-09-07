// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  getScreenCanvas: vi.fn(),
  saveScreenMetadata: vi.fn(),
  listOrgGroups: vi.fn(),
  listScreenRoles: vi.fn(),
  listScreenAccessRoles: vi.fn(),
  saveScreenAccessRoles: vi.fn(),
  listScreenPublishLogs: vi.fn(),
  rollbackScreenCanvas: vi.fn()
}));

vi.mock('@/api/screen', () => api);

import PanoramaSettings from '../PanoramaSettings.vue';

const existingScreen = {
  id: 7,
  screenCode: 'SCR_RETAIL',
  screenName: '零售总览',
  viewLevel: 'PROVINCE',
  bizLine: 'RETAIL',
  orgScopeMode: 'NAMED_GROUP',
  orgGroupCode: 'ORG_RETAIL',
  allowedRoleCodes: ['R_OLD'],
  themeJson: '{"theme":"dark"}',
  status: 'ACTIVE',
  canvasVersion: 4
};

function resetApi() {
  api.getScreenCanvas.mockResolvedValue({ canvasVersion: 4 });
  api.listOrgGroups.mockResolvedValue([
    { groupCode: 'ORG_RETAIL', groupName: '零售机构组', groupPurpose: 'REPORT_SCREEN', status: 'ACTIVE' }
  ]);
  api.listScreenRoles.mockResolvedValue([
    { roleCode: 'R_OLD', roleChName: '旧角色', recordStatus: 0 },
    { roleCode: 'R_NEW', roleChName: '新角色', recordStatus: 0 }
  ]);
  api.listScreenAccessRoles.mockResolvedValue(['R_OLD']);
  api.listScreenPublishLogs.mockResolvedValue([
    { id: 99, screenId: 7, publishedAt: '2026-09-07 09:00:00', publishedBy: 'alice' }
  ]);
  api.saveScreenMetadata.mockResolvedValue(7);
  api.saveScreenAccessRoles.mockResolvedValue(undefined);
  api.rollbackScreenCanvas.mockResolvedValue(undefined);
}

function mountSettings(props = {}) {
  return mount(PanoramaSettings, {
    props: { screen: existingScreen, visible: true, ...props }
  });
}

describe('PanoramaSettings', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('accepts screen/null and visible props, and closes with update:visible', async () => {
    resetApi();
    const wrapper = mountSettings({ screen: null, visible: true });
    expect(wrapper.find('[data-testid="panorama-settings"]').exists()).toBe(true);
    await wrapper.find('[data-testid="settings-close"]').trigger('click');
    expect(wrapper.emitted('update:visible')).toEqual([[false]]);
  });

  it('creates a new screen with explicit metadata and emits saved', async () => {
    resetApi();
    api.saveScreenMetadata.mockResolvedValueOnce(99);
    const wrapper = mountSettings({ screen: null });
    await flushPromises();

    await wrapper.find('[data-testid="settings-screen-name"]').setValue('新经营全景');
    await wrapper.find('[data-testid="settings-screen-code"]').setValue('SCR_NEW');
    await wrapper.find('[data-testid="settings-view-level"]').setValue('BRANCH');
    await wrapper.find('[data-testid="settings-business-domain"]').setValue('COMMON');
    await wrapper.find('[data-testid="settings-scope-mode"]').setValue('LEGACY_CONTEXT');
    await wrapper.find('[data-testid="settings-save"]').trigger('click');
    await flushPromises();

    expect(api.saveScreenMetadata).toHaveBeenCalledWith({
      screenCode: 'SCR_NEW', screenName: '新经营全景', viewLevel: 'BRANCH',
      bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT', orgGroupCode: null
    });
    expect(wrapper.emitted('saved')?.[0]?.[0]).toMatchObject({ action: 'metadata', screenId: 99 });
    expect(wrapper.emitted('update:visible')).toContainEqual([false]);
  });

  it('updates metadata with the existing CAS version and audit reason', async () => {
    resetApi();
    const wrapper = mountSettings();
    await flushPromises();
    await wrapper.find('[data-testid="settings-screen-name"]').setValue('零售经营总览');
    await wrapper.find('[data-testid="settings-reason"]').setValue('调整经营屏名称');
    await wrapper.find('[data-testid="settings-save"]').trigger('click');
    await flushPromises();

    expect(api.saveScreenMetadata).toHaveBeenCalledWith({
      id: 7, screenCode: 'SCR_RETAIL', screenName: '零售经营总览', viewLevel: 'PROVINCE',
      bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'ORG_RETAIL',
      themeJson: '{"theme":"dark"}', status: 'ACTIVE', expectedVersion: 4, reason: '调整经营屏名称'
    });
  });

  it('saves role whitelist through the independent permission endpoint with CAS and reason', async () => {
    resetApi();
    const wrapper = mountSettings();
    await flushPromises();
    await wrapper.find('[data-testid="role-R_NEW"]').setValue(true);
    await wrapper.find('[data-testid="role-R_OLD"]').setValue(false);
    await wrapper.find('[data-testid="settings-role-reason"]').setValue('调整查看角色');
    await wrapper.find('[data-testid="settings-save-roles"]').trigger('click');
    await flushPromises();

    expect(api.saveScreenAccessRoles).toHaveBeenCalledWith(7, {
      roleCodes: ['R_NEW'], expectedVersion: 4, reason: '调整查看角色'
    });
    expect(api.saveScreenMetadata).not.toHaveBeenCalled();
  });

  it('loads publish logs and rolls back a selected archive with CAS and audit reason', async () => {
    resetApi();
    const wrapper = mountSettings();
    await flushPromises();
    expect(wrapper.find('[data-testid="settings-publish-log"] option[value="99"]').exists()).toBe(true);
    await wrapper.find('[data-testid="settings-publish-log"]').setValue('99');
    await wrapper.find('[data-testid="settings-rollback-reason"]').setValue('回退异常发布');
    await wrapper.find('[data-testid="settings-rollback"]').trigger('click');
    await flushPromises();

    expect(api.rollbackScreenCanvas).toHaveBeenCalledWith({
      screenId: 7, publishLogId: 99, expectedVersion: 4, reason: '回退异常发布'
    });
  });

  it('角色白名单接口失败时明确报错并禁止角色写入', async () => {
    resetApi();
    api.listScreenAccessRoles.mockRejectedValueOnce(new Error('角色服务不可用'));
    const wrapper = mountSettings();
    await flushPromises();

    expect(wrapper.find('[data-testid="settings-error"]').text()).toContain('角色服务不可用');
    expect(wrapper.find('[data-testid="settings-save-roles"]').element.disabled).toBe(true);
    await wrapper.find('[data-testid="settings-role-reason"]').setValue('修正角色范围');
    await wrapper.find('[data-testid="settings-save-roles"]').trigger('click');
    expect(api.saveScreenAccessRoles).not.toHaveBeenCalled();
  });

  it('角色候选接口失败时即使白名单成功也禁止写入', async () => {
    resetApi();
    api.listScreenRoles.mockRejectedValueOnce(new Error('角色目录不可用'));
    const wrapper = mountSettings();
    await flushPromises();

    expect(wrapper.find('[data-testid="settings-error"]').text()).toContain('角色目录不可用');
    expect(wrapper.find('[data-testid="settings-save-roles"]').element.disabled).toBe(true);
    await wrapper.find('[data-testid="settings-role-reason"]').setValue('角色目录恢复前不写入');
    await wrapper.find('[data-testid="settings-save-roles"]').trigger('click');
    expect(api.saveScreenAccessRoles).not.toHaveBeenCalled();
  });

  it('设置加载期间三个写入口都被禁用且函数守卫拒绝写请求', async () => {
    resetApi();
    let resolveCanvas;
    api.getScreenCanvas.mockReturnValue(new Promise(resolve => { resolveCanvas = resolve; }));
    const wrapper = mountSettings({ screen: { ...existingScreen, canvasVersion: null } });
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(7));

    expect(wrapper.find('[data-testid="settings-save"]').element.disabled).toBe(true);
    expect(wrapper.find('[data-testid="settings-save-roles"]').element.disabled).toBe(true);
    expect(wrapper.find('[data-testid="settings-rollback"]').element.disabled).toBe(true);
    expect(await wrapper.vm.saveMetadata()).toBe(false);
    expect(await wrapper.vm.saveRoles()).toBe(false);
    expect(await wrapper.vm.rollbackCanvas()).toBe(false);
    expect(api.saveScreenMetadata).not.toHaveBeenCalled();
    expect(api.saveScreenAccessRoles).not.toHaveBeenCalled();
    expect(api.rollbackScreenCanvas).not.toHaveBeenCalled();

    resolveCanvas({ canvasVersion: 4 });
    await flushPromises();
  });

  it('A/B 同版本切换时迟到的 A 响应不能覆盖 B 的角色、归档或版本', async () => {
    resetApi();
    const screenA = { ...existingScreen, id: 11, screenName: '屏 A', canvasVersion: null };
    const screenB = { ...existingScreen, id: 12, screenName: '屏 B', canvasVersion: null };
    const canvasResolvers = {};
    api.getScreenCanvas.mockImplementation(id => new Promise(resolve => { canvasResolvers[id] = resolve; }));
    api.listScreenAccessRoles.mockImplementation(id => Promise.resolve(id === 11 ? ['R_OLD'] : ['R_NEW']));
    api.listScreenPublishLogs.mockImplementation(id => Promise.resolve([
      { id: id * 10, screenId: id, publishedAt: `2026-09-07 ${id}`, publishedBy: `user-${id}` }
    ]));
    const wrapper = mount(PanoramaSettings, { props: { screen: screenA, visible: true } });
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(11));

    await wrapper.setProps({ screen: screenB });
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(12));
    // Resolve B first, then resolve A with the exact same version.  The
    // captured target + generation must keep the late A result inert.
    canvasResolvers[12]({ canvasVersion: 4 });
    await flushPromises();
    expect(wrapper.find('[data-testid="settings-screen-name"]').element.value).toBe('屏 B');
    expect(wrapper.find('[data-testid="settings-publish-log"] option[value="120"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="role-R_NEW"]').element.checked).toBe(true);

    canvasResolvers[11]({ canvasVersion: 4 });
    await flushPromises();
    expect(wrapper.find('[data-testid="settings-screen-name"]').element.value).toBe('屏 B');
    expect(wrapper.find('[data-testid="settings-publish-log"] option[value="110"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="role-R_NEW"]').element.checked).toBe(true);
  });
});
