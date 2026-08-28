// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const route = vi.hoisted(() => ({ name: 'AssetProjects', query: {}, params: {} }));
const api = vi.hoisted(() => ({
  cancelAssetProject: vi.fn(),
  createAssetProject: vi.fn(),
  deleteAssetProject: vi.fn(),
  getAssetProject: vi.fn(),
  getAssetProjectCustomer: vi.fn(),
  getAssetProjectUrgentContext: vi.fn(),
  listAssetProjectCustomers: vi.fn(),
  listAssetProjects: vi.fn(),
  requestAssetProjectUrgent: vi.fn(),
  submitAssetProject: vi.fn(),
  updateAssetProject: vi.fn(),
  uploadAssetProjectAttachment: vi.fn()
}));
const message = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn() }));

vi.mock('vue-router', () => ({ useRoute: () => route }));
vi.mock('@/api/assetProjects', () => api);
vi.mock('element-plus', () => ({
  ElMessage: message,
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() }
}));

import AssetProjects from '../AssetProjects.vue';

const passthrough = (name, template = '<div><slot /><slot name="footer" /></div>') => ({
  name,
  inheritAttrs: false,
  template
});
const stubs = {
  PageTitle: passthrough('PageTitle'),
  BpAdaptiveRowActions: passthrough('BpAdaptiveRowActions'),
  LeadAttachmentPreview: passthrough('LeadAttachmentPreview'),
  'el-alert': { name: 'ElAlert', props: ['title'], template: '<div class="alert">{{ title }}</div>' },
  'el-dialog': { name: 'ElDialog', props: ['modelValue', 'closeOnClickModal'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-drawer': { name: 'ElDrawer', props: ['modelValue', 'closeOnClickModal'], template: '<div v-if="modelValue"><slot /></div>' },
  'el-form': { name: 'ElForm', template: '<form><slot /></form>', methods: { validate: vi.fn(), validateField: vi.fn() } },
  'el-form-item': passthrough('ElFormItem'),
  'el-table': passthrough('ElTable'),
  'el-table-column': { name: 'ElTableColumn', template: '<span />' },
  'el-tabs': passthrough('ElTabs'),
  'el-tab-pane': passthrough('ElTabPane'),
  'el-upload': passthrough('ElUpload'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-empty': passthrough('ElEmpty'),
  'el-pagination': passthrough('ElPagination'),
  'el-button': passthrough('ElButton', '<button><slot /></button>'),
  'el-input': passthrough('ElInput'),
  'el-input-number': passthrough('ElInputNumber'),
  'el-select': passthrough('ElSelect'),
  'el-option': passthrough('ElOption'),
  'el-date-picker': passthrough('ElDatePicker'),
  'el-checkbox': passthrough('ElCheckbox'),
  'el-tag': passthrough('ElTag'),
  'el-dropdown': passthrough('ElDropdown'),
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': passthrough('ElDropdownItem')
};

let wrapper;

async function mountPage() {
  wrapper = mount(AssetProjects, {
    global: { stubs, directives: { loading: () => {}, 'bp-overflow-tooltip': () => {} } }
  });
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(route, { name: 'AssetProjects', query: {}, params: {} });
  api.listAssetProjects.mockResolvedValue({ records: [], total: 0 });
  api.listAssetProjectCustomers.mockResolvedValue([]);
});

afterEach(() => wrapper?.unmount());

describe('资产立项页面', () => {
  it('从客户或触达入口新建时按 custId 精确反显客户并锁定来源', async () => {
    Object.assign(route, {
      name: 'AssetProjectCreate',
      query: { custId: '7', sourceTouchTaskId: '81', sourceWorklogId: '91' },
      params: {}
    });
    api.getAssetProjectCustomer.mockResolvedValue({
      id: 7,
      custNo: 'C-007',
      custName: '测试客户',
      unifiedCreditCode: '913100000000000007',
      mainManagerId: 'E-7',
      mainManagerName: '张三',
      mainOrgId: 'ORG-7',
      mainOrgName: '公司部'
    });

    const view = await mountPage();

    expect(api.getAssetProjectCustomer).toHaveBeenCalledWith(7);
    expect(view.vm.form).toMatchObject({
      custId: 7,
      custNo: 'C-007',
      customerName: '测试客户',
      unifiedCreditCode: '913100000000000007',
      mainManagerId: 'E-7',
      mainManagerName: '张三',
      mainOrgId: 'ORG-7',
      mainOrgName: '公司部',
      sourceTouchTaskId: 81,
      sourceWorklogId: 91
    });
    expect(view.vm.sourceLocked).toBe(true);
    expect(view.vm.formVisible).toBe(true);
  });

  it('动态 id 路由直接打开统一详情', async () => {
    Object.assign(route, { name: 'AssetProjectDetail', query: {}, params: { id: '42' } });
    api.getAssetProject.mockResolvedValue({ id: 42, applyNo: 'AP-42', urgentApplies: [] });

    const view = await mountPage();

    expect(api.getAssetProject).toHaveBeenCalledWith('42');
    expect(view.vm.detail).toMatchObject({ id: 42, applyNo: 'AP-42' });
    expect(view.vm.detailVisible).toBe(true);
  });

  it('列表请求失败时保留可见错误态，不伪装空列表', async () => {
    api.listAssetProjects.mockRejectedValueOnce(new Error('资产立项接口暂不可用'));

    const view = await mountPage();

    expect(view.vm.listError).toBe('资产立项接口暂不可用');
    expect(view.text()).toContain('资产立项接口暂不可用');
  });

  it('编辑详情加载失败时仍打开表单并展示可见错误', async () => {
    api.getAssetProject.mockRejectedValueOnce(new Error('资产立项草稿加载失败'));
    const view = await mountPage();

    await view.vm.openEdit({ id: 42 });
    await flushPromises();

    expect(view.vm.formVisible).toBe(true);
    expect(view.vm.formError).toBe('资产立项草稿加载失败');
    expect(view.text()).toContain('资产立项草稿加载失败');
  });

  it('项目贷款高于总投资只做页面提示，不阻断草稿保存', async () => {
    api.createAssetProject.mockResolvedValue({ id: 88 });
    const view = await mountPage();
    Object.assign(view.vm.form, {
      custId: 7,
      projectTotalInvestment: 100,
      projectLoanAmount: 120,
      creditAmount: 80,
      creditExposureAmount: 60
    });

    expect(view.vm.amountWarning).toContain('仅提示');
    await view.vm.save(false);

    expect(api.createAssetProject).toHaveBeenCalledTimes(1);
  });

  it('附件上限与平台 FileService 的 50MB 限制一致', async () => {
    const view = await mountPage();
    const onError = vi.fn();

    await view.vm.uploadFile({ file: { size: 50 * 1024 * 1024 + 1 }, onError });

    expect(api.uploadAssetProjectAttachment).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ message: '单个附件不能超过50MB' }));
    expect(view.vm.formError).toBe('单个附件不能超过50MB');
  });

  it('表单、详情和加急弹层都不允许点击遮罩误关闭', async () => {
    const view = await mountPage();
    const overlays = [
      ...view.findAllComponents({ name: 'ElDialog' }),
      ...view.findAllComponents({ name: 'ElDrawer' })
    ];
    expect(overlays).toHaveLength(3);
    expect(overlays.every(item => item.props('closeOnClickModal') === false)).toBe(true);
  });
});
