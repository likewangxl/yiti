import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ records: [], total: 0 }) }));

import { call } from '../http';
import {
  approveLead,
  approveTagCustomers,
  confirmLeadImportBatch,
  createCustomerTagImportBatch,
  createMarketingLead,
  getLeadImportBatchDetails,
  listLeadApprovalHistory,
  listLeadApprovalPending,
  listLeadImportBatches,
  listManualLeads,
  listMarketingCustomerTags,
  listMarketingCustomers,
  listMyCustomers,
  listPendingTagCustomerApprovals,
  restoreCustomerOwnershipAuto,
  transferCustomerOwner,
  updateMarketingCustomerProfile,
} from '../marketingManagement';

describe('六页面营销管理 API', () => {
  beforeEach(() => call.mockClear());

  it('区分管理员营销客户列表和我的客户', async () => {
    await listMarketingCustomers({ keyword: '科技' });
    await listMyCustomers({ ownershipStatus: 'ASSIGNED' });
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/marketing/customers', {
      params: { keyword: '科技' },
    }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/marketing/customers/mine', {
      params: { ownershipStatus: 'ASSIGNED' },
    }, null);
  });

  it('客户资料和主办权使用独立高危动作', async () => {
    await updateMarketingCustomerProfile(1, { custName: '示例企业', lockVersion: 2, reason: '资料核实' });
    await transferCustomerOwner(1, { transferAction: 'UNASSIGN', reason: '暂时无主办', lockVersion: 3 });
    await restoreCustomerOwnershipAuto(1, { reason: '恢复上游同步', lockVersion: 4 });
    expect(call).toHaveBeenNthCalledWith(1, 'put', '/marketing/customers/1/profile', {
      data: { custName: '示例企业', lockVersion: 2, reason: '资料核实' },
    });
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/marketing/customers/1/transfer', {
      data: { transferAction: 'UNASSIGN', reason: '暂时无主办', lockVersion: 3 },
    });
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/marketing/customers/1/ownership/restore-auto', {
      data: { reason: '恢复上游同步', lockVersion: 4 },
    });
  });

  it('手工线索逐条查询并固定 MANUAL 来源', async () => {
    await listManualLeads({ pageNo: 2 });
    await createMarketingLead({ custName: '示例企业' });
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/marketing/leads', {
      params: { pageNo: 2, leadSource: 'MANUAL' },
    }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/marketing/leads', {
      data: { custName: '示例企业' },
    });
  });

  it('线索导入批次支持失败优先详情和待确认动作', async () => {
    await listLeadImportBatches({ pageNo: 1 });
    await getLeadImportBatchDetails(9, { pageNo: 1 });
    await confirmLeadImportBatch(9, { action: 'PROCESS_VALID', remark: '只处理正常数据', lockVersion: 1 });
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/marketing/lead-import-batches', {
      params: { pageNo: 1 },
    }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/marketing/lead-import-batches/9/details', {
      params: { pageNo: 1 },
    }, null);
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/marketing/lead-import-batches/9/confirm', {
      data: { action: 'PROCESS_VALID', remark: '只处理正常数据', lockVersion: 1 },
    });
  });

  it('线索审批区分本人待办和本人已办', async () => {
    await listLeadApprovalPending({ custName: '示例' });
    await listLeadApprovalHistory({ result: 'APPROVED' });
    await approveLead(7, { opinion: '同意', lockVersion: 1 });
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/marketing/lead-approvals/pending', {
      params: { custName: '示例' },
    }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/marketing/lead-approvals/history', {
      params: { result: 'APPROVED' },
    }, null);
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/marketing/lead-approvals/7/approve', {
      data: { opinion: '同意', lockVersion: 1 },
    });
  });

  it('标签导入只创建审批批次，批量审批可联动通过标签', async () => {
    const file = new File(['xlsx'], '标签客户.xlsx');
    await listMarketingCustomerTags({ status: 'ENABLED' });
    await createCustomerTagImportBatch({ tagId: 3, importMode: 'REPLACE', file });
    await listPendingTagCustomerApprovals({ tagId: 3 });
    await approveTagCustomers({ detailIds: [11, 12], approveTag: true, opinion: '同意' });

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/marketing/customer-tags', {
      params: { status: 'ENABLED' },
    }, { records: [], total: 0 });
    const createCall = call.mock.calls[1];
    expect(createCall.slice(0, 2)).toEqual(['post', '/marketing/customer-tag-import-batches']);
    expect(createCall[2].data).toBeInstanceOf(FormData);
    expect(createCall[2].data.get('tagId')).toBe('3');
    expect(createCall[2].data.get('importMode')).toBe('REPLACE');
    expect(createCall[2].data.get('file')).toBe(file);
    expect(call).toHaveBeenNthCalledWith(3, 'get', '/marketing/customer-tag-approvals/pending', {
      params: { tagId: 3 },
    }, { records: [], total: 0 });
    expect(call).toHaveBeenNthCalledWith(4, 'post', '/marketing/customer-tag-approvals/batch-approve', {
      data: { detailIds: [11, 12], approveTag: true, opinion: '同意' },
    });
  });
});
