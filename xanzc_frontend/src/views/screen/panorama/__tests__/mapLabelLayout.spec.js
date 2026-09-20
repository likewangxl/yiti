import { describe, expect, it } from 'vitest';
import { layoutMapLabels } from '../mapLabelLayout';
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
});
