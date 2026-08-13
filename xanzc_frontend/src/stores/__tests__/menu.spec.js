// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('@/api/auth', () => ({ getMyMenus: vi.fn() }));
import { getMyMenus } from '@/api/auth';
import { useMenuStore } from '../menu';
import { useUserStore } from '../user';

// 两层树：报表分析 > 数据公式(原自由报表)；工作台为顶层叶子
const TREE = [
  { resourceId: 1, resourceUrl: '/report', menuName: '报表分析', children: [
    { resourceId: 2, resourceUrl: '/report/free', menuName: '数据公式', children: [] }
  ]},
  { resourceId: 9, resourceUrl: '/workspace', menuName: '工作台', children: [] }
];

beforeEach(() => {
  sessionStorage.clear();
  setActivePinia(createPinia());
  vi.clearAllMocks();
});

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe('menu store', () => {
  it('load 后 resolve 子节点返回 menuName + 父分组', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();
    expect(s.resolve('/report/free')).toEqual({ title: '数据公式', group: '报表分析' });
  });

  it('顶层叶子 resolve 的 group 为 null', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();
    expect(s.resolve('/workspace')).toEqual({ title: '工作台', group: null });
  });

  it('未命中路径 resolve 返回 null', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();
    expect(s.resolve('/nope')).toBeNull();
  });

  it('load 幂等：第二次不再请求，force 才重取', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();
    await s.load();
    expect(getMyMenus).toHaveBeenCalledTimes(1);
    await s.load(true);
    expect(getMyMenus).toHaveBeenCalledTimes(2);
  });

  it('提供菜单存在判断、首个叶子与安全登录落点', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();

    expect(s.hasUrl('/workspace')).toBe(true);
    expect(s.hasUrl('/missing')).toBe(false);
    expect(s.firstNavigableUrl()).toBe('/report/free');
    expect(s.resolveLandingPath('/report/free?period=2026')).toBe('/report/free?period=2026');
    expect(s.resolveLandingPath('/report/free/42?tab=detail')).toBe('/report/free/42?tab=detail');
    expect(s.resolveLandingPath('/announcement/42')).toBe('/workspace');
    expect(s.resolveLandingPath('/reporting')).toBe('/workspace');
    expect(s.resolveLandingPath('/report/free/../admin')).toBe('/workspace');
    expect(s.resolveLandingPath('/report/free/%2e%2e/admin')).toBe('/workspace');
    expect(s.resolveLandingPath('https://evil.example')).toBe('/workspace');
    expect(s.resolveLandingPath()).toBe('/workspace');
  });

  it('红色引擎入口授权其子路由，且专属登录可优先进入红色工作台', async () => {
    getMyMenus.mockResolvedValue([
      { resourceId: 'M_WORKSPACE', resourceUrl: '/workspace', menuName: '工作台', children: [] },
      { resourceId: 'M_RE_ENGINE', resourceUrl: '/redengine/dashboard', menuName: '红色引擎', children: [] }
    ]);
    const s = useMenuStore();
    await s.load();

    expect(s.isAuthorizedRedirect('/redengine/review?task=1')).toBe(true);
    expect(s.resolveLandingPath('/redengine/review?task=1', { preferredUrl: '/redengine/dashboard' }))
      .toBe('/redengine/review?task=1');
    expect(s.resolveLandingPath('/system/users', { preferredUrl: '/redengine/dashboard' }))
      .toBe('/redengine/dashboard');
    expect(s.resolveLandingPath(undefined, { preferredUrl: '/redengine/dashboard' }))
      .toBe('/redengine/dashboard');
  });

  it('没有工作台时进入首个授权叶子，无菜单时进入 no-access', async () => {
    getMyMenus.mockResolvedValueOnce([
      { resourceId: 'M_RE_ENGINE', resourceUrl: '/redengine/dashboard', menuName: '红色引擎', children: [] }
    ]);
    const s = useMenuStore();
    await s.load();
    expect(s.resolveLandingPath()).toBe('/redengine/dashboard');

    getMyMenus.mockResolvedValueOnce([]);
    await s.load(true);
    expect(s.resolveLandingPath()).toBe('/no-access');
  });

  it('强制重拉失败时清空上一用户菜单并把错误交给调用方', async () => {
    getMyMenus.mockResolvedValueOnce(TREE);
    const s = useMenuStore();
    await s.load();
    expect(s.tree.length).toBeGreaterThan(0);

    const denied = new Error('403 denied');
    getMyMenus.mockRejectedValueOnce(denied);
    await expect(s.load(true)).rejects.toBe(denied);
    expect(s.tree).toEqual([]);
    expect(s.resolve('/workspace')).toBeNull();
    expect(s.loaded).toBe(false);
  });

  it('force 请求废弃旧 pending，迟到的旧响应不得覆盖新菜单', async () => {
    const oldRequest = deferred();
    const newRequest = deferred();
    getMyMenus
      .mockReturnValueOnce(oldRequest.promise)
      .mockReturnValueOnce(newRequest.promise);
    const s = useMenuStore();

    const oldLoad = s.load();
    const newLoad = s.load(true);
    expect(getMyMenus).toHaveBeenCalledTimes(2);

    newRequest.resolve([{ resourceId: 'B', resourceUrl: '/b', menuName: 'B菜单' }]);
    await newLoad;
    oldRequest.resolve([{ resourceId: 'A', resourceUrl: '/a', menuName: 'A菜单' }]);
    await oldLoad;

    expect(s.tree.map((item) => item.menuName)).toEqual(['B菜单']);
    expect(s.hasUrl('/b')).toBe(true);
    expect(s.hasUrl('/a')).toBe(false);
  });

  it('换用户会清空授权快照、废弃旧请求并只接收新用户响应', async () => {
    const oldRequest = deferred();
    const newRequest = deferred();
    getMyMenus
      .mockReturnValueOnce(oldRequest.promise)
      .mockReturnValueOnce(newRequest.promise);
    const userStore = useUserStore();
    const menuStore = useMenuStore();

    userStore.setUser({ empId: 'A', username: 'userA' });
    const oldLoad = menuStore.load();
    userStore.setUser({ empId: 'B', username: 'userB' });
    expect(menuStore.loaded).toBe(false);
    expect(menuStore.tree).toEqual([]);
    const newLoad = menuStore.load();
    expect(getMyMenus).toHaveBeenCalledTimes(2);

    newRequest.resolve([{ resourceId: 'B', resourceUrl: '/b', menuName: 'B菜单' }]);
    await newLoad;
    oldRequest.resolve([{ resourceId: 'A', resourceUrl: '/a', menuName: 'A菜单' }]);
    await oldLoad;
    expect(menuStore.firstNavigableUrl()).toBe('/b');
  });
});
