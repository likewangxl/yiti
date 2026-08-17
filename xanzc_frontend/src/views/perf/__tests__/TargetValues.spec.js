// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { planId: 'P1' } }),
  useRouter: () => ({ push: vi.fn() })
}));

const loadingClose = vi.fn();
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
  ElLoading: { service: vi.fn(() => ({ close: loadingClose })) }
}));

vi.mock('@/api/perf', () => ({
  listTargets: vi.fn().mockResolvedValue([{ id: 'P1', planCode: 'TARGET_2026', planName: '年度目标', targetDim: 'EMP', createdBy: 'E100', ownerOrgId: 'ORG-1' }]),
  listTargetValues: vi.fn().mockResolvedValue([]),
  listTargetValueSubjects: vi.fn().mockResolvedValue([]),
  listTargetValueStageNames: vi.fn().mockResolvedValue([]),
  upsertTargetValue: vi.fn(),
  batchUpsertTargetValues: vi.fn(),
  submitTargetAdjust: vi.fn(),
  listMetrics: vi.fn().mockResolvedValue([]),
  deleteTargetValue: vi.fn(),
  listKpiRules: vi.fn().mockResolvedValue([]),
  getKpiSchemeDetail: vi.fn().mockResolvedValue({ items: [] }),
  searchPerfEmployees: vi.fn().mockResolvedValue([])
}));

vi.mock('@/api/orgs', () => ({ getOrgTree: vi.fn().mockResolvedValue([]) }));
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ user: { empId: 'E100', orgCode: 'ORG-1', roles: [{ roleId: '238' }] } })
}));

import { ElLoading } from 'element-plus';
import { batchUpsertTargetValues, submitTargetAdjust } from '@/api/perf';
import TargetValues from '../TargetValues.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="header" /><slot name="footer" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: {
    name: 'PageTitle',
    inheritAttrs: false,
    template: '<h1 class="page-title" v-bind="$attrs">目标值<slot /></h1>'
  },
  'el-button': {
    name: 'ElButton',
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-tooltip': passthrough('ElTooltip'),
  'el-form': { name: 'ElForm', methods: { validate: () => Promise.resolve(true) }, template: '<form><slot /></form>' },
  'el-form-item': passthrough('ElFormItem'),
  'el-input': { name: 'ElInput', props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-input-number': empty('ElInputNumber'),
  'el-select': passthrough('ElSelect'),
  'el-option': empty('ElOption'),
  'el-autocomplete': { name: 'ElAutocomplete', template: '<div><slot :item="{ value: \'\', label: \'\', display: \'\' }" /></div>' },
  'el-date-picker': empty('ElDatePicker'),
  'el-dropdown': { name: 'ElDropdown', template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': { name: 'ElDropdownItem', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-table': { name: 'ElTable', props: ['data'], template: '<div><slot /></div>' },
  'el-table-column': empty('ElTableColumn'),
  'el-pagination': empty('ElPagination'),
  'el-tag': passthrough('ElTag'),
  'el-alert': empty('ElAlert'),
  'el-dialog': passthrough('ElDialog')
};

let wrapper;

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  vi.clearAllMocks();
});

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function deferred() {
  let resolve;
  const promise = new Promise((res) => { resolve = res; });
  return { promise, resolve };
}

describe('TargetValues.vue 目标值工作区', () => {
  it('以 bp-crud 页面骨架公开筛选、目标值表格和加载语义', async () => {
    wrapper = mount(TargetValues, { global: { stubs, directives: { loading: { mounted() {}, updated() {} }, 'bp-overflow-tooltip': {} } } });
    await settle();

    expect(wrapper.find('main.bp-crud.target-values-page[aria-labelledby="target-values-page-title"]').exists()).toBe(true);
    expect(wrapper.get('h1#target-values-page-title').text()).toContain('目标值');
    expect(wrapper.find('form[aria-label="目标值筛选"]').exists()).toBe(true);
    expect(wrapper.find('section[aria-label="目标值列表"][aria-describedby="target-values-table-state"]').exists()).toBe(true);
    expect(wrapper.get('#target-values-table-state').text()).toContain('暂无目标值数据');
    expect(wrapper.find('input[aria-label="导入目标值文件"]').exists()).toBe(true);
  });

  it('取消文件选择不会启动遮罩或提交批量导入', async () => {
    wrapper = mount(TargetValues, { global: { stubs, directives: { loading: { mounted() {}, updated() {} }, 'bp-overflow-tooltip': {} } } });
    await settle();

    await wrapper.vm.onImportFileSelected({ target: { files: [] } });

    expect(ElLoading.service).not.toHaveBeenCalled();
    expect(batchUpsertTargetValues).not.toHaveBeenCalled();
  });

  it('目标修正在保存中互斥，且保持原有 submitTargetAdjust 请求体', async () => {
    const pending = deferred();
    submitTargetAdjust.mockReturnValueOnce(pending.promise);
    wrapper = mount(TargetValues, { global: { stubs, directives: { loading: { mounted() {}, updated() {} }, 'bp-overflow-tooltip': {} } } });
    await settle();

    wrapper.vm.adjDlg.row = {
      subjectType: 'EMP', subjectId: 'E200', metricCode: 'M_001', targetValue: 100,
      baseValue: 10, cycleKey: '2026Q3', ownerOrgId: 'ORG-1'
    };
    wrapper.vm.adjDlg.form.newValue = 120;
    wrapper.vm.adjDlg.form.newBaseValue = 12;
    wrapper.vm.adjDlg.form.reason = '月度目标修正';
    const first = wrapper.vm.onSubmitAdjust();
    const second = wrapper.vm.onSubmitAdjust();

    await nextTick();
    expect(submitTargetAdjust).toHaveBeenCalledTimes(1);
    expect(submitTargetAdjust).toHaveBeenCalledWith({
      planId: 'P1', subjectType: 'EMP', subjectId: 'E200', cycleKey: '2026Q3', ownerOrgId: 'ORG-1',
      reason: '月度目标修正', metricCode: 'M_001', oldValue: 100, newValue: 120, oldBaseValue: 10, newBaseValue: 12
    });
    pending.resolve({ ok: true });
    await Promise.all([first, second]);
    await settle();
  });
});
