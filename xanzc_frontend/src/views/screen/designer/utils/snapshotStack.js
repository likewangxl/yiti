// 撤销/重做快照栈——参照 DataEase snapshot.ts 的「全量深拷贝快照数组 + 指针前进/后退」改写为纯函数。
// 与原实现差异:①剥离 store/eventBus/localStorage 副作用,只管栈结构,便于 vitest 覆盖;
// ②3 秒防抖(snapshotDisableTime)属交互层,放在 store 里,不进纯函数;
// ③加 limit 容量上限(DataEase 无上限),防长时间编辑内存膨胀。

export function deepClone(o) {
  return o == null ? o : JSON.parse(JSON.stringify(o));
}

export function createSnapshotStack(limit = 50) {
  return { data: [], index: -1, limit };
}

/** 记录一份全量深拷贝快照;undo 后再 record 会截断 redo 分支(对齐 snapshot.ts slice) */
export function record(stack, snapshot) {
  if (stack.index < stack.data.length - 1) {
    stack.data = stack.data.slice(0, stack.index + 1);
  }
  stack.data.push(deepClone(snapshot));
  if (stack.data.length > stack.limit) {
    stack.data.shift();
  }
  stack.index = stack.data.length - 1;
  return stack;
}

export function undo(stack) {
  if (stack.index > 0) stack.index--;
  return currentSnapshot(stack);
}

export function redo(stack) {
  if (stack.index < stack.data.length - 1) stack.index++;
  return currentSnapshot(stack);
}

export function canUndo(stack) { return stack.index > 0; }
export function canRedo(stack) { return stack.index < stack.data.length - 1; }

export function currentSnapshot(stack) {
  return stack.index >= 0 ? deepClone(stack.data[stack.index]) : null;
}
