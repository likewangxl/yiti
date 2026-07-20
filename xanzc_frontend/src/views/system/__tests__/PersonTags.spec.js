// @vitest-environment happy-dom
// 人员标签页核心行为回归：
//  1) 列表加载并展示关联人数；2) 删除标签的确认文案必须提示级联删除关联人员；
//  3) 详情抽屉按被点标签拉成员（工号/姓名/机构）；4) 成员导入（全量覆盖）提交前必须弹覆盖确认，
//     且确认后按被点详情的标签 ID 调导入接口；5) 新增员工支持逗号/换行分隔的多工号。
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}));

vi.mock('@/api/system', () => ({
  listPersonTags: vi.fn().mockResolvedValue({
    records: [{ tagId: 1, tagName: '重点培养', remark: '备注A', memberCount: 3, createTime: '2026-07-20T10:00:00' }],
    total: 1
  }),
  createPersonTag: vi.fn().mockResolvedValue({ ok: true }),
  updatePersonTag: vi.fn().mockResolvedValue({ ok: true }),
  deletePersonTag: vi.fn().mockResolvedValue({ ok: true }),
  listPersonTagMembers: vi.fn().mockResolvedValue({
    records: [{ id: 11, username: '100001', displayName: '张三', orgCode: '107', orgName: '城东支行' }],
    total: 1
  }),
  addPersonTagMembers: vi.fn().mockResolvedValue(2),
  updatePersonTagMember: vi.fn().mockResolvedValue({ ok: true }),
  removePersonTagMember: vi.fn().mockResolvedValue({ ok: true }),
  importPersonTags: vi.fn().mockResolvedValue({ success: true, importedCount: 2, createdTagCount: 1, skippedCount: 0 }),
  downloadPersonTagTemplate: vi.fn().mockResolvedValue(new Blob()),
  importPersonTagMembers: vi.fn().mockResolvedValue({ success: true, importedCount: 5 }),
  downloadPersonTagMemberTemplate: vi.fn().mockResolvedValue(new Blob())
}));

import { ElMessageBox } from 'element-plus';
import {
  listPersonTags, deletePersonTag, listPersonTagMembers,
  addPersonTagMembers, importPersonTagMembers
} from '@/api/system';
import PersonTags from '../PersonTags.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="header" /><slot name="footer" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: passthrough('PageTitle'),
  // 必须声明 emits，否则父级 @click 经 attrs 透传到原生 button 后与 $emit 叠加，处理器被调两次
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-card': passthrough('ElCard'),
  'el-dialog': { name: 'ElDialog', props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-drawer': { name: 'ElDrawer', props: ['modelValue'], template: '<div v-if="modelValue" class="drawer-stub"><slot /></div>' },
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': empty('ElInput'),
  'el-alert': empty('ElAlert'),
  'el-upload': passthrough('ElUpload'),
  'el-pagination': empty('ElPagination'),
  'el-table': { name: 'ElTable', props: ['data'], template: '<div class="tbl-stub"><slot /></div>' },
  'el-table-column': empty('ElTableColumn')
};

function mountPage() {
  return mount(PersonTags, { global: { stubs } });
}

// 注意：不能用 vi.restoreAllMocks()——它会把 vi.mock 工厂里的 mockResolvedValue 实现一并清掉
beforeEach(() => {
  vi.clearAllMocks();
  ElMessageBox.confirm.mockResolvedValue('confirm');
});

describe('PersonTags.vue', () => {
  it('挂载时加载标签列表', async () => {
    mountPage();
    await flushPromises();
    expect(listPersonTags).toHaveBeenCalledTimes(1);
    expect(listPersonTags.mock.calls[0][0]).toMatchObject({ pageNo: 1, pageSize: 20 });
  });

  it('删除标签：确认文案必须提示级联删除关联人员，确认后调删除并刷新', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.onDeleteTag({ tagId: 1, tagName: '重点培养', memberCount: 3 });
    await flushPromises();

    const confirmText = ElMessageBox.confirm.mock.calls[0][0];
    expect(confirmText).toContain('重点培养');
    expect(confirmText).toContain('3 条关联');
    expect(deletePersonTag).toHaveBeenCalledWith(1);
    // 删除后刷新列表（挂载 1 次 + 删除后 1 次）
    expect(listPersonTags).toHaveBeenCalledTimes(2);
  });

  it('删除标签：用户取消则不调删除接口', async () => {
    ElMessageBox.confirm.mockRejectedValueOnce('cancel');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.onDeleteTag({ tagId: 1, tagName: '重点培养', memberCount: 3 });
    await flushPromises();

    expect(deletePersonTag).not.toHaveBeenCalled();
  });

  it('点详情：按被点标签拉成员列表', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();

    expect(listPersonTagMembers).toHaveBeenCalledWith(7, { pageNo: 1, pageSize: 20 });
    expect(wrapper.vm.detail.rows[0]).toMatchObject({ username: '100001', displayName: '张三', orgName: '城东支行' });
  });

  it('成员导入（全量覆盖）：提交前必须弹覆盖确认，确认后按被点标签 ID 导入并刷新', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.openMemberImport();
    wrapper.vm.memberImp.file = new File([1], 'members.xlsx');

    await wrapper.vm.doMemberImport();
    await flushPromises();

    const confirmText = ElMessageBox.confirm.mock.calls[0][0];
    expect(confirmText).toContain('全量覆盖');
    expect(confirmText).toContain('骨干');
    expect(importPersonTagMembers).toHaveBeenCalledWith(7, wrapper.vm.memberImp.file);
  });

  it('成员导入：用户取消覆盖确认则不调导入接口', async () => {
    ElMessageBox.confirm.mockRejectedValueOnce('cancel');
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.openMemberImport();
    wrapper.vm.memberImp.file = new File([1], 'members.xlsx');

    await wrapper.vm.doMemberImport();
    await flushPromises();

    expect(importPersonTagMembers).not.toHaveBeenCalled();
  });

  it('成员导入失败：展示行级错误明细且不关闭弹窗', async () => {
    importPersonTagMembers.mockResolvedValueOnce({
      success: false,
      errors: [{ row: 2, username: 'BAD', message: '工号在系统中不存在' }]
    });
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.openMemberImport();
    wrapper.vm.memberImp.file = new File([1], 'members.xlsx');

    await wrapper.vm.doMemberImport();
    await flushPromises();

    expect(wrapper.vm.memberImp.errors).toHaveLength(1);
    expect(wrapper.vm.memberImp.visible).toBe(true);
  });

  it('新增员工：逗号/换行分隔的多工号被拆分提交', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.openMemberAdd();
    wrapper.vm.memberAdd.text = ' 100001 ，100002\n100003 ';

    await wrapper.vm.saveMemberAdd();
    await flushPromises();

    expect(addPersonTagMembers).toHaveBeenCalledWith(7, ['100001', '100002', '100003']);
  });
});
