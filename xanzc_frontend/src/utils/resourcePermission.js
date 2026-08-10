/**
 * 判断权限 URL 集合能否满足一个菜单/路由资源要求。
 * required 允许以 /* 或 /** 结尾，表示按该前缀匹配平台登记的资源 URL。
 */
export function matchesRequiredResource(required, resourceUrls) {
  if (!required || !resourceUrls) return false;
  const urls = resourceUrls instanceof Set ? resourceUrls : new Set(resourceUrls);
  if (required.endsWith('/**') || required.endsWith('/*')) {
    const prefix = required.replace(/\/\*+$/, '');
    for (const url of urls) {
      if (typeof url === 'string' && (url === prefix || url.startsWith(`${prefix}/`))) return true;
    }
    return false;
  }
  return urls.has(required);
}
