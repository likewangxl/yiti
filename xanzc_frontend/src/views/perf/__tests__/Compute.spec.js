// @vitest-environment happy-dom
// 考核计算页「批量触发计算」回归测试。
// 需求：计算记录列表增加勾选列，顶部「触发计算」按对勾选行批量重算；
// 弹窗不再提供 KPI方案下拉，方案取自勾选行（去重），仅需确认数据日期与触发原因。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';

// 三行日志：S1 出现两次，用于验证按方案去重后只触发 2 次计算
const { LOG_ROWS, SCHEMES } = vi.hoisted(() => ({
  LOG_ROWS: [
    { dataDate: '2026-07-15', schemeCode: 'S1', triggerType: 'MANUAL', result: 'SUCCESS' },
    { dataDate: '2026-07-15', schemeCode: 'S2', triggerType: 'AUTO', result: 'SUCCESS' },
    { dataDate: '2026-07-15', schemeCode: 'S1', triggerType: 'MANUAL', result: 'FAILED' }
  ],
  SCHEMES: [
    { schemeCode: 'S1', schemeName: '方案一', status: 'ACTIVE' },
    { schemeCode: 'S2', schemeName: '方案二', status: 'ACTIVE' }
  ]
}));

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));
vi.mock('@/api/perf', () => ({
  listComputeBatches: vi.fn().mockResolvedValue([]),
  triggerCompute: vi.fn(),
  getComputeBatch: vi.fn(),
  getKpiScoreStats: vi.fn().mockResolvedValue({}),
  listKpiCalcLogs: vi.fn().mockResolvedValue({ records: LOG_ROWS, total: LOG_ROWS.length }),
  listKpiRules: vi.fn().mockResolvedValue(SCHEMES),
  calcKpiScore: vi.fn().mockResolvedValue('OK'),
  getLatestKpiCalcLogDate: vi.fn().mockResolvedValue('2026-07-15')
}));

import { ElMessage } from 'element-plus';
import { calcKpiScore } from '@/api/perf';
import Compute from '../Compute.vue';

// el-* 打桩：el-table 渲染列插槽并提供「全选」按钮模拟 selection-change；
// el-table-column 以 data-type 暴露 type 便于断言勾选列；el-input 支持 v-model。
const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: passthrough('PageTitle'),
  // 必须声明 emits，否则父级 @click 经 attrs 透传到原生 button 后与 $emit 叠加，处理器被调两次
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-dialog': { name: 'ElDialog', template: '<div><slot /><slot name="footer" /></div>' },
  'el-drawer': empty('ElDrawer'),
  'el-alert': empty('ElAlert'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput',
    props: ['modelValue'],
    template: '<input class="inp-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': empty('ElSelect'),
  'el-option': empty('ElOption'),
  'el-tag': passthrough('ElTag'),
  'el-tooltip': passthrough('ElTooltip'),
  'el-table': {
    name: 'ElTable',
    props: ['data'],
    template: '<div class="tbl-stub"><slot /><button class="sel-all" @click="$emit(\'selection-change\', data)">全选</button></div>'
  },
  'el-table-column': {
    name: 'ElTableColumn',
    props: ['type', 'label'],
    template: '<div class="col-stub" :data-type="type || \'\'" :data-label="label || \'\'" />'
  },
  'el-date-picker': empty('ElDatePicker'),
  'el-pagination': empty('ElPagination')
};

let wrapper;
afterEach(() => { wrapper?.unmount(); });
beforeEach(() => { vi.clearAllMocks(); });

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function findBtn(text) {
  return wrapper.findAll('button').find((b) => b.text().includes(text));
}

async function mountPage() {
  wrapper = mount(Compute, { global: { stubs } });
  await settle();
}

describe('Compute.vue 批量触发计算', () => {
  it('计算记录列表包含勾选列（type=selection）', async () => {
    await mountPage();
    expect(wrapper.find('.tbl-stub [data-type="selection"]').exists(), '列表应有勾选列').toBe(true);
  });

  it('未勾选任何行点击「触发计算」→ 提示先勾选，不触发计算', async () => {
    await mountPage();
    await findBtn('触发计算').trigger('click');
    await settle();
    expect(ElMessage.warning).toHaveBeenCalled();
    expect(calcKpiScore).not.toHaveBeenCalled();
  });

  it('勾选 3 行（含重复方案）→ 弹窗按方案去重展示且无方案下拉 → 确认后逐个触发计算', async () => {
    await mountPage();
    // 模拟全选 3 行（S1、S2、S1）
    await wrapper.find('.tbl-stub .sel-all').trigger('click');
    await findBtn('触发计算').trigger('click');
    await settle();

    // 弹窗展示去重后的 2 个方案，不再出现方案下拉占位文案
    const tags = wrapper.findAll('.sel-scheme');
    expect(tags.length, '弹窗应展示去重后的 2 个方案').toBe(2);
    expect(wrapper.html()).not.toContain('请选择 KPI 方案');

    // 填触发原因 → 确认执行
    await wrapper.find('.inp-stub').setValue('批量补算');
    await findBtn('确认执行').trigger('click');
    await settle();

    expect(calcKpiScore).toHaveBeenCalledTimes(2);
    // 数据日期取自勾选行（三行同为 2026-07-15）
    expect(calcKpiScore).toHaveBeenCalledWith({ dataDate: '2026-07-15', schemeCode: 'S1', reason: '批量补算' });
    expect(calcKpiScore).toHaveBeenCalledWith({ dataDate: '2026-07-15', schemeCode: 'S2', reason: '批量补算' });
    expect(ElMessage.success).toHaveBeenCalled();
  });

  it('勾选后未填触发原因点确认 → 提示且不触发计算', async () => {
    await mountPage();
    await wrapper.find('.tbl-stub .sel-all').trigger('click');
    await findBtn('触发计算').trigger('click');
    await settle();
    await findBtn('确认执行').trigger('click');
    await settle();
    expect(ElMessage.warning).toHaveBeenCalled();
    expect(calcKpiScore).not.toHaveBeenCalled();
  });

  it('部分方案计算失败 → 汇总提示错误且全部方案都已尝试', async () => {
    calcKpiScore.mockImplementation(({ schemeCode }) =>
      schemeCode === 'S2' ? Promise.reject(new Error('boom')) : Promise.resolve('OK'));
    await mountPage();
    await wrapper.find('.tbl-stub .sel-all').trigger('click');
    await findBtn('触发计算').trigger('click');
    await settle();
    await wrapper.find('.inp-stub').setValue('批量补算');
    await findBtn('确认执行').trigger('click');
    await settle();
    expect(calcKpiScore).toHaveBeenCalledTimes(2);
    expect(ElMessage.error).toHaveBeenCalled();
  });
});
