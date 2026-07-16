import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('@/api/auth', () => ({ getMyMenus: vi.fn() }));
import { getMyMenus } from '@/api/auth';
import { useMenuStore } from '../menu';

// 两层树：报表分析 > 数据公式(原自由报表)；工作台为顶层叶子
const TREE = [
  { resourceId: 1, resourceUrl: '/report', menuName: '报表分析', children: [
    { resourceId: 2, resourceUrl: '/report/free', menuName: '数据公式', children: [] }
  ]},
  { resourceId: 9, resourceUrl: '/workspace', menuName: '工作台', children: [] }
];

beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks(); });

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
});
