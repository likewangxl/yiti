// @vitest-environment happy-dom
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listOrgProfiles: vi.fn(),
  updateOrgProfile: vi.fn()
}));

vi.mock('@/api/screen', () => api);
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn() }
}));

import OrgProfiles from '../OrgProfiles.vue';

const passthrough = { template: '<div><slot /></div>' };
const stubs = {
  PageTitle: passthrough,
  'el-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' },
  'el-form': passthrough,
  'el-form-item': passthrough,
  'el-input': { props: ['modelValue'], template: '<input :value="modelValue" />' },
  'el-select': passthrough,
  'el-option': passthrough,
  'el-table': passthrough,
  'el-table-column': { template: '<div />' },
  'el-tag': passthrough,
  'el-dialog': passthrough,
  'el-input-number': passthrough,
  'el-radio-group': passthrough,
  'el-radio-button': passthrough,
  OrgLocationDialog: {
    props: ['modelValue', 'org'],
    emits: ['update:modelValue', 'saved'],
    template: '<div data-testid="org-location-dialog-stub" />'
  }
};

let wrapper;

async function settle() {
  await flushPromises();
  await flushPromises();
}

beforeEach(() => {
  vi.clearAllMocks();
  api.listOrgProfiles.mockResolvedValue([]);
});

afterEach(() => wrapper?.unmount());

describe('OrgProfiles.vue 查询契约', () => {
  it('城市筛选独立传 city；空白城市不会混入只匹配机构编码/名称的 keyword', async () => {
    wrapper = mount(OrgProfiles, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await settle();
    api.listOrgProfiles.mockClear();

    wrapper.vm.filters.keyword = '  ORG_XIAN  ';
    wrapper.vm.filters.city = '   ';
    await wrapper.vm.reload();

    expect(api.listOrgProfiles).toHaveBeenCalledWith({
      keyword: 'ORG_XIAN',
      orgNature: undefined,
      operatingLevel: undefined,
      city: undefined
    });
  });

  it('接入 CRUD 基线，并提供不改变查询契约的筛选重置入口', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/OrgProfiles.vue'), 'utf8');

    expect(source).toMatch(/<main\b[^>]*class="[^\"]*\bbp-crud\b[^\"]*"/);
    expect(source).toMatch(/@click="resetFilters"[^>]*>重置/);
    expect(source).toMatch(/function resetFilters\(\)/);
  });

  it('状态单选按钮改用 value 并保持 ACTIVE/DISABLED 模型值', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/OrgProfiles.vue'), 'utf8');

    expect(source).toContain('<el-radio-button value="ACTIVE">启用</el-radio-button>');
    expect(source).toContain('<el-radio-button value="DISABLED">停用</el-radio-button>');
    expect(source).not.toMatch(/<el-radio-button\b[^>]*\blabel=/);
  });

  it('显示当前查询结果的画像、城市和定位缺口统计，未配置行不能显示启用', async () => {
    api.listOrgProfiles.mockResolvedValue([
      { orgCode: 'U1', orgName: '未配置机构' },
      { orgCode: 'A1', orgName: '已启用机构', status: 'ACTIVE', version: 1, cityCode: '610100', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' }
    ]);
    wrapper = mount(OrgProfiles, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await settle();

    expect(wrapper.vm.readiness).toMatchObject({
      total: 2, profileConfigured: 1, missingProfile: 1, enabled: 1,
      located: 1, missingCoordinates: 1, missingCity: 1, missingAddress: 0
    });
    expect(wrapper.vm.profileStatus(wrapper.vm.rows[0])).toBe('UNCONFIGURED');
    expect(wrapper.vm.active(wrapper.vm.rows[0])).toBe(false);
    expect(wrapper.find('.table-state').text()).toContain('共 2 个机构');
    expect(wrapper.html()).toContain('已配置画像');
    expect(wrapper.html()).toContain('未配置画像');
    expect(wrapper.html()).toContain('城市缺失');
    expect(wrapper.html()).toContain('有效坐标');
    expect(wrapper.html()).toContain('待定位');
  });

  it('原始机构读取失败显示错误态而不是暂无机构画像', async () => {
    api.listOrgProfiles.mockRejectedValue(new Error('network down'));
    wrapper = mount(OrgProfiles, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await settle();

    expect(wrapper.vm.loadError).toBe('机构经营画像列表加载失败，请刷新重试');
    expect(wrapper.find('[role="alert"]').text()).toContain('机构经营画像列表加载失败');
    expect(wrapper.find('.table-state').text()).toContain('机构经营画像列表加载失败');
    expect(wrapper.find('.table-state').text()).not.toContain('暂无机构画像');
  });

  it('本地缺口筛选只影响当前展示，不改变后端 keyword/city 查询语义', async () => {
    const rows = [
      { orgCode: 'LOCATED', orgName: '已定位', status: 'ACTIVE', version: 1, cityCode: '610100', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'MISSING', orgName: '待定位', status: 'ACTIVE', version: 1, cityCode: '610100' }
    ];
    api.listOrgProfiles.mockResolvedValue(rows);
    wrapper = mount(OrgProfiles, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await settle();
    api.listOrgProfiles.mockClear();

    wrapper.vm.filters.keyword = 'missing';
    wrapper.vm.filters.city = '610100';
    wrapper.vm.filters.gap = 'MISSING_COORDINATES';
    await wrapper.vm.reload();
    await settle();

    expect(api.listOrgProfiles).toHaveBeenCalledWith({
      keyword: 'missing', orgNature: undefined, operatingLevel: undefined, city: '610100'
    });
    expect(wrapper.vm.rows.map(row => row.orgCode)).toEqual(['MISSING']);
  });

  it('缺口筛选不清空编辑下属机构所需的有效一级机构候选', async () => {
    api.listOrgProfiles.mockResolvedValue([
      { orgCode: 'PRIMARY', orgName: '一级机构', status: 'ACTIVE', version: 1, operatingLevel: 'PRIMARY', cityCode: '610100', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'SUB', orgName: '下属机构', status: 'ACTIVE', version: 1, operatingLevel: 'SUBORDINATE' }
    ]);
    wrapper = mount(OrgProfiles, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await settle();

    wrapper.vm.filters.gap = 'MISSING_COORDINATES';
    await wrapper.vm.$nextTick();

    expect(wrapper.vm.rows.map(row => row.orgCode)).toEqual(['SUB']);
    expect(wrapper.vm.primaryOptions.map(row => row.orgCode)).toEqual(['PRIMARY']);
  });

  it('机构画像列表不提供地址缺失筛选，地址资料仅在地址与定位详情中读取', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/OrgProfiles.vue'), 'utf8');

    expect(source).not.toContain('MISSING_ADDRESS');
    expect(source).not.toContain('地址缺失');
  });

  it('每行提供地址与定位入口，打开时只交给独立位置对话框', async () => {
    const row = { orgCode: 'ORG_1', orgName: '西安机构', status: 'ACTIVE', version: 1, cityCode: '610100' };
    api.listOrgProfiles.mockResolvedValue([row]);
    wrapper = mount(OrgProfiles, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await settle();

    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/OrgProfiles.vue'), 'utf8');
    expect(source).toContain('地址与定位');
    expect(source).toContain('<OrgLocationDialog');
    wrapper.vm.openLocation(row);
    await wrapper.vm.$nextTick();
    expect(wrapper.vm.locationDialog.show).toBe(true);
    expect(wrapper.vm.locationDialog.org.orgCode).toBe('ORG_1');
  });

  it('迟到响应不能覆盖最新查询，卸载后响应也不能更新状态', async () => {
    let resolveFirst;
    let resolveSecond;
    const first = new Promise(resolve => { resolveFirst = resolve; });
    const second = new Promise(resolve => { resolveSecond = resolve; });
    api.listOrgProfiles.mockReset();
    api.listOrgProfiles.mockReturnValueOnce(first).mockReturnValueOnce(second);
    wrapper = mount(OrgProfiles, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    const latest = wrapper.vm.reload();

    resolveSecond([{ orgCode: 'NEW', orgName: '新查询', status: 'ACTIVE', version: 1 }]);
    await latest;
    await settle();
    expect(wrapper.vm.rows.map(row => row.orgCode)).toEqual(['NEW']);

    resolveFirst([{ orgCode: 'OLD', orgName: '旧查询', status: 'ACTIVE', version: 1 }]);
    await settle();
    expect(wrapper.vm.rows.map(row => row.orgCode)).toEqual(['NEW']);

    let resolveUnmounted;
    api.listOrgProfiles.mockReturnValueOnce(new Promise(resolve => { resolveUnmounted = resolve; }));
    const pending = wrapper.vm.reload();
    wrapper.unmount();
    resolveUnmounted([{ orgCode: 'AFTER_UNMOUNT', orgName: '卸载后', status: 'ACTIVE', version: 1 }]);
    await pending;
    await settle();
    expect(wrapper.vm.rows.map(row => row.orgCode)).toEqual(['NEW']);
  });
});
