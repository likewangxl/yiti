// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() } }));
vi.mock('@/api/nav', () => ({
  listNav: vi.fn(),
  createNav: vi.fn(),
  updateNav: vi.fn(),
  deleteNav: vi.fn(),
  sortNav: vi.fn()
}));

import { listNav } from '@/api/nav';
import NavHub from '../NavHub.vue';

const passthrough = (name, template = '<div><slot /></div>') => ({ name, template });
const stubs = {
  PageTitle: { name: 'PageTitle', inheritAttrs: false, template: '<h1 v-bind="$attrs">网址导航<slot /></h1>' },
  'el-button': { name: 'ElButton', inheritAttrs: false, template: '<button v-bind="$attrs"><slot /></button>' },
  'el-dialog': passthrough('ElDialog'),
  'el-form': passthrough('ElForm', '<form><slot /></form>'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': passthrough('ElInput'),
  'el-select': passthrough('ElSelect'),
  'el-option': passthrough('ElOption'),
  'el-tag': passthrough('ElTag'),
  'el-empty': passthrough('ElEmpty'),
  'el-popconfirm': passthrough('ElPopconfirm')
};

function mountPage() {
  return mount(NavHub, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  listNav.mockResolvedValue({
    groups: [
      {
        category: '零售 与 对公 / 2026',
        navs: [{ id: 1, navName: '客户管理', navUrl: 'https://example.test/customer', status: 'ACTIVE' }]
      },
      {
        category: '特别 # 分组',
        navs: [{ id: 2, navName: '风险提示', navUrl: 'https://example.test/risk', status: 'ACTIVE' }]
      }
    ]
  });
});

afterEach(() => wrapper?.unmount());

describe('NavHub.vue 网址分组可访问名称', () => {
  it('为含空格和特殊字符的分组生成稳定、无空格且唯一的标题 ID', async () => {
    wrapper = mountPage();
    await flushPromises();
    await flushPromises();

    const groups = wrapper.findAll('.nav-group');
    const labelledByIds = groups.map(group => group.attributes('aria-labelledby'));

    expect(groups).toHaveLength(2);
    expect(new Set(labelledByIds).size).toBe(2);
    for (const [index, group] of groups.entries()) {
      const labelledBy = labelledByIds[index];
      expect(labelledBy).toMatch(/^nav-group-\d+$/);
      expect(group.get('h3').attributes('id')).toBe(labelledBy);
      expect(wrapper.find(`#${labelledBy}`).exists()).toBe(true);
    }
  });
});
