// getScreenView 改调新渲染契约:支持 preview 参数(?preview=draft 读草稿包,不传则读发布态)。
// 只校验 call() 的调用参数(方法/URL/params/fallback),不关心 http.js 内部拦截器行为。
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ screenCode: 'SCR_TEST' }) }));

import { call } from '../http';
import { getScreenView } from '../screen';

describe('getScreenView', () => {
  beforeEach(() => call.mockClear());

  it('不传 preview 时 params 为空对象(读发布态,silent:false 走全局 toast)', async () => {
    await getScreenView('SCR_TEST');
    expect(call).toHaveBeenCalledWith('get', '/screen/view/SCR_TEST', { params: {}, timeout: 60000 }, null);
  });

  it('preview=draft 时透传到 params.preview(读草稿态)', async () => {
    await getScreenView('SCR_TEST', 'draft');
    expect(call).toHaveBeenCalledWith('get', '/screen/view/SCR_TEST', { params: { preview: 'draft' }, timeout: 60000 }, null);
  });
});
