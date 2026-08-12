// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const api = vi.hoisted(() => ({
  getWorkspace: vi.fn(),
  listNotifications: vi.fn(),
  getUnreadNotificationCount: vi.fn(),
  markRead: vi.fn(),
  listTodoTasks: vi.fn(),
  listDoneTasks: vi.fn(),
  transferInbox: vi.fn(),
  transferAccept: vi.fn(),
  transferDecline: vi.fn(),
  transferOutbox: vi.fn(),
  transferCancel: vi.fn(),
  listRecentAnnouncements: vi.fn()
}));
const ui = vi.hoisted(() => ({
  success: vi.fn(),
  info: vi.fn(),
  confirm: vi.fn(),
  prompt: vi.fn()
}));
const routerPush = vi.hoisted(() => vi.fn());

vi.mock('element-plus', () => ({
  ElMessage: { success: ui.success, info: ui.info },
  ElMessageBox: { confirm: ui.confirm, prompt: ui.prompt }
}));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ displayName: '测试用户', roleSummary: '客户经理', orgName: '营业部' })
}));
vi.mock('@/mock', () => ({ workspace: {} }));
vi.mock('@/utils/datetime', () => ({ fmtDateTime: (value) => String(value || '-') }));
vi.mock('@/api/workspace', () => ({
  getWorkspace: api.getWorkspace,
  listNotifications: api.listNotifications,
  getUnreadNotificationCount: api.getUnreadNotificationCount,
  markRead: api.markRead
}));
vi.mock('@/api/workflow', () => ({
  listTodoTasks: api.listTodoTasks,
  listDoneTasks: api.listDoneTasks,
  transferInbox: api.transferInbox,
  transferAccept: api.transferAccept,
  transferDecline: api.transferDecline,
  transferOutbox: api.transferOutbox,
  transferCancel: api.transferCancel
}));
vi.mock('@/api/announcement', () => ({ listRecentAnnouncements: api.listRecentAnnouncements }));

import Workspace from '../Index.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="empty" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: { name: 'PageTitle', props: ['title'], template: '<h1 class="page-title">{{ title }}<slot /></h1>' },
  'el-button': {
    name: 'ElButton',
    inheritAttrs: false,
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button type="button" v-bind="$attrs" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-radio-button': passthrough('ElRadioButton'),
  'el-table': passthrough('ElTable'),
  'el-table-column': empty('ElTableColumn'),
  'el-tag': passthrough('ElTag')
};

let wrapper;

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function mountPage() {
  return mount(Workspace, {
    global: {
      stubs,
      directives: { loading: {} }
    }
  });
}

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function resetMocks() {
  Object.values(api).forEach((mock) => mock.mockReset());
  Object.values(ui).forEach((mock) => mock.mockReset());
  routerPush.mockReset();

  api.getWorkspace.mockResolvedValue({});
  api.listNotifications.mockResolvedValue({ records: [] });
  api.getUnreadNotificationCount.mockResolvedValue(0);
  api.markRead.mockResolvedValue({ ok: true });
  api.listTodoTasks.mockResolvedValue({ records: [], total: 0 });
  api.listDoneTasks.mockResolvedValue({ records: [], total: 0 });
  api.transferInbox.mockResolvedValue([]);
  api.transferAccept.mockResolvedValue({ ok: true });
  api.transferDecline.mockResolvedValue({ ok: true });
  api.transferOutbox.mockResolvedValue([]);
  api.transferCancel.mockResolvedValue({ ok: true });
  api.listRecentAnnouncements.mockResolvedValue([]);
  ui.confirm.mockResolvedValue('confirm');
  ui.prompt.mockResolvedValue({ value: '工作安排冲突' });
}

beforeEach(resetMocks);
afterEach(() => wrapper?.unmount());

describe('Workspace Index', () => {
  it('不请求模板未消费的 getWorkspace，且挂载时立即启动所有真实区块加载', async () => {
    const obsoleteRequest = deferred();
    api.getWorkspace.mockReturnValue(obsoleteRequest.promise);

    wrapper = mountPage();
    await nextTick();

    expect(api.getWorkspace).not.toHaveBeenCalled();
    expect(api.listRecentAnnouncements).toHaveBeenCalledWith(3);
    expect(api.listNotifications).toHaveBeenCalledWith({ pageNo: 1, pageSize: 3 });
    expect(api.getUnreadNotificationCount).toHaveBeenCalledTimes(1);
    expect(api.listTodoTasks).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 20 }));
    expect(api.transferInbox).toHaveBeenCalledTimes(1);
    expect(api.transferOutbox).toHaveBeenCalledTimes(1);
  });

  it('通知列表与未读数在彼此返回前并行加载', async () => {
    const notificationRows = deferred();
    const unreadCount = deferred();
    api.listNotifications.mockReturnValue(notificationRows.promise);
    api.getUnreadNotificationCount.mockReturnValue(unreadCount.promise);

    wrapper = mountPage();
    await flushPromises();

    expect(api.listNotifications).toHaveBeenCalledWith({ pageNo: 1, pageSize: 3 });
    expect(api.getUnreadNotificationCount).toHaveBeenCalledTimes(1);
  });

  it('以 h1 标出工作台，并展示真实的待办总数与未读数摘要', async () => {
    api.listTodoTasks.mockResolvedValue({ records: [], total: 7 });
    api.getUnreadNotificationCount.mockResolvedValue(4);

    wrapper = mountPage();
    await settle();

    expect(wrapper.find('h1').text()).toBe('工作台');
    const summaryItems = wrapper.findAll('.workspace-summary .summary-item');
    expect(summaryItems[0].text()).toContain('待办总数7项');
    expect(summaryItems[1].text()).toContain('未读4条');
  });

  it('加载期间与空态互斥，待办和已办分别显示准确的空文案', async () => {
    const announcementRequest = deferred();
    const notificationRequest = deferred();
    const unreadRequest = deferred();
    const todoRequest = deferred();
    api.listRecentAnnouncements.mockReturnValue(announcementRequest.promise);
    api.listNotifications.mockReturnValue(notificationRequest.promise);
    api.getUnreadNotificationCount.mockReturnValue(unreadRequest.promise);
    api.listTodoTasks.mockReturnValue(todoRequest.promise);

    wrapper = mountPage();
    await nextTick();

    const announcements = wrapper.find('[aria-label="公告"]');
    const notifications = wrapper.find('[aria-label="通知"]');
    const tasks = wrapper.find('[aria-label="我的任务"]');
    expect(announcements.attributes('aria-busy')).toBe('true');
    expect(announcements.text()).toContain('公告加载中');
    expect(announcements.text()).not.toContain('暂无公告');
    expect(notifications.attributes('aria-busy')).toBe('true');
    expect(notifications.text()).toContain('通知加载中');
    expect(notifications.text()).not.toContain('暂无通知');
    expect(tasks.attributes('aria-busy')).toBe('true');
    expect(tasks.text()).not.toContain('暂无待办任务');

    announcementRequest.resolve([]);
    notificationRequest.resolve({ records: [] });
    unreadRequest.resolve(0);
    todoRequest.resolve({ records: [], total: 0 });
    await settle();

    expect(announcements.attributes('aria-busy')).toBe('false');
    expect(announcements.text()).toContain('暂无公告');
    expect(announcements.text()).not.toContain('公告加载中');
    expect(notifications.attributes('aria-busy')).toBe('false');
    expect(notifications.text()).toContain('暂无通知');
    expect(notifications.text()).not.toContain('通知加载中');
    expect(tasks.attributes('aria-busy')).toBe('false');
    expect(tasks.text()).toContain('暂无待办任务');

    wrapper.vm.taskTab = 'DONE';
    await wrapper.vm.loadTasks();
    await settle();
    expect(tasks.text()).toContain('暂无已办任务');
  });

  it('公告和通知以具名按钮跳转，不保留无 href 的锚点或可点击 div', async () => {
    api.listRecentAnnouncements.mockResolvedValue([{ id: 'ANN_1', title: '季度经营公告', publishDate: '2026-08-12T09:00:00' }]);
    api.listNotifications.mockResolvedValue({
      records: [{ id: 'NTF_1', title: '系统通知', isRead: true, createdTime: '2026-08-12T09:00:00' }]
    });

    wrapper = mountPage();
    await settle();

    expect(wrapper.findAll('a')).toHaveLength(0);
    const announcement = wrapper.find('button[aria-label="查看公告：季度经营公告"]');
    const notification = wrapper.find('button[aria-label="查看通知：系统通知"]');
    expect(announcement.exists()).toBe(true);
    expect(notification.exists()).toBe(true);

    await announcement.trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith('/announcement/ANN_1');
    await notification.trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith('/system/notifications');
  });

  it('标记通知已读时函数级防重，成功后更新计数、派发事件再跳转', async () => {
    const markReadRequest = deferred();
    const order = [];
    const onNotificationChanged = () => order.push('event');
    window.addEventListener('notification-changed', onNotificationChanged);
    routerPush.mockImplementation(() => order.push('navigate'));
    api.markRead.mockReturnValue(markReadRequest.promise);
    api.getUnreadNotificationCount.mockResolvedValue(2);
    api.listNotifications.mockResolvedValue({
      records: [{ id: 'NTF_2', title: '待处理事项', isRead: false, createdTime: '2026-08-12T09:00:00' }]
    });

    wrapper = mountPage();
    await settle();

    const notification = wrapper.find('button[aria-label="查看通知：待处理事项"]');
    await notification.trigger('click');
    await notification.trigger('click');
    expect(api.markRead).toHaveBeenCalledTimes(1);
    expect(api.markRead).toHaveBeenCalledWith('NTF_2');

    markReadRequest.resolve({ ok: true });
    await settle();

    expect(wrapper.findAll('.workspace-summary .summary-item')[1].text()).toContain('未读1条');
    expect(order).toEqual(['event', 'navigate']);
    window.removeEventListener('notification-changed', onNotificationChanged);
  });

  it('认领写操作函数级防重，并保留撤回二次确认契约和成功反馈', async () => {
    const acceptRequest = deferred();
    api.transferAccept.mockReturnValue(acceptRequest.promise);
    wrapper = mountPage();
    await settle();

    const transfer = { id: 'TRANSFER_1', nodeName: '部门审批' };
    const firstAttempt = wrapper.vm.acceptTransfer(transfer);
    const repeatedAttempt = wrapper.vm.acceptTransfer(transfer);
    await flushPromises();

    expect(ui.confirm).toHaveBeenCalledTimes(1);
    expect(api.transferAccept).toHaveBeenCalledTimes(1);
    acceptRequest.resolve({ ok: true });
    await firstAttempt;
    await repeatedAttempt;
    expect(ui.success).toHaveBeenCalledWith('已认领');

    ui.confirm.mockClear();
    const revocable = { id: 'TRANSFER_2', businessKey: 'ALLOC_ADJUST:42' };
    await wrapper.vm.cancelTransfer(revocable);
    expect(ui.confirm).toHaveBeenCalledWith(
      '确认撤回转交：ALLOC_ADJUST:42？',
      '撤回转交',
      expect.objectContaining({ type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' })
    );
    expect(api.transferCancel).toHaveBeenCalledWith('TRANSFER_2');
    expect(ui.success).toHaveBeenCalledWith('已撤回');
  });

  it('拒绝转交保留必填理由和成功反馈', async () => {
    wrapper = mountPage();
    await settle();

    await wrapper.vm.declineTransfer({ id: 'TRANSFER_3', nodeName: '风险复核' });

    expect(ui.prompt).toHaveBeenCalledWith(
      '请填写拒绝理由（必填）',
      '拒绝转交：风险复核',
      expect.objectContaining({ inputPattern: expect.any(RegExp), inputErrorMessage: '拒绝理由必填' })
    );
    expect(api.transferDecline).toHaveBeenCalledWith('TRANSFER_3', { reason: '工作安排冲突' });
    expect(ui.success).toHaveBeenCalledWith('已拒绝');
  });
});
