// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

const routerPush = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush })
}));

vi.mock('@/api/redengine', () => ({
  createTask: vi.fn(),
  getOrgTree: vi.fn(),
  listEligibleUsers: vi.fn()
}));

import {
  createTask,
  getOrgTree,
  listEligibleUsers
} from '@/api/redengine';
import NewTaskView from '../NewTaskView.vue';

const stubs = {
  'el-card': { template: '<section><slot name="header" /><slot /></section>' },
  'el-form': {
    template: '<form><slot /></form>',
    setup(_, { expose }) {
      expose({ validate: () => Promise.resolve(true), clearValidate: () => {} });
      return {};
    }
  },
  'el-form-item': { props: ['label'], template: '<label><span>{{ label }}</span><slot /></label>' },
  'el-input': {
    props: ['modelValue', 'placeholder', 'type'],
    emits: ['update:modelValue'],
    template: '<textarea v-if="type === \'textarea\'" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': {
    props: ['modelValue', 'placeholder', 'multiple'],
    emits: ['update:modelValue'],
    template: '<select :multiple="multiple" :data-placeholder="placeholder" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>'
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-date-picker': {
    props: ['modelValue', 'placeholder', 'type'],
    emits: ['update:modelValue'],
    template: '<input class="date-picker-stub" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-input-number': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input class="number-input-stub" type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />'
  },
  'el-radio-group': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-radio-button': { props: ['label'], template: '<button type="button"><slot /></button>' },
  'el-checkbox-group': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-checkbox': { props: ['label'], template: '<label><input type="checkbox" :value="label" /><slot /></label>' },
  'el-switch': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' },
  'el-button': { props: ['loading'], emits: ['click'], template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' },
  'el-tree': { template: '<div><slot /></div>' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('新增任务', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    createTask.mockResolvedValue({ taskId: 100 });
    getOrgTree.mockResolvedValue([{ id: 11, orgName: '第一党支部', children: [] }]);
    listEligibleUsers.mockResolvedValue({ records: [{ employeeId: 'U1', username: 'zhangw', displayName: '张伟', branchId: 11, branchName: '第一党支部' }] });
  });

  it('加载后端组织和员工范围，并使用任务域固定枚举与文件编码选项', async () => {
    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(getOrgTree).toHaveBeenCalledTimes(1);
    expect(listEligibleUsers).not.toHaveBeenCalled();
    expect(wrapper.vm.taskTypeOptions.map((item) => item.value)).toEqual(['FOUR_DIMENSION', 'GENERAL']);
    expect(wrapper.vm.fileTypeOptions.map((item) => item.value)).toContain('PDF');
    expect(wrapper.find('.page-title').text()).toBe('新增任务');
    wrapper.unmount();
  });

  it('员工范围超过单页上限时继续读取后续分页', async () => {
    listEligibleUsers
      .mockResolvedValueOnce({ records: [{ employeeId: 'U1', username: 'zhangw', displayName: '张伟', branchId: 11, branchName: '第一党支部' }], total: 101 })
      .mockResolvedValueOnce({ records: [{ employeeId: 'U2', username: 'lin', displayName: '李娜', branchId: 11, branchName: '第一党支部' }], total: 101 })
      .mockResolvedValueOnce({ records: [], total: 101 });

    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    wrapper.vm.formData.audienceType = 'SPECIFIED_EMPLOYEE';
    await settle();

    expect(listEligibleUsers).toHaveBeenNthCalledWith(1, { pageNo: 1, pageSize: 100 });
    expect(listEligibleUsers).toHaveBeenNthCalledWith(2, { pageNo: 2, pageSize: 100 });
    expect(listEligibleUsers).toHaveBeenNthCalledWith(3, { pageNo: 3, pageSize: 100 });
    expect(wrapper.vm.employeeOptions.map((item) => item.value)).toEqual(['U1', 'U2']);
    expect(wrapper.vm.employeeOptions.map((item) => item.label)).toEqual([
      'zhangw · 张伟 · 第一党支部',
      'lin · 李娜 · 第一党支部'
    ]);
    wrapper.unmount();
  });

  it('发布临时任务时提交三种对象字段及文件限制', async () => {
    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    Object.assign(wrapper.vm.formData, {
      nature: 'TEMPORARY',
      businessType: 'NOTICE',
      title: '临时通知',
      description: '请完成确认',
      audienceType: 'SPECIFIED_BRANCH',
      targetBranchIds: [11],
      targetEmployeeIds: [],
      startAt: '2026-08-20 09:00:00',
      endAt: '2026-08-31 18:00:00',
      requiresFile: true,
      allowedFileTypes: ['PDF']
    });

    await wrapper.vm.handleSubmit();
    await settle();

    expect(createTask).toHaveBeenCalledWith(expect.objectContaining({
      taskNature: 'TEMPORARY',
      businessType: 'GENERAL',
      cycleType: null,
      durationDays: null,
      temporaryStartTime: '2026-08-20T09:00:00',
      temporaryEndTime: '2026-08-31T18:00:00',
      targets: [{ targetType: 'SPECIFIED_BRANCH', partyOrgId: 11 }],
      requiresFile: true,
      fileTypeCodes: ['PDF']
    }));
    expect(routerPush).toHaveBeenCalledWith('/redengine/task-management');
    wrapper.unmount();
  });

  it('日期控件继续显示空格分隔格式，但提交 payload 使用无时区 ISO 秒精度', async () => {
    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    Object.assign(wrapper.vm.formData, {
      nature: 'TEMPORARY',
      businessType: 'GENERAL',
      title: '临时任务',
      description: '任务说明',
      audienceType: 'ALL_BRANCH',
      startAt: '2026-09-01 12:00:00',
      endAt: '2026-09-02 18:30:00'
    });
    await nextTick();

    const datePickers = wrapper.findAll('.date-picker-stub');
    expect(datePickers[0].element.value).toBe('2026-09-01 12:00:00');
    expect(datePickers[1].element.value).toBe('2026-09-02 18:30:00');
    expect(wrapper.vm.buildPayload()).toMatchObject({
      temporaryStartTime: '2026-09-01T12:00:00',
      temporaryEndTime: '2026-09-02T18:30:00'
    });
    wrapper.unmount();
  });

  it('定时任务显示由周期和持续天数计算的窗口', async () => {
    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    Object.assign(wrapper.vm.formData, {
      nature: 'PERIODIC',
      businessType: 'FOUR_DIMENSION',
      title: '季度材料',
      description: '请按要求上报',
      audienceType: 'ALL_BRANCH',
      cycle: 'QUARTER_END',
      durationDays: 5,
      requiresFile: true,
      allowedFileTypes: ['PDF']
    });
    await nextTick();

    expect(wrapper.vm.windowPreview).toMatchObject({ start: expect.any(String), end: expect.any(String) });
    expect(wrapper.find('[data-test="window-preview"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('组织或员工选项加载失败时展示错误态', async () => {
    getOrgTree.mockRejectedValueOnce(new Error('服务不可用'));
    listEligibleUsers.mockRejectedValueOnce(new Error('服务不可用'));
    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    wrapper.vm.formData.audienceType = 'SPECIFIED_EMPLOYEE';
    await settle();

    expect(wrapper.vm.optionsError).toBe('任务对象选项加载失败，请稍后重试');
    expect(wrapper.find('[role="alert"]').text()).toContain('任务对象选项加载失败');
    wrapper.unmount();
  });
});
