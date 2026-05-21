<template>
  <aside class="side">
    <div class="logo">
      <span class="mark">银</span>
      <span>银行营销平台</span>
    </div>

    <div v-for="grp in groups" :key="grp.title" class="group">
      <div v-if="grp.title === '__root__'" class="root-list">
        <router-link
          v-for="r in grp.items" :key="r.path"
          :to="r.path"
          class="item"
          :class="{ active: route.path === r.path }"
        >
          <span class="ico">{{ r.icon || '·' }}</span>
          <span>{{ r.title }}</span>
        </router-link>
      </div>
      <template v-else>
        <div class="parent" :class="{ open: openMap[grp.title] }" @click="toggle(grp.title)">
          <span class="ico">{{ grp.icon }}</span>
          <span>{{ grp.title }}</span>
          <span class="chev">▸</span>
        </div>
        <div v-show="openMap[grp.title]" class="children">
          <router-link
            v-for="r in grp.items" :key="r.path"
            :to="r.path"
            class="item"
            :class="{ active: route.path === r.path }"
          >
            <span class="dot"></span>
            <span>{{ r.title }}</span>
          </router-link>
        </div>
      </template>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getMyMenus } from '@/api/auth';

const router = useRouter();
const route = useRoute();

// 当前用户可见菜单 path 集合：null = 还没拉到 → 全显（避免登录瞬间空白）；Set → 按权限过滤
const allowedPaths = ref(null);

async function loadMyMenus() {
  try {
    const tree = await getMyMenus();
    const paths = new Set();
    (function walk(nodes) {
      if (!nodes) return;
      for (const n of nodes) {
        if (n.resourceUrl) paths.add(n.resourceUrl);
        if (n.children) walk(n.children);
      }
    })(tree);
    allowedPaths.value = paths;
  } catch {
    // 拉失败保持 null（全显），不阻塞 sidebar
    allowedPaths.value = null;
  }
}
onMounted(loadMyMenus);

// 把 router 表按 group 分组
// 注意：顶层 routes 现在第 0 项是 /login，业务路由在 path:'/' 那一项的 children
const groups = computed(() => {
  const layoutRoute = router.options.routes.find(r => r.path === '/' && r.children?.length);
  const all = layoutRoute?.children || [];
  const root = [];
  const grouped = {};
  for (const r of all) {
    // 跳过没 title 的子路由（redirect / 空 path / 占位项），否则会渲染成"空白菜单"
    if (!r.meta?.title || !r.path) continue;
    // 隐藏路由（meta.hidden）不进 sidebar：如审批办理详情页 /workflow/task/:taskId
    if (r.meta?.hidden) continue;
    const path = '/' + r.path;
    // 按权限过滤：拉到了菜单清单且当前 path 不在集合中 → 跳过
    if (allowedPaths.value !== null && !allowedPaths.value.has(path)) continue;
    const item = { path, title: r.meta.title, icon: r.meta?.icon };
    if (!r.meta?.group) {
      root.push(item);
    } else {
      (grouped[r.meta.group] ??= []).push(item);
    }
  }
  const result = [];
  if (root.length) result.push({ title: '__root__', items: root });
  const groupOrder = [
    { title: '绩效与考核', icon: '📈' },
    { title: '报表分析',   icon: '📊' },
    { title: '系统设置',   icon: '⚙' }
  ];
  for (const g of groupOrder) {
    if (grouped[g.title]) result.push({ ...g, items: grouped[g.title] });
  }
  return result;
});

// 默认全部展开（截图里也是全开的）
const openMap = reactive({ '绩效与考核': true, '报表分析': true, '系统设置': true });
function toggle(t) { openMap[t] = !openMap[t]; }
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
.root-list .item { padding-left: 16px; }
</style>
