// 动态查询对象选择器的数据范围裁剪(纯函数,便于单测)。
// 从 Dynamic.vue 抽取,逻辑逐字保持:保留 code 命中、或有命中后代的节点(容器)。
export function filterTreeByCodes(nodes, codeSet = new Set()) {
  // 数据范围缺失时按最小权限处理，不能因为组件初始化异常而放大到整棵机构树。
  if (!(codeSet instanceof Set) || codeSet.size === 0) return [];
  const out = [];
  for (const n of nodes || []) {
    const children = filterTreeByCodes(n.children, codeSet);
    if (codeSet.has(n.code) || children.length) {
      out.push({ ...n, children });
    }
  }
  return out;
}
