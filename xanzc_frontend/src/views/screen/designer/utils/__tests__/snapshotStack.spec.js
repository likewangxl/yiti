import { describe, it, expect } from 'vitest';
import { createSnapshotStack, record, undo, redo, canUndo, canRedo, currentSnapshot } from '../snapshotStack';

describe('snapshotStack.js 撤销重做(参照 DataEase snapshot.ts 全量深拷贝快照数组)', () => {
  it('record 后 index 前进,currentSnapshot 是深拷贝', () => {
    let st = createSnapshotStack();
    const a = { components: [{ id: 'w-1' }] };
    st = record(st, a);
    a.components[0].id = 'mutated'; // 改原对象不应影响快照(深拷贝)
    expect(currentSnapshot(st).components[0].id).toBe('w-1');
  });

  it('undo/redo 指针移动', () => {
    let st = createSnapshotStack();
    st = record(st, { v: 1 });
    st = record(st, { v: 2 });
    st = record(st, { v: 3 });
    expect(canUndo(st)).toBe(true);
    expect(undo(st).v).toBe(2);
    expect(undo(st).v).toBe(1);
    expect(canUndo(st)).toBe(false); // 到底
    expect(redo(st).v).toBe(2);
  });

  it('undo 后 record 截断 redo 分支(对齐 snapshot.ts slice)', () => {
    let st = createSnapshotStack();
    st = record(st, { v: 1 });
    st = record(st, { v: 2 });
    undo(st);                 // 回到 v1
    st = record(st, { v: 9 }); // 新分支,截断 v2
    expect(canRedo(st)).toBe(false);
    expect(currentSnapshot(st).v).toBe(9);
  });

  it('超过 limit 丢弃最旧', () => {
    let st = createSnapshotStack(3);
    for (let i = 1; i <= 5; i++) st = record(st, { v: i });
    expect(st.data.length).toBe(3);
    expect(st.data[0].v).toBe(3); // 最旧 v1/v2 已丢
    expect(currentSnapshot(st).v).toBe(5);
  });
});
