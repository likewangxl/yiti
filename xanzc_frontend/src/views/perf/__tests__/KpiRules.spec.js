// @vitest-environment happy-dom
// KPI 方案「员工范围」由角色改为人员标签（2026-07-20）的回归测试：
//  1) 下拉数据源来自人员标签接口（tagId/tagName），不再调角色接口；
//  2) 新增/编辑提交的字段是 empTagScopes(标签 ID 数组)；
//  3) 详情回显以 getKpiSchemeDetail 的 empTagScopes 为准；
//  4) 已选标签在标签库中不存在（被删除）时，页面给出失效提示。
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}));

vi.mock('@/api/perf', () => ({
  listKpiRules: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  getKpiSchemeDetail: vi.fn().mockResolvedValue({ items: [], empTagScopes: [11, 99] }),
  createKpiScheme: vi.fn().mockResolvedValue({ id: 'S1' }),
  updateKpiScheme: vi.fn().mockResolvedValue({ ok: true }),
  deleteKpiScheme: vi.fn(), publishKpiScheme: vi.fn(),
  addKpiItem: vi.fn(), updateKpiItem: vi.fn(), deleteKpiItem: vi.fn(),
  listMetrics: vi.fn().mockResolvedValue({ records: [] }),
  uploadImportFile: vi.fn()
}));

vi.mock('@/api/system', () => ({
  listPersonTags: vi.fn().mockResolvedValue({
    records: [
      { tagId: 11, tagName: '重点培养' },
      { tagId: 22, tagName: '骨干' }
    ],
    total: 2
  })
}));

vi.mock('@/api/orgs', () => ({ getOrgTree: vi.fn().mockResolvedValue([]) }));
vi.mock('@/composables/useDict', () => ({
  useDict: () => ({ options: { value: [] } })
}));
vi.mock('@/stores/user', () => ({
  // 资财部经办人(238)：新增/编辑按钮可见
  useUserStore: () => ({ user: { roles: [{ roleId: '238' }] } })
}));

import { listPersonTags } from '@/api/system';
import { createKpiScheme, getKpiSchemeDetail, listKpiRules, publishKpiScheme, uploadImportFile } from '@/api/perf';
import KpiRules from '../KpiRules.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="header" /><slot name="footer" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: {
    name: 'PageTitle',
    inheritAttrs: false,
    template: '<h1 class="page-title" v-bind="$attrs">KPI 方案<slot /></h1>'
  },
  // 必须声明 emits，否则父级 @click 经 attrs 透传后与 $emit 叠加，处理器被调两次
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-card': passthrough('ElCard'),
  'el-dialog': { name: 'ElDialog', props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  // 必须提供 validate：组件用 schemeFormRef.value?.validate() 做必填校验，
  // ?. 只防空对象不防缺失方法，缺了会抛 TypeError 被 catch 成"校验不通过"而静默 return
  'el-form': {
    name: 'ElForm',
    inheritAttrs: false,
    methods: { validate: () => Promise.resolve(true) },
    template: '<form v-bind="$attrs"><slot /></form>'
  },
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput',
    inheritAttrs: false,
    props: { modelValue: [String, Number] },
    emits: ['update:modelValue'],
    template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': { name: 'ElSelect', inheritAttrs: false, template: '<select v-bind="$attrs"><slot /></select>' },
  'el-option': empty('ElOption'),
  'el-table': { name: 'ElTable', props: ['data'], template: '<div><slot /></div>' },
  'el-table-column': empty('ElTableColumn'),
  'el-pagination': empty('ElPagination'),
  'el-tag': passthrough('ElTag'),
  'el-tooltip': passthrough('ElTooltip'),
  'el-popconfirm': passthrough('ElPopconfirm'),
  'el-switch': empty('ElSwitch'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-radio': passthrough('ElRadio'),
  'el-radio-button': passthrough('ElRadioButton'),
  'el-input-number': empty('ElInputNumber'),
  'el-upload': passthrough('ElUpload'),
  'el-alert': empty('ElAlert')
};

function mountPage() {
  return mount(KpiRules, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

beforeEach(() => vi.clearAllMocks());

describe('KpiRules.vue 员工标签范围', () => {
  it('以 bp-crud 工作区组织页头、筛选、方案列表和分页语义', async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.find('main.bp-crud.kpi-rules-page[aria-labelledby="kpi-rules-page-title"]').exists()).toBe(true);
    expect(wrapper.get('h1#kpi-rules-page-title').text()).toContain('KPI 方案');
    expect(wrapper.find('form[aria-label="KPI方案筛选"]').exists()).toBe(true);
    expect(wrapper.find('input[aria-label="按方案编码或名称筛选"]').exists()).toBe(true);
    expect(wrapper.find('section[aria-label="KPI方案列表"][aria-describedby="kpi-rules-table-state"]').exists()).toBe(true);
    expect(wrapper.find('nav[aria-label="KPI方案列表分页"]').exists()).toBe(true);
    expect(wrapper.get('#kpi-rules-table-state').text()).toContain('暂无KPI方案数据');
  });

  it('加载方案列表时向数据面板公开 aria-busy 状态', async () => {
    const pending = deferred();
    listKpiRules.mockImplementationOnce(() => pending.promise);
    const wrapper = mountPage();
    await wrapper.vm.$nextTick();

    const panel = wrapper.get('section[aria-label="KPI方案列表"]');
    expect(panel.attributes('aria-busy')).toBe('true');
    expect(wrapper.get('#kpi-rules-table-state').text()).toContain('KPI方案列表加载中');

    pending.resolve({ records: [], total: 0 });
    await flushPromises();

    expect(panel.attributes('aria-busy')).toBe('false');
    expect(wrapper.get('#kpi-rules-table-state').text()).toContain('暂无KPI方案数据');
    wrapper.unmount();
  });

  it('打开新增弹窗：下拉数据源为人员标签（tagId/tagName）', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.openCreate();
    await flushPromises();

    expect(listPersonTags).toHaveBeenCalled();
    expect(wrapper.vm.empTagOptions).toEqual([
      { tagId: 11, tagName: '重点培养' },
      { tagId: 22, tagName: '骨干' }
    ]);
    // 表单字段是标签数组，初始为空=不限定
    expect(wrapper.vm.dlg.scheme.empTagScopes).toEqual([]);
    expect(wrapper.findAll('select').some(select => 'multiple' in select.attributes())).toBe(true);
  });

  it('编辑回显：以详情接口的 empTagScopes 为准', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.openEdit({ id: 'S1', schemeCode: 'KPI_A', schemeName: '方案A' }, false);
    await flushPromises();

    expect(getKpiSchemeDetail).toHaveBeenCalled();
    expect(wrapper.vm.dlg.scheme.empTagScopes).toEqual([11, 99]);
  });

  it('已选标签被删除：给出失效提示，只列出失效的那个标签', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.openEdit({ id: 'S1', schemeCode: 'KPI_A', schemeName: '方案A' }, false);
    await flushPromises();

    // 标签库只有 11/22；详情选了 11 与 99 → 99 已失效
    expect(wrapper.vm.invalidTagIds).toEqual([99]);
    expect(wrapper.html()).toContain('已被删除');
  });

  it('标签全部有效时不显示失效提示', async () => {
    getKpiSchemeDetail.mockResolvedValueOnce({ items: [], empTagScopes: [11, 22] });
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.openEdit({ id: 'S2', schemeCode: 'KPI_B', schemeName: '方案B' }, false);
    await flushPromises();

    expect(wrapper.vm.invalidTagIds).toEqual([]);
    expect(wrapper.html()).not.toContain('已被删除');
  });

  it('提交新增：请求体带 empTagScopes 标签 ID 数组', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.openCreate();
    await flushPromises();
    wrapper.vm.dlg.scheme.schemeCode = 'KPI_NEW';
    wrapper.vm.dlg.scheme.schemeName = '新方案';
    wrapper.vm.dlg.scheme.empTagScopes = [11, 22];
    // openCreate 已预置一个空指标行；onSave 要求每行都填完整，故填充首行而非追加
    Object.assign(wrapper.vm.dlg.items[0], {
      metricCode: 'M_0001', baseDim: 'EMP', exprType: 'FORMULA',
      formula: 'actual / target * weight', weight: 100, maxScore: 120, minScore: 0
    });

    await wrapper.vm.onSave('DRAFT');
    await flushPromises();

    expect(createKpiScheme).toHaveBeenCalled();
    expect(createKpiScheme.mock.calls[0][0]).toMatchObject({ empTagScopes: [11, 22] });
  });

  it('发布方案仍先保存原标签范围，再按既有发布契约调用接口', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.vm.openCreate();
    await flushPromises();
    wrapper.vm.dlg.scheme.schemeCode = 'KPI_PUBLISH';
    wrapper.vm.dlg.scheme.schemeName = '发布方案';
    wrapper.vm.dlg.scheme.empTagScopes = [11, 22];
    Object.assign(wrapper.vm.dlg.items[0], {
      metricCode: 'M_0001', baseDim: 'EMP', exprType: 'FORMULA',
      formula: 'actual / target * weight', weight: 100, maxScore: 120, minScore: 0
    });

    await wrapper.vm.onSave('ACTIVE');
    await flushPromises();

    expect(createKpiScheme).toHaveBeenCalledWith(expect.objectContaining({ empTagScopes: [11, 22] }));
    expect(publishKpiScheme).toHaveBeenCalledWith('S1', '前端发布');
    wrapper.unmount();
  });

  it('导入仍向 KPI_SCHEME 通道提交文件，不复用指标导入契约', async () => {
    uploadImportFile.mockResolvedValueOnce({ totalRows: 2, errorRows: 0 });
    const wrapper = mountPage();
    await flushPromises();
    const file = { name: 'KPI方案.xlsx' };

    await wrapper.vm.onImportKpiFile({ target: { files: [file] } });

    expect(uploadImportFile).toHaveBeenCalledWith('KPI_SCHEME', file);
    wrapper.unmount();
  });
});
