import { beforeEach, describe, expect, it, vi } from 'vitest';

const call = vi.hoisted(() => vi.fn());
const unwrapPage = vi.hoisted(() => vi.fn((value) => {
  if (Array.isArray(value)) return { records: value, total: value.length };
  return value;
}));

vi.mock('../http', () => ({ call, unwrapPage }));

import {
  cancelLoanApplication,
  createLoanApplication,
  getLoanApplication,
  listLoanCustomerCandidates,
  listLoanApplications,
  listLoanAttachments,
  updateLoanApplication,
  uploadLoanAttachment
} from '../businessApplication';

describe('business application API', () => {
  beforeEach(() => call.mockReset());

  it('分页查询资产立项申请仅发送后端支持的筛选并把元转换为万元', async () => {
    call.mockResolvedValueOnce({ records: [{ id: 'L-1', creditAmount: '1230000.00', creditExposureAmount: 800000 }], total: 1 });

    const result = await listLoanApplications({
      keyword: '浦爱', status: 'DRAFT', projectType: 'UNSUPPORTED', bizType: 'UNSUPPORTED',
      pageNo: 2, pageSize: 10
    });

    expect(call).toHaveBeenCalledWith('get', '/loans', {
      params: { keyword: '浦爱', status: 'DRAFT', pageNo: 2, pageSize: 10 }
    });
    expect(result).toEqual({
      records: [{ id: 'L-1', creditAmount: 123, creditExposureAmount: 80 }], total: 1
    });
  });

  it('保存、更新草稿和详情把页面万元转换为后端元', async () => {
    const draft = { custId: 'C-1', projectType: 'NEW_CREDIT', bizType: 'WORKING_CAPITAL', guaranteeType: 'MORTGAGE', creditAmount: 100, creditExposureAmount: 80 };
    await createLoanApplication(draft);
    await updateLoanApplication('L-1', { creditAmount: 120 });
    call.mockResolvedValueOnce({ id: 'L-1', creditAmount: '2500000', creditExposureAmount: '1250000' });
    const detail = await getLoanApplication('L-1');
    await listLoanAttachments('L-1');

    expect(call).toHaveBeenNthCalledWith(1, 'post', '/loans', {
      data: { ...draft, creditAmount: 1000000, creditExposureAmount: 800000 }
    });
    expect(call).toHaveBeenNthCalledWith(2, 'put', '/loans/L-1', { data: { creditAmount: 1200000 } });
    expect(detail).toEqual({ id: 'L-1', creditAmount: 250, creditExposureAmount: 125 });
    expect(call).toHaveBeenNthCalledWith(4, 'get', '/files', {
      params: { bizType: 'LOAN', bizId: 'L-1' }
    });
  });

  it('撤回只提交后端审计 DTO 要求的 reason 请求体', async () => {
    await cancelLoanApplication('L-1', '客户补充材料后重新发起');

    expect(call).toHaveBeenCalledWith('post', '/loans/L-1/cancel', {
      data: { reason: '客户补充材料后重新发起' }
    });
  });

  it('客户候选使用真实客户分页契约并保留客户类型', async () => {
    call.mockResolvedValueOnce({
      records: [{ id: 'C-1', custNo: 'KH001', custName: '浦爱科技', customerType: 'CORP', status: 'ACTIVE' }],
      total: 1
    });

    const result = await listLoanCustomerCandidates({ keyword: '浦爱', status: 'ACTIVE', pageNo: 1, pageSize: 20 });

    expect(call).toHaveBeenCalledWith('get', '/customers', {
      params: { keyword: '浦爱', status: 'ACTIVE', pageNo: 1, pageSize: 20 }
    });
    expect(result).toEqual([
      expect.objectContaining({ id: 'C-1', name: '浦爱科技', customerType: 'CORP', status: 'ACTIVE' })
    ]);
  });

  it('草稿创建后上传附件时关联 LOAN 和申请 id', async () => {
    const file = new File(['pdf'], '尽调报告.pdf', { type: 'application/pdf' });
    await uploadLoanAttachment(file, 'L-1');

    const config = call.mock.calls.at(-1)[2];
    expect(call).toHaveBeenCalledWith('post', '/files/upload', expect.objectContaining({
      params: { bizType: 'LOAN', bizId: 'L-1' },
      headers: { 'Content-Type': 'multipart/form-data' }
    }));
    expect(config.data).toBeInstanceOf(FormData);
    expect(config.data.get('file')).toBe(file);
  });
});
