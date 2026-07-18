// 对齐/分布纯函数测试(TDD Red 先行)——多选工具条的左/右/上/下对齐、水平/垂直居中、
// 水平/垂直等间距分布。坐标全部为 1920×1080 设计态像素,与 scale 无关。
import { describe, it, expect } from 'vitest';
import { alignRects, distributeRects } from '../align';

/** 造矩形快捷函数 */
function r(id, top, left, width, height) {
  return { id, top, left, width, height };
}

describe('alignRects 对齐(基准=选区包围盒)', () => {
  const rects = [r('a', 100, 200, 100, 50), r('b', 300, 400, 200, 100), r('c', 50, 600, 50, 30)];
  // 包围盒: left=200, right=650, top=50, bottom=400 → cx=425, cy=225

  it('left 左对齐:全部 left = 包围盒最左', () => {
    const patch = alignRects('left', rects);
    expect(patch).toEqual([
      { id: 'a', top: 100, left: 200 },
      { id: 'b', top: 300, left: 200 },
      { id: 'c', top: 50, left: 200 }
    ]);
  });

  it('right 右对齐:全部右缘 = 包围盒最右', () => {
    const patch = alignRects('right', rects);
    expect(patch).toEqual([
      { id: 'a', top: 100, left: 550 },
      { id: 'b', top: 300, left: 450 },
      { id: 'c', top: 50, left: 600 }
    ]);
  });

  it('top 顶对齐:全部 top = 包围盒最上', () => {
    const patch = alignRects('top', rects);
    expect(patch.map(p => p.top)).toEqual([50, 50, 50]);
    expect(patch.map(p => p.left)).toEqual([200, 400, 600]); // left 不动
  });

  it('bottom 底对齐:全部底缘 = 包围盒最下', () => {
    const patch = alignRects('bottom', rects);
    expect(patch).toEqual([
      { id: 'a', top: 350, left: 200 },
      { id: 'b', top: 300, left: 400 },
      { id: 'c', top: 370, left: 600 }
    ]);
  });

  it('hcenter 水平居中:各组件水平中心对齐包围盒水平中心(cx=425)', () => {
    const patch = alignRects('hcenter', rects);
    expect(patch).toEqual([
      { id: 'a', top: 100, left: 375 },
      { id: 'b', top: 300, left: 325 },
      { id: 'c', top: 50, left: 400 }
    ]);
  });

  it('vcenter 垂直居中:各组件垂直中心对齐包围盒垂直中心', () => {
    const patch = alignRects('vcenter', rects);
    expect(patch).toEqual([
      { id: 'a', top: 200, left: 200 },
      { id: 'b', top: 175, left: 400 },
      { id: 'c', top: 210, left: 600 }
    ]);
  });

  it('居中结果为半像素时四舍五入为整数', () => {
    // 包围盒 left=0,right=101 → cx=50.5;宽 100 → left=0.5 → round=1
    const patch = alignRects('hcenter', [r('a', 0, 0, 100, 10), r('b', 20, 100, 1, 10)]);
    expect(patch.find(p => p.id === 'a').left).toBe(1);
  });

  it('不足 2 个或未知类型返回空数组(调用方按钮禁用,纯函数兜底)', () => {
    expect(alignRects('left', [r('a', 0, 0, 10, 10)])).toEqual([]);
    expect(alignRects('unknown', rects)).toEqual([]);
  });
});

describe('distributeRects 等间距分布(首尾不动,中间均分间隙)', () => {
  it('h 水平等间距:按 left 排序,间隙均分', () => {
    // span=[0,600],总宽=100+100+100=300,gap=(600-0-300)/2=150
    const rects = [r('a', 0, 0, 100, 10), r('c', 0, 500, 100, 10), r('b', 0, 180, 100, 10)];
    const patch = distributeRects('h', rects);
    // 排序后 a(0) b(180) c(500):a 不动,b.left=0+100+150=250,c 不动
    expect(patch).toEqual([
      { id: 'a', top: 0, left: 0 },
      { id: 'b', top: 0, left: 250 },
      { id: 'c', top: 0, left: 500 }
    ]);
  });

  it('v 垂直等间距:按 top 排序,间隙均分', () => {
    // 首 a(top=0,h=100) 尾 c(top=900,h=100) → 可分配区间 [100,900] 宽 800,
    // 中间组件 b 高 100,gap=(800-100)/2=350 → b.top=100+350=450
    const rects = [r('a', 0, 0, 10, 100), r('b', 400, 0, 10, 100), r('c', 900, 0, 10, 100)];
    const patch = distributeRects('v', rects);
    expect(patch).toEqual([
      { id: 'a', top: 0, left: 0 },
      { id: 'b', top: 450, left: 0 },
      { id: 'c', top: 900, left: 0 }
    ]);
  });

  it('间隙含小数时中间项四舍五入取整,尾项仍保持原位', () => {
    // 首 a(0,w30) 尾 c(471,w30) → 可分配区间 [30,471] 宽 441,gap=(441-30)/2=205.5
    const rects = [r('a', 0, 0, 30, 10), r('b', 0, 100, 30, 10), r('c', 0, 471, 30, 10)];
    const patch = distributeRects('h', rects);
    expect(patch.find(p => p.id === 'b').left).toBe(236); // round(30+205.5)
    expect(patch.find(p => p.id === 'c').left).toBe(471); // 尾项不动
  });

  it('不足 3 个返回空数组(≥3 才可分布)', () => {
    expect(distributeRects('h', [r('a', 0, 0, 10, 10), r('b', 0, 50, 10, 10)])).toEqual([]);
  });
});
