import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('@/api/auth', () => ({ getMyPermissions: vi.fn() }));
import { getMyPermissions } from '@/api/auth';
import { usePermissionStore } from '../permission';

function deferred() {
  let resolve;
  const promise = new Promise((resolvePromise) => { resolve = resolvePromise; });
  return { promise, resolve };
}

beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
});

describe('permission store', () => {
  it('并发加载复用同一请求，并支持精确/前缀资源判断', async () => {
    let release;
    getMyPermissions.mockReturnValue(new Promise((resolve) => { release = resolve; }));
    const store = usePermissionStore();
    const first = store.load();
    const second = store.load();
    expect(getMyPermissions).toHaveBeenCalledTimes(1);
    release({ resourceUrls: ['/api/re/submits/my', '/api/re/reviews/**'] });
    await Promise.all([first, second]);

    expect(store.canAccess('/api/re/submits/my')).toBe(true);
    expect(store.canAccess('/api/re/reviews/**')).toBe(true);
    expect(store.canAccess('/api/re/cockpit/**')).toBe(false);
  });

  it('加载失败清空权限并 fail-close，后续仍可重试', async () => {
    const store = usePermissionStore();
    getMyPermissions.mockResolvedValueOnce({ resourceUrls: ['/api/re/submits/my'] });
    await store.load();
    expect(store.canAccess('/api/re/submits/my')).toBe(true);

    const denied = new Error('403 denied');
    getMyPermissions.mockRejectedValueOnce(denied);
    await expect(store.load(true)).rejects.toBe(denied);
    expect(store.loaded).toBe(false);
    expect(store.canAccess('/api/re/submits/my')).toBe(false);

    getMyPermissions.mockResolvedValueOnce({ resourceUrls: ['/api/re/cockpit/**'] });
    await store.load();
    expect(store.canAccess('/api/re/cockpit/**')).toBe(true);
  });

  it('force/clear 均废弃旧 pending，迟到响应不得恢复旧用户权限', async () => {
    const oldRequest = deferred();
    const newRequest = deferred();
    getMyPermissions
      .mockReturnValueOnce(oldRequest.promise)
      .mockReturnValueOnce(newRequest.promise);
    const store = usePermissionStore();

    const oldLoad = store.load();
    const newLoad = store.load(true);
    expect(getMyPermissions).toHaveBeenCalledTimes(2);
    newRequest.resolve({ resourceUrls: ['/api/re/new'] });
    await newLoad;
    oldRequest.resolve({ resourceUrls: ['/api/re/old'] });
    await oldLoad;
    expect(store.canAccess('/api/re/new')).toBe(true);
    expect(store.canAccess('/api/re/old')).toBe(false);

    const clearedRequest = deferred();
    getMyPermissions.mockReturnValueOnce(clearedRequest.promise);
    const pending = store.load(true);
    store.clear();
    clearedRequest.resolve({ resourceUrls: ['/api/re/restored-by-stale-response'] });
    await pending;
    expect(store.loaded).toBe(false);
    expect(store.canAccess('/api/re/restored-by-stale-response')).toBe(false);
  });
});
