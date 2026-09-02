// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick, reactive } from 'vue';

const api = vi.hoisted(() => ({
  listSupportRequests: vi.fn(), getSupportRequest: vi.fn(), createSupportRequest: vi.fn(),
  submitSupportRequest: vi.fn(), withdrawSupportRequest: vi.fn(), listSupportRequestLogs: vi.fn(),
  listSupportDeptRequests: vi.fn(), getSupportDeptRequest: vi.fn(), listSupportDeptLogs: vi.fn(),
  addSupportDeptLog: vi.fn(), dispatchSupportRequest: vi.fn(), completeSupportRequest: vi.fn(),
  listSupportCustomers: vi.fn(), listSupportProducts: vi.fn(), uploadSupportPhoto: vi.fn()
}));
const directory = vi.hoisted(() => ({
  getOrgTree: vi.fn(), listOrgUsers: vi.fn(), searchEmployees: vi.fn()
}));
const ui = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() }));
const messageBox = vi.hoisted(() => ({ confirm: vi.fn(), prompt: vi.fn() }));
const routerPush = vi.hoisted(() => vi.fn());
const currentRoute = reactive({
  name: 'SupportRequests', path: '/bizexec/supports', params: {}, query: {}
});

vi.mock('element-plus', () => ({ ElMessage: ui, ElMessageBox: messageBox }));
vi.mock('vue-router', () => ({
  useRoute: () => currentRoute,
  useRouter: () => ({ push: routerPush })
}));
vi.mock('@/api/supportRequests', () => api);
vi.mock('@/api/orgs', () => ({ getOrgTree: directory.getOrgTree, listOrgUsers: directory.listOrgUsers }));
vi.mock('@/api/employees', () => ({ searchEmployees: directory.searchEmployees }));

import SupportRequests from '../SupportRequests.vue';

const passthrough = (name, template = '<div><slot /><slot name="footer" /><slot name="default" /></div>') => ({ name, template });
const stubs = {
  PageTitle: { name: 'PageTitle', props: ['title'], template: '<h1>{{ title }}</h1>' },
  'el-alert': passthrough('ElAlert'),
  'el-button': {
    name: 'ElButton', inheritAttrs: false, props: { disabled: Boolean, loading: Boolean },
    emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-card': passthrough('ElCard'),
  'el-checkbox': passthrough('ElCheckbox'),
  'el-checkbox-group': passthrough('ElCheckboxGroup'),
  'el-date-picker': passthrough('ElDatePicker'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-dialog': passthrough('ElDialog'),
  'el-drawer': passthrough('ElDrawer'),
  'el-empty': passthrough('ElEmpty'),
  'el-form': { name: 'ElForm', inheritAttrs: false, template: '<form v-bind="$attrs"><slot /></form>' },
  'el-form-item': passthrough('ElFormItem'),
  'el-image': passthrough('ElImage'),
  'el-input': {
    name: 'ElInput', inheritAttrs: false, props: { modelValue: [String, Number], disabled: Boolean },
    emits: ['update:modelValue'], template: '<input v-bind="$attrs" :disabled="disabled" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-option': passthrough('ElOption'),
  'el-pagination': passthrough('ElPagination'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-radio-button': passthrough('ElRadioButton'),
  'el-select': passthrough('ElSelect'),
  'el-tab-pane': passthrough('ElTabPane'),
  'el-table': passthrough('ElTable'),
  'el-table-column': { name: 'ElTableColumn', template: '<div />' },
  'el-tabs': passthrough('ElTabs'),
  'el-tag': passthrough('ElTag'),
  'el-timeline': passthrough('ElTimeline'),
  'el-timeline-item': passthrough('ElTimelineItem'),
  'el-upload': passthrough('ElUpload')
};

let wrapper;

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function resetMocks() {
  Object.values(api).forEach(mock => mock.mockReset());
  Object.values(directory).forEach(mock => mock.mockReset());
  Object.values(ui).forEach(mock => mock.mockReset());
  Object.values(messageBox).forEach(mock => mock.mockReset());
  routerPush.mockReset();
  currentRoute.name = 'SupportRequests';
  currentRoute.path = '/bizexec/supports';
  currentRoute.params = {};
  currentRoute.query = {};
  api.listSupportRequests.mockResolvedValue({ records: [], total: 0 });
  api.listSupportDeptRequests.mockResolvedValue({ records: [], total: 0 });
  api.listSupportRequestLogs.mockResolvedValue([]);
  api.listSupportDeptLogs.mockResolvedValue([]);
  api.listSupportCustomers.mockResolvedValue({ records: [{ id: 'C-9', custName: '测试客户' }], total: 1 });
  api.listSupportProducts.mockResolvedValue([{ id: 'P-1', name: '产品一', productName: '产品一' }]);
  directory.getOrgTree.mockResolvedValue([{ code: 'D-1', name: '支持部门' }]);
  directory.listOrgUsers.mockResolvedValue([{ id: 'E-1', name: '支持人员' }]);
  directory.searchEmployees.mockResolvedValue([]);
  messageBox.confirm.mockResolvedValue(true);
  messageBox.prompt.mockResolvedValue({ value: '说明' });
}

function mountPage() {
  return mount(SupportRequests, {
    global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } }
  });
}

beforeEach(resetMocks);
afterEach(() => wrapper?.unmount());

describe('SupportRequests 中台支持工作台', () => {
  it('工作台展示带本页口径的四项摘要卡，并可点击卡片筛选列表', async () => {
    api.listSupportRequests.mockResolvedValue({
      records: [
        { id: 'SR-1', status: 'DRAFT' },
        { id: 'SR-2', status: 'IN_PROGRESS' },
        { id: 'SR-3', status: 'COMPLETED', slaStatus: 'RED' }
      ],
      total: 99
    });

    wrapper = mountPage();
    await settle();
    const cards = wrapper.findAll('[data-testid="support-summary-card"]');

    expect(cards).toHaveLength(4);
    expect(cards[0].attributes('aria-label')).toContain('中台支持本页');
    expect(cards[0].text()).toContain('本页');
    expect(cards[0].text()).toContain('3');
    const listPanel = wrapper.find('section.card-section[aria-label="我的申请列表"]');
    expect(listPanel.attributes('aria-describedby')).toBe('support-request-list-state');
    expect(wrapper.find('#support-request-list-state').attributes('aria-live')).toBe('polite');

    await cards[2].trigger('click');

    expect(wrapper.vm.query.status).toBe('IN_PROGRESS');
    expect(wrapper.vm.query.slaStatus).toBe('');
    expect(api.listSupportRequests).toHaveBeenCalledTimes(2);
  });

  it('SLA 缺失时显示未知，不把未返回的状态伪装为正常', async () => {
    wrapper = mountPage();
    await settle();

    expect(wrapper.vm.slaLabel({})).toBe('-');
    expect(wrapper.vm.slaClass({})).toBe('unknown');
    expect(wrapper.vm.slaLabel({ slaStatus: 'RED' })).toBe('超时');
  });

  it('从触达任务进入新建时自动带出并锁定客户和来源', async () => {
    currentRoute.name = 'SupportRequestCreate';
    currentRoute.path = '/bizexec/supports/new';
    currentRoute.query = { custId: 'C-9', sourceTouchTaskId: 'TT-9' };
    wrapper = mountPage();
    await settle();

    expect(wrapper.vm.form.custId).toBe('C-9');
    expect(wrapper.vm.form.sourceTouchTaskId).toBe('TT-9');
    expect(wrapper.vm.sourceLocked).toBe(true);
    expect(wrapper.text()).toContain('中台支持');
    expect(wrapper.find('[data-field="custId"]').exists()).toBe(true);
  });

  it('创建多产品申请后按返回 requests 顺序逐条提交', async () => {
    currentRoute.name = 'SupportRequestCreate';
    currentRoute.path = '/bizexec/supports/new';
    api.createSupportRequest.mockResolvedValue({
      submitGroupId: 'GROUP-1', requests: [{ id: 'SR-1' }, { id: 'SR-2' }]
    });
    api.submitSupportRequest.mockResolvedValue({ status: 'IN_APPROVAL' });
    wrapper = mountPage();
    await settle();
    wrapper.vm.form.custId = 'C-9';
    wrapper.vm.form.productIds = ['P-1'];

    await wrapper.vm.saveAndSubmit();

    expect(api.createSupportRequest).toHaveBeenCalledTimes(1);
    expect(api.submitSupportRequest).toHaveBeenNthCalledWith(1, 'SR-1');
    expect(api.submitSupportRequest).toHaveBeenNthCalledWith(2, 'SR-2');
    expect(routerPush).toHaveBeenCalledWith('/bizexec/supports');
  });

  it('客户已有在途申请时确认后携带 confirmParallel 重试', async () => {
    currentRoute.name = 'SupportRequestCreate';
    currentRoute.path = '/bizexec/supports/new';
    const conflict = Object.assign(new Error('该客户已有 2 条进行中的中台支持'), { code: 'BIZ-40907' });
    api.createSupportRequest
      .mockRejectedValueOnce(conflict)
      .mockResolvedValueOnce({ submitGroupId: 'GROUP-2', requests: [{ id: 'SR-8' }] });
    wrapper = mountPage();
    await settle();
    wrapper.vm.form.custId = 'C-9';
    wrapper.vm.form.productIds = ['P-1'];

    const result = await wrapper.vm.createDraft();

    expect(messageBox.confirm).toHaveBeenCalledWith(
      conflict.message,
      '并行流程确认',
      expect.objectContaining({ confirmButtonText: '继续创建' })
    );
    expect(api.createSupportRequest).toHaveBeenNthCalledWith(2, expect.objectContaining({
      custId: 'C-9', confirmParallel: true
    }));
    expect(result?.requests?.[0]?.id).toBe('SR-8');
  });

  it('承接待办按 supportDeptId 查询员工，支持日志后可成功办理或驳回', async () => {
    currentRoute.query = { tab: 'DEPT_TODO' };
    api.listSupportDeptRequests.mockResolvedValue({
      records: [{ id: 'SR-3', supportDeptId: 'D-1', status: 'IN_APPROVAL' }], total: 1
    });
    api.getSupportDeptRequest.mockResolvedValue({ id: 'SR-3', supportDeptId: 'D-1', status: 'IN_APPROVAL' });
    api.addSupportDeptLog.mockResolvedValue({ id: 'LOG-1' });
    api.completeSupportRequest.mockResolvedValue({ ok: true });
    wrapper = mountPage();
    await settle();
    wrapper.vm.activeTab = 'DEPT_TODO';

    await wrapper.vm.openDispatch({ id: 'SR-3', supportDeptId: 'D-1' });
    expect(directory.listOrgUsers).toHaveBeenCalledWith('D-1', expect.any(Object));

    wrapper.vm.detail = { id: 'SR-3', supportDeptId: 'D-1', status: 'IN_PROGRESS' };
    wrapper.vm.detailMode = 'DEPT_TODO';
    wrapper.vm.logForm.content = '已完成沟通';
    wrapper.vm.logForm.operatorLocation = '西安高新区';
    wrapper.vm.logForm.checkInTime = '2026-09-01T10:00:00';
    wrapper.vm.logForm.photoUrls = ['/api/files/F-1/download'];
    wrapper.vm.logForm.photoFileIds = ['F-1'];
    await wrapper.vm.addLog();
    await wrapper.vm.complete(true);
    expect(api.addSupportDeptLog).toHaveBeenCalledWith('SR-3', expect.objectContaining({
      clientUuid: expect.stringContaining('SUPPORT_LOG:'),
      content: '已完成沟通', checkinTime: '2026-09-01T10:00:00', locationAddress: '西安高新区', fileIds: ['F-1']
    }));
    expect(api.completeSupportRequest).toHaveBeenCalledWith('SR-3', expect.objectContaining({
      result: 'SUCCESS', handleResult: '已完成沟通', outputAttachmentIds: [], fileIds: []
    }));
  });

  it('PROCESS 过程记录的定位、打卡时间和现场照片均为必填', async () => {
    currentRoute.query = { tab: 'DEPT_TODO' };
    wrapper = mountPage();
    await settle();
    wrapper.vm.detail = { id: 'SR-4', supportDeptId: 'D-1', status: 'IN_PROGRESS' };
    wrapper.vm.detailMode = 'DEPT_TODO';
    wrapper.vm.logForm.content = '已完成现场沟通';

    await wrapper.vm.addLog();

    expect(ui.warning).toHaveBeenCalledWith('请填写定位地址或获取当前位置');
    expect(api.addSupportDeptLog).not.toHaveBeenCalled();
  });

  it('定位通过后仍拦截缺少打卡时间或现场照片的过程记录', async () => {
    currentRoute.query = { tab: 'DEPT_TODO' };
    wrapper = mountPage();
    await settle();
    wrapper.vm.detail = { id: 'SR-6', supportDeptId: 'D-1', status: 'IN_PROGRESS' };
    wrapper.vm.detailMode = 'DEPT_TODO';
    wrapper.vm.logForm.content = '已完成现场沟通';
    wrapper.vm.logForm.operatorLocation = '西安高新区';

    await wrapper.vm.addLog();
    expect(ui.warning).toHaveBeenCalledWith('请选择打卡时间');
    expect(api.addSupportDeptLog).not.toHaveBeenCalled();

    wrapper.vm.logForm.checkInTime = '2026-09-01T10:00:00';
    await wrapper.vm.addLog();
    expect(ui.warning).toHaveBeenCalledWith('请上传至少一张现场照片');
    expect(api.addSupportDeptLog).not.toHaveBeenCalled();
  });

  it('浏览器定位成功后自动填充经纬度，并随过程记录提交', async () => {
    const previousGeolocation = navigator.geolocation;
    Object.defineProperty(navigator, 'geolocation', {
      configurable: true,
      value: { getCurrentPosition: vi.fn(success => success({ coords: { longitude: 108.94, latitude: 34.34 } })) }
    });
    try {
      currentRoute.query = { tab: 'DEPT_TODO' };
      api.getSupportDeptRequest.mockResolvedValue({ id: 'SR-5', supportDeptId: 'D-1', status: 'IN_PROGRESS' });
      wrapper = mountPage();
      await settle();
      await wrapper.vm.openDetail('SR-5', 'DEPT_TODO');
      await settle();

      expect(wrapper.vm.logForm.longitude).toBe(108.94);
      expect(wrapper.vm.logForm.latitude).toBe(34.34);
      expect(wrapper.vm.logForm.operatorLocation).toContain('108.94');

      wrapper.vm.detail = { id: 'SR-5', supportDeptId: 'D-1', status: 'IN_PROGRESS' };
      wrapper.vm.detailMode = 'DEPT_TODO';
      wrapper.vm.logForm.content = '已完成现场沟通';
      wrapper.vm.logForm.checkInTime = '2026-09-01T10:00:00';
      wrapper.vm.logForm.photoUrls = ['/api/files/F-5/download'];
      wrapper.vm.logForm.photoFileIds = ['F-5'];
      await wrapper.vm.addLog();

      expect(api.addSupportDeptLog).toHaveBeenCalledWith('SR-5', expect.objectContaining({
        longitude: 108.94, latitude: 34.34, locationAddress: expect.stringContaining('108.94'),
        checkinTime: '2026-09-01T10:00:00', fileIds: ['F-5']
      }));
    } finally {
      Object.defineProperty(navigator, 'geolocation', { configurable: true, value: previousGeolocation });
    }
  });

  it('自动定位失败时允许手工填写定位地址后提交过程记录', async () => {
    const previousGeolocation = navigator.geolocation;
    Object.defineProperty(navigator, 'geolocation', {
      configurable: true,
      value: { getCurrentPosition: vi.fn((_, failure) => failure(new Error('permission denied'))) }
    });
    try {
      currentRoute.query = { tab: 'DEPT_TODO' };
      api.getSupportDeptRequest.mockResolvedValue({ id: 'SR-7', supportDeptId: 'D-1', status: 'IN_PROGRESS' });
      wrapper = mountPage();
      await settle();
      await wrapper.vm.openDetail('SR-7', 'DEPT_TODO');
      await settle();

      expect(wrapper.vm.geoError).toContain('手工填写定位地址');
      wrapper.vm.logForm.content = '已完成现场沟通';
      wrapper.vm.logForm.operatorLocation = '西安高新区客户现场';
      wrapper.vm.logForm.checkInTime = '2026-09-01T10:00:00';
      wrapper.vm.logForm.photoUrls = ['/api/files/F-7/download'];
      wrapper.vm.logForm.photoFileIds = ['F-7'];
      await wrapper.vm.addLog();

      expect(api.addSupportDeptLog).toHaveBeenCalledWith('SR-7', expect.objectContaining({
        locationAddress: '西安高新区客户现场', longitude: undefined, latitude: undefined, fileIds: ['F-7']
      }));
    } finally {
      Object.defineProperty(navigator, 'geolocation', { configurable: true, value: previousGeolocation });
    }
  });

  it('过程时间线按后端 logType 展示过程记录和办理结果', async () => {
    wrapper = mountPage();
    await settle();
    wrapper.vm.detail = { id: 'SR-LOG' };
    wrapper.vm.logs = [
      { id: 'LOG-P', logType: 'PROCESS', content: '现场沟通', createdBy: 'E-1' },
      { id: 'LOG-R', logType: 'RESULT', content: '已完成办理', createdBy: 'E-1' }
    ];
    await nextTick();

    expect(wrapper.text()).toContain('过程记录');
    expect(wrapper.text()).toContain('办理结果');
  });

  it('真实承接字段优先于旧兼容字段', async () => {
    wrapper = mountPage();
    await settle();

    expect(wrapper.vm.assignedName({ assignedName: '旧字段', assignedEmpId: 'E-9' })).toBe('E-9');
    expect(wrapper.vm.assignedName({ assignedName: '旧字段', assignedEmpId: 'E-9', assignedEmpName: '承接人' })).toBe('承接人');
  });
});
