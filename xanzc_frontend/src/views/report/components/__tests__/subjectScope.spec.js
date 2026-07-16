import { describe, it, expect } from 'vitest';
import { filterTreeByCodes } from '../subjectScope';

const tree = [
  { code: 'A', name: '西安分行', children: [
    { code: 'A1', name: '雁塔支行', children: [] },
    { code: 'A2', name: '碑林支行', children: [] }
  ] },
  { code: 'B', name: '榆林分行', children: [] }
];

describe('filterTreeByCodes', () => {
  it('保留命中节点及其命中路径上的祖先容器', () => {
    const out = filterTreeByCodes(tree, new Set(['A1']));
    expect(out.length).toBe(1);
    expect(out[0].code).toBe('A');
    expect(out[0].children.map(n => n.code)).toEqual(['A1']);
  });
  it('命中集合为空则返回空数组', () => {
    expect(filterTreeByCodes(tree, new Set())).toEqual([]);
  });
  it('祖先本身命中也保留', () => {
    const out = filterTreeByCodes(tree, new Set(['B']));
    expect(out.map(n => n.code)).toEqual(['B']);
  });
});
