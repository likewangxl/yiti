// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  getLoanApplication: vi.fn(),
  listLoanAttachments: vi.fn(),
  getProcessHistory: vi.fn(),
  getProcessNodes: vi.fn()
}));
vi.mock('@/api/businessApplication', () => ({
  getLoanApplication: api.getLoanApplication,
  listLoanAttachments: api.listLoanAttachments
}));
vi.mock('@/api/workflow', () => ({
  getProcessHistory: api.getProcessHistory,
  getProcessNodes: api.getProcessNodes
}));
vi.mock('@/composables/useDict', () => ({
  useDict: () => ({ options: [], labelOf: value => value, loading: false })
}));

import LoanDetail from '../LoanDetail.vue';

const stubs = {
  'el-drawer': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { props: ['label'], template: '<div class="description"><b>{{ label }}</b><slot /></div>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-empty': { template: '<div>暂无数据</div>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': { template: '<button><slot /></button>' },
  LeadAttachmentPreview: { props: ['attachments'], template: '<div class="attachments">{{ attachments.map(item => item.fileName).join(",") }}</div>' }
};

describe('资产立项详情', () => {
  it('并行加载贷款详情、流程节点、流程历史和 LOAN 附件', async () => {
    api.getLoanApplication.mockResolvedValue({
      id: 'L-1', custId: 'C-1', custInfo: { custName: '浦爱科技' }, status: 'IN_APPROVAL', processInstanceId: 'PI-1'
    });
    api.listLoanAttachments.mockResolvedValue([{ id: 'F-1', fileName: '尽调报告.pdf', fileType: 'application/pdf' }]);
    api.getProcessHistory.mockResolvedValue([{ action: 'APPROVE', opinion: '同意', operateTime: '2026-08-24T09:30:00' }]);
    api.getProcessNodes.mockResolvedValue({ nodes: [{ nodeName: '公司业务部审核', status: 'COMPLETED', endTime: '2026-08-24T09:20:00' }] });

    const wrapper = mount(LoanDetail, { props: { modelValue: true, loanId: 'L-1' }, global: { stubs, directives: { loading: {} } } });
    await flushPromises();

    expect(api.getLoanApplication).toHaveBeenCalledWith('L-1');
    expect(api.listLoanAttachments).toHaveBeenCalledWith('L-1');
    expect(api.getProcessHistory).toHaveBeenCalledWith('PI-1');
    expect(api.getProcessNodes).toHaveBeenCalledWith('PI-1');
    expect(wrapper.text()).toContain('浦爱科技');
    expect(wrapper.find('.attachments').text()).toContain('尽调报告.pdf');
    expect(wrapper.vm.processNodes[0]).toEqual(expect.objectContaining({ finishTime: '2026-08-24 09:20:00' }));
    expect(wrapper.vm.history[0]).toEqual(expect.objectContaining({ comment: '同意', operateTime: '2026-08-24 09:30:00' }));
  });
});
