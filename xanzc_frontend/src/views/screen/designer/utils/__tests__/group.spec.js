// 成组/解组坐标换算纯函数测试(TDD Red 先行)——多选成组生成 Group 节点(children 坐标
// 转相对组左上角)、解组回填绝对坐标、组 8 点缩放时子组件按比例换算。
import { describe, it, expect } from 'vitest';
import { boundingRect, makeGroup, ungroup, scaleGroupChildren } from '../group';

const rectA = { top: 100, left: 200, width: 100, height: 50 };
const rectB = { top: 200, left: 400, width: 300, height: 100 };

function compA() {
  return { id: 'a', component: 'RectShape', style: { ...rectA }, propValue: { fill: '#123' }, isLock: false, isShow: true };
}
function compB() {
  return { id: 'b', component: 'ChartWidget', innerType: 'LINE_TREND', blockId: 9,
    style: { ...rectB }, propValue: {}, isLock: false, isShow: true };
}

describe('boundingRect 包围盒', () => {
  it('多矩形取最小外接矩形', () => {
    expect(boundingRect([rectA, rectB])).toEqual({ top: 100, left: 200, width: 500, height: 200 });
  });

  it('单矩形即自身', () => {
    expect(boundingRect([rectA])).toEqual({ ...rectA });
  });
});

describe('makeGroup 成组(children 坐标转相对组左上角)', () => {
  it('生成 Group 节点:style=包围盒,children 相对坐标,blockId 引用不变', () => {
    const g = makeGroup([compA(), compB()]);
    expect(g.component).toBe('Group');
    expect(g.id).toMatch(/^w-/);           // 与画布组件同一 id 命名规则
    expect(g.isLock).toBe(false);
    expect(g.isShow).toBe(true);
    expect(g.style).toEqual({ top: 100, left: 200, width: 500, height: 200 });
    expect(g.children.length).toBe(2);
    expect(g.children[0].style).toEqual({ top: 0, left: 0, width: 100, height: 50 });
    expect(g.children[1].style).toEqual({ top: 100, left: 200, width: 300, height: 100 });
    expect(g.children[1].blockId).toBe(9); // 图表子组件 blockId 引用不动
    expect(g.children[1].innerType).toBe('LINE_TREND');
  });

  it('children 是深拷贝,改组内不影响原节点', () => {
    const a = compA();
    const g = makeGroup([a, compB()]);
    g.children[0].style.top = 999;
    expect(a.style.top).toBe(100);
  });

  it('成员含 Group 时先展开吸收其 children(组不嵌套)', () => {
    const inner = makeGroup([compA(), compB()]); // 包围盒 (100,200,500,200)
    const c = { id: 'c', component: 'TextLabel', style: { top: 400, left: 100, width: 50, height: 20 },
      propValue: {}, isLock: false, isShow: true };
    const g = makeGroup([inner, c]);
    expect(g.children.length).toBe(3); // a/b 被展开吸收 + c
    expect(g.children.every(ch => ch.component !== 'Group')).toBe(true);
    // 包围盒:top=100..420,left=100..700 → 吸收后 a 的绝对(100,200)转相对新组(0,100)
    expect(g.style).toEqual({ top: 100, left: 100, width: 600, height: 320 });
    const a = g.children.find(ch => ch.id === 'a');
    expect(a.style).toEqual({ top: 0, left: 100, width: 100, height: 50 });
  });

  it('不足 2 个返回 null(调用方按钮禁用,纯函数兜底)', () => {
    expect(makeGroup([compA()])).toBeNull();
    expect(makeGroup([])).toBeNull();
  });
});

describe('ungroup 解组(回填绝对坐标)', () => {
  it('children 相对坐标 + 组左上角 = 绝对坐标,其余字段保持', () => {
    const g = makeGroup([compA(), compB()]);
    const out = ungroup(g);
    expect(out.length).toBe(2);
    expect(out[0].style).toEqual({ ...rectA });
    expect(out[1].style).toEqual({ ...rectB });
    expect(out[1].blockId).toBe(9);
    expect(out[1].component).toBe('ChartWidget');
  });

  it('组被拖动后解组:children 跟随组新位置回填', () => {
    const g = makeGroup([compA(), compB()]);
    g.style.top += 50; g.style.left += 30; // 模拟组整体拖动
    const out = ungroup(g);
    expect(out[0].style.top).toBe(150);
    expect(out[0].style.left).toBe(230);
  });

  it('非 Group 节点返回空数组', () => {
    expect(ungroup(compA())).toEqual([]);
  });
});

describe('scaleGroupChildren 组缩放时子组件按比例换算', () => {
  const children = [
    { id: 'a', style: { top: 0, left: 0, width: 100, height: 50 } },
    { id: 'b', style: { top: 100, left: 200, width: 300, height: 100 } }
  ];

  it('等比缩小一半:相对坐标与尺寸全部 ×0.5', () => {
    const out = scaleGroupChildren(children, { width: 500, height: 200 }, { width: 250, height: 100 });
    expect(out[0].style).toEqual({ top: 0, left: 0, width: 50, height: 25 });
    expect(out[1].style).toEqual({ top: 50, left: 100, width: 150, height: 50 });
  });

  it('横向拉伸不影响纵向', () => {
    const out = scaleGroupChildren(children, { width: 500, height: 200 }, { width: 1000, height: 200 });
    expect(out[1].style).toEqual({ top: 100, left: 400, width: 600, height: 100 });
  });

  it('极端缩小时宽高下限 1(后端 validateStyle 拒绝 <1)', () => {
    const out = scaleGroupChildren(
      [{ id: 'x', style: { top: 0, left: 0, width: 5, height: 5 } }],
      { width: 1000, height: 1000 }, { width: 20, height: 20 });
    expect(out[0].style.width).toBeGreaterThanOrEqual(1);
    expect(out[0].style.height).toBeGreaterThanOrEqual(1);
  });

  it('返回新数组,不 mutate 原 children', () => {
    scaleGroupChildren(children, { width: 500, height: 200 }, { width: 250, height: 100 });
    expect(children[1].style.width).toBe(300);
  });
});
