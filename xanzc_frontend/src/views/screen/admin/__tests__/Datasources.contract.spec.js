// @vitest-environment happy-dom
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listScreenDatasources: vi.fn(), saveScreenDatasource: vi.fn(), updateScreenDatasource: vi.fn(),
  deleteScreenDatasource: vi.fn(), tryRunScreenDatasource: vi.fn(), probeScreenDatasourceColumns: vi.fn(),
  listKpiSchemes: vi.fn(), listOrgGroups: vi.fn()
}));

vi.mock('@/api/screen', () => api);
vi.mock('@/api/metrics', () => ({ listMetrics: vi.fn().mockResolvedValue([]) }));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}));

import Datasources from '../Datasources.vue';

const frozenRow = {
  id: 72, dsCode: 'DS_ORG', dsName: '机构经营宽表', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE', bizLine: 'RETAIL',
  configJson: JSON.stringify({ schemaVersion: 2, scopeMode: 'NAMED_GROUP', table: 'ORG_INDEX_RESULT', metrics: [{ metricCode: 'M1' }] }),
  timeParamJson: '[]', status: 'ACTIVE', remark: '原始备注',
  draftReferenceScreenCodes: ['SCR_DRAFT_A', 'SCR_DRAFT_B'],
  publishedReferenceScreenCodes: ['SCR_LIVE_A', 'SCR_ARCHIVE_B']
};

const passthrough = { template: '<div><slot /></div>' };
const stubs = {
  PageTitle: passthrough,
  'el-button': { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
  'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': passthrough,
  'el-option': passthrough,
  'el-radio-group': passthrough,
  'el-radio-button': { template: '<button v-bind="$attrs"><slot /></button>' },
  'el-radio': passthrough,
  'el-switch': true,
  'el-form': passthrough,
  'el-form-item': passthrough,
  'el-table': passthrough,
  // 表格列的 scoped slot 依赖 Element Plus 提供 row；本测试聚焦弹框/动作状态，不在 stub 中渲染列内容。
  'el-table-column': { template: '<div />' },
  'el-dropdown': { emits: ['command'], template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': passthrough,
  'el-dropdown-item': { template: '<button><slot /></button>' },
  'el-tag': passthrough,
  'el-alert': { props: ['title', 'description'], template: '<div v-bind="$attrs">{{ title }} {{ description }}</div>' },
  'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' }
};

async function mountPage() {
  api.listScreenDatasources.mockResolvedValue([frozenRow]);
  api.listKpiSchemes.mockResolvedValue([]);
  api.listOrgGroups.mockResolvedValue([]);
  const wrapper = mount(Datasources, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
  await flushPromises();
  await flushPromises();
  return wrapper;
}

describe('Datasources.vue 最终契约', () => {
  beforeEach(() => vi.clearAllMocks());

  it('完整发布/归档引用允许编辑语义字段并显示影响已发布大屏的风险提示，但仍不允许删除', async () => {
    const wrapper = await mountPage();
    wrapper.vm.openEdit(frozenRow);
    await wrapper.vm.$nextTick();

    expect(wrapper.vm.publishedReferenced).toBe(true);
    expect(wrapper.vm.referenceState(frozenRow)).toMatchObject({
      draftCodes: ['SCR_DRAFT_A', 'SCR_DRAFT_B'],
      publishedCodes: ['SCR_LIVE_A', 'SCR_ARCHIVE_B'], publishedReferenced: true, deleteBlocked: true
    });
    expect(wrapper.find('[data-testid="datasource-freeze-notice"]').text())
      .toContain('直接影响引用该数据源的已发布大屏');
    expect(wrapper.vm.dlg.remark).toBe('原始备注');

    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/Datasources.vue'), 'utf8');
    expect(source).not.toContain(':disabled="publishedReferenced"');
    expect(source).not.toContain(':disabled="semanticFrozen"');
    expect(source).toContain('新建副本');
  });

  it('NAMED_GROUP 时 CUSTOM_SQL 在编辑器中不可选，LEGACY_CONTEXT 不受此限制', async () => {
    const wrapper = await mountPage();
    wrapper.vm.openCreate();
    wrapper.vm.dlg.m.scopeMode = 'NAMED_GROUP';
    await wrapper.vm.$nextTick();
    const customSql = wrapper.findAll('button').find(button => button.text() === '自定义 SQL');
    const kpiResult = wrapper.findAll('button').find(button => button.text() === 'KPI结果(引导式)');
    expect(customSql.attributes('disabled')).toBeDefined();
    expect(kpiResult.attributes('disabled')).toBeDefined();
    wrapper.vm.dlg.m.scopeMode = 'LEGACY_CONTEXT';
    await wrapper.vm.$nextTick();
    expect(customSql.attributes('disabled')).toBeUndefined();
  });

  it('已保存 NAMED_GROUP 数据源的列探测单独命中 probe-columns，并携带原因和测试组', async () => {
    const wrapper = await mountPage();
    api.probeScreenDatasourceColumns.mockResolvedValue({ columns: ['complete_rate'], rows: [] });
    wrapper.vm.openProbeColumns(frozenRow);
    wrapper.vm.tr.reason = '确认仪表盘列';
    wrapper.vm.tr.testOrgGroupCode = 'G_REPORT';
    await wrapper.vm.runTry();

    expect(api.probeScreenDatasourceColumns).toHaveBeenCalledWith(72, {
      period: 'LATEST', dateFrom: null, dateTo: null,
      contextParams: { orgCode: null, empId: null }, testOrgGroupCode: 'G_REPORT', reason: '确认仪表盘列'
    });
    expect(api.tryRunScreenDatasource).not.toHaveBeenCalled();
  });

  it('所有数据源保存均强制原因，编辑显式保留 ACTIVE/DISABLED 状态而不静默复原', async () => {
    const wrapper = await mountPage();
    const editable = {
      ...frozenRow, id: 73, draftReferenceScreenCodes: [], publishedReferenceScreenCodes: [], status: 'ACTIVE'
    };
    wrapper.vm.openEdit(editable);
    await wrapper.vm.$nextTick();

    expect(wrapper.vm.dlg.status).toBe('ACTIVE');
    await wrapper.vm.onSave();
    expect(api.updateScreenDatasource).not.toHaveBeenCalled();

    wrapper.vm.dlg.reason = '修正展示备注';
    api.updateScreenDatasource.mockResolvedValueOnce();
    await wrapper.vm.onSave();
    expect(api.updateScreenDatasource).toHaveBeenCalledWith(73, expect.objectContaining({
      status: 'ACTIVE', reason: '修正展示备注'
    }));
  });

  it('删除未引用数据源也必须先打开独立原因对话框，不能由确认框直接发删除请求', async () => {
    const wrapper = await mountPage();
    const deletable = {
      ...frozenRow, id: 74, dsName: '待删除数据源', draftReferenceScreenCodes: [], publishedReferenceScreenCodes: []
    };
    await wrapper.vm.onDelete(deletable);
    await wrapper.vm.$nextTick();

    expect(wrapper.vm.deleteDialog).toMatchObject({ show: true, row: deletable, reason: '' });
    expect(wrapper.find('[data-testid="datasource-delete-reason"]').exists()).toBe(true);
    expect(api.deleteScreenDatasource).not.toHaveBeenCalled();
  });

  it('接入普通后台基线，筛选可重置且低频动作收纳在更多菜单', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/Datasources.vue'), 'utf8');

    expect(source).toMatch(/<main\b[^>]*class="[^\"]*\bbp-crud\b[^\"]*"/);
    expect(source).toMatch(/@click="resetFilters"[^>]*>重置/);
    expect(source).toMatch(/<el-dropdown[\s\S]*?<el-dropdown-menu/);
    expect(source).toMatch(/新建副本/);
    expect(source).toMatch(/确认删除/);
  });

  it('Element Plus 单选项使用 value 契约并保持原模型字符串', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/Datasources.vue'), 'utf8');
    const radios = [...source.matchAll(/<el-radio(?:-button)?(?=\s|>)[^>]*>/g)].map((match) => match[0]);

    expect(radios.length).toBeGreaterThan(0);
    expect(radios.every((radio) => /\bvalue="[^"]+"/.test(radio))).toBe(true);
    expect(radios.every((radio) => !/\blabel="/.test(radio))).toBe(true);
    expect(source).toContain('<el-radio value="ACTIVE">启用 ACTIVE</el-radio>');
    expect(source).toContain('<el-radio-button value="WIDE_TABLE">');
  });

  it('度量字段提供可清空的金额量级预设，并在选中时锁定单位/小数位', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/Datasources.vue'), 'utf8');

    expect(source).toContain('金额量级');
    expect(source).toContain('amountScale');
    expect(source).toMatch(/v-if="row\.role === 'METRIC'"[\s\S]*?金额量级/);
    expect(source).toContain('clearable');
    expect(source).toMatch(/amountScale[\s\S]*?disabled/);
    expect(source).toContain('AMOUNT_SCALE_OPTIONS');
    expect(source).toContain('清空');
  });

  it('金额量级选择只转换组件/预览展示值，不修改接口原始数据', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/admin/Datasources.vue'), 'utf8');
    expect(source).toContain('仅影响组件/试跑预览');
    expect(source).toContain('不修改接口原始 rows、SQL 或数据库');
  });

  it('选中金额量级时显示预设并保留自定义值，清空后恢复编辑', async () => {
    const wrapper = await mountPage();
    const row = { role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN', unit: '自定义单位', decimals: '4' };

    expect(wrapper.vm.hasAmountScale(row)).toBe(true);
    expect(wrapper.vm.fieldMetaUnitDisplay(row)).toBe('万元');
    expect(wrapper.vm.fieldMetaDecimalsDisplay(row)).toBe('2');
    wrapper.vm.updateFieldMetaUnit(row, '不应写入');
    wrapper.vm.updateFieldMetaDecimals(row, '9');
    expect(row).toMatchObject({ unit: '自定义单位', decimals: '4' });

    row.amountScale = '';
    expect(wrapper.vm.hasAmountScale(row)).toBe(false);
    expect(wrapper.vm.fieldMetaUnitDisplay(row)).toBe('自定义单位');
    expect(wrapper.vm.fieldMetaDecimalsDisplay(row)).toBe('4');
    wrapper.vm.updateFieldMetaUnit(row, '元/户');
    wrapper.vm.updateFieldMetaDecimals(row, '1');
    expect(row).toMatchObject({ unit: '元/户', decimals: '1' });
  });

  it('金额量级行切换为 DIM 后不再显示预设值，单位和小数位恢复为自定义值并可编辑', async () => {
    const wrapper = await mountPage();
    const row = { role: 'METRIC', amountScale: 'TEN_THOUSAND_YUAN', unit: '自定义单位', decimals: '4' };

    expect(wrapper.vm.fieldMetaUnitDisplay(row)).toBe('万元');
    expect(wrapper.vm.fieldMetaDecimalsDisplay(row)).toBe('2');
    row.role = 'DIM';
    expect(wrapper.vm.hasAmountScale(row)).toBe(false);
    expect(wrapper.vm.fieldMetaUnitDisplay(row)).toBe('自定义单位');
    expect(wrapper.vm.fieldMetaDecimalsDisplay(row)).toBe('4');
    wrapper.vm.updateFieldMetaUnit(row, '维度单位');
    wrapper.vm.updateFieldMetaDecimals(row, '1');
    expect(row).toMatchObject({ unit: '维度单位', decimals: '1' });
  });
});
