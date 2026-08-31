// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const api = vi.hoisted(() => ({
  executeTaskOverdue: vi.fn(),
  getTaskOverdueList: vi.fn(),
  getWarningPool: vi.fn()
}));
const session = vi.hoisted(() => ({ roleCodes: [] }));
const ui = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }));

vi.mock('@/api/redengine', () => ({
  executeTaskOverdue: api.executeTaskOverdue,
  getTaskOverdueList: api.getTaskOverdueList,
  getWarningPool: api.getWarningPool
}));
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    isSystemAdmin: session.roleCodes.includes('SYS_ADMIN')
  })
}));
vi.mock('element-plus', () => ({
  ElMessage: ui
}));

import WarningView from '../WarningView.vue';

const stubs = {
  'el-input': {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-input-number': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input class="deduction-input" type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />'
  },
  'el-select': { props: ['modelValue'], template: '<select :value="modelValue"><slot /></select>' },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-table': { props: ['data'], template: '<div class="table-stub"><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { props: ['total'], template: '<div class="pagination-stub" :data-total="total" />' },
  'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>' },
  'el-button': { props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('红黄牌预警池', () => {
  beforeEach(() => {
    session.roleCodes = [];
    vi.clearAllMocks();
    api.getWarningPool.mockResolvedValue({
      quarter: '2026-Q3',
      previousQuarter: '2026-Q2',
      redBranches: [{ branchId: 1, branchName: '红牌支部', score: 55, previousScore: 58, rank: 9, previousRank: 9 }],
      yellowBranches: [{ branchId: 2, branchName: '黄牌支部', score: 70, previousScore: 72, rank: 5, previousRank: 5 }]
    });
    api.getTaskOverdueList.mockResolvedValue({ records: [], total: 0 });
    api.executeTaskOverdue.mockResolvedValue({});
  });

  it('所有角色展示红牌和黄牌，且红牌板块优先', async () => {
    const wrapper = mount(WarningView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    const sectionTitles = wrapper.findAll('.section-title').map((node) => node.text());
    expect(sectionTitles[0]).toContain('红牌支部');
    expect(sectionTitles[1]).toContain('黄牌支部');
    expect(wrapper.text()).toContain('红牌支部');
    expect(wrapper.text()).toContain('黄牌支部');
    expect(wrapper.text()).toContain('2026-Q3');
    expect(api.getTaskOverdueList).not.toHaveBeenCalled();
    expect(wrapper.find('.overdue-section').exists()).toBe(false);
    wrapper.unmount();
  });

  it('仅 SYS_ADMIN 显示逾期分页查询与扣分区，未上报人和时间显示 --', async () => {
    session.roleCodes = ['SYS_ADMIN'];
    api.getTaskOverdueList.mockResolvedValue({
      records: [{
        assignmentId: 9,
        taskType: 'TEMPORARY',
        taskName: '专项整改',
        taskContent: '请补充说明',
        branchName: '第三党支部',
        taskStartAt: '2026-08-01 09:00:00',
        taskEndAt: '2026-08-10 18:00:00',
        isUnreported: true,
        submitterName: null,
        submittedAt: null
      }],
      total: 1
    });
    const wrapper = mount(WarningView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    expect(wrapper.vm.isSystemAdmin).toBe(true);
    expect(wrapper.vm.loadError).toBe('');
    expect(wrapper.find('.overdue-section').exists()).toBe(true);
    expect(api.getTaskOverdueList).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20 });
    expect(wrapper.vm.overdueItems[0]).toMatchObject({
      taskType: 'TEMPORARY',
      taskName: '专项整改',
      branchName: '第三党支部',
      submitterName: '--',
      submittedAt: '--'
    });

    await wrapper.vm.handlePageChange(2);
    expect(api.getTaskOverdueList).toHaveBeenLastCalledWith({ pageNo: 2, pageSize: 20 });

    wrapper.vm.query.keyword = '整改';
    await wrapper.vm.handleSearch();
    expect(api.getTaskOverdueList).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 20, keyword: '整改' });

    wrapper.vm.openExecDialog(wrapper.vm.overdueItems[0]);
    await wrapper.vm.confirmExecute();
    expect(api.executeTaskOverdue).not.toHaveBeenCalled();
    expect(ui.warning).toHaveBeenCalledWith('请填写扣分原因');

    wrapper.vm.execDialog.form.reason = '已核实逾期未报送';
    await wrapper.vm.confirmExecute();
    expect(api.executeTaskOverdue).toHaveBeenCalledWith({
      assignmentId: 9,
      deductionPoints: 5,
      reason: '已核实逾期未报送'
    });
    wrapper.unmount();
  });

  it('预警查询失败时清空数据并显示失败态，不伪造红黄牌', async () => {
    api.getWarningPool.mockRejectedValue(new Error('服务不可用'));
    const wrapper = mount(WarningView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    expect(wrapper.find('.load-error').text()).toContain('预警数据加载失败');
    expect(wrapper.findAll('.warning-card')).toHaveLength(0);
    wrapper.unmount();
  });
});
