// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';

const rawGet = vi.hoisted(() => vi.fn());
vi.mock('../http', () => ({ default: { get: rawGet } }));

import { listAvailableScreens } from '../screen';

describe('listAvailableScreens', () => {
  beforeEach(() => rawGet.mockReset());

  it('直接请求已授权大屏目录，不走 mock 或 fallback', async () => {
    const catalog = [{ screenCode: 'SCR_CORP', screenName: '对公经营总览', viewLevel: 'PROVINCE', bizLine: 'CORP' }];
    rawGet.mockResolvedValue(catalog);

    await expect(listAvailableScreens()).resolves.toBe(catalog);
    expect(rawGet).toHaveBeenCalledTimes(1);
    expect(rawGet).toHaveBeenCalledWith('/api/screen/view/catalog', { timeout: 60000 });
  });
});
