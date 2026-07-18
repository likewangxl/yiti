// 成组/解组坐标换算纯函数——参照 DataEase compose/decompose 语义裁剪:
// 成组 = 生成 Group 节点(style=选区包围盒,children 坐标转相对组左上角);
// 解组 = children 相对坐标 + 组左上角回填绝对坐标;
// 组 8 点缩放 = children 相对坐标与尺寸按新旧尺寸比例换算(children 始终与组当前尺寸自洽,
// 不引入"基准尺寸+百分比"的有状态模型,与坐标幂等原则一致)。
// 约束:组不嵌套——makeGroup 遇到成员是 Group 时先展开吸收其 children(DataEase 合并语义)。

function deepClone(o) { return JSON.parse(JSON.stringify(o)); }

/** 与画布组件同一 id 命名规则(registry.newComponentFromMeta / clipboard 口径) */
function genId() { return 'w-' + Math.random().toString(36).slice(2, 8); }

/** 多矩形最小外接矩形 */
export function boundingRect(rects) {
  let top = Infinity, left = Infinity, right = -Infinity, bottom = -Infinity;
  for (const r of rects) {
    top = Math.min(top, r.top);
    left = Math.min(left, r.left);
    right = Math.max(right, r.left + r.width);
    bottom = Math.max(bottom, r.top + r.height);
  }
  return { top, left, width: right - left, height: bottom - top };
}

/**
 * 成组:components(≥2 个顶层节点,绝对坐标)→ Group 节点。
 * children 深拷贝且坐标转为相对组左上角;成员含 Group 时展开吸收其 children(组不嵌套);
 * 图表子组件 blockId/innerType 等字段原样保留。不足 2 个返回 null。
 */
export function makeGroup(components) {
  if (!Array.isArray(components) || components.length < 2) return null;
  // 先把成员统一摊平成"绝对坐标节点"列表(Group 成员展开吸收)
  const flat = [];
  for (const c of components) {
    if (c.component === 'Group' && Array.isArray(c.children)) {
      flat.push(...ungroup(c));
    } else {
      flat.push(deepClone(c));
    }
  }
  const box = boundingRect(flat.map(c => c.style));
  const children = flat.map(c => {
    c.style = { ...c.style, top: c.style.top - box.top, left: c.style.left - box.left };
    return c;
  });
  return {
    id: genId(),
    component: 'Group',
    style: { ...box },
    propValue: {},
    children,
    isLock: false,
    isShow: true
  };
}

/**
 * 解组:Group 节点 → children 数组(回填绝对坐标 = 组左上角 + 相对坐标)。
 * 非 Group 节点返回空数组。返回的是深拷贝,不 mutate 入参。
 */
export function ungroup(groupNode) {
  if (!groupNode || groupNode.component !== 'Group' || !Array.isArray(groupNode.children)) return [];
  const { top, left } = groupNode.style;
  return groupNode.children.map(ch => {
    const c = deepClone(ch);
    c.style = { ...c.style, top: c.style.top + top, left: c.style.left + left };
    return c;
  });
}

/**
 * 组缩放换算:children(相对坐标)按 fromSize→toSize 比例换算坐标与尺寸。
 * 宽高下限 1(后端 validateStyle 拒绝 <1);保留 2 位小数抑制连续缩放的累积误差与 JSON 膨胀。
 * 返回新数组,不 mutate 原 children。
 */
export function scaleGroupChildren(children, fromSize, toSize) {
  const sx = fromSize.width > 0 ? toSize.width / fromSize.width : 1;
  const sy = fromSize.height > 0 ? toSize.height / fromSize.height : 1;
  const round2 = n => Math.round(n * 100) / 100;
  return (children || []).map(ch => {
    const c = deepClone(ch);
    c.style = {
      ...c.style,
      top: round2(c.style.top * sy),
      left: round2(c.style.left * sx),
      width: Math.max(1, round2(c.style.width * sx)),
      height: Math.max(1, round2(c.style.height * sy))
    };
    return c;
  });
}
