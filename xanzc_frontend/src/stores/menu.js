import { defineStore } from 'pinia';
import { onScopeDispose, ref } from 'vue';
import { getMyMenus } from '@/api/auth';
import { registerAuthorizationReset } from './authorizationSnapshot';

/**
 * 菜单 store：应用内缓存 my-menus 菜单树，提供按路由 path 解析显示名。
 * 让侧边栏 / 面包屑 / 页面标题统一以 DB menuName 为显示真源。
 */
export const useMenuStore = defineStore('menu', () => {
  const tree = ref([]);              // 原始菜单树
  const loaded = ref(false);
  const loading = ref(false);
  const byUrl = ref(new Map());      // resourceUrl -> { title, group }
  const detailBaseUrls = ref(new Set());
  const resourceIds = ref(new Set());
  let pendingLoad = null;
  let requestGeneration = 0;

  // 递归遍历构建索引；group 取直接父节点 menuName（顶层为 null）
  function buildIndex(nodes, parentName, map, detailBases, ids) {
    for (const n of nodes || []) {
      if (n.resourceId != null) ids.add(String(n.resourceId));
      if (n.resourceUrl) {
        map.set(n.resourceUrl, { title: n.menuName || '', group: parentName || null });
        if (!n.children?.length) detailBases.add(n.resourceUrl);
      }
      if (n.children && n.children.length) {
        buildIndex(n.children, n.menuName || parentName || null, map, detailBases, ids);
      }
    }
  }

  // 拉取菜单树并建索引；loaded 后默认跳过，force=true 强制重取（资源改名后即时刷新）
  function clearMenuState() {
    tree.value = [];
    byUrl.value = new Map();
    detailBaseUrls.value = new Set();
    resourceIds.value = new Set();
    loaded.value = false;
  }

  function clear() {
    requestGeneration += 1;
    pendingLoad = null;
    loading.value = false;
    clearMenuState();
  }

  const unregisterReset = registerAuthorizationReset(clear);
  onScopeDispose(unregisterReset);

  async function load(force = false) {
    // force 是新的授权代际：不能继续复用上一用户或上一轮的 pending。
    if (force) clear();
    if (loaded.value && !force) return;
    if (pendingLoad) return pendingLoad;
    const generation = ++requestGeneration;
    loading.value = true;
    pendingLoad = (async () => {
      try {
        const t = await getMyMenus();
        if (generation !== requestGeneration) return t;
        tree.value = Array.isArray(t) ? t : [];
        const map = new Map();
        const detailBases = new Set();
        const ids = new Set();
        buildIndex(tree.value, null, map, detailBases, ids);
        byUrl.value = map;
        detailBaseUrls.value = detailBases;
        resourceIds.value = ids;
        loaded.value = true;
        return t;
      } catch (e) {
        if (generation !== requestGeneration) throw e;
        // 菜单属于授权状态，失败时必须清掉旧用户菜单并把错误交给调用方处理。
        clearMenuState();
        console.warn('[menu] 加载菜单树失败', e);
        throw e;
      } finally {
        if (generation === requestGeneration) {
          loading.value = false;
          pendingLoad = null;
        }
      }
    })();
    return pendingLoad;
  }

  // 命中返回 { title, group }，未命中返回 null
  function resolve(path) {
    return byUrl.value.get(path) || null;
  }

  function hasUrl(path) {
    return typeof path === 'string' && byUrl.value.has(path);
  }

  function hasResourceId(resourceId) {
    return resourceId != null && resourceIds.value.has(String(resourceId));
  }

  // 分组菜单优先进入其第一个可导航叶子，避免把只有展开语义的父 URL 当首页。
  function firstNavigableUrl(nodes = tree.value) {
    for (const node of nodes || []) {
      if (node.children?.length) {
        const childUrl = firstNavigableUrl(node.children);
        if (childUrl) return childUrl;
      }
      if (typeof node.resourceUrl === 'string' && node.resourceUrl.startsWith('/')
          && !node.resourceUrl.startsWith('//')) {
        return node.resourceUrl;
      }
    }
    return '';
  }

  function normalizeRedirect(redirect) {
    if (typeof redirect !== 'string') return '';
    const target = redirect.trim();
    if (!target.startsWith('/') || target.startsWith('//') || target.includes('\\')) return '';
    const path = target.split(/[?#]/, 1)[0];
    let decodedPath;
    try {
      decodedPath = decodeURIComponent(path);
    } catch (_) {
      return '';
    }
    if (decodedPath.includes('\\')
        || decodedPath.split('/').some((segment) => segment === '.' || segment === '..')) return '';
    if (['/login', '/redengine/login', '/no-access'].includes(path)) return '';
    return target;
  }

  function isAuthorizedRedirect(redirect) {
    const safeRedirect = normalizeRedirect(redirect);
    if (!safeRedirect) return false;
    const path = safeRedirect.split(/[?#]/, 1)[0];
    if (hasUrl(path)) return true;
    for (const base of detailBaseUrls.value) {
      if (base !== '/' && path.startsWith(`${base}/`)) return true;
    }
    return hasResourceId('M_RE_ENGINE')
      && (path === '/redengine' || path.startsWith('/redengine/'));
  }

  function resolveLandingPath(redirect, { preferredUrl = '' } = {}) {
    const safeRedirect = normalizeRedirect(redirect);
    if (safeRedirect && isAuthorizedRedirect(safeRedirect)) return safeRedirect;
    if (preferredUrl === '/redengine/dashboard'
        && hasResourceId('M_RE_ENGINE') && hasUrl(preferredUrl)) return preferredUrl;
    if (preferredUrl && hasUrl(preferredUrl)) return preferredUrl;
    if (hasUrl('/workspace')) return '/workspace';
    return firstNavigableUrl() || '/no-access';
  }

  return {
    tree, loaded, loading, load, resolve, hasUrl,
    hasResourceId, firstNavigableUrl, isAuthorizedRedirect, resolveLandingPath, clear
  };
});
