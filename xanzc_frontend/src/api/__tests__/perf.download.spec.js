// @vitest-environment happy-dom
// downloadImportSourceFile：优先取 OBS 预签名 URL 直连下载；本地文件(空 URL)回退字节流。
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn() }));

import { call } from '../http';
import { downloadImportSourceFile } from '../perf';

describe('downloadImportSourceFile', () => {
  beforeEach(() => {
    call.mockReset();
    global.URL.createObjectURL = vi.fn(() => 'blob:mock');
    global.URL.revokeObjectURL = vi.fn();
  });

  it('OBS 有预签名 URL → window.open 直连，不再请求字节流', async () => {
    call.mockResolvedValueOnce({ url: 'https://obs.example/presigned?sig=x' });
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);

    await downloadImportSourceFile('B1', 'f.xlsx');

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/perf/import/batches/B1/source-file',
      { params: { asUrl: true } }, null);
    expect(openSpy).toHaveBeenCalledWith('https://obs.example/presigned?sig=x', '_blank');
    expect(call).toHaveBeenCalledTimes(1); // 无字节流回退
  });

  it('本地文件(空 URL) → 回退请求字节流 blob', async () => {
    call.mockResolvedValueOnce({ url: '' });       // asUrl 空
    call.mockResolvedValueOnce(new Blob(['x']));   // 回退 blob

    await downloadImportSourceFile('BL', 'g.xlsx');

    expect(call).toHaveBeenCalledTimes(2);
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/perf/import/batches/BL/source-file',
      { responseType: 'blob' }, null);
  });
});
