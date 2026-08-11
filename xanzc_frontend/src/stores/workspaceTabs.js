import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

/** 工作台固定页签的路由地址。 */
export const WORKSPACE_PATH = '/workspace';

const WORKSPACE_TITLE = '工作台';

/**
 * 将路由字段转换成可展示的短文本。
 * 页签只渲染文本插值，避免把路由对象或任意复杂值直接带入模板。
 */
function asTitle(value) {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return '';
}

/**
 * 从路由安全地取得页签标题：优先使用 meta.title，随后使用 route.name，
 * 最后使用 path，确保动态菜单或未声明标题的业务路由仍然有可识别名称。
 */
export function resolveTabTitle(route) {
  if (!route) return '';
  const metaTitle = asTitle(route.meta?.title);
  if (metaTitle) return metaTitle;

  const matchedTitle = Array.isArray(route.matched)
    ? asTitle(route.matched[route.matched.length - 1]?.meta?.title)
    : '';
  if (matchedTitle) return matchedTitle;

  const routeName = asTitle(route.name);
  if (routeName) return routeName;

  return asTitle(route.path || route.fullPath);
}

/** 计算页签唯一键；同一路由的不同查询参数保留为不同页签。 */
export function resolveTabKey(route) {
  if (!route) return '';
  const path = asTitle(route.path);
  if (path === WORKSPACE_PATH) return WORKSPACE_PATH;
  return asTitle(route.fullPath) || path;
}

function createWorkspaceTab(title = WORKSPACE_TITLE) {
  return {
    key: WORKSPACE_PATH,
    path: WORKSPACE_PATH,
    fullPath: WORKSPACE_PATH,
    name: 'Workspace',
    title: title || WORKSPACE_TITLE,
    closable: false
  };
}

function createTab(route, key, title) {
  return {
    key,
    path: asTitle(route.path) || key,
    fullPath: key,
    name: asTitle(route.name),
    title: title || key,
    closable: key !== WORKSPACE_PATH
  };
}

/**
 * 工作区页签状态。
 * 页签状态只保存当前 SPA 会话，不写入 localStorage，避免不同用户之间串页签。
 */
export const useWorkspaceTabsStore = defineStore('workspaceTabs', () => {
  const tabs = ref([createWorkspaceTab()]);

  /** 重置会话页签，仅保留固定工作台。 */
  function reset() {
    tabs.value = [createWorkspaceTab()];
  }

  /**
   * 记录一次 DefaultLayout 路由访问并返回对应页签。
   * 重复访问同一 fullPath 只更新标题，不重复创建页签。
   */
  function record(route, preferredTitle = '') {
    const key = resolveTabKey(route);
    if (!key) return null;

    // 动态菜单是展示名真源；尚未加载时再回退到静态路由信息。
    const title = asTitle(preferredTitle) || resolveTabTitle(route);
    const current = tabs.value.find((tab) => tab.key === key);
    if (current) {
      if (title) current.title = title;
      return current;
    }

    const tab = createTab(route, key, title);
    tabs.value.push(tab);
    return tab;
  }

  /**
   * 关闭一个可关闭页签，返回关闭后应该激活的相邻页签。
   * 选择左侧页签优先，左侧不存在时再选择右侧，固定工作台永不删除。
   */
  function close(tabOrKey) {
    const key = typeof tabOrKey === 'string' ? tabOrKey : tabOrKey?.key;
    if (!key || key === WORKSPACE_PATH) return null;

    const index = tabs.value.findIndex((tab) => tab.key === key);
    if (index < 0) return null;

    tabs.value.splice(index, 1);
    return tabs.value[index - 1] || tabs.value[index] || tabs.value[0] || null;
  }

  const fixedTab = computed(() => tabs.value[0]);
  return { tabs, fixedTab, reset, record, close };
});
