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

// el-* 统一打桩：渲染默认插槽的用透传 div；带作用域插槽(tree/table)的渲染空 div 避免解构报错；
// el-button 渲染原生 button 并把原生点击转成组件 click 事件，供详情区「编辑」按钮触发 openEdit。
const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  'el-button': { name: 'ElButton', template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-dialog': passthrough('ElDialog'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': passthrough('ElInput'),
  'el-select': passthrough('ElSelect'),
  'el-option': passthrough('ElOption'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-radio': passthrough('ElRadio'),
  'el-tag': passthrough('ElTag'),
  'el-tree': empty('ElTree'),
  'el-table': empty('ElTable'),
  'el-table-column': empty('ElTableColumn'),
  'el-date-picker': empty('ElDatePicker')
};

let wrapper;
afterEach(() => { wrapper?.unmount(); });

async function settle() {
  await flushPromises();
  await nextTick();
  await nextTick();
  await flushPromises();
}

describe('Metrics.vue 编辑二级指标 Groovy 表达式渲染', () => {
  it('首次点击「编辑」二级(EXPR)指标即渲染出表达式（不被 metricLevel watch 清空）', async () => {
    wrapper = mount(Metrics, { global: { stubs } });
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
});
