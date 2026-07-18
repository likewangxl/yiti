<template>
  <div class="dsn-layer">
    <div class="toolbar">
      <el-button link size="small" @click="store.topComponent()">置顶</el-button>
      <el-button link size="small" @click="store.bottomComponent()">置底</el-button>
      <el-button link size="small" @click="store.upComponent()">上移</el-button>
      <el-button link size="small" @click="store.downComponent()">下移</el-button>
    </div>
    <div v-for="(c, di) in reversed" :key="c.id" class="layer-item"
         :class="{ on: isSelected(c.id), 'drag-over': overIndex === di }"
         draggable="true"
         @dragstart="onDragStart(di, $event)" @dragover.prevent="overIndex = di"
         @dragleave="overIndex === di && (overIndex = -1)" @drop.prevent="onDrop(di)"
         @dragend="onDragEnd"
         @click="store.selectComponent(c.id)" @dblclick="startRename(c)">
      <!-- 双击改名:行内输入框,Enter/失焦提交,Esc 取消 -->
      <input v-if="renamingId === c.id" class="rename" v-model="renameText"
             :ref="el => el && el.focus()" @click.stop @dblclick.stop
             @blur="commitRename" @keyup.enter="commitRename" @keyup.esc="cancelRename" />
      <span v-else class="name" :title="labelOf(c)">{{ labelOf(c) }}</span>
      <span class="ops">
        <el-icon @click.stop="store.toggleShow(c.id)"><View v-if="c.isShow !== false" /><Hide v-else /></el-icon>
        <el-icon @click.stop="store.toggleLock(c.id)"><Lock v-if="c.isLock" /><Unlock v-else /></el-icon>
      </span>
    </div>
  </div>
</template>
<script setup>
// 图层面板——原生 HTML5 drag 拖拽排序(守零运行时新依赖,不引 vuedraggable)+ 双击改名
// (组件树节点 name 字段,读时兼容:无 name 显示组件类型 label,name 随保存进 JSON)。
// 面板倒序展示(顶层=数组末尾=列表最上),显示下标 di 与数组下标换算:idx = length-1-di。
import { computed, ref } from 'vue';
import { View, Hide, Lock, Unlock } from '@element-plus/icons-vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const store = useScreenDesignerStore();
const reversed = computed(() => [...store.componentData].reverse()); // 顶层(数组末尾)显示在最上

function isSelected(id) { return store.curComponents.some(c => c.id === id); }
function labelOf(c) {
  if (c.name) return c.name; // 改名优先(读时兼容:旧 JSON 无 name 走类型 label)
  if (c.component === 'Group') return `成组(${(c.children || []).length})`;
  return c.component === 'ChartWidget' ? `图表·${c.innerType}` : c.component + (c.propValue?.text ? `(${c.propValue.text})` : '');
}

// ===== 原生 drag 拖拽排序 =====
const dragIndex = ref(-1);   // 拖起项的显示下标
const overIndex = ref(-1);   // 悬停项的显示下标(高亮插入位)
const toIdx = di => store.componentData.length - 1 - di; // 显示下标 → 数组下标
function onDragStart(di, e) {
  dragIndex.value = di;
  e.dataTransfer.effectAllowed = 'move';
  e.dataTransfer.setData('text/plain', String(di)); // Firefox 需 setData 才启动拖拽
}
function onDrop(di) {
  if (dragIndex.value >= 0 && dragIndex.value !== di) {
    store.moveComponentIndex(toIdx(dragIndex.value), toIdx(di)); // store 内记 undo 快照
  }
  onDragEnd();
}
function onDragEnd() { dragIndex.value = -1; overIndex.value = -1; }

// ===== 双击改名 =====
const renamingId = ref(null);
const renameText = ref('');
function startRename(c) { renamingId.value = c.id; renameText.value = c.name || ''; }
function commitRename() {
  if (renamingId.value !== null) {
    const c = store.componentData.find(x => x.id === renamingId.value);
    // 无变化不写(避免冗余 undo 快照);清空输入=还原默认类型 label(name 置空串,labelOf 回退)
    if (c && (c.name || '') !== renameText.value.trim()) {
      store.renameComponent(renamingId.value, renameText.value);
    }
  }
  renamingId.value = null;
}
function cancelRename() { renamingId.value = null; }
</script>
<style scoped>
.dsn-layer { padding: 8px; } .toolbar { display: flex; gap: 4px; margin-bottom: 6px; }
.layer-item { display: flex; justify-content: space-between; align-items: center; padding: 6px 8px;
  border-radius: 4px; cursor: pointer; color: #d5e6ff; font-size: 12px; }
.layer-item:hover, .layer-item.on { background: rgba(0,229,255,.12); }
.layer-item.drag-over { box-shadow: 0 -2px 0 0 #00e5ff inset, 0 2px 0 0 rgba(0,229,255,.5); }
.name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rename { flex: 1; min-width: 0; background: #0a1f4e; color: #d5e6ff; font-size: 12px;
  border: 1px solid rgba(0,229,255,.5); border-radius: 3px; padding: 2px 4px; outline: none; }
.ops { display: flex; gap: 8px; color: #7d9bc9; }
</style>
