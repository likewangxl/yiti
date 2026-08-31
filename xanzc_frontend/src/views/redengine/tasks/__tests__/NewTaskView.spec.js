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
  getOrgTree: vi.fn()
}));

vi.mock('@/api/users', () => ({
  listUsers: vi.fn()
}));

import {
  createTask,
  getOrgTree
} from '@/api/redengine';
import { listUsers } from '@/api/users';
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
    listUsers.mockResolvedValue({ records: [{ userId: 'U1', displayName: '张伟', partyOrgId: 11 }] });
  });

  it('加载后端组织和员工范围，并使用任务域固定枚举与文件编码选项', async () => {
    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(getOrgTree).toHaveBeenCalledTimes(1);
    expect(listUsers).toHaveBeenCalledTimes(1);
    expect(wrapper.vm.taskTypeOptions.map((item) => item.value)).toEqual(['FOUR_DIMENSION', 'GENERAL']);
    expect(wrapper.vm.fileTypeOptions.map((item) => item.value)).toContain('PDF');
    expect(wrapper.find('.page-title').text()).toBe('新增任务');
    wrapper.unmount();
  });

  it('员工范围超过单页上限时继续读取后续分页', async () => {
    listUsers
      .mockResolvedValueOnce({ records: [{ userId: 'U1', displayName: '张伟', partyOrgId: 11 }], total: 101 })
      .mockResolvedValueOnce({ records: [{ userId: 'U2', displayName: '李娜', partyOrgId: 11 }], total: 101 })
      .mockResolvedValueOnce({ records: [], total: 101 });

    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(listUsers).toHaveBeenNthCalledWith(1, { pageNo: 1, pageSize: 100 });
    expect(listUsers).toHaveBeenNthCalledWith(2, { pageNo: 2, pageSize: 100 });
    expect(listUsers).toHaveBeenNthCalledWith(3, { pageNo: 3, pageSize: 100 });
    expect(wrapper.vm.employeeOptions.map((item) => item.value)).toEqual(['U1', 'U2']);
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
      temporaryStartTime: '2026-08-20 09:00:00',
      temporaryEndTime: '2026-08-31 18:00:00',
      targets: [{ targetType: 'SPECIFIED_BRANCH', partyOrgId: 11 }],
      requiresFile: true,
      fileTypeCodes: ['PDF']
    }));
    expect(routerPush).toHaveBeenCalledWith('/redengine/task-management');
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
});
