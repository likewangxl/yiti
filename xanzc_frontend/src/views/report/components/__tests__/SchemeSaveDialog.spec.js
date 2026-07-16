// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const saveQuery = vi.fn().mockResolvedValue({ id: 'SQNEW' });
vi.mock('@/api/report', () => ({ saveQuery: (...a) => saveQuery(...a) }));
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() } }));

import SchemeSaveDialog from '../SchemeSaveDialog.vue';

const stubs = {
  'el-dialog': { template: '<div><slot/><slot name="footer"/></div>' },
  'el-form': true, 'el-form-item': true, 'el-input': true, 'el-alert': true,
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>' }
};

function mountWith(payload) {
  return mount(SchemeSaveDialog, {
    props: { visible: true, context: { dim: payload.dim, metrics: payload.metrics.length, objects: payload.subjects.length, payload } },
    global: { stubs }
  });
}

describe('SchemeSaveDialog 保存校验', () => {
  beforeEach(() => saveQuery.mockClear());

  it('对象为空也能保存(已去掉对象≥1校验)', async () => {
    const w = mountWith({ dim: 'EMP', metrics: ['M1'], subjects: [] });
    await w.vm.save();
    expect(saveQuery).toHaveBeenCalledTimes(1);
    expect(saveQuery.mock.calls[0][0].subjects).toEqual([]);
  });

  it('指标为空仍被拦截(保留指标≥1校验)', async () => {
    const w = mountWith({ dim: 'EMP', metrics: [], subjects: [{ id: 'E1', name: '张三', org: '' }] });
    await w.vm.save();
    expect(saveQuery).not.toHaveBeenCalled();
  });
});
