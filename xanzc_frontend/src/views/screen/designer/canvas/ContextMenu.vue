<template>
  <ul v-show="visible" class="dsn-ctx" :style="{ top: top + 'px', left: left + 'px' }"
      @mouseleave="hide">
    <!-- 多选态:只保留批量语义的条目(成组/删除);单组件条目在单选态显示 -->
    <li v-if="multi" @click="act('group')">成组</li>
    <li v-if="isGroup" @click="act('ungroup')">解组</li>
    <template v-if="!multi">
      <li @click="act('copy')">复制</li>
      <li @click="act('paste')">粘贴</li>
    </template>
    <li class="danger" @click="act('delete')">删除</li>
    <template v-if="!multi">
      <li class="sep" />
      <li @click="act('top')">置顶</li>
      <li @click="act('bottom')">置底</li>
      <li @click="act('up')">上移一层</li>
      <li @click="act('down')">下移一层</li>
      <li class="sep" />
      <li @click="act('lock')">{{ lockedLabel }}</li>
    </template>
  </ul>
</template>

<script setup>
import { ref, computed } from 'vue';
import { pasteFromClipboard } from '@/views/screen/designer/utils/clipboard';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const store = useScreenDesignerStore();
const visible = ref(false);
const top = ref(0);
const left = ref(0);
let clipboard = null;

const lockedLabel = computed(() => store.curComponent?.isLock ? '解锁' : '锁定');
const multi = computed(() => store.curComponents.length > 1);
const isGroup = computed(() => store.curComponent?.component === 'Group');

function show(x, y) { visible.value = true; top.value = y; left.value = x; }
function hide() { visible.value = false; }

function act(type) {
  const cur = store.curComponent;
  switch (type) {
    case 'copy': clipboard = cur ? JSON.parse(JSON.stringify(cur)) : null; break;
    case 'paste': {
      // 统一走纯函数口径(新 id + 偏移 20;Group children id 一并重生成防重复 id)
      const node = pasteFromClipboard(clipboard);
      if (node) store.addComponent(node);
      break;
    }
    case 'delete': store.removeSelected(); break; // 批量口径(单选=长度 1 特例)
    case 'group': store.groupSelected(); break;
    case 'ungroup': store.ungroupSelected(); break;
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
