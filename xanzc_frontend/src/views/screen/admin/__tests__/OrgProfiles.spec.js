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
  'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
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
  'el-radio-button': passthrough
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
});
