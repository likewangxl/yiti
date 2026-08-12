// @vitest-environment happy-dom
// 指标库「编辑」首开 Groovy 表达式渲染不出来回归测试。
// 根因：watch(() => dlg.form.metricLevel) 只要层级变化就清空 dlg.form.exprText；
// dlg.form.metricLevel 初值=1，首次编辑二级(EXPR)指标时 openEdit 把层级 1→2，
// 该 watch 作为异步(pre-flush)回调在 openEdit 同步赋值之后触发，把刚载入的 exprText
// 清成空串，renderExprEditor 因此渲染空白；第二次点编辑层级已=2、watch 不触发才正常。
// 本用例：首次编辑二级指标后，表达式编辑器必须已渲染出表达式内容（含引用指标编号）。
import { describe, it, expect, vi, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';

// vi.mock 工厂被提升到文件顶部，故 mock 数据需用 vi.hoisted 一并提升后再引用
const { L1, L2 } = vi.hoisted(() => ({
  L1: {
    metricCode: 'M_L1', metricName: '一级规模', baseDim: 'EMP', metricLevel: 1,
    status: 'ACTIVE', calcLogicType: 'SQL', calcMode: 'AUTO', calcFreq: 'DAY',
    metricCategory: '规模类', sqlText: 'select 1'
  },
  L2: {
    metricCode: 'M_L2', metricName: '二级复合', baseDim: 'EMP', metricLevel: 2,
    status: 'ACTIVE', calcLogicType: 'EXPR', calcMode: 'AUTO', calcFreq: 'DAY',
    metricCategory: '规模类', exprText: 'M_L1 * 2'
  }
}));

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/api/system', () => ({ listAuditLogs: vi.fn().mockResolvedValue([]) }));
vi.mock('@/api/perf', () => ({
  // L2 放首位 → reload() 默认 onPick(allMetrics[0]) 选中二级指标作为详情
  listMetrics: vi.fn().mockResolvedValue([L2, L1]),
  listMetricCategories: vi.fn().mockResolvedValue([]),
  getMetricDetail: vi.fn((code) => Promise.resolve(code === 'M_L2' ? L2 : L1)),
  createMetric: vi.fn(), updateMetric: vi.fn(), deleteMetric: vi.fn(),
  changeMetricStatus: vi.fn(), trialRunMetric: vi.fn(), executeMetric: vi.fn(),
  uploadImportFile: vi.fn()
}));

import Metrics from '../Metrics.vue';
import { executeMetric, listMetrics, trialRunMetric } from '@/api/perf';

// el-* 统一打桩：渲染默认插槽的用透传 div；带作用域插槽(tree/table)的渲染空 div 避免解构报错；
// el-button 渲染原生 button 并把原生点击转成组件 click 事件，供详情区「编辑」按钮触发 openEdit。
const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: {
    name: 'PageTitle',
    inheritAttrs: false,
    template: '<h1 class="page-title" v-bind="$attrs">指标库<slot /></h1>'
  },
  'el-button': {
    name: 'ElButton',
    props: { disabled: Boolean, loading: Boolean },
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-dialog': passthrough('ElDialog'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput',
    inheritAttrs: false,
    props: { modelValue: [String, Number] },
    emits: ['update:modelValue'],
    template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': passthrough('ElSelect'),
  'el-option': passthrough('ElOption'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-radio': passthrough('ElRadio'),
  'el-tag': passthrough('ElTag'),
  'el-tree': { name: 'ElTree', inheritAttrs: false, template: '<div class="tree-stub" v-bind="$attrs" />' },
  'el-table': { name: 'ElTable', inheritAttrs: false, template: '<div class="table-stub" v-bind="$attrs"><slot /></div>' },
  'el-table-column': empty('ElTableColumn'),
  'el-date-picker': empty('ElDatePicker')
};
const globalOptions = {
  stubs,
  directives: { loading: { mounted() {}, updated() {} } }
};

let wrapper;
afterEach(() => { wrapper?.unmount(); });

async function settle() {
  await flushPromises();
  await nextTick();
  await nextTick();
  await flushPromises();
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

describe('Metrics.vue 编辑二级指标 Groovy 表达式渲染', () => {
  it('以 bp-crud 双栏工作区提供指标树、详情区和可读的筛选语义', async () => {
    wrapper = mount(Metrics, { global: globalOptions });
    await settle();

    expect(wrapper.find('main.bp-crud.metrics-page[aria-labelledby="metrics-page-title"]').exists()).toBe(true);
    expect(wrapper.get('h1#metrics-page-title').text()).toContain('指标库');
    expect(wrapper.find('section[aria-label="指标层级"]').exists()).toBe(true);
    expect(wrapper.find('form[aria-label="指标树筛选"]').exists()).toBe(true);
    expect(wrapper.find('input[aria-label="按指标名称、编号或分类搜索"]').exists()).toBe(true);
    expect(wrapper.find('section[aria-label="指标详情"][aria-describedby="metrics-detail-state"]').exists()).toBe(true);
    expect(wrapper.get('#metrics-tree-state').text()).toContain('共 2 项指标');
  });

  it('加载指标库时向树和详情面板公开 aria-busy 状态', async () => {
    const pending = deferred();
    listMetrics.mockImplementationOnce(() => pending.promise);
    wrapper = mount(Metrics, { global: globalOptions });
    await nextTick();

    const treePanel = wrapper.get('section[aria-label="指标层级"]');
    expect(treePanel.attributes('aria-busy')).toBe('true');
    expect(wrapper.get('#metrics-tree-state').text()).toContain('指标库加载中');

    pending.resolve([L2, L1]);
    await settle();

    expect(treePanel.attributes('aria-busy')).toBe('false');
    expect(wrapper.get('#metrics-tree-state').text()).toContain('共 2 项指标');
  });

  it('首次点击「编辑」二级(EXPR)指标即渲染出表达式（不被 metricLevel watch 清空）', async () => {
    wrapper = mount(Metrics, { global: globalOptions });
    await settle(); // onMounted->reload() 拉列表并默认选中 M_L2 作为详情

    // 详情动作区的「编辑」按钮 → openEdit(detail=二级指标)
    const editBtn = wrapper.find('.acts').findAll('button').find((b) => b.text() === '编辑');
    expect(editBtn, '详情区应有「编辑」按钮').toBeTruthy();
    await editBtn.trigger('click');
    await settle();

    const editor = wrapper.find('.expr-editor');
    expect(editor.exists(), '弹框内 Groovy 表达式编辑器应已挂载').toBe(true);
    // 修复前：exprText 被 watch 清空，编辑器为空；修复后：还原出表达式（引用指标编号 M_L1）
    expect(editor.text().trim()).not.toBe('');
    expect(editor.text()).toContain('M_L1');
  });

  it('保留 SQL 宏插入与试运行请求契约', async () => {
    trialRunMetric.mockResolvedValueOnce({
      status: 'SUCCESS', totalRows: 1, executionMillis: 3, sampleRows: [{ metric_value: 1 }]
    });
    wrapper = mount(Metrics, { global: globalOptions });
    await settle();

    wrapper.vm.openEdit(L1);
    await settle();
    const textarea = { selectionStart: 7, selectionEnd: 7, focus: vi.fn(), setSelectionRange: vi.fn() };
    wrapper.vm.sqlInputRef = { textarea };
    wrapper.vm.dlg.form.sqlText = 'SELECT ';
    wrapper.vm.insertMacro(':dataDate');
    await nextTick();

    expect(wrapper.vm.dlg.form.sqlText).toBe('SELECT :dataDate');

    wrapper.vm.dlg.trialDate = '2026-08-11';
    wrapper.vm.dlg.trialSubject = 'E100';
    await wrapper.vm.onTrialFromDialog();

    expect(trialRunMetric).toHaveBeenCalledWith('M_L1', expect.objectContaining({
      dataDate: '2026-08-11',
      calcLogicType: 'SQL',
      baseDim: 'EMP',
      sqlText: 'SELECT :dataDate',
      params: { objectId: 'E100' }
    }));
  });

  it('立即执行仍携带异步执行、日期和高危原因', async () => {
    executeMetric.mockResolvedValueOnce({ ok: true });
    wrapper = mount(Metrics, { global: globalOptions });
    await settle();

    wrapper.vm.onExecute();
    wrapper.vm.execDlg.dataDate = '2026-08-11';
    wrapper.vm.execDlg.allocDate = '2026-08-10';
    wrapper.vm.execDlg.reason = '补跑日终指标';
    await wrapper.vm.confirmExecute();

    expect(executeMetric).toHaveBeenCalledWith('M_L2', {
      dataDate: '2026-08-11',
      allocDate: '2026-08-10',
      cascade: true,
      async: true,
      reason: '补跑日终指标'
    });
  });
});
