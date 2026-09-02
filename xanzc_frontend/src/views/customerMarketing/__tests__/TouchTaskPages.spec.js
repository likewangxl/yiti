// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { computed, nextTick } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listMyTouchTasks: vi.fn(),
  listTouchOverview: vi.fn(),
  exportTouchOverview: vi.fn()
}));

vi.mock('@/api/customerMarketing', () => api);

import MyTouches from '../MyTouches.vue';
import TouchOverview from '../TouchOverview.vue';

const passthrough = (name, template = '<div><slot /></div>') => ({ name, inheritAttrs: false, template });
const TableStub = {
  name: 'ElTable',
  inheritAttrs: false,
  props: { data: { type: Array, default: () => [] } },
  provide() { return { tableRows: computed(() => this.data) }; },
  template: '<div class="table-stub" v-bind="$attrs"><slot /></div>'
};
const TableColumnStub = {
  name: 'ElTableColumn',
  inheritAttrs: false,
  inject: ['tableRows'],
  props: ['prop', 'label'],
  template: '<div class="table-column"><span class="column-label">{{ label }}</span><div v-for="row in tableRows" :key="row.id"><slot :row="row"><span>{{ prop ? row[prop] : "" }}</span></slot></div></div>'
};
const stubs = {
  PageTitle: { name: 'PageTitle', template: '<h1>触达任务管理</h1>' },
  TouchTaskDetailDialog: passthrough('TouchTaskDetailDialog'),
  'el-button': { name: 'ElButton', inheritAttrs: false, template: '<button v-bind="$attrs"><slot /></button>' },
  'el-card': passthrough('ElCard'),
  'el-form': passthrough('ElForm', '<form><slot /></form>'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': passthrough('ElInput', '<input />'),
  'el-select': passthrough('ElSelect'),
  'el-option': passthrough('ElOption'),
  'el-tag': { name: 'ElTag', inheritAttrs: false, template: '<span v-bind="$attrs"><slot /></span>' },
  'el-pagination': passthrough('ElPagination')
};

let wrappers = [];

async function mountPage(component) {
  const wrapper = mount(component, {
    global: { stubs: { ...stubs, 'el-table': TableStub, 'el-table-column': TableColumnStub }, directives: { loading: () => {}, 'bp-overflow-tooltip': () => {} } }
  });
  wrappers.push(wrapper);
  await nextTick();
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  vi.clearAllMocks();
  api.listMyTouchTasks.mockResolvedValue({ records: [], total: 0 });
  api.listTouchOverview.mockResolvedValue({ records: [], total: 0 });
  api.exportTouchOverview.mockResolvedValue(new Blob(['']))
});

afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()));

describe('我的触达任务列表', () => {
  it('兼容 customerName、participantEmpIds 和 logCount，并以摘要层级展示可访问状态', async () => {
    api.listMyTouchTasks.mockResolvedValue({
      records: [{
        id: 'TASK-1', taskNo: 'TOUCH-001', custId: 'CUST-1', customerName: '陕西数字交通科技有限公司',
        taskStatus: 'IN_PROGRESS', slaStatus: 'YELLOW', participantEmpIds: '["E-2","E-3"]', logCount: 2
      }],
      total: 1
    });

    const view = await mountPage(MyTouches);

    expect(view.text()).toContain('陕西数字交通科技有限公司');
    expect(view.text()).toContain('E-2、E-3');
    expect(view.text()).toContain('2');
    expect(view.find('.summary-grid').exists()).toBe(true);
    expect(view.find('.task-table').attributes('aria-label')).toBe('我的触达任务列表');
    expect(view.find('[aria-label="任务状态：办理中"]').exists()).toBe(true);
    expect(view.find('[aria-label="历史日志数量：2"]').exists()).toBe(true);
  });
});

describe('触达任务一览', () => {
  it('兼容 custName、数组参与人和字符串日志数，并保留 Demo 风格摘要层级', async () => {
    api.listTouchOverview.mockResolvedValue({
      records: [{
        id: 'TASK-2', taskNo: 'TOUCH-002', custId: 'CUST-2', custName: '西安航空新材料有限公司',
        orgId: 'ORG-1', assigneeEmpId: 'E-1', taskStatus: 'SUCCESS', slaStatus: 'BLUE',
        participantEmpIds: ['E-4', 'E-5'], logCount: '3'
      }],
      total: 1
    });

    const view = await mountPage(TouchOverview);

    expect(view.text()).toContain('西安航空新材料有限公司');
    expect(view.text()).toContain('E-4、E-5');
    expect(view.text()).toContain('3');
    expect(view.find('.summary-grid').exists()).toBe(true);
    expect(view.find('.task-table').attributes('aria-label')).toBe('触达任务一览列表');
    expect(view.find('[aria-label="任务状态：已完成"]').exists()).toBe(true);
    expect(view.find('[aria-label="历史日志数量：3"]').exists()).toBe(true);
  });
});
