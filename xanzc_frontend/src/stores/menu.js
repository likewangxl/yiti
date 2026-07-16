import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getMyMenus } from '@/api/auth';

/**
 * 菜单 store：应用内缓存 my-menus 菜单树，提供按路由 path 解析显示名。
 * 让侧边栏 / 面包屑 / 页面标题统一以 DB menuName 为显示真源。
 */
export const useMenuStore = defineStore('menu', () => {
  const tree = ref([]);              // 原始菜单树
  const loaded = ref(false);
  const loading = ref(false);
  const byUrl = ref(new Map());      // resourceUrl -> { title, group }

  // 递归遍历构建索引；group 取直接父节点 menuName（顶层为 null）
  function buildIndex(nodes, parentName, map) {
    for (const n of nodes || []) {
      if (n.resourceUrl) {
        map.set(n.resourceUrl, { title: n.menuName || '', group: parentName || null });
      }
      if (n.children && n.children.length) {
        buildIndex(n.children, n.menuName || parentName || null, map);
      }
    }
  }

  // 拉取菜单树并建索引；loaded 后默认跳过，force=true 强制重取（资源改名后即时刷新）
  async function load(force = false) {
    if (loaded.value && !force) return;
    if (loading.value) return;
    loading.value = true;
    try {
      const t = await getMyMenus();
      tree.value = Array.isArray(t) ? t : [];
      const map = new Map();
      buildIndex(tree.value, null, map);
      byUrl.value = map;
      loaded.value = true;
    } catch (e) {
      // 加载失败保持原值（loaded 仍 false，非强制调用下次可重试）；记录以便排查
      console.warn('[menu] 加载菜单树失败', e);
    } finally {
      loading.value = false;
    }
  }

  // 命中返回 { title, group }，未命中返回 null
  function resolve(path) {
    return byUrl.value.get(path) || null;
  }

  return { tree, loaded, loading, load, resolve };
});
