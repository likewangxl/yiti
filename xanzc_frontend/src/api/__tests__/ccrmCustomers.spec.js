import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ records: [], total: 0 }) }));

import { call } from '../http';
import {
  createCcrmCustomer,
  deleteCcrmCustomer,
  downloadCcrmImportTemplate,
  executeCcrmImport,
  exportCcrmCustomers,
  getCcrmCustomer,
  listCcrmCustomers,
  lookupCcrmCustomer,
  previewCcrmImport,
  updateCcrmCustomer,
} from '../ccrmCustomers';

describe('CCRM 独立客户源 API', () => {
  beforeEach(() => call.mockClear());

  it('分页、详情和线索历史客户检索都使用独立 CCRM 资源', async () => {
    await listCcrmCustomers({ keyword: '华夏', recordStatus: 'ACTIVE' });
    await getCcrmCustomer('S1');
    await lookupCcrmCustomer({ unifiedCreditCode: '91310000123456789A' });

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/ccrm/customers', {
      params: { keyword: '华夏', recordStatus: 'ACTIVE' },
    }, null);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/ccrm/customers/S1', {}, null);
    expect(call).toHaveBeenNthCalledWith(3, 'get', '/ccrm/customers/lookup', {
      params: { unifiedCreditCode: '91310000123456789A' },
    }, null);
  });

  it('人工新增、修改和逻辑停用不带任何成功 fallback', async () => {
    const payload = { custName: '华夏科技', customerType: 'CORP' };
    await createCcrmCustomer(payload);
    await updateCcrmCustomer('S1', { ...payload, lockVersion: 3 });
    await deleteCcrmCustomer('S1', { reason: '客户已迁移', lockVersion: 3 });

    expect(call).toHaveBeenNthCalledWith(1, 'post', '/ccrm/customers', { data: payload });
    expect(call).toHaveBeenNthCalledWith(2, 'put', '/ccrm/customers/S1', {
      data: { ...payload, lockVersion: 3 },
    });
    expect(call).toHaveBeenNthCalledWith(3, 'delete', '/ccrm/customers/S1', {
      data: { reason: '客户已迁移', lockVersion: 3 },
    });
    for (const args of call.mock.calls) expect(args).toHaveLength(3);
  });

  it('模板下载、导入预览、整批执行和按筛选导出使用 blob 或 FormData 契约', async () => {
    const file = new File(['xlsx'], 'ccrm.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    await downloadCcrmImportTemplate();
    await previewCcrmImport(file);
    await executeCcrmImport('BATCH-1');
    await exportCcrmCustomers({ customerType: 'CORP', pageNo: 1 });

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/ccrm/customers/import-template', {
      responseType: 'blob',
    }, null);
    const previewConfig = call.mock.calls[1][2];
    expect(call.mock.calls[1].slice(0, 2)).toEqual(['post', '/ccrm/customers/import/preview']);
    expect(previewConfig.data).toBeInstanceOf(FormData);
    expect(previewConfig.data.get('file')).toBe(file);
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/ccrm/customers/import/execute', {
      data: { batchId: 'BATCH-1' },
    });
    expect(call).toHaveBeenNthCalledWith(4, 'get', '/ccrm/customers/export', {
      params: { customerType: 'CORP', pageNo: 1 },
      responseType: 'blob',
    }, null);
  });
});
