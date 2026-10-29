import { describe, expect, it } from 'vitest';
import {
  buildLegacyCanvasPayload, collectLegacyNodes, parseLegacyJson, updateLegacyNode
} from '../legacyCanvasEditorModel';

const block = { id: 17, componentType: 'METRIC_CARD', bindJson: '{"dsId":9,"period":"LATEST","fields":{"value":"amount"},"units":{"value":"YUAN"}}', styleJson: '{"title":"存款"}', drillJson: '{"drillEnabled":false}' };
const canvas = { screenId: 9102, canvasVersion: 4, canvasStyle: { background: '#03132b', unknownStyle: { keep: true } }, components: [
  { id: 'a', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 17, style: { top: 10, left: 20, width: 200, height: 80 }, propValue: { keep: 'x' }, bindJson: '{}', styleJson: '{}', drillJson: '{}', unknownNode: { keep: 1 } },
  { id: 'hidden', component: 'TextLabel', isShow: false, style: { top: 3 }, children: [{ id: 'child', component: 'ClockWidget', style: { top: 1 }, propValue: { format: 'HH:mm' } }] }
] };

describe('legacyCanvasEditorModel', () => {
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
});
