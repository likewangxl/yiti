// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const api = vi.hoisted(() => ({ getHomeSummary: vi.fn() }));
const session = vi.hoisted(() => ({ roleCodes: [] }));
const routerPush = vi.hoisted(() => vi.fn());

vi.mock('@/api/redengine', () => ({ getHomeSummary: api.getHomeSummary }));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush })
}));
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    hasRoleCode: (...codes) => codes.some((code) => session.roleCodes.includes(code)),
    isSystemAdmin: session.roleCodes.includes('SYS_ADMIN')
  })
}));

import DashboardView from '../DashboardView.vue';

const stubs = {
  'el-table': { props: ['data'], template: '<div class="table-stub"><slot /></div>' },
  'el-table-column': { template: '<div />' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

const summary = {
  mode: 'ORGANIZATION',
  todoCount: 2,
  todoItems: [
    { id: 1, taskTitle: '季度材料上报', branchName: '第一党支部', score: 92, rank: 1, dueTime: '本季度末' },
    { id: 2, taskTitle: '专项整改任务', branchName: '第二党支部', score: 86, rank: 2, dueTime: '剩余5天' }
  ],
  branchName: '第一党支部',
  branchScore: 92,
  branchRank: 1,
  organizationTaskCount: 1,
  organizationTasks: [{ taskId: 100, title: '季度材料上报', taskNature: 'SCHEDULED', currentWindowEndAt: '2026-09-30' }],
  branchRankings: [{ branchId: 1, branchName: '第一党支部', score: 92, rank: 1, quarter: '2026-Q3' }],
  totalScore: 92,
  rank: 1,
  passRate: 100
};

describe('红色引擎首页工作台', () => {
  beforeEach(() => {
    session.roleCodes = [];
    vi.clearAllMocks();
    api.getHomeSummary.mockResolvedValue(summary);
  });

  it('组织审核员首页移除总得分和总排名，保留任务概览并展示待办支部排名', async () => {
    session.roleCodes = ['R_RE_ORGREV'];
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    expect(api.getHomeSummary).toHaveBeenCalledWith();
    expect(wrapper.text()).toContain('任务概览');
    expect(wrapper.text()).toContain('第一党支部');
    expect(wrapper.text()).toContain('92');
    expect(wrapper.text()).toContain('排名 1');
    expect(wrapper.find('[data-metric="total-score"]').exists()).toBe(false);
    expect(wrapper.find('[data-metric="overall-rank"]').exists()).toBe(false);
    expect(wrapper.find('[data-metric="org-score"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('报送员和支部书记首页显示所在机构得分排名，不显示全员达标率', async () => {
    session.roleCodes = ['R_RE_SECR'];
    api.getHomeSummary.mockResolvedValue({ ...summary, mode: 'INSTITUTION' });
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    expect(wrapper.find('[data-metric="org-score"]').text()).toContain('所在机构得分');
    expect(wrapper.find('[data-metric="org-rank"]').text()).toContain('所在机构排名');
    expect(wrapper.find('[data-metric="pass-rate"]').exists()).toBe(false);
    expect(wrapper.find('[data-metric="total-score"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('按后端 INSTITUTION mode 和首页 DTO 字段展示所在机构与待办窗口', async () => {
    session.roleCodes = ['R_RE_SECR'];
    api.getHomeSummary.mockResolvedValue({
      mode: 'INSTITUTION',
      branchName: '第三党支部',
      branchScore: 88,
      branchRank: 3,
      todoCount: 1,
      todoItems: [{
        taskId: 7,
        title: '临时走访任务',
        taskType: 'TEMPORARY',
        taskNature: 'TEMPORARY',
        cycle: 'NONE',
        status: 'PENDING',
        windowEndAt: '2026-09-30 18:00:00'
      }]
    });
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    expect(wrapper.find('[data-metric="org-score"]').text()).toContain('88');
    expect(wrapper.find('[data-metric="org-rank"]').text()).toContain('3');
    expect(wrapper.text()).toContain('临时走访任务');
    expect(wrapper.text()).toContain('2026-09-30 18:00:00');
    expect(wrapper.text()).not.toContain('得分 --');
    expect(wrapper.text()).not.toContain('排名 --');
    wrapper.unmount();
  });

  it('首页接口失败时只展示失败空态，不伪造静态生产数据', async () => {
    api.getHomeSummary.mockRejectedValue(new Error('服务不可用'));
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    expect(wrapper.find('.load-error').text()).toContain('工作台数据加载失败');
    expect(wrapper.text()).not.toContain('78.5');
    expect(wrapper.text()).not.toContain('党建联建维度材料上报（截止3月31日）');
    expect(wrapper.find('.todo-empty').exists()).toBe(true);
    wrapper.unmount();
  });

  it('组织首页判断只认后端实际的 SYS_ADMIN 和 R_RE_ORGREV', async () => {
    session.roleCodes = ['R_RE_ORGADM'];
    api.getHomeSummary.mockResolvedValue({ mode: '', organizationTasks: [] });
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    expect(wrapper.find('.organization-task-section').exists()).toBe(false);
    wrapper.unmount();
  });

  it.each([
    ['R_RE_REPORT', '/redengine/records'],
    ['R_RE_SECR', '/redengine/branch-review'],
    ['R_RE_ORGREV', '/redengine/review']
  ])('角色 %s 点击首页待办按角色跳转并保留待处理 assignment 上下文', async (roleCode, path) => {
    session.roleCodes = [roleCode];
    api.getHomeSummary.mockResolvedValue({
      mode: 'INSTITUTION',
      todoItems: [{
        assignmentId: 1001,
        taskId: 42,
        taskTitle: '专项整改任务',
        status: 'UNREPORTED',
        dueTime: '2026-09-30'
      }]
    });
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    const item = wrapper.find('.todo-section .todo-item');
    expect(item.attributes('role')).toBe('button');
    await item.trigger('click');
    expect(routerPush).toHaveBeenCalledWith({
      path,
      query: {
        tab: 'pending',
        taskId: 42,
        assignmentId: 1001,
        status: 'UNREPORTED'
      }
    });
    wrapper.unmount();
  });

  it('首页旧四维材料待办透传 source 和 submitId，进入支部任务处理', async () => {
    session.roleCodes = ['R_RE_SECR'];
    api.getHomeSummary.mockResolvedValue({
      mode: 'INSTITUTION',
      todoItems: [{
        submitId: 17,
        source: 'material',
        title: '四大维度材料上报',
        status: 'PENDING',
        dueTime: '2026-09-03'
      }]
    });
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    await wrapper.find('.todo-section .todo-item').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({
      path: '/redengine/branch-review',
      query: {
        tab: 'pending',
        source: 'material',
        submitId: 17,
        status: 'PENDING'
      }
    });
    wrapper.unmount();
  });

  it('无红色引擎业务角色时首页待办不可点击，不把支部书记导向报送员页面', async () => {
    session.roleCodes = ['R_RE_ORGADM'];
    api.getHomeSummary.mockResolvedValue({
      mode: 'INSTITUTION',
      todoItems: [{ assignmentId: 1001, taskId: 42, taskTitle: '专项整改任务' }]
    });
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    const item = wrapper.find('.todo-section .todo-item');
    expect(item.attributes('role')).toBeUndefined();
    await item.trigger('click');
    expect(routerPush).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('组织审核角色与报送角色并存时优先进入组织审核工作台', async () => {
    session.roleCodes = ['SYS_ADMIN', 'R_RE_REPORT'];
    api.getHomeSummary.mockResolvedValue({
      mode: 'ORGANIZATION',
      todoItems: [{ assignmentId: 1001, taskId: 42, taskTitle: '待审核任务', status: 'ORG_PENDING' }]
    });
    const wrapper = mount(DashboardView, { global: { stubs, directives: { loading: {} } } });
    await settle();

    await wrapper.find('.todo-section .todo-item').trigger('keydown', { key: 'Enter' });
    expect(routerPush).toHaveBeenCalledWith(expect.objectContaining({ path: '/redengine/review' }));
    wrapper.unmount();
  });
});
