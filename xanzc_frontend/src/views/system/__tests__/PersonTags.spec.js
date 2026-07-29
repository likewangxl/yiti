// @vitest-environment happy-dom
// 业务标签页核心行为回归（员工/机构两维度）：
//  1) 列表加载并展示关联成员数；2) 删除标签的确认文案必须提示级联删除关联成员；
//  3) 详情抽屉按被点标签+当前维度拉成员；4) 切换维度按新维度重新拉取；
//  5) 成员导入（按维度全量覆盖）提交前必须弹覆盖确认，且确认后按被点标签 ID + 维度调导入接口；
//  6) 新增成员支持员工工号 + 机构编号两个输入框，逗号/换行分隔多值，按 { usernames, orgDeptNos } 提交。
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
  genFileId: vi.fn(() => 1)
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
    records: [{ id: 11, dimType: 'EMP', username: '100001', displayName: '张三' }],
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
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-radio-button': passthrough('ElRadioButton'),
  'el-upload': passthrough('ElUpload'),
  'el-pagination': empty('ElPagination'),
  'el-table': { name: 'ElTable', props: ['data'], template: '<div class="tbl-stub"><slot /></div>' },
  'el-table-column': {
    name: 'ElTableColumn',
    props: ['prop', 'label'],
    template: '<div />'
  }
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

  it('删除标签：确认文案必须提示级联删除关联成员，确认后调删除并刷新', async () => {
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

  it('点详情：默认按被点标签 + 员工维度拉成员列表', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();

    expect(listPersonTagMembers).toHaveBeenCalledWith(7, { dim: 'EMP', pageNo: 1, pageSize: 20 });
    expect(wrapper.vm.detail.rows[0]).toMatchObject({
      username: '100001',
      displayName: '张三',
      dimType: 'EMP'
    });
    const columns = wrapper.findAllComponents({ name: 'ElTableColumn' });
    expect(columns.some(column =>
      column.props('prop') === 'displayName' && column.props('label') === '员工姓名'
    )).toBe(true);
  });

  it('切换到机构维度：回到第 1 页并按 ORG 维度重新拉取', async () => {
    listPersonTagMembers.mockResolvedValueOnce({
      records: [{ id: 11, dimType: 'EMP', username: '100001' }], total: 1
    }).mockResolvedValueOnce({
      records: [{ id: 21, dimType: 'ORG', orgDeptNo: '0101', orgName: '城东支行' }], total: 1
    });
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();

    wrapper.vm.detail.dim = 'ORG';
    wrapper.vm.onDetailDimChange();
    await flushPromises();

    expect(listPersonTagMembers).toHaveBeenLastCalledWith(7, { dim: 'ORG', pageNo: 1, pageSize: 20 });
    expect(wrapper.vm.detail.rows[0]).toMatchObject({ orgDeptNo: '0101', orgName: '城东支行' });
  });

  it('成员导入（当前维度全量覆盖）：提交前必须弹覆盖确认，确认后按被点标签 ID + 维度导入并刷新', async () => {
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
    expect(importPersonTagMembers).toHaveBeenCalledWith(7, wrapper.vm.memberImp.file, 'EMP');
  });

  it('机构维度成员导入：按 ORG 维度调导入接口', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.detail.dim = 'ORG';
    wrapper.vm.openMemberImport();
    wrapper.vm.memberImp.file = new File([1], 'orgs.xlsx');

    await wrapper.vm.doMemberImport();
    await flushPromises();

    expect(importPersonTagMembers).toHaveBeenCalledWith(7, wrapper.vm.memberImp.file, 'ORG');
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

  it('成员导入失败：展示行级错误明细、不关闭弹窗，且清空已选文件强制重选', async () => {
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
    // 失败后必须清空文件：limit=1 会挡住再次选择，且改过的旧 File 浏览器会拒发(ERR_UPLOAD_FILE_CHANGED)
    expect(wrapper.vm.memberImp.file).toBeNull();
  });

  it('成员导入请求异常（如文件句柄失效）：清空已选文件强制重选', async () => {
    importPersonTagMembers.mockRejectedValueOnce(new Error('ERR_UPLOAD_FILE_CHANGED'));
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.openMemberImport();
    wrapper.vm.memberImp.file = new File([1], 'members.xlsx');

    await wrapper.vm.doMemberImport();
    await flushPromises();

    expect(wrapper.vm.memberImp.file).toBeNull();
    expect(wrapper.vm.memberImp.visible).toBe(true);
  });

  it('limit=1 已有文件时再选新文件：on-exceed 覆盖式替换而非静默丢弃', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.openMemberImport();
    const oldFile = new File([1], 'old.xlsx');
    wrapper.vm.memberImp.file = oldFile;

    const newFile = new File([2], 'new.xlsx');
    wrapper.vm.onMemberExceed([newFile]);

    // happy-dom 下 File 会被 reactive 代理包装,引用不等,按文件名断言
    expect(wrapper.vm.memberImp.file?.name).toBe('new.xlsx');
    // 全局导入弹窗同样的处理器
    wrapper.vm.onGlobalExceed([newFile]);
    expect(wrapper.vm.globalImp.file?.name).toBe('new.xlsx');
  });

  it('新增成员：员工工号 + 机构编号两个输入框，逗号/换行分隔多值按 { usernames, orgDeptNos } 提交', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.openMemberAdd();
    wrapper.vm.memberAdd.empText = ' 100001 ，100002\n100003 ';
    wrapper.vm.memberAdd.orgText = '0101，0102';

    await wrapper.vm.saveMemberAdd();
    await flushPromises();

    expect(addPersonTagMembers).toHaveBeenCalledWith(7, {
      usernames: ['100001', '100002', '100003'],
      orgDeptNos: ['0101', '0102']
    });
  });

  it('新增成员：员工与机构均为空时提示且不调接口', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
    await flushPromises();
    wrapper.vm.openMemberAdd();
    wrapper.vm.memberAdd.empText = '   ';
    wrapper.vm.memberAdd.orgText = '';

    await wrapper.vm.saveMemberAdd();
    await flushPromises();

    expect(addPersonTagMembers).not.toHaveBeenCalled();
  });
});
