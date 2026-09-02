<template>
  <el-container class="re-layout">
    <el-aside width="var(--layout-sidebar-width)" class="re-aside">
      <div class="re-sidebar">
        <div class="re-sidebar-logo">
          <component :is="Star" class="re-logo-icon" />
          <span class="re-logo-text">红色引擎</span>
        </div>

        <el-menu
          mode="vertical"
          :router="true"
          :default-active="route.path"
          background-color="#2d1b1b"
          text-color="#fff"
          active-text-color="#f87171"
          class="re-sidebar-menu"
        >
          <el-menu-item
            v-for="item in visibleMenuItems"
            :key="item.path"
            :index="item.path"
          >
            <component :is="iconMap[item.icon]" class="re-menu-icon" />
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-menu>
      </div>
    </el-aside>

    <el-container class="re-main-content">
      <el-header class="re-header-container">
        <div class="re-top-nav">
          <div class="re-nav-left">
            <span class="re-app-title">红色引擎工程管理系统</span>
          </div>
          <div class="re-nav-right">
            <span class="re-user-name">{{ displayName }}</span>
            <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
          </div>
        </div>
      </el-header>
      <el-main class="re-main-area">
        <transition name="re-fade" mode="out-in">
          <router-view />
        </transition>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
// 红色引擎（党建）独立布局：移植自 redengine/red-engine-web/src/layout/
// MainLayout.vue + Sidebar.vue + SidebarItem.vue + TopNav.vue，合并为单文件组件。
// 变换要点（见 Task 13 简报 F1-F6）：
// - F1: 平台侧 API 调用一律走统一 API 层；权限由 permission store 严格加载，业务 API 见 @/api/redengine.js
// - F2: 源工程的 JWT/localStorage token 逻辑全部不移植，登录态完全靠 yiti session cookie（http.js withCredentials:true）
// - F3: 路由路径统一加 /redengine 前缀，登出后整页跳转平台统一登录页 /#/login
// - F5: 源工程的 v-permission 指令替换为本组件维护的 canSee()，经 provide/inject 供 Task 14 子视图使用
// - 源工程 Sidebar/SidebarItem 支持多级子菜单（el-sub-menu 递归），但 Task 13 简报给定的菜单数据源是
//   扁平的 10 项（无 children），故本次移植未保留递归子菜单渲染；如 Task 14/15 需要二级菜单再补 SidebarItem 递归。
// - 源工程 MainLayout 的侧栏折叠开关（Sidebar @toggle-collapse / TopNav @toggle-sidebar）在源码里两端均未真正
//   emit 事件（Sidebar.vue 只是 defineExpose 了一个方法，从未被调用），是无效代码；本次移植未保留这段死代码，
//   侧栏宽度复用主系统 --layout-sidebar-width，避免红色引擎与主系统出现不同的左侧基线。
// - menuItems 数据源 + canSee() 判断逻辑位于同目录 canSee.js，供布局、子视图和 Vitest 复用。
import { computed, provide, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import {
  Star,
  HomeFilled,
  EditPen,
  Document,
  Stamp,
  DataAnalysis,
  WarningFilled,
  Checked,
  List,
  Setting,
  Connection
} from '@element-plus/icons-vue';
import { logout } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';
import { menuItems, canSee as canSeeImpl, normalizeRoleCodes } from './canSee';

const route = useRoute();
const userStore = useUserStore();
const permissionStore = usePermissionStore();
const effectiveRoleCodes = computed(() => normalizeRoleCodes(userStore.roles, userStore.isSystemAdmin));

// 平台未做图标全局注册（main.js 只 app.use(ElementPlus)，未 app.component 逐个注册图标），
// 故按平台既有用法（如 views/report/Dynamic.vue）本地 import 后建 name → 组件映射，供 <component :is> 用
const iconMap = {
  HomeFilled,
  EditPen,
  Document,
  Stamp,
  DataAnalysis,
  WarningFilled,
  Checked,
  List,
  Setting,
  Connection
};

/**
 * 判断某菜单项/子视图内某资源对当前用户是否可见；绑定共享权限 store，供模板与 provide 复用。
 */
function canSee(item) {
  // 敏感菜单在权限快照完成前保持隐藏；角色白名单和资源权限均满足才显示。
  if (item?.res && !permissionStore.loaded) return false;
  return canSeeImpl(
    item,
    permissionStore.resourceUrls,
    effectiveRoleCodes.value,
    permissionStore.isSystemAdmin
  );
}

// 供 Task 14 子视图通过 inject('canSee') 复用同一份鉴权判断
provide('canSee', canSee);

const visibleMenuItems = computed(() => menuItems.filter(canSee));

const displayName = computed(() => userStore.displayName);

// 路由守卫与布局复用同一个权限 store；load() 会合并并发请求，失败时保持 fail-close。
onMounted(async () => {
  try {
    await permissionStore.load();
  } catch (e) {
    console.warn('[RedEngineLayout] 拉取 /api/auth/permissions 失败，敏感菜单保持隐藏', e?.message);
  }
});

// 登出：清 yiti session（后端）+ 本地用户态，整页回到平台统一登录页。
// 使用整页导航确保菜单等 Pinia 内存状态不会残留给下一位用户。
async function handleLogout() {
  try {
    await logout();
  } catch (e) {
    // 登出接口异常也继续清本地态、跳登录页，避免用户卡在原页面
  }
  userStore.clear();
  window.location.replace('/#/login');
}
</script>

<style scoped lang="scss">
.re-layout {
  height: 100vh;
  width: 100%;
  min-width: 0;
  min-height: 0;
  display: flex !important;
  flex-direction: row !important;

  :deep(.el-container) {
    height: 100%;
  }

  :deep(.el-aside) {
    background-color: #2d1b1b;
    overflow-y: auto;
    overflow-x: hidden;
    flex-shrink: 0;

    &::-webkit-scrollbar {
      width: 6px;
    }
    &::-webkit-scrollbar-track {
      background: transparent;
    }
    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.2);
      border-radius: 3px;
      &:hover {
        background: rgba(255, 255, 255, 0.3);
      }
    }
  }
}

.re-aside {
  width: var(--layout-sidebar-width) !important;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

.re-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #2d1b1b;
  overflow-y: auto;

  .re-sidebar-logo {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: var(--space-2);
    height: var(--layout-header-height);
    padding: 0 var(--space-2) 0 var(--space-4);
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    color: #fecaca;
    font-weight: bold;
    font-size: 14px;
    flex-shrink: 0;

    .re-logo-icon {
      width: 22px;
      height: 22px;
      color: #f87171;
      flex-shrink: 0;

      :deep(svg) {
        width: 22px;
        height: 22px;
      }
    }

    .re-logo-text {
      white-space: nowrap;
    }
  }

  .re-sidebar-menu {
    width: 100% !important;
  }

  :deep(.el-menu) {
    flex: 1;
    border: none;
    background-color: #2d1b1b;

    .el-menu-item {
      color: rgba(255, 255, 255, 0.7);
      min-height: 40px;
      height: auto;
      line-height: normal;
      font-size: 14px;
      transition: all 0.3s;
      justify-content: flex-start;
      text-align: left;
      gap: var(--space-2);
      padding: var(--space-2) var(--space-4) !important;

      &:hover {
        background-color: rgba(255, 255, 255, 0.08) !important;
        color: white;
      }

      &.is-active {
        background-color: rgba(248, 113, 113, 0.15) !important;
        color: #f87171;
        border-left: 2px solid #f87171;
      }
    }

    .re-menu-icon {
      margin-right: 0;
      width: 16px;
      height: 16px;
      font-size: 16px;

      :deep(svg) {
        width: 16px;
        height: 16px;
      }
    }
  }
}

.re-main-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.re-header-container {
  background: linear-gradient(135deg, #991b1b 0%, #b91c1c 100%);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 0 20px;
  height: 60px;
  display: flex;
  align-items: center;
}

.re-top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  width: 100%;
  color: white;

  .re-nav-left {
    .re-app-title {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .re-nav-right {
    display: flex;
    align-items: center;
    gap: 12px;

    .re-user-name {
      font-size: 14px;
    }
  }
}

.re-main-area {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  background-color: #f5f7fa;
  padding: 20px;

  &::-webkit-scrollbar {
    width: 8px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: #d0d0d0;
    border-radius: 4px;
    &:hover {
      background: #b0b0b0;
    }
  }
}

// router-view 切换过渡：Vue 作用域 CSS 对动态子组件根节点生效的规则同平台既有 DefaultLayout.vue 的
// .page-enter-active 写法（父组件 scoped 样式会作用到子组件根节点，用于布局/过渡目的）
.re-fade-enter-active,
.re-fade-leave-active {
  transition: opacity 0.3s ease;
}
.re-fade-enter-from,
.re-fade-leave-to {
  opacity: 0;
}
</style>
