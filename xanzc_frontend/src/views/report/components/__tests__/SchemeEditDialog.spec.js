// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const updateSavedQuery = vi.fn().mockResolvedValue({ ok: true });
vi.mock('@/api/report', () => ({ updateSavedQuery: (...a) => updateSavedQuery(...a) }));
const confirmMock = vi.fn().mockResolvedValue(true);
vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: (...a) => confirmMock(...a) }
}));

import SchemeEditDialog from '../SchemeEditDialog.vue';

const stubs = {
  'el-dialog': { template: '<div><slot/><slot name="footer"/></div>' },
  'el-form': true, 'el-form-item': true, 'el-input': true, 'el-radio-group': true, 'el-radio-button': true,
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>' }, 'el-tag': { template: '<span><slot/></span>' },
  MetricPicker: true, SubjectPicker: true
};
const scheme = { id: 'SQ1', name: '方案一', dim: 'EMP', metrics: ['M1'], subjects: [{ id: 'E1', name: '张三', org: '' }], version: 2 };

describe('SchemeEditDialog.vue', () => {
  beforeEach(() => { updateSavedQuery.mockClear(); confirmMock.mockClear(); });

  it('预填 scheme 到表单', () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme }, global: { stubs } });
    expect(w.vm.form.name).toBe('方案一');
    expect(w.vm.form.dim).toBe('EMP');
    expect(w.vm.form.metrics).toEqual(['M1']);
    expect(w.vm.form.subjects).toEqual([{ id: 'E1', name: '张三', org: '' }]);
  });

  it('保存下发 expectedVersion 并 emit saved', async () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme }, global: { stubs } });
    await w.vm.save();
    expect(updateSavedQuery).toHaveBeenCalledWith('SQ1', {
      name: '方案一', dim: 'EMP', metrics: ['M1'],
      subjects: [{ id: 'E1', name: '张三', org: '' }], expectedVersion: 2
    });
    expect(w.emitted('saved')).toBeTruthy();
  });

  it('指标为空时拦截,不调用后端', async () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme: { ...scheme, metrics: [] } }, global: { stubs } });
    await w.vm.save();
    expect(updateSavedQuery).not.toHaveBeenCalled();
  });

  it('对象为空时放行(编辑允许对象为空)', async () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme: { ...scheme, subjects: [] } }, global: { stubs } });
    await w.vm.save();
    expect(updateSavedQuery).toHaveBeenCalledTimes(1);
    expect(updateSavedQuery.mock.calls[0][1].subjects).toEqual([]);
  });

  it('切换维度确认后清空指标和对象', async () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme }, global: { stubs } });
    await w.vm.onDimChange('ORG');
    expect(confirmMock).toHaveBeenCalled();
    expect(w.vm.form.dim).toBe('ORG');
    expect(w.vm.form.metrics).toEqual([]);
    expect(w.vm.form.subjects).toEqual([]);
  });
});
