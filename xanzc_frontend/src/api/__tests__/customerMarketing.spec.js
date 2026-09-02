import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ records: [], total: 0 }) }));

import { call } from '../http';
import {
  addTouchLog,
  claimCustomer,
  getTouchTask,
  listAvailableCustomers,
  listClaimedCustomers,
  listMyTouchTasks,
  startFirstTouch,
  listTags, createTag, approveTag, importTagCustomers,
  downloadTagCustomerImportTemplate, importTagCustomersFile,
  validateCrossOrgMarketing, createCrossOrgMarketing, approveCrossOrgMarketing,
  listCustomerTransfers, transferCustomer,
  listMarketingCustomers, getMarketingCustomer, exportMarketingCustomers,
  listLeads, getLead, createLead, updateLead, deleteLead, submitLead,
  lookupLeadMainManager, listLeadApprovals, getLeadApproval, getAvailableCustomerLeadDetail, exportLeadApprovals,
  previewLeadImport, executeLeadImport
  ,listNameList, importNameList, downloadNameListTemplate, exportNameList
} from '../customerMarketing';

describe('customer marketing APIs', () => {
  beforeEach(() => call.mockClear());

  it('查询待认领客户池', async () => {
    await listAvailableCustomers({ pageNo: 2 });
    expect(call).toHaveBeenCalledWith('get', '/customer-pool', { params: { pageNo: 2 } }, null);
  });

  it('认领后由已认领页单独发起首次触达', async () => {
    await claimCustomer('C1');
    await startFirstTouch('CL1', { planFinishTime: '2026-08-18 18:00:00' });
    expect(call).toHaveBeenNthCalledWith(1, 'post', '/claims', { data: { custId: 'C1' } });
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/claims/CL1/touch', {
      data: { planFinishTime: '2026-08-18 18:00:00' }
    });
  });

  it('我的认领与我的触达均使用当前登录人后端端点', async () => {
    await listClaimedCustomers({ pageNo: 1 });
    await listMyTouchTasks({ status: 'PENDING' });
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/claims/mine/customers', { params: { pageNo: 1 } }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/touch-tasks', { params: { status: 'PENDING' } }, null);
  });

  it('详情与分类触达日志使用独立资源', async () => {
    const payload = { touchMethod: 'VISIT', photoGroups: { keyPerson: ['a.jpg'] } };
    await getTouchTask('T1');
    await addTouchLog('T1', payload);
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/touch-tasks/T1', {}, null);
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/touch-tasks/T1/logs', { data: payload });
  });

  it('客户标签管理与审核使用独立动作资源', async () => {
    await listTags({ approvalStatus: 'PENDING' });
    await createTag({ tagName: '重点项目' });
    await approveTag('TAG1');
    await importTagCustomers('TAG1', ['C1'], 'APPEND');
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/tags', { params: { approvalStatus: 'PENDING' } }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/tags', { data: { tagName: '重点项目' } });
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/tags/TAG1/approve', { data: {} });
    expect(call).toHaveBeenNthCalledWith(4, 'post', '/tags/TAG1/customers/import', {
      data: { custIds: ['C1'], mode: 'APPEND' }
    });
  });

  it('客户标签 Excel 模板下载与文件导入', async () => {
    const file = new File(['xlsx'], '客户标签.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    });
    await downloadTagCustomerImportTemplate('TAG1');
    await importTagCustomersFile('TAG1', file, 'REPLACE');

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/tags/TAG1/customers/import-template', {
      responseType: 'blob'
    }, null);
    const config = call.mock.calls[1][2];
    expect(call.mock.calls[1].slice(0, 2)).toEqual(['post', '/tags/TAG1/customers/import-file']);
    expect(config.data).toBeInstanceOf(FormData);
    expect(config.data.get('file')).toBe(file);
    expect(config.data.get('mode')).toBe('REPLACE');
  });

  it('跨机构营销校验、申请与审核接口保持分离', async () => {
    await validateCrossOrgMarketing('C1');
    await createCrossOrgMarketing({ custId: 'C1', reason: '联合营销' });
    await approveCrossOrgMarketing('A1', '审批通过');
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/cross-org-marketing/validate', { params: { custNo: 'C1' } }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/cross-org-marketing', { data: { custId: 'C1', reason: '联合营销' } });
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/cross-org-marketing/A1/approve', {
      data: { reason: '审批通过' }
    });
  });

  it('客户转交记录与发起转交使用同一业务资源组', async () => {
    await listCustomerTransfers({ keyword: '测试' });
    await transferCustomer({ custId: 'C1', targetEmpIds: ['E2'], reason: '岗位调整' });
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/customer-transfers', { params: { keyword: '测试' } }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/customer-transfers', {
      data: { custId: 'C1', targetEmpIds: ['E2'], reason: '岗位调整' }
    });
  });

  it('客户列表、详情和导出使用客户主档资源', async () => {
    await listMarketingCustomers({ keyword: '华夏' });
    await getMarketingCustomer('C1');
    await exportMarketingCustomers({ status: 'VALID' });
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/customers', { params: { keyword: '华夏' } }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/customers/C1', {}, null);
    expect(call).toHaveBeenNthCalledWith(3, 'get', '/customers/export', {
      params: { status: 'VALID' }, responseType: 'blob'
    }, null);
  });

  it('线索录入页使用独立的查询、维护、主办权与提交接口', async () => {
    const payload = { custName: '华夏科技', unifiedCreditCode: '91310000123456789A' };
    await listLeads({ status: 'DRAFT' });
    await getLead('L1');
    await lookupLeadMainManager({ unifiedCreditCode: payload.unifiedCreditCode });
    await createLead(payload);
    await updateLead('L1', payload);
    await submitLead('L1');
    await deleteLead('L1');
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/leads', { params: { status: 'DRAFT' } }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/leads/L1', {}, null);
    expect(call).toHaveBeenNthCalledWith(3, 'get', '/leads/main-manager', {
      params: { unifiedCreditCode: payload.unifiedCreditCode }
    }, null);
    expect(call).toHaveBeenNthCalledWith(4, 'post', '/leads', { data: payload });
    expect(call).toHaveBeenNthCalledWith(5, 'put', '/leads/L1', { data: payload });
    expect(call).toHaveBeenNthCalledWith(6, 'post', '/leads/L1/submit', { data: {} });
    expect(call).toHaveBeenNthCalledWith(7, 'delete', '/leads/L1', {});
  });

  it('线索批量导入使用预览 FormData 和批次执行接口', async () => {
    const file = new File(['客户名称,是否触达限制\n华夏科技,是'], '线索导入.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    });

    await previewLeadImport(file);
    await executeLeadImport('BATCH-1');

    const previewConfig = call.mock.calls[0][2];
    expect(call.mock.calls[0].slice(0, 2)).toEqual(['post', '/leads/import/preview']);
    expect(previewConfig.data).toBeInstanceOf(FormData);
    expect(previewConfig.data.get('file')).toBe(file);
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/leads/import/execute', {
      data: { batchId: 'BATCH-1' }
    });
  });

  it('线索审批页使用独立待办已办、详情和导出接口', async () => {
    await listLeadApprovals({ tab: 'PENDING' });
    await getLeadApproval('L1');
    await exportLeadApprovals({ result: 'APPROVED' });
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/lead-approvals', { params: { tab: 'PENDING' } }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/lead-approvals/L1', {}, null);
    expect(call).toHaveBeenNthCalledWith(3, 'get', '/lead-approvals/export', {
      params: { result: 'APPROVED' }, responseType: 'blob'
    }, null);
  });

  it('待认领客户池详情复用客户池资源并按来源线索查询', async () => {
    await getAvailableCustomerLeadDetail('LEAD-POOL-1');
    expect(call).toHaveBeenCalledWith('get', '/customer-pool', {
      params: { leadId: 'LEAD-POOL-1' }
    }, null);
  });

  it('标签客户名单查询、模板下载和按筛选条件导出使用独立资源', async () => {
    const params = {
      companyName: '华夏科技',
      companyUsci: '91310000123456789A',
      nameType: '重点客户',
      startDate: '2026-08-01',
      endDate: '2026-08-25',
      pageNo: 2,
      pageSize: 20
    };
    await listNameList(params);
    await downloadNameListTemplate();
    await exportNameList(params);

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/name-list', { params }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/name-list/template', { responseType: 'blob' }, null);
    expect(call).toHaveBeenNthCalledWith(3, 'get', '/name-list/export', { params, responseType: 'blob' }, null);
  });

  it('标签客户名单导入使用 multipart FormData 并保留后端汇总结果', async () => {
    const file = new File(['companyName,companyUsci\n华夏科技,91310000123456789A'], '名单.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    });
    const result = { totalRows: 1, importedCount: 1, skippedCount: 0, errors: [] };
    call.mockResolvedValueOnce(result);

    const actual = await importNameList(file);

    const config = call.mock.calls[0][2];
    expect(call.mock.calls[0].slice(0, 2)).toEqual(['post', '/name-list/import']);
    expect(config.data).toBeInstanceOf(FormData);
    expect(config.data.get('file')).toBe(file);
    expect(config.headers).toEqual({ 'Content-Type': 'multipart/form-data' });
    expect(actual).toEqual(result);
  });
});
