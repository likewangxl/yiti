// 画布组件复制/粘贴的纯逻辑——DesignerV2 顶层快捷键 Ctrl+C/Ctrl+V 与
// canvas/ContextMenu.vue 右键菜单的复制/粘贴对齐同一口径(深拷贝 + 新 id + top/left 各 +20)。
// 不强行抽公共层给 ContextMenu.vue 复用(它是自包含闭包变量,改造成本与收益不对等),
// 这里只保证两个入口"粘贴出来的东西长得一样",并让该逻辑可独立单测。

/** 复制:深拷贝一份节点放入剪贴板;传 null(未选中组件)原样返回 null。 */
export function cloneComponentForClipboard(node) {
  return node ? JSON.parse(JSON.stringify(node)) : null;
}

/**
 * 粘贴:从剪贴板内容生成一个可直接 store.addComponent() 的新节点——
 * 新 id(与 registry.newComponentFromMeta / ContextMenu.vue 同一命名规则 'w-'+随机串,
 * 避免与画布现有组件 id 冲突),top/left 各加 offset(默认 20,对齐 ContextMenu.vue 现状),
 * 不做 clampRect 兜底(与 ContextMenu.vue 现状一致,越界由后续拖拽/属性面板修正)。
 */
export function pasteFromClipboard(clip, offset = 20) {
  if (!clip) return null;
  const node = JSON.parse(JSON.stringify(clip));
  node.id = 'w-' + Math.random().toString(36).slice(2, 8);
  node.style = { ...node.style, top: (node.style.top || 0) + offset, left: (node.style.left || 0) + offset };
  return node;
}
