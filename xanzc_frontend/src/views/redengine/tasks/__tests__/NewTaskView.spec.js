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
  listMaterialDetailItems: vi.fn(),
  listTaskFileTypes: vi.fn(),
  listTaskTypes: vi.fn()
}));

vi.mock('@/api/users', () => ({
  listUsers: vi.fn()
}));

import {
  createTask,
  getOrgTree,
  listMaterialDetailItems,
  listTaskFileTypes,
  listTaskTypes
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
    listTaskTypes.mockResolvedValue([
      { value: 'NOTICE', label: '通知确认' },
      { value: 'FOUR_DIMENSION', label: '四大维度材料上报' }
    ]);
    listTaskFileTypes.mockResolvedValue([
      { value: 'PDF', label: 'PDF' },
      { value: 'DOCX', label: 'Word' }
    ]);
    listMaterialDetailItems.mockResolvedValue([{ value: 'JC_STANDARD', label: '联建规范度' }]);
  });

  it('加载任务类型、文件类型和目标范围数据', async () => {
    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(listTaskTypes).toHaveBeenCalledTimes(1);
    expect(listTaskFileTypes).toHaveBeenCalledTimes(1);
    expect(getOrgTree).toHaveBeenCalledTimes(1);
    expect(listUsers).toHaveBeenCalledTimes(1);
    expect(wrapper.find('.page-title').text()).toBe('新增任务');
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
