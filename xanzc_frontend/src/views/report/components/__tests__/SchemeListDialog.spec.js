// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const getSavedQuery = vi.fn().mockResolvedValue({ id: 'SQ1', name: 'A', dim: 'EMP', metrics: ['M1'], subjects: [], version: 1 });
vi.mock('@/api/report', () => ({
  listSavedQueries: vi.fn().mockResolvedValue([{ id: 'SQ1', name: 'A', dim: 'EMP', updatedTime: '2026-07-16T10:00:00' }]),
  deleteSavedQuery: vi.fn(),
  getSavedQuery: (...a) => getSavedQuery(...a)
}));
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }));

import SchemeListDialog from '../SchemeListDialog.vue';

const stubs = {
  'el-dialog': { template: '<div><slot/><slot name="footer"/></div>' },
  'el-table': true, 'el-table-column': true,
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>' }, 'el-divider': true,
  SchemeEditDialog: true
};

describe('SchemeListDialog 编辑', () => {
  beforeEach(() => getSavedQuery.mockClear());

  it('点编辑先取详情再打开编辑弹框', async () => {
    const w = mount(SchemeListDialog, { props: { visible: true }, global: { stubs } });
    await w.vm.openEdit({ id: 'SQ1', name: 'A' });
    expect(getSavedQuery).toHaveBeenCalledWith('SQ1');
    expect(w.vm.editVisible).toBe(true);
    expect(w.vm.editScheme.id).toBe('SQ1');
    expect(w.vm.editScheme.version).toBe(1);
  });
});
