import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import {
  createSnapshotStack, record as snapRecord, undo as snapUndo,
  redo as snapRedo, canUndo as snapCanUndo, canRedo as snapCanRedo, deepClone
} from '@/views/screen/designer/utils/snapshotStack';
import { clampRect } from '@/views/screen/designer/utils/scale';
import { normalizeCanvasStyle } from '@/views/screen/designer/utils/background';
import { alignRects, distributeRects } from '@/views/screen/designer/utils/align';
import { makeGroup, ungroup } from '@/views/screen/designer/utils/group';

/**
 * 大屏设计器 store(setup-store 写法,对齐 stores/user.js)。
 * - componentData:组件树(图层顺序=数组顺序,无 zIndex,照搬 DataEase)
 * - curComponent:选中组件的同一响应式引用(属性面板直接 mutate,画布自动联动)
 * - 坐标恒 1920×1080 设计基准
 */
export const useScreenDesignerStore = defineStore('screenDesigner', () => {
  const screenId = ref(null);
  const screenCode = ref('');
  const viewLevel = ref('BRANCH');
  const canvasVersion = ref(0);
  const publishStatus = ref(0);
  // 背景增强(2026-07-17):backgroundType 纯色/渐变/图片三选一 + bgGradient/bgImage,
  // 旧 canvas_style_json(仅 background 纯色)由 normalizeCanvasStyle 读时补默认,不写迁移
  const canvasStyle = ref(normalizeCanvasStyle({ schemaVersion: 1, designWidth: 1920, designHeight: 1080,
    background: '#050e2b', adaptor: 'keepProportion', themeOverride: {} }));
  // CANVAS_DRAFT_JSON 契约的顶层 schemaVersion(与 canvasStyle.schemaVersion 是两套独立版本号,
  // 分别对应 draft/style 两份 JSON 契约);loadFromEditor 装载、toSavePayload 原样带回(评审 Important-2)。
  const draftSchemaVersion = ref(1);
  const componentData = ref([]);
  const curComponent = ref(null);
  const curIndex = ref(-1);
  // 多选选中集(组件树内同一响应式引用数组)。单选 = 长度 1 的特例:curComponent 仅在
  // 恰好选中 1 个时有值(属性面板只在单选时显示),多选时为 null、右栏切多选工具条。
  const curComponents = ref([]);
  const blocks = ref([]);           // 区块行(ChartWidget 按 blockId 关联)
  const scale = ref(0.5);           // 编辑器画布缩放(50%~150% + 适应窗口)
  const dirty = ref(false);

  // 快照栈 + 3 秒防抖(防抖属交互层,故放 store)
  let stack = createSnapshotStack(60);
  let snapshotDisableUntil = 0;
  let debounceTimer = null;
  // stack 是普通闭包变量,Vue 响应式系统追踪不到其内部读写;canUndo/canRedo 的 computed
  // 若只读 stack 本身,首次求值后既无依赖可触发重算,会永久缓存首次结果(评审 Important-1)。
  // 用这个版本号 ref 作为显式响应式依赖:每次栈发生实际变化就自增,逼 computed 重新求值。
  const stackVersion = ref(0);

  const canUndo = computed(() => { void stackVersion.value; return snapCanUndo(stack); });
  const canRedo = computed(() => { void stackVersion.value; return snapCanRedo(stack); });

  function snapshotBody() {
    return { componentData: deepClone(componentData.value), canvasStyle: deepClone(canvasStyle.value) };
  }
  function applySnapshot(snap) {
    if (!snap) return;
    componentData.value = deepClone(snap.componentData);
    canvasStyle.value = deepClone(snap.canvasStyle);
    curComponent.value = null;
    curIndex.value = -1;
    curComponents.value = [];
  }
  /**
   * 立即记快照(3 秒防抖窗口内跳过,避免连续拖拽污染栈——对齐 snapshot.ts snapshotDisableTime)。
   * 与栈顶快照内容相同则跳过(去重):离散操作(addComponent 等)已各自记过一次,
   * 调用方在同一状态上重复调用不应再压栈,否则 undo 会先弹出一份"无变化"的冗余快照。
   */
  function recordSnapshot() {
    if (Date.now() < snapshotDisableUntil) return;
    const body = snapshotBody();
    const top = stack.data[stack.index];
    if (top && JSON.stringify(top) === JSON.stringify(body)) return;
    stack = snapRecord(stack, body);
    stackVersion.value++;
    dirty.value = true;
  }
  /** 防抖记快照(拖拽/属性连续修改用) */
  function pushSnapshotDebounced() {
    dirty.value = true;
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => recordSnapshot(), 300);
  }
  function undo() {
    applySnapshot(snapUndo(stack));
    stackVersion.value++;
    snapshotDisableUntil = Date.now() + 3000;
  }
  function redo() {
    applySnapshot(snapRedo(stack));
    stackVersion.value++;
    snapshotDisableUntil = Date.now() + 3000;
  }

  // ===== 装载 / 序列化 =====
  function loadFromEditor(resp) {
    screenId.value = resp.screenId;
    screenCode.value = resp.screenCode || '';
    viewLevel.value = resp.viewLevel || 'BRANCH';
    canvasVersion.value = resp.canvasVersion ?? 0;
    publishStatus.value = resp.publishStatus ?? 0;
    blocks.value = resp.blocks || [];
    // 读时兼容:旧 canvas_style_json 缺 backgroundType/bgGradient/bgImage 时补默认(唯一适配点)
    canvasStyle.value = normalizeCanvasStyle(parse(resp.canvasStyleJson, canvasStyle.value));
    const draft = parse(resp.canvasDraftJson, { components: [] });
    draftSchemaVersion.value = draft.schemaVersion ?? 1;
    componentData.value = Array.isArray(draft.components) ? draft.components : [];
    curComponent.value = null;
    curIndex.value = -1;
    stack = createSnapshotStack(60);
    recordSnapshot();
    dirty.value = false;
  }
  function toSavePayload() {
    return {
      screenId: screenId.value,
      expectedVersion: canvasVersion.value,
      schemaVersion: draftSchemaVersion.value,
      canvasStyle: deepClone(canvasStyle.value),
      // ChartWidget 携带 bind/style/drill(供后端 upsert block);素材组件带 propValue
      components: componentData.value.map(c => deepClone(c))
    };
  }
  function adoptSaveResult(resp) {
    canvasVersion.value = resp.canvasVersion;
    const draft = parse(resp.canvasDraftJson, { components: [] });
    draftSchemaVersion.value = draft.schemaVersion ?? draftSchemaVersion.value;
    componentData.value = Array.isArray(draft.components) ? draft.components : componentData.value;
    dirty.value = false;
  }

  // ===== 组件增删选中 =====
  function addComponent(node) {
    componentData.value.push(node);
    selectComponent(node.id);
    recordSnapshot();
  }
  function removeCurrent() {
    if (curIndex.value < 0) return;
    componentData.value.splice(curIndex.value, 1);
    curComponent.value = null;
    curIndex.value = -1;
    curComponents.value = [];
    recordSnapshot();
  }
  function selectComponent(id) {
    const i = componentData.value.findIndex(c => c.id === id);
    curIndex.value = i;
    curComponent.value = i >= 0 ? componentData.value[i] : null;
    // 单选是多选的长度 1 特例:保持 curComponents 与 curComponent 同步
    curComponents.value = i >= 0 ? [componentData.value[i]] : [];
  }
  function setShapeStyle(patch) {
    if (!curComponent.value) return;
    curComponent.value.style = { ...curComponent.value.style, ...patch };
  }

  // ===== 多选(框选/Ctrl+点选;单选=长度 1 特例) =====
  /** 统一选区归一化:恰好 1 个时退化为单选(curComponent 有值),否则 curComponent=null */
  function syncSelection(list) {
    curComponents.value = list;
    if (list.length === 1) {
      curComponent.value = list[0];
      curIndex.value = componentData.value.indexOf(list[0]);
    } else {
      curComponent.value = null;
      curIndex.value = -1;
    }
  }
  /** 按 id 列表设置多选(框选命中/解组后全选子组件用);无效 id 静默跳过 */
  function setCurComponents(ids) {
    const list = (ids || [])
      .map(id => componentData.value.find(c => c.id === id))
      .filter(Boolean);
    syncSelection(list);
  }
  /** Ctrl+点击:选中集中有则移出,无则加入 */
  function toggleSelect(id) {
    const c = componentData.value.find(x => x.id === id);
    if (!c) return;
    const list = curComponents.value.includes(c)
      ? curComponents.value.filter(x => x !== c)
      : [...curComponents.value, c];
    syncSelection(list);
  }
  /** 清空选区(Esc / 画布空白点击) */
  function clearSelection() { syncSelection([]); }
  /** 批量删除选中组件(Del;单选场景同样走此口径) */
  function removeSelected() {
    if (!curComponents.value.length) return;
    const ids = new Set(curComponents.value.map(c => c.id));
    componentData.value = componentData.value.filter(c => !ids.has(c.id));
    syncSelection([]);
    recordSnapshot();
  }
  /** 批量微移(方向键):跳过锁定组件,逐个 clamp 画布内;连续按键走防抖快照 */
  function nudgeSelected(dx, dy) {
    const targets = curComponents.value.filter(c => !c.isLock);
    if (!targets.length) return;
    for (const c of targets) {
      const next = clampRect({ ...c.style, top: c.style.top + dy, left: c.style.left + dx });
      c.style = { ...c.style, top: next.top, left: next.left };
    }
    pushSnapshotDebounced();
  }

  // ===== 成组/解组 =====
  /** 多选成组:成员从顶层移除,Group 插入到原最上层成员的位置(保持视觉层级),并选中组 */
  function groupSelected() {
    const members = curComponents.value;
    if (members.length < 2) return;
    const g = makeGroup(members);
    if (!g) return;
    const idSet = new Set(members.map(c => c.id));
    const indices = [];
    componentData.value.forEach((c, i) => { if (idSet.has(c.id)) indices.push(i); });
    // 移除成员后,原最上层成员的位置 = 其原 index - 排在它前面的成员数
    const insertAt = indices[indices.length - 1] - (indices.length - 1);
    componentData.value = componentData.value.filter(c => !idSet.has(c.id));
    componentData.value.splice(insertAt, 0, g);
    selectComponent(g.id);
    recordSnapshot();
  }
  /** 解组:children 回填绝对坐标插回组所在位置,并多选全部子组件 */
  function ungroupSelected() {
    const g = curComponents.value.length === 1 && curComponents.value[0].component === 'Group'
      ? curComponents.value[0] : null;
    if (!g) return;
    const children = ungroup(g);
    if (!children.length) return;
    const i = componentData.value.findIndex(c => c.id === g.id);
    componentData.value.splice(i, 1, ...children);
    syncSelection(componentData.value.slice(i, i + children.length));
    recordSnapshot();
  }

  // ===== 对齐/分布(多选工具条) =====
  function applyPositionPatches(patches) {
    for (const p of patches) {
      const c = componentData.value.find(x => x.id === p.id);
      if (c) c.style = { ...c.style, top: p.top, left: p.left };
    }
  }
  /** 对齐:type ∈ left|right|top|bottom|hcenter|vcenter;跳过锁定组件 */
  function alignSelected(type) {
    const movable = curComponents.value.filter(c => !c.isLock);
    const patches = alignRects(type, movable.map(c => ({ id: c.id, ...c.style })));
    if (!patches.length) return;
    applyPositionPatches(patches);
    recordSnapshot();
  }
  /** 等间距分布:dir ∈ h|v,≥3 个可用;跳过锁定组件 */
  function distributeSelected(dir) {
    const movable = curComponents.value.filter(c => !c.isLock);
    const patches = distributeRects(dir, movable.map(c => ({ id: c.id, ...c.style })));
    if (!patches.length) return;
    applyPositionPatches(patches);
    recordSnapshot();
  }

  // ===== 图层拖拽排序 / 组件改名 =====
  /** 图层面板拖拽排序:数组内 splice 移动(from→to 均为 componentData 下标) */
  function moveComponentIndex(from, to) {
    const arr = componentData.value;
    if (from === to || from < 0 || to < 0 || from >= arr.length || to >= arr.length) return;
    const [item] = arr.splice(from, 1);
    arr.splice(to, 0, item);
    if (curComponent.value) curIndex.value = arr.findIndex(c => c.id === curComponent.value.id);
    recordSnapshot();
  }
  /** 组件改名:name 进组件树 JSON(读时兼容:无 name 时图层面板显示组件类型 label) */
  function renameComponent(id, name) {
    const c = componentData.value.find(x => x.id === id);
    if (!c) return;
    c.name = (name || '').trim();
    recordSnapshot();
  }

  // ===== 图层(参照 layer.ts:数组内 swap / splice) =====
  function swap(i, j) {
    const arr = componentData.value;
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  function upComponent() {
    if (curIndex.value >= 0 && curIndex.value < componentData.value.length - 1) {
      swap(curIndex.value, curIndex.value + 1); curIndex.value++; recordSnapshot();
    }
  }
  function downComponent() {
    if (curIndex.value > 0) { swap(curIndex.value, curIndex.value - 1); curIndex.value--; recordSnapshot(); }
  }
  function topComponent() {
    if (curIndex.value >= 0 && curIndex.value < componentData.value.length - 1) {
      const [c] = componentData.value.splice(curIndex.value, 1);
      componentData.value.push(c);
      curIndex.value = componentData.value.length - 1; recordSnapshot();
    }
  }
  function bottomComponent() {
    if (curIndex.value > 0) {
      const [c] = componentData.value.splice(curIndex.value, 1);
      componentData.value.unshift(c);
      curIndex.value = 0; recordSnapshot();
    }
  }
  function toggleLock(id) {
    const c = componentData.value.find(x => x.id === id);
    if (c) { c.isLock = !c.isLock; recordSnapshot(); }
  }
  function toggleShow(id) {
    const c = componentData.value.find(x => x.id === id);
    if (c) { c.isShow = c.isShow === false ? true : false; recordSnapshot(); }
  }

  function parse(json, fallback) {
    try { return json ? JSON.parse(json) : fallback; } catch { return fallback; }
  }

  return {
    screenId, screenCode, viewLevel, canvasVersion, publishStatus,
    canvasStyle, draftSchemaVersion, componentData, curComponent, curIndex, curComponents, blocks, scale, dirty,
    canUndo, canRedo,
    loadFromEditor, toSavePayload, adoptSaveResult,
    addComponent, removeCurrent, selectComponent, setShapeStyle,
    setCurComponents, toggleSelect, clearSelection, removeSelected, nudgeSelected,
    groupSelected, ungroupSelected, alignSelected, distributeSelected,
    moveComponentIndex, renameComponent,
    upComponent, downComponent, topComponent, bottomComponent, toggleLock, toggleShow,
    recordSnapshot, pushSnapshotDebounced, undo, redo
  };
});
