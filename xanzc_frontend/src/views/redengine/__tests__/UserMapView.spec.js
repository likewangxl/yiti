// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn() }
}));
vi.mock('@/api/redengine', () => ({
  listUserMaps: vi.fn(),
  bindUserMap: vi.fn(),
  getOrgTree: vi.fn()
}));
vi.mock('@/api/users', () => ({
  listUsers: vi.fn()
}));

import { getOrgTree, listUserMaps } from '@/api/redengine';
import { listUsers } from '@/api/users';
import UserMapView from '../system/UserMapView.vue';

const passthrough = (name) => ({
  name,
  template: '<div><slot /><slot name="header" /><slot name="footer" /></div>'
});

const stubs = {
  'el-card': passthrough('ElCard'),
  'el-table': passthrough('ElTable'),
  'el-table-column': {
    name: 'ElTableColumn',
    props: ['label', 'prop'],
    template: '<div class="column-stub" :data-label="label || \'\'" :data-prop="prop || \'\'"><slot :row="{}" /></div>'
  },
  'el-button': {
    name: 'ElButton',
    emits: ['click'],
    template: '<button @click="$emit(\'click\')"><slot /></button>'
  },
  'el-dialog': {
    name: 'ElDialog',
    props: { modelValue: Boolean },
    template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>'
  },
  'el-form': passthrough('ElForm'),
  'el-form-item': {
    name: 'ElFormItem',
    props: ['label'],
    template: '<div class="form-item-stub" :data-label="label"><slot /></div>'
  },
  'el-input': {
    name: 'ElInput',
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue', 'keyup'],
    template: `<input
      class="input-stub"
      :placeholder="placeholder"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
      @keyup="$emit('keyup', $event)"
    />`
  },
  'el-select': {
    name: 'ElSelect',
    props: {
      modelValue: [String, Number],
      placeholder: String,
      remote: Boolean,
      filterable: Boolean
    },
    emits: ['update:modelValue'],
    template: `<select
      class="select-stub"
      :data-remote="String(remote)"
      :data-filterable="String(filterable)"
      :data-placeholder="placeholder || ''"
      :value="modelValue"
      @change="$emit('update:modelValue', $event.target.value)"
    ><slot /></select>`
  },
  'el-option': {
    name: 'ElOption',
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>'
  },
  'el-empty': passthrough('ElEmpty'),
  'el-pagination': {
    name: 'ElPagination',
    props: ['currentPage', 'pageSize', 'total'],
    emits: ['current-change', 'size-change'],
    template: `<div
      class="pagination-stub"
      :data-current-page="currentPage"
      :data-page-size="pageSize"
      :data-total="total"
    >
      <button class="next-page" @click="$emit('current-change', Number(currentPage) + 1)">下一页</button>
      <button class="change-size" @click="$emit('size-change', 20)">每页20条</button>
    </div>`
  }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('UserMapView 用户来源与展示契约', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listUserMaps.mockResolvedValue({
      records: [
        {
          id: 1,
          userId: 'PT_USER_ID_1001',
          username: 'EMP001',
          displayName: '测试用户',
          partyOrgId: 3,
          partyRole: 'REPORTER'
        }
      ],
      total: 23,
      pageNo: 1,
      pageSize: 10,
      totalPages: 3
    });
    getOrgTree.mockResolvedValue([
      { id: 3, orgName: '第一党支部', children: [] }
    ]);
    listUsers.mockResolvedValue({
      records: [{ userId: 'PT_USER_ID_1001', username: 'EMP001', userchnname: '测试用户' }],
      total: 1
    });
  });

  it('按 PageResult 首次加载并展示用户工号、姓名及总数', async () => {
    const wrapper = mount(UserMapView, {
      global: {
        stubs,
        directives: { loading: { mounted() {}, updated() {} } }
      }
    });
    await settle();

    expect(listUserMaps).toHaveBeenCalledWith({ pageNo: 1, pageSize: 10 });
    const userColumn = wrapper.find('.column-stub[data-label="用户工号"]');
    expect(userColumn.attributes('data-prop')).toBe('username');
    const displayNameColumn = wrapper.find('.column-stub[data-label="姓名"]');
    expect(displayNameColumn.attributes('data-prop')).toBe('displayName');

    const pagination = wrapper.find('.pagination-stub');
    expect(pagination.attributes('data-current-page')).toBe('1');
    expect(pagination.attributes('data-page-size')).toBe('10');
    expect(pagination.attributes('data-total')).toBe('23');

    wrapper.unmount();
  });

  it('新增映射的用户候选同时展示工号和 USERCHNNAME，但提交值仍为 userId', async () => {
    const wrapper = mount(UserMapView, {
      global: {
        stubs,
        directives: { loading: { mounted() {}, updated() {} } }
      }
    });
    await settle();

    const addButton = wrapper.findAll('button').find((button) => button.text().includes('新增映射'));
    await addButton.trigger('click');
    await settle();

    expect(listUsers).toHaveBeenCalled();
    const userSelector = wrapper.find('.form-item-stub[data-label="用户工号"] .select-stub');
    expect(userSelector.exists()).toBe(true);
    expect(userSelector.attributes('data-remote')).toBe('true');
    expect(userSelector.find('option').attributes('value')).toBe('PT_USER_ID_1001');
    expect(userSelector.find('option').text()).toBe('EMP001 · 测试用户');

    const roleSelectors = wrapper.findAll('.form-item-stub[data-label="党内角色"] .select-stub');
    expect(roleSelectors).toHaveLength(2);
    expect(roleSelectors[1].findAll('option').map((option) => option.text())).toEqual([
      '组织审核员',
      '支部书记',
      '报送员'
    ]);
    expect(wrapper.text()).not.toContain('支部审核员');

    wrapper.unmount();
  });

  it('按四项条件查询、重置查询并正确维护页码', async () => {
    const wrapper = mount(UserMapView, {
      global: {
        stubs,
        directives: { loading: { mounted() {}, updated() {} } }
      }
    });
    await settle();

    await wrapper.find('input[placeholder="请输入用户工号"]').setValue(' EMP001 ');
    await wrapper.find('input[placeholder="请输入姓名"]').setValue(' 测试用户 ');
    await wrapper.find('select[data-placeholder="请选择党组织"]').setValue('3');
    await wrapper.find('select[data-placeholder="请选择党内角色"]').setValue('REPORTER');

    const searchButton = wrapper.findAll('button').find((button) => button.text() === '查询');
    await searchButton.trigger('click');
    await settle();

    expect(listUserMaps).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 10,
      username: 'EMP001',
      displayName: '测试用户',
      partyOrgId: 3,
      partyRole: 'REPORTER'
    });

    await wrapper.find('.next-page').trigger('click');
    await settle();
    expect(listUserMaps).toHaveBeenLastCalledWith({
      pageNo: 2,
      pageSize: 10,
      username: 'EMP001',
      displayName: '测试用户',
      partyOrgId: 3,
      partyRole: 'REPORTER'
    });

    await wrapper.find('.change-size').trigger('click');
    await settle();
    expect(listUserMaps).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 20,
      username: 'EMP001',
      displayName: '测试用户',
      partyOrgId: 3,
      partyRole: 'REPORTER'
    });

    const resetButton = wrapper.findAll('button').find((button) => button.text() === '重置');
    await resetButton.trigger('click');
    await settle();
    expect(listUserMaps).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 20 });

    wrapper.unmount();
  });
});
