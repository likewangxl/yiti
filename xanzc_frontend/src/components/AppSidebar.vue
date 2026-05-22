<template>
  <aside class="side">
    <div class="logo">
      <span class="mark">银</span>
      <span>银行营销平台</span>
    </div>

    <div v-loading="loading">
      <template v-for="m in menus" :key="m.resourceId">
        <!-- 顶层叶子菜单（无子节点）—— 直接单项 -->
        <router-link
          v-if="!m.children || !m.children.length"
          :to="m.resourceUrl"
          class="item root-item"
          :class="{ active: route.path === m.resourceUrl }"
        >
          <span>{{ m.menuName }}</span>
        </router-link>

        <!-- 分组节点（有 children）—— 可展开/折叠 -->
        <template v-else>
          <div class="parent" :class="{ open: openMap[m.resourceId] }" @click="toggle(m.resourceId)">
            <span>{{ m.menuName }}</span>
            <span class="chev">▸</span>
          </div>
          <div v-show="openMap[m.resourceId]" class="children">
            <router-link
              v-for="c in m.children" :key="c.resourceId"
              :to="c.resourceUrl"
              class="item"
              :class="{ active: route.path === c.resourceUrl }"
            >
              <span class="dot"></span>
              <span>{{ c.menuName }}</span>
            </router-link>
          </div>
        </template>
      </template>
    </div>
  </aside>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { getMyMenus } from '@/api/auth';

const route = useRoute();
// 直接从 my-menus 渲染，不再用 router.options.routes 作为基础。
// 这样在「菜单管理」新建一条菜单 + 给角色绑定后，刷新页面 sidebar 立即出现。
const menus = ref([]);
const loading = ref(false);
const openMap = reactive({});

async function load() {
  loading.value = true;
  try {
    const tree = await getMyMenus();
    menus.value = Array.isArray(tree) ? tree : [];
    // 默认所有分组节点展开（按 resourceId）
    for (const m of menus.value) {
      if (m.children && m.children.length) openMap[m.resourceId] = true;
    }
  } catch {
    menus.value = [];
  } finally {
    loading.value = false;
  }
}
function toggle(id) { openMap[id] = !openMap[id]; }

onMounted(load);
</script>

<style lang="scss" scoped>
.side {
  background: $side-bg;
  color: $side-text;
  overflow-y: auto;
  border-right: 1px solid $side-bg-2;
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: rgba(255,255,255,.13); border-radius: 3px; }
}
.logo {
  height: $header-h;
  display: flex; align-items: center; gap: 10px;
  padding: 0 16px;
  color: #fff; font-weight: 600; font-size: 14px;
  border-bottom: 1px solid $side-bg-2;
  letter-spacing: .3px;
  position: sticky; top: 0; background: $side-bg; z-index: 1;
  .mark {
    width: 26px; height: 26px; border-radius: 4px;
    background: linear-gradient(135deg, $primary-400, $primary);
    display: grid; place-items: center;
    color: #fff; font-size: 13px; font-weight: 700;
    flex-shrink: 0;
  }
}
.parent {
  padding: 9px 16px;
  font-size: 13px;
  cursor: pointer;
  color: $side-text;
  display: flex; align-items: center; gap: 8px;
  &:hover { color: #fff; }
  .ico { width: 16px; opacity: .8; }
  .chev { margin-left: auto; font-size: 10px; opacity: .6; transition: transform .15s; }
  &.open .chev { transform: rotate(90deg); }
}
.item {
  padding: 8px 16px 8px 30px;
  font-size: 13px;
  cursor: pointer;
  display: flex; align-items: center; gap: 8px;
  border-left: 2px solid transparent;
  white-space: nowrap;
  text-decoration: none;
  color: $side-text;
  &:hover { background: $side-bg-2; color: #fff; }
  &.active {
    background: linear-gradient(90deg, $side-bg-3 0%, #002a55 100%);
    color: #fff;
    border-left-color: #4d8be8;
    .dot { background: #60a5fa; }
  }
  .dot { width: 4px; height: 4px; border-radius: 50%; background: #475569; }
  .ico { width: 16px; }
}
.root-item { padding-left: 16px; }
</style>
