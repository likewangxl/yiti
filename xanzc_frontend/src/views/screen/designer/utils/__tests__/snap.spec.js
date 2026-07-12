import { describe, it, expect } from 'vitest';
import { computeSnap } from '../snap';

describe('snap.js 吸附对齐(参照 DataEase MarkLine.vue showLine 改写的纯函数)', () => {
  const other = { top: 100, left: 200, width: 300, height: 150 }; // 参照组件

  it('左对左命中(diff<=3)→ 吸附并显示 yl 线', () => {
    const cur = { top: 500, left: 202, width: 100, height: 60 }; // left 202 距 200 差 2
    const r = computeSnap(cur, [other], 3);
    expect(r.left).toBe(200);           // 吸附到 200
    expect(r.lines.yl).toBe(200);        // 竖线在 x=200
    expect(r.top).toBe(500);             // top 无命中,原值
  });

  it('顶对顶命中 → 吸附并显示 xt 线', () => {
    const cur = { top: 98, left: 900, width: 100, height: 60 }; // top 98 距 100 差 2
    const r = computeSnap(cur, [other], 3);
    expect(r.top).toBe(100);
    expect(r.lines.xt).toBe(100);
  });

  it('中对中命中 → 吸附并显示 yc/xc 线', () => {
    // other 水平中心 = 200+150 = 350;cur 宽 100,left=301 → 中心 351,距 350 差 1
    const cur = { top: 500, left: 301, width: 100, height: 60 };
    const r = computeSnap(cur, [other], 3);
    expect(r.left).toBe(300);            // 中心对齐 → left = 350 - 50 = 300
    expect(r.lines.yc).toBe(350);
  });

  it('超阈值不吸附', () => {
    const cur = { top: 500, left: 210, width: 100, height: 60 }; // 距 200 差 10 > 3
    const r = computeSnap(cur, [other], 3);
    expect(r.left).toBe(210);
    expect(r.lines.yl).toBeFalsy();
  });

  it('多组件取最近命中', () => {
    const o2 = { top: 100, left: 205, width: 50, height: 50 };
    const cur = { top: 500, left: 203, width: 100, height: 60 };
    // 距 o.left(200)=3, 距 o2.left(205)=2 → 取更近的 205
    const r = computeSnap(cur, [other, o2], 3);
    expect(r.left).toBe(205);
  });
});
