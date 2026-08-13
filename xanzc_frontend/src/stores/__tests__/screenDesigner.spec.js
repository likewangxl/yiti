import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useScreenDesignerStore } from '../screenDesigner';

describe('screenDesigner store', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('loadFromEditor 装载 draft/style/blocks 并可 toSavePayload 往返', () => {
    const store = useScreenDesignerStore();
    store.loadFromEditor({
      screenId: 7, canvasVersion: 3, publishStatus: 0,
      canvasStyleJson: JSON.stringify({ schemaVersion: 1, adaptor: 'keepProportion' }),
      canvasDraftJson: JSON.stringify({ schemaVersion: 1, components: [
        { id: 'w-1', component: 'TextLabel', style: { top: 10, left: 10, width: 100, height: 40 }, propValue: { text: 'A' } }
      ] }),
      blocks: []
    });
    expect(store.componentData.length).toBe(1);
    expect(store.canvasVersion).toBe(3);
    const payload = store.toSavePayload();
    expect(payload.screenId).toBe(7);
    expect(payload.expectedVersion).toBe(3);
    expect(payload.components[0].component).toBe('TextLabel');
  });

  it('addComponent 后可选中并 setShapeStyle', () => {
    const store = useScreenDesignerStore();
    store.addComponent({ id: 'w-2', component: 'RectShape', style: { top: 0, left: 0, width: 50, height: 50 } });
    store.selectComponent('w-2');
    expect(store.curComponent.id).toBe('w-2');
    store.setShapeStyle({ left: 120, top: 60 });
    expect(store.curComponent.style.left).toBe(120);
  });

  it('图层 topComponent 把选中移到数组末尾(zIndex=顺序)', () => {
    const store = useScreenDesignerStore();
    store.addComponent({ id: 'a', component: 'RectShape', style: {} });
    store.addComponent({ id: 'b', component: 'RectShape', style: {} });
    store.selectComponent('a');
    store.topComponent();
    expect(store.componentData[store.componentData.length - 1].id).toBe('a');
  });

  it('undo/redo 还原 componentData', () => {
    const store = useScreenDesignerStore();
    store.recordSnapshot();                       // 空态
    store.addComponent({ id: 'x', component: 'RectShape', style: {} });
    store.recordSnapshot();                       // 有 x 态
    store.undo();
    expect(store.componentData.length).toBe(0);
    store.redo();
    expect(store.componentData.length).toBe(1);
  });

  // Important-1(评审):stack 是普通闭包变量,canUndo/canRedo 若不显式追踪响应式依赖,
  // computed 只会在首次访问时求值一次并永久缓存——即使栈内容已经变化也不会重算。
  it('canUndo/canRedo 随快照栈变化响应式更新(Important-1)', () => {
    const store = useScreenDesignerStore();
    expect(store.canUndo).toBe(false);
    expect(store.canRedo).toBe(false);
    store.recordSnapshot();                       // 第 1 条快照:空态
    store.addComponent({ id: 'z', component: 'RectShape', style: {} }); // 状态变化 + 内部第 2 条快照
    expect(store.canUndo).toBe(true);
    store.undo();
    expect(store.canUndo).toBe(false);
    expect(store.canRedo).toBe(true);
  });

  // 画布保存与元数据/角色保存是三个独立契约：不能把草稿读取字段或高危角色字段夹带到画布保存。
  it('toSavePayload 仅输出画布保存契约，不夹带 schema/元数据/角色(Important-2)', () => {
    const store = useScreenDesignerStore();
    store.loadFromEditor({
      screenId: 9, canvasVersion: 1, publishStatus: 0,
      canvasStyleJson: JSON.stringify({ schemaVersion: 1, adaptor: 'keep' }),
      canvasDraftJson: JSON.stringify({ schemaVersion: 1, components: [] }),
      blocks: []
    });
    const payload = store.toSavePayload();
    expect(payload).toEqual(expect.objectContaining({ screenId: 9, expectedVersion: 1, components: [] }));
    expect(payload).not.toHaveProperty('schemaVersion');
    expect(payload).not.toHaveProperty('bizLine');
    expect(payload).not.toHaveProperty('orgScopeMode');
    expect(payload).not.toHaveProperty('allowedRoleCodes');
  });
});

// ===== 画布交互增强:多选/成组/对齐分布/排序/改名(TDD Red 先行) =====
describe('screenDesigner store 多选(curComponents)', () => {
  let store;
  beforeEach(() => {
    setActivePinia(createPinia());
    store = useScreenDesignerStore();
    store.addComponent({ id: 'a', component: 'RectShape', style: { top: 0, left: 0, width: 100, height: 50 }, isLock: false, isShow: true });
    store.addComponent({ id: 'b', component: 'TextLabel', style: { top: 100, left: 200, width: 100, height: 50 }, isLock: false, isShow: true });
    store.addComponent({ id: 'c', component: 'RectShape', style: { top: 300, left: 400, width: 100, height: 50 }, isLock: false, isShow: true });
  });

  it('selectComponent 单选时 curComponents=长度 1(单选是多选特例)', () => {
    store.selectComponent('a');
    expect(store.curComponents.length).toBe(1);
    expect(store.curComponents[0].id).toBe('a');
    expect(store.curComponent.id).toBe('a');
  });

  it('setCurComponents 多选:curComponent 为 null(属性面板仅单选显示)', () => {
    store.setCurComponents(['a', 'b']);
    expect(store.curComponents.map(x => x.id)).toEqual(['a', 'b']);
    expect(store.curComponent).toBeNull();
  });

  it('setCurComponents 单 id 退化为单选(curComponent 有值)', () => {
    store.setCurComponents(['b']);
    expect(store.curComponent.id).toBe('b');
    expect(store.curComponents.length).toBe(1);
  });

  it('toggleSelect Ctrl+点击增删选择', () => {
    store.selectComponent('a');
    store.toggleSelect('b');
    expect(store.curComponents.map(x => x.id).sort()).toEqual(['a', 'b']);
    store.toggleSelect('a');
    expect(store.curComponents.map(x => x.id)).toEqual(['b']);
    expect(store.curComponent.id).toBe('b'); // 退化回单选
  });

  it('clearSelection 清空选区(Esc)', () => {
    store.setCurComponents(['a', 'b']);
    store.clearSelection();
    expect(store.curComponents.length).toBe(0);
    expect(store.curComponent).toBeNull();
  });

  it('selectComponent 清空口径(__none__)同时清 curComponents(兼容现有 deselect)', () => {
    store.setCurComponents(['a', 'b']);
    store.selectComponent('__none__');
    expect(store.curComponents.length).toBe(0);
  });

  it('removeSelected 批量删除并记快照(undo 可回)', () => {
    store.recordSnapshot();
    store.setCurComponents(['a', 'c']);
    store.removeSelected();
    expect(store.componentData.map(x => x.id)).toEqual(['b']);
    expect(store.curComponents.length).toBe(0);
    store.undo();
    expect(store.componentData.length).toBe(3);
  });

  it('nudgeSelected 批量位移并 clamp 画布内', () => {
    store.setCurComponents(['a', 'b']);
    store.nudgeSelected(-10, 5); // a 在 (0,0),left 不能为负
    const a = store.componentData.find(x => x.id === 'a');
    const b = store.componentData.find(x => x.id === 'b');
    expect(a.style.left).toBe(0);
    expect(a.style.top).toBe(5);
    expect(b.style.left).toBe(190);
    expect(b.style.top).toBe(105);
  });
});

describe('screenDesigner store 成组/解组/对齐分布/排序/改名', () => {
  let store;
  beforeEach(() => {
    setActivePinia(createPinia());
    store = useScreenDesignerStore();
    store.addComponent({ id: 'a', component: 'RectShape', style: { top: 100, left: 200, width: 100, height: 50 }, isLock: false, isShow: true });
    store.addComponent({ id: 'b', component: 'ChartWidget', innerType: 'LINE_TREND', blockId: 9,
      style: { top: 200, left: 400, width: 300, height: 100 }, isLock: false, isShow: true });
    store.addComponent({ id: 'c', component: 'TextLabel', style: { top: 500, left: 50, width: 80, height: 30 }, isLock: false, isShow: true });
  });

  it('groupSelected 成组:组件树生成 Group 节点并选中之,undo 可回', () => {
    store.recordSnapshot();
    store.setCurComponents(['a', 'b']);
    store.groupSelected();
    expect(store.componentData.length).toBe(2); // c + Group
    const g = store.componentData.find(x => x.component === 'Group');
    expect(g.style).toEqual({ top: 100, left: 200, width: 500, height: 200 });
    expect(g.children.length).toBe(2);
    expect(store.curComponent.id).toBe(g.id); // 成组后选中组
    store.undo();
    expect(store.componentData.length).toBe(3);
    expect(store.componentData.find(x => x.component === 'Group')).toBeUndefined();
  });

  it('ungroupSelected 解组:回填绝对坐标到顶层并多选子组件', () => {
    store.setCurComponents(['a', 'b']);
    store.groupSelected();
    store.ungroupSelected();
    expect(store.componentData.length).toBe(3);
    const a = store.componentData.find(x => x.id === 'a');
    expect(a.style).toEqual({ top: 100, left: 200, width: 100, height: 50 });
    const b = store.componentData.find(x => x.id === 'b');
    expect(b.blockId).toBe(9); // blockId 引用不变
    expect(store.curComponents.map(x => x.id).sort()).toEqual(['a', 'b']);
  });

  it('alignSelected 应用对齐 patch 并记快照', () => {
    store.recordSnapshot();
    store.setCurComponents(['a', 'b']);
    store.alignSelected('left');
    expect(store.componentData.find(x => x.id === 'a').style.left).toBe(200);
    expect(store.componentData.find(x => x.id === 'b').style.left).toBe(200);
    store.undo();
    expect(store.componentData.find(x => x.id === 'b').style.left).toBe(400);
  });

  it('distributeSelected 需 ≥3 个:2 个时不变,3 个时等间距', () => {
    store.setCurComponents(['a', 'b']);
    store.distributeSelected('h');
    expect(store.componentData.find(x => x.id === 'b').style.left).toBe(400); // 不变
    store.setCurComponents(['a', 'b', 'c']);
    store.distributeSelected('h');
    // 按 left 排序 c(50,w80) a(200,w100) b(400,w300):区间 [130,400] 宽 270,gap=(270-100)/2=85
    expect(store.componentData.find(x => x.id === 'a').style.left).toBe(215);
  });

  it('moveComponentIndex 图层拖拽排序(splice 移动)并记快照', () => {
    store.recordSnapshot();
    store.moveComponentIndex(0, 2); // a 移到末尾
    expect(store.componentData.map(x => x.id)).toEqual(['b', 'c', 'a']);
    store.undo();
    expect(store.componentData.map(x => x.id)).toEqual(['a', 'b', 'c']);
  });

  it('renameComponent 改名并记快照;name 随 toSavePayload 进 JSON', () => {
    store.renameComponent('a', '主背景框');
    expect(store.componentData.find(x => x.id === 'a').name).toBe('主背景框');
    const payload = store.toSavePayload();
    expect(payload.components.find(x => x.id === 'a').name).toBe('主背景框');
  });
});
