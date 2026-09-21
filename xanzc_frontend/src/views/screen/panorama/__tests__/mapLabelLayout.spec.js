import { describe, expect, it } from 'vitest';
import { createMapCalloutPath, layoutMapCallouts, layoutMapLabels } from '../mapLabelLayout';
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
