// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listOrgProfiles: vi.fn(),
  listOrgGroups: vi.fn()
}));
vi.mock('@/api/screen', () => api);

const router = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock('vue-router', () => ({ useRouter: () => router }));

vi.mock('@/utils/orgProfileReadiness', () => ({
  analyzeOrgProfiles: vi.fn(rows => ({
    total: rows.length,
    profileConfigured: rows.length,
    missingProfile: 0,
    enabled: rows.length,
    disabled: 0,
    unknownStatus: 0,
    located: 0,
    missingCoordinates: rows.length,
    missingCity: 0,
    missingAddress: rows.length,
    entries: rows.map(row => ({
      orgCode: row.orgCode,
      orgName: row.orgName,
      profileStatus: 'ACTIVE',
      hasCity: Boolean(row.cityCode),
      located: false,
      hasAddress: false,
      issues: ['UNCONFIGURED', 'MISSING_COORDINATES', 'MISSING_CITY', 'MISSING_ADDRESS', 'UNKNOWN_STATUS']
    }))
  }))
}));

import PanoramaIntegrationReadiness from '../PanoramaIntegrationReadiness.vue';

const source = {
  id: 77,
  dsName: '机构经营宽表',
  sourceKind: 'WIDE_TABLE',
  status: 'ACTIVE',
  configJson: JSON.stringify({ fieldMeta: [{ col: 'deposit_raw', alias: '存款原值', role: 'METRIC' }] })
};
const screen = { id: 9, screenCode: 'SCR_A', screenName: 'A屏', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'G_A' };
const bindingState = { deposit: { dsId: 77, period: 'LATEST', fields: { value: 'deposit_raw' }, units: { value: 'YUAN' } } };

async function settle() {
  await flushPromises();
  await flushPromises();
}

describe('PanoramaIntegrationReadiness', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.listOrgProfiles.mockResolvedValue([]);
    api.listOrgGroups.mockResolvedValue([]);
  });

  afterEach(() => vi.restoreAllMocks());

  it('显示 14 槽静态预检和明确的非取数/非发布边界，初始不调用机构接口', () => {
    const wrapper = mount(PanoramaIntegrationReadiness, {
      props: { screens: [screen], screen, canvas: { screenId: 9 }, datasources: [source], bindingState }
    });

    expect(wrapper.find('[data-testid="integration-readiness"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid^="readiness-slot-"]')).toHaveLength(14);
    expect(wrapper.text()).toContain('静态预检');
    expect(wrapper.text()).toContain('未执行取数');
    expect(wrapper.text()).toContain('未发布');
    expect(wrapper.find('[data-testid="check-org-profiles"]').text()).toBe('检查机构画像');
    expect(wrapper.find('[data-testid="check-org-group"]').text()).toBe('检查当前屏机构组');
    expect(api.listOrgProfiles).not.toHaveBeenCalled();
    expect(api.listOrgGroups).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="readiness-try-run"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="readiness-probe"]').exists()).toBe(false);
  });

  it('机构画像只在点击后读取，并显示画像分析结果和管理入口', async () => {
    api.listOrgProfiles.mockResolvedValue([{ orgCode: 'ORG-1', orgName: '一号机构', cityCode: '610100' }]);
    const wrapper = mount(PanoramaIntegrationReadiness, { props: { screen, canvas: {}, datasources: [], bindingState: {} } });

    await wrapper.find('[data-testid="check-org-profiles"]').trigger('click');
    await settle();

    expect(api.listOrgProfiles).toHaveBeenCalledTimes(1);
    expect(api.listOrgProfiles).toHaveBeenCalledWith({});
    expect(wrapper.text()).toContain('一号机构');
    expect(wrapper.text()).toContain('未配置画像');
    expect(wrapper.text()).toContain('待定位');
    expect(wrapper.text()).toContain('城市编码待维护');
    expect(wrapper.text()).toContain('地址需在地址与定位中核对');
    expect(wrapper.text()).toContain('画像状态待核验');
    expect(wrapper.text()).not.toContain('MISSING_COORDINATES');
    await wrapper.find('[data-testid="goto-org-profiles"]').trigger('click');
    expect(router.push).toHaveBeenCalledWith('/screen-admin/org-profiles');
  });

  it('机构目录响应格式错误时显示检查失败，不把 malformed 响应伪装为空目录', async () => {
    api.listOrgProfiles.mockResolvedValue({ unexpected: [] });
    const wrapper = mount(PanoramaIntegrationReadiness, { props: { screen, canvas: {}, datasources: [], bindingState: {} } });

    await wrapper.find('[data-testid="check-org-profiles"]').trigger('click');
    await settle();

    expect(wrapper.find('[data-testid="org-profile-check-error"]').text()).toContain('格式无法识别');
    expect(wrapper.text()).not.toContain('机构画像：共 0 条');
  });

  it('接受管理接口约定的 records/list 包装并保留目录行', async () => {
    api.listOrgProfiles.mockResolvedValueOnce({ list: [{ orgCode: 'ORG-LIST', orgName: '列表机构' }] });
    api.listOrgGroups.mockResolvedValueOnce({ records: [{ groupCode: 'G_A', groupName: 'records机构组', memberOrgCodes: ['ORG-RECORDS'] }] });
    const wrapper = mount(PanoramaIntegrationReadiness, { props: { screen, canvas: {}, datasources: [], bindingState: {} } });

    await wrapper.find('[data-testid="check-org-profiles"]').trigger('click');
    await settle();
    expect(wrapper.text()).toContain('列表机构');

    await wrapper.find('[data-testid="check-org-group"]').trigger('click');
    await settle();
    expect(wrapper.text()).toContain('records机构组');
    expect(wrapper.text()).toContain('ORG-RECORDS');
  });

  it('机构组检查只匹配当前 screen 的编码并展示直接成员，不按组名推断或补成员', async () => {
    api.listOrgGroups.mockResolvedValue([
      { groupCode: 'G_OTHER', groupName: '全省一级经营机构', memberOrgCodes: ['OUTSIDE'] },
      { groupCode: 'G_A', groupName: '当前屏明确机构组', memberOrgCodes: ['ORG-1', 'ORG-2'] }
    ]);
    const wrapper = mount(PanoramaIntegrationReadiness, { props: { screen, canvas: {}, datasources: [], bindingState: {} } });

    await wrapper.find('[data-testid="check-org-group"]').trigger('click');
    await settle();

    expect(api.listOrgGroups).toHaveBeenCalledTimes(1);
    expect(api.listOrgGroups).toHaveBeenCalledWith({});
    expect(wrapper.text()).toContain('当前屏明确机构组');
    expect(wrapper.text()).toContain('ORG-1、ORG-2');
    expect(wrapper.text()).not.toContain('OUTSIDE');
    await wrapper.find('[data-testid="goto-org-groups"]').trigger('click');
    expect(router.push).toHaveBeenCalledWith('/screen-admin/org-groups');
  });

  it('403 会清空旧机构检查并明确显示拒绝，屏切换后丢弃迟到结果', async () => {
    let resolveProfiles;
    api.listOrgProfiles.mockResolvedValueOnce([{ orgCode: 'ORG-1', orgName: '旧检查机构' }]);
    const wrapper = mount(PanoramaIntegrationReadiness, { props: { screen, canvas: {}, datasources: [], bindingState: {} } });
    await wrapper.find('[data-testid="check-org-profiles"]').trigger('click');
    await settle();
    expect(wrapper.text()).toContain('旧检查机构');

    api.listOrgProfiles.mockRejectedValueOnce({ response: { status: 403 } });
    await wrapper.find('[data-testid="check-org-profiles"]').trigger('click');
    await settle();
    expect(wrapper.text()).toContain('403');
    expect(wrapper.text()).not.toContain('旧检查机构');

    api.listOrgProfiles.mockImplementationOnce(() => new Promise(resolve => { resolveProfiles = resolve; }));
    await wrapper.find('[data-testid="check-org-profiles"]').trigger('click');
    await wrapper.setProps({ screen: { ...screen, id: 10, screenCode: 'SCR_B', orgGroupCode: 'G_B' } });
    resolveProfiles([{ orgCode: 'ORG-LATE', orgName: '迟到机构' }]);
    await settle();
    expect(wrapper.text()).not.toContain('迟到机构');
  });

  it('开始另一类检查会废弃旧请求的 loading，迟到的旧响应不重新显示结果', async () => {
    let resolveProfiles;
    api.listOrgProfiles.mockImplementationOnce(() => new Promise(resolve => { resolveProfiles = resolve; }));
    api.listOrgGroups.mockResolvedValueOnce([{ groupCode: 'G_A', groupName: '当前屏机构组', memberOrgCodes: ['ORG-1'] }]);
    const wrapper = mount(PanoramaIntegrationReadiness, { props: { screen, canvas: {}, datasources: [], bindingState: {} } });

    await wrapper.find('[data-testid="check-org-profiles"]').trigger('click');
    expect(wrapper.find('[data-testid="check-org-profiles"]').element.disabled).toBe(true);
    await wrapper.find('[data-testid="check-org-group"]').trigger('click');
    await settle();
    expect(wrapper.find('[data-testid="check-org-profiles"]').element.disabled).toBe(false);
    resolveProfiles([{ orgCode: 'ORG-LATE', orgName: '画像迟到结果' }]);
    await settle();
    expect(wrapper.text()).not.toContain('画像迟到结果');
  });
});
