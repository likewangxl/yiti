// @vitest-environment happy-dom
// TDD Red→Green：ReOrgController.deleteOrg（DELETE /api/re/orgs/{id}）新增
// ReOrgDeleteReqDTO.reason（@NotBlank）配合 @AuditLog(reasonRequired=true) 强制审计留痕后，
// 前端配套修复：OrgManageView.vue 删除交互由纯 ElMessageBox 二次确认改为「删除原因」弹窗必填，
// 未填原因点确认不应发起 deleteOrg 请求；填写后需将 reason 一并携带（deleteOrg(id, reason)）。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick, ref } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}));

vi.mock('@/api/redengine', () => ({
  getOrgTree: vi.fn(),
  addOrg: vi.fn(),
  updateOrg: vi.fn(),
  deleteOrg: vi.fn()
}));

vi.mock('@/composables/useDict', () => ({
  useDict: () => ({ options: ref([]), labelOf: (v) => v, loading: ref(false), reload: vi.fn() })
}));

import { getOrgTree, deleteOrg } from '@/api/redengine';
import { ElMessage } from 'element-plus';
import OrgManageView from '../system/OrgManageView.vue';

// 手写可交互桩：沿用 ReviewApproveScoreReset.spec.js 既有惯例。
// el-dialog 桩把 title 透出到 data-dialog-title，用于在"新增/编辑"与"删除原因"两个弹窗并存时
// 精确定位目标弹窗内的按钮/输入框，避免元素选择器歧义。
const stubs = {
  'el-row': { template: '<div><slot /></div>' },
  'el-col': { template: '<div><slot /></div>' },
  'el-card': { template: '<div><slot name="header" /><slot /></div>' },
  'el-empty': { template: '<div class="empty-stub" />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-icon': { template: '<span><slot /></span>' },
  'el-tree': {
    name: 'ElTree',
    props: ['data'],
    template:
      '<div class="tree-stub"><div v-for="item in data" :key="item.id" class="tree-node-wrap">' +
      '<slot :node="{ label: item.orgName }" :data="item" /></div></div>'
  },
  'el-dialog': {
    props: ['title'],
    template: '<div :data-dialog-title="title"><slot /><slot name="footer" /></div>'
  },
  'el-form': {
    props: ['model', 'rules'],
    template: '<form><slot /></form>',
    methods: {
      // 极简校验桩：按 rules 里标记 required 的字段核对 model 是否为空，模拟 el-form 真实 validate() 的核心行为
      validate() {
        for (const field of Object.keys(this.rules || {})) {
          const required = (this.rules[field] || []).some((r) => r.required);
          const val = this.model ? this.model[field] : undefined;
          if (required && (val === undefined || val === null || String(val).trim() === '')) {
            return Promise.reject(new Error(field + ' required'));
          }
        }
        return Promise.resolve(true);
      }
    }
  },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': {
    props: ['modelValue'],
    template:
      '<textarea class="reason-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>'
  },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
};

let wrapper;
beforeEach(() => { vi.clearAllMocks(); });
afterEach(() => { wrapper?.unmount(); });

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('党组织管理(OrgManageView) - 删除原因必填', () => {
  const treeNode = { id: 1, orgName: '分行党委', orgLevel: 1, orgCode: 'ORG1', orgType: '经营单位', children: [] };

  it('点击删除图标后未填原因直接确认，不应发起 deleteOrg 请求', async () => {
    getOrgTree.mockResolvedValue([treeNode]);
    wrapper = mount(OrgManageView, { global: { stubs } });
    await settle();

    // 树节点操作图标顺序：新增子级(Plus)/编辑(Edit)/删除(Delete)
    const deleteIcon = wrapper.findAll('.action-icon')[2];
    await deleteIcon.trigger('click');
    await settle();

    const dialog = wrapper.find('[data-dialog-title="删除党组织"]');
    expect(dialog.exists()).toBe(true);
    const confirmBtn = dialog.findAll('button').find((b) => b.text().includes('确认删除'));
    await confirmBtn.trigger('click');
    await settle();

    expect(deleteOrg).not.toHaveBeenCalled();
  });

  it('填写删除原因后确认，应携带 reason 调用 deleteOrg', async () => {
    getOrgTree.mockResolvedValue([treeNode]);
    deleteOrg.mockResolvedValue({});
    wrapper = mount(OrgManageView, { global: { stubs } });
    await settle();

    const deleteIcon = wrapper.findAll('.action-icon')[2];
    await deleteIcon.trigger('click');
    await settle();

    const dialog = wrapper.find('[data-dialog-title="删除党组织"]');
    await dialog.find('.reason-stub').setValue('组织已撤销，清理历史数据');
    await settle();

    const confirmBtn = dialog.findAll('button').find((b) => b.text().includes('确认删除'));
    await confirmBtn.trigger('click');
    await settle();

    expect(deleteOrg).toHaveBeenCalledWith(1, '组织已撤销，清理历史数据');
    expect(ElMessage.success).toHaveBeenCalled();
  });
});
