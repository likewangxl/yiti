import { describe, expect, it } from 'vitest';
import {
  buildLegacyCanvasPayload, collectLegacyNodes, legacyCanvasSnapshotsEqual, normalizeLegacyCanvasSnapshot, parseLegacyJson, updateLegacyNode
} from '../legacyCanvasEditorModel';

const block = { id: 17, componentType: 'METRIC_CARD', bindJson: '{"dsId":9,"period":"LATEST","fields":{"value":"amount"},"units":{"value":"YUAN"}}', styleJson: '{"title":"存款"}', drillJson: '{"drillEnabled":false}' };
const canvas = { screenId: 9102, canvasVersion: 4, canvasStyle: { background: '#03132b', unknownStyle: { keep: true } }, components: [
  { id: 'a', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 17, style: { top: 10, left: 20, width: 200, height: 80 }, propValue: { keep: 'x' }, bindJson: '{}', styleJson: '{}', drillJson: '{}', unknownNode: { keep: 1 } },
  { id: 'hidden', component: 'TextLabel', isShow: false, style: { top: 3 }, children: [{ id: 'child', component: 'ClockWidget', style: { top: 1 }, propValue: { format: 'HH:mm' } }] }
] };

describe('legacyCanvasEditorModel', () => {
  it('回读允许 DTO 新增的空样式字段，但不能把已配置背景清空当作成功', () => {
    const left = { canvasVersion: 2, canvasStyle: {}, components: [{id:'x',component:'TitleBar'}] };
    const right = { ...left, canvasStyle: { background:null, backgroundType:null, bgGradient:null, bgImage:null, themeOverride:null } };
    expect(legacyCanvasSnapshotsEqual(left,right)).toBe(true);
    expect(legacyCanvasSnapshotsEqual({...left,canvasStyle:{background:'#123456'}},right)).toBe(false);
  });
  it('同一三段 JSON 的对象/字符串表示应相等，未知字段仍不得丢失', () => {
    const left={canvasVersion:2,canvasStyle:{},components:[{id:'x',bindJson:{dsId:3,period:'LATEST'},unknown:42}]};
    const right={...left,components:[{id:'x',bindJson:'{"period":"LATEST","dsId":3}',unknown:42}]};
    expect(legacyCanvasSnapshotsEqual(left,right)).toBe(true);
    expect(legacyCanvasSnapshotsEqual(left,{...right,components:[{id:'x',bindJson:right.components[0].bindJson}]})).toBe(false);
  });
  it('递归保留隐藏节点/children，按 blockId 合并三段 JSON，roundtrip 不丢未知键', () => {
    const payload = buildLegacyCanvasPayload({ ...canvas, blocks: [block] });
    expect(payload.components).toHaveLength(2);
    expect(payload.components[0].style.top).toBe(10);
    expect(payload.components[0].unknownNode.keep).toBe(1);
    expect(payload.components[0].styleJson).toContain('存款');
    expect(payload.components[1].isShow).toBe(false);
    expect(payload.components[1].children[0].propValue.format).toBe('HH:mm');
    expect(collectLegacyNodes(payload.components).map(node => node.id)).toEqual(['a', 'hidden', 'child']);
  });

  it('只更新目标节点，JSON 损坏/缺 block 的 ChartWidget fail-close', () => {
    const next = updateLegacyNode(canvas.components, 'a', node => ({ ...node, style: { ...node.style, top: 99 } }));
    expect(next[0].style.top).toBe(99);
    expect(next[1]).toEqual(canvas.components[1]);
    expect(parseLegacyJson('{bad', null)).toBe(null);
    expect(() => buildLegacyCanvasPayload({ ...canvas, blocks: [] })).toThrow(/blockId|区块/);
  });

  it('画布快照比较忽略 JSON 对象键顺序但保留未知属性', () => {
    const left = { canvasVersion: 5, canvasStyle: { z: 1, unknown: { b: 2, a: 1 } }, components: [{ id: 'x', unknown: { b: 2, a: 1 }, bindJson: '{"b":2,"a":1}' }] };
    const right = { canvasVersion: 5, canvasStyleJson: '{"unknown":{"a":1,"b":2},"z":1}', canvasDraftJson: JSON.stringify({ components: [{ bindJson: '{"a":1,"b":2}', unknown: { a: 1, b: 2 }, id: 'x' }] }) };
    expect(normalizeLegacyCanvasSnapshot(right).components[0].unknown).toEqual({ a: 1, b: 2 });
    expect(legacyCanvasSnapshotsEqual(left, right)).toBe(true);
  });

  it('仅三段 JSON 字段忽略键序，标题文本即使看起来像 JSON 也必须严格比较', () => {
    const left = { canvasVersion: 5, canvasStyle: {}, components: [{ id: 'x', propValue: { text: '{"b":2,"a":1}' }, bindJson: '{"b":2,"a":1}', isShow: false }] };
    const right = { canvasVersion: 5, canvasStyleJson: '{"schemaVersion":1,"designWidth":1920,"designHeight":1080,"adaptor":"keepProportion"}', canvasDraftJson: JSON.stringify({ components: [{ id: 'x', propValue: { text: '{"a":1,"b":2}' }, bindJson: '{"a":1,"b":2}', isShow: false }] }) };
    expect(legacyCanvasSnapshotsEqual(left, right)).toBe(false);
  });

  it('兼容 CanvasStyleDTO 和 CanvasComponentDTO 的合法默认字段', () => {
    const left = { canvasVersion: 5, canvasStyle: {}, components: [{ id: 'x', component: 'TextLabel', propValue: {}, bindJson: null, styleJson: null, drillJson: null }] };
    const right = { canvasVersion: 5, canvasStyleJson: '{"schemaVersion":1,"designWidth":1920,"designHeight":1080,"adaptor":"keepProportion"}', canvasDraftJson: JSON.stringify({ components: [{ id: 'x', component: 'TextLabel', propValue: {}, bindJson: null, styleJson: null, drillJson: null, isLock: false, isShow: true }] }) };
    expect(legacyCanvasSnapshotsEqual(left, right)).toBe(true);
  });
});
