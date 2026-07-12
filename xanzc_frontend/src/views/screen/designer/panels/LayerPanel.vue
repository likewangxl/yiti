<template>
  <div class="dsn-layer">
    <div class="toolbar">
      <el-button link size="small" @click="store.topComponent()">置顶</el-button>
      <el-button link size="small" @click="store.bottomComponent()">置底</el-button>
      <el-button link size="small" @click="store.upComponent()">上移</el-button>
      <el-button link size="small" @click="store.downComponent()">下移</el-button>
    </div>
    <div v-for="c in reversed" :key="c.id" class="layer-item"
         :class="{ on: store.curComponent?.id === c.id }" @click="store.selectComponent(c.id)">
      <span class="name">{{ labelOf(c) }}</span>
      <span class="ops">
        <el-icon @click.stop="store.toggleShow(c.id)"><View v-if="c.isShow !== false" /><Hide v-else /></el-icon>
        <el-icon @click.stop="store.toggleLock(c.id)"><Lock v-if="c.isLock" /><Unlock v-else /></el-icon>
      </span>
    </div>
  </div>
</template>
<script setup>
// 不引入 vuedraggable(守零运行时新依赖);拖拽排序二期,一期用置顶/置底/上移/下移按钮。
import { computed } from 'vue';
import { View, Hide, Lock, Unlock } from '@element-plus/icons-vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const store = useScreenDesignerStore();
const reversed = computed(() => [...store.componentData].reverse()); // 顶层(数组末尾)显示在最上
function labelOf(c) {
  return c.component === 'ChartWidget' ? `图表·${c.innerType}` : c.component + (c.propValue?.text ? `(${c.propValue.text})` : '');
}
</script>
<style scoped>
.dsn-layer { padding: 8px; } .toolbar { display: flex; gap: 4px; margin-bottom: 6px; }
.layer-item { display: flex; justify-content: space-between; align-items: center; padding: 6px 8px;
  border-radius: 4px; cursor: pointer; color: #d5e6ff; font-size: 12px; }
.layer-item:hover, .layer-item.on { background: rgba(0,229,255,.12); }
.ops { display: flex; gap: 8px; color: #7d9bc9; }
</style>
