<template>
  <ul v-show="visible" class="dsn-ctx" :style="{ top: top + 'px', left: left + 'px' }"
      @mouseleave="hide">
    <li @click="act('copy')">复制</li>
    <li @click="act('paste')">粘贴</li>
    <li class="danger" @click="act('delete')">删除</li>
    <li class="sep" />
    <li @click="act('top')">置顶</li>
    <li @click="act('bottom')">置底</li>
    <li @click="act('up')">上移一层</li>
    <li @click="act('down')">下移一层</li>
    <li class="sep" />
    <li @click="act('lock')">{{ lockedLabel }}</li>
  </ul>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const store = useScreenDesignerStore();
const visible = ref(false);
const top = ref(0);
const left = ref(0);
let clipboard = null;

const lockedLabel = computed(() => store.curComponent?.isLock ? '解锁' : '锁定');

function show(x, y) { visible.value = true; top.value = y; left.value = x; }
function hide() { visible.value = false; }

function act(type) {
  const cur = store.curComponent;
  switch (type) {
    case 'copy': clipboard = cur ? JSON.parse(JSON.stringify(cur)) : null; break;
    case 'paste':
      if (clipboard) {
        const node = JSON.parse(JSON.stringify(clipboard));
        node.id = 'w-' + Math.random().toString(36).slice(2, 8);
        node.style = { ...node.style, top: (node.style.top || 0) + 20, left: (node.style.left || 0) + 20 };
        store.addComponent(node);
      }
      break;
    case 'delete': store.removeCurrent(); break;
    case 'top': store.topComponent(); break;
    case 'bottom': store.bottomComponent(); break;
    case 'up': store.upComponent(); break;
    case 'down': store.downComponent(); break;
    case 'lock': if (cur) store.toggleLock(cur.id); break;
  }
  hide();
}

defineExpose({ show, hide });
</script>

<style scoped>
.dsn-ctx { position: absolute; z-index: 2000; min-width: 120px; padding: 4px 0; margin: 0;
  list-style: none; background: #0a1f4e; border: 1px solid rgba(0,229,255,.3); border-radius: 4px;
  box-shadow: 0 4px 16px rgba(0,0,0,.4); color: #d5e6ff; font-size: 13px; }
.dsn-ctx li { padding: 6px 16px; cursor: pointer; }
.dsn-ctx li:hover { background: rgba(0,229,255,.12); }
.dsn-ctx li.danger:hover { background: rgba(255,82,82,.2); }
.dsn-ctx li.sep { height: 1px; padding: 0; margin: 4px 0; background: rgba(0,229,255,.15); cursor: default; }
</style>
