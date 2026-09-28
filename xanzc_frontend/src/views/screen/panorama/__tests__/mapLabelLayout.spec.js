import { describe, expect, it } from 'vitest';
import { createMapCalloutPath, layoutMapCallouts, layoutMapLabels, layoutPointCallouts } from '../mapLabelLayout';
describe('投影后地图标签避让', () => {
  it('相邻城市带数值的标签不重叠，且保留地图内边距', () => {
    const labels=Array.from({length:5},(_,i)=>({key:String(i),x:50+i,y:50,width:14,height:10}));
    const positions=layoutMapLabels(labels);
    labels.forEach((a,i)=>labels.slice(i+1).forEach(b=> {
      const p=positions[a.key],q=positions[b.key];
      expect(Math.abs(p.x-q.x)>=14||Math.abs(p.y-q.y)>=10).toBe(true);
    }));
    expect(positions['0']).toEqual({x:50,y:50});
    const edge=layoutMapLabels([{key:'edge',x:99,y:1,width:20,height:10}]).edge;
    expect(edge.x).toBeLessThanOrEqual(89);expect(edge.y).toBeGreaterThanOrEqual(6);
  });

  it('二次避让有区域约束时不得把标签中心移出所属区域', () => {
    const positions = layoutMapLabels([
      { key: 'first', x: 50, y: 50, width: 20, height: 10 },
      { key: 'second', x: 50, y: 50, width: 20, height: 10, contains: point => (
        point.x >= 45 && point.x <= 55 && point.y >= 45 && point.y <= 55
      ) }
    ]);

    expect(positions.second.x).toBeGreaterThanOrEqual(45);
    expect(positions.second.x).toBeLessThanOrEqual(55);
    expect(positions.second.y).toBeGreaterThanOrEqual(45);
    expect(positions.second.y).toBeLessThanOrEqual(55);
  });

  it('按原始行政中心锚点把标签分散到左右两列，并输出锚点到标签边缘的折线', () => {
    const labels = Array.from({ length: 10 }, (_, index) => ({
      key: `city-${index}`,
      anchor: { x: 42 + (index % 3) * 4, y: 18 + index * 6 },
      width: 14,
      height: 6
    }));
    const positions = layoutMapCallouts(labels, {
      bounds: { left: 7, right: 93, top: 14, bottom: 84 },
      minGap: 2
    });

    expect(Object.values(positions)).toHaveLength(10);
    const sides = Object.values(positions).map(position => position.side);
    expect(sides.filter(side => side === 'left')).toHaveLength(5);
    expect(sides.filter(side => side === 'right')).toHaveLength(5);
    for (const label of labels) {
      const position = positions[label.key];
      expect(position.anchor).toEqual(label.anchor);
      expect(position.points[0]).toEqual([label.anchor.x, label.anchor.y]);
      expect(position.points.at(-1)[1]).toBe(position.label.y);
      expect(position.label.x).toBeGreaterThanOrEqual(7);
      expect(position.label.x).toBeLessThanOrEqual(93);
    }
    const leftRows = Object.values(positions).filter(position => position.side === 'left');
    const rightRows = Object.values(positions).filter(position => position.side === 'right');
    [leftRows, rightRows].forEach(rows => {
      rows.sort((a, b) => a.label.y - b.label.y);
      rows.slice(1).forEach((row, index) => {
        expect(row.label.y - rows[index].label.y).toBeGreaterThanOrEqual(8);
      });
    });
  });
});


describe('城市标注视觉排布', () => {
  it('左右两列使用相同的等距行，不因地市集中在南部而堆积', () => {
    const labels=Array.from({length:10},(_,i)=>({key:String(i),anchor:{x:35+i*3,y:60+i},width:16,height:8}));
    const result=Object.values(layoutMapCallouts(labels,{bounds:{left:5,right:88,top:15,bottom:85}}));
    const left=result.filter(r=>r.side==='left').map(r=>r.label.y);
    const right=result.filter(r=>r.side==='right').map(r=>r.label.y);
    expect(left).toEqual(right);
    expect(left[0]).toBeLessThan(25);
    left.slice(2).forEach((y,i)=>expect(y-left[i+1]).toBeCloseTo(left[1]-left[0]));
    const path=createMapCalloutPath(result[0]);
    expect(path).toContain(' C ');
    expect(path).toContain(' L ');
    expect(path.startsWith(`M ${result[0].anchor.x} ${result[0].anchor.y}`)).toBe(true);
  });
});

describe('市级网点外围标注排布', () => {
  const bounds = { left: 8, right: 92, top: 10, bottom: 90 };
  const labels = Array.from({ length: 26 }, (_, index) => ({
    key: `branch-${index + 1}`,
    anchor: {
      x: index < 13 ? 32 + index * 0.2 : 68 + (index - 13) * 0.2,
      y: 20 + (index % 13) * 3.5
    },
    width: 12,
    height: 4
  }));

  it('空列表安全返回空对象', () => {
    expect(layoutPointCallouts([], { bounds })).toEqual({});
  });

  it('26 个网点固定分成两列，各 13 个，保留锚点并保持卡片在边界内且不重叠', () => {
    const positions = layoutPointCallouts(labels, { bounds });
    const values = Object.values(positions);

    expect(values).toHaveLength(26);
    expect(values.filter(position => position.side === 'left')).toHaveLength(13);
    expect(values.filter(position => position.side === 'right')).toHaveLength(13);

    for (const source of labels) {
      const position = positions[source.key];
      expect(position.anchor).toEqual(source.anchor);
      expect(position.points[0]).toEqual([source.anchor.x, source.anchor.y]);
      expect(position.label.x - source.width / 2).toBeGreaterThanOrEqual(bounds.left);
      expect(position.label.x + source.width / 2).toBeLessThanOrEqual(bounds.right);
      expect(position.label.y - source.height / 2).toBeGreaterThanOrEqual(bounds.top);
      expect(position.label.y + source.height / 2).toBeLessThanOrEqual(bounds.bottom);
    }

    for (const side of ['left', 'right']) {
      const rows = values
        .filter(position => position.side === side)
        .sort((a, b) => a.label.y - b.label.y);
      rows.slice(1).forEach((row, index) => {
        expect(row.label.y - rows[index].label.y).toBeGreaterThanOrEqual(4);
      });
      rows.slice(1).forEach((row, index) => {
        expect(row.anchor.y).toBeGreaterThan(rows[index].anchor.y);
      });
    }
  });

  it('相同输入始终得到相同结果', () => {
    expect(layoutPointCallouts(labels, { bounds })).toEqual(layoutPointCallouts(labels, { bounds }));
    expect(layoutPointCallouts([...labels].reverse(), { bounds })).toEqual(layoutPointCallouts(labels, { bounds }));
  });
});
