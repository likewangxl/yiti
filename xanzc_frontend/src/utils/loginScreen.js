import { BUSINESS_LINE_TARGETS, resolveAuthorizedScreen, resolveInstitution } from '@/views/screen/presentation/navigation/navigationModel';
import { parseParentBranchOperatingSource } from '@/views/screen/panorama/parentBranchOperatingSource';

export const LOGIN_SCREEN_TARGETS = Object.freeze(['WORKSPACE', 'PERSONAL', 'BRANCH', 'PROVINCE', 'RETAIL', 'CORPORATE']);
const TARGETS = new Set(LOGIN_SCREEN_TARGETS);
const LINES = { PROVINCE: 'COMMON', BRANCH: 'COMMON', RETAIL: 'RETAIL', CORPORATE: 'CORP' };
const SHOWN_KEY = 'xanzc:login-screen-shown';

/** 仅记录本标签页已处理的身份，不保存或替代权限。 */
export function markLoginScreenShown(user) { sessionStorage.setItem(SHOWN_KEY, String(user?.empId || '')); }
export function resetLoginScreenShown() { sessionStorage.removeItem(SHOWN_KEY); }
export function shouldShowRestoredLoginScreen(user, path) {
  return ['/', '/workspace'].includes(path) && Boolean(user?.empId)
    && sessionStorage.getItem(SHOWN_KEY) !== String(user.empId);
}

/** 主角色优先，备用规则按显式优先级及稳定ID排序。 */
export function orderedLoginScreenRules(user, payload) {
  if (!Array.isArray(payload?.rules)) throw new Error('登录首屏规则格式无效');
  const roles = Array.isArray(user?.roles) ? user.roles : [];
  const ids = new Set(roles.map(r => String(r?.roleId || '')));
  const primaryIds = new Set(roles.filter(r => r?.primary === true).map(r => String(r.roleId)));
  if (primaryIds.size > 1) throw new Error('主角色信息不明确');
  const rules = payload.rules.filter(r => ids.has(String(r?.roleId || '')));
  const seen = new Set();
  for (const rule of rules) {
    if (!TARGETS.has(rule.target) || !Number.isInteger(rule.priority) || rule.priority < 0 || rule.priority > 10000
      || seen.has(rule.roleId)) throw new Error('登录首屏规则无效');
    seen.add(rule.roleId);
  }
  return [...rules].sort((a, b) => Number(primaryIds.has(b.roleId)) - Number(primaryIds.has(a.roleId))
    || a.priority - b.priority || String(a.roleId).localeCompare(String(b.roleId)));
}
export function selectLoginScreenTarget(user, payload) {
  return orderedLoginScreenRules(user, payload)[0]?.target || 'WORKSPACE';
}

/** 固定目标生成路由，支行必须携带已验证的父屏机构上下文。 */
export function targetRoute(target, user, authorized = null) {
  if (target === 'PERSONAL') return { name: 'PersonalDashboard', query: { from: 'login' } };
  if (target === 'BRANCH') {
    const orgCode = String(user?.mainOrgCode || '').trim();
    const resolved = authorized?.institution;
    if (!orgCode || !resolved?.authorized || !resolved.layer?.known || !resolved.layer?.displayable
      || resolved.orgCode !== orgCode || !resolved.institution?.cityCode || authorized?.view?.screenCode !== 'SCR_PROVINCE') return null;
    return { name: 'BranchOperatingPage', query: { from: 'login', orgCode,
      cityCode: String(resolved.institution.cityCode), sourceScreenCode: 'SCR_PROVINCE', businessLine: 'COMMON' } };
  }
  const fixed = BUSINESS_LINE_TARGETS[LINES[target]];
  return fixed ? { name: 'CodeScreenPage', params: { template: fixed.template }, query: { from: 'login' } } : { path: '/workspace' };
}

function validPublishedPackage(view, entry) {
  if (view?.state !== 'published' || view?.runtimeSchemaVersion !== 2 || view.screenCode !== entry.screenCode) return false;
  try {
    const pkg = JSON.parse(view.renderPackageJson);
    return pkg?.schemaVersion === 2 && Array.isArray(pkg.components) && pkg.bindSnapshots
      && typeof pkg.bindSnapshots === 'object' && !Array.isArray(pkg.bindSnapshots)
      && pkg.canvasStyle?.presentation?.type === 'CODE' && pkg.canvasStyle.presentation.template === entry.template
      && (entry.template !== 'branch-overview-v1' || entry.dataMode === 'TEST');
  } catch { return false; }
}

/** 目录、发布包、机构门禁由既有在线接口复核，偏好本身不授予权限。 */
export async function resolveLoginScreen({ user, payload, menuStore, permissionStore, listAvailableScreens, getScreenView, isCurrent = () => true }) {
  const fallback = () => menuStore.resolveLandingPath();
  let catalog;
  for (const rule of orderedLoginScreenRules(user, payload)) {
    if (!isCurrent()) return null;
    if (rule.target === 'WORKSPACE') return fallback();
    if (rule.target === 'PERSONAL') {
      if (menuStore.hasUrl('/workspace')) return targetRoute('PERSONAL', user);
      continue;
    }
    if (!permissionStore.canAccess('/api/screen/view/*')) continue;
    if (rule.target === 'BRANCH' && !user?.mainOrgCode) continue;
    try {
      if (!catalog) catalog = await listAvailableScreens();
      if (!isCurrent()) return null;
      const authorized = await resolveAuthorizedScreen({ businessLine: LINES[rule.target],
        orgCode: rule.target === 'BRANCH' ? String(user.mainOrgCode) : '',
        listAvailableScreens: async () => catalog, getScreenView });
      if (!isCurrent()) return null;
      if (!validPublishedPackage(authorized.view, authorized.entry)) continue;
      if (rule.target === 'BRANCH') {
        let source;
        try { source = parseParentBranchOperatingSource(authorized.view, { sourceScreenCode: 'SCR_PROVINCE' }); }
        catch { continue; }
        authorized.institution = resolveInstitution(source, user.mainOrgCode);
      }
      const destination = targetRoute(rule.target, user, authorized);
      if (destination) return destination;
    } catch (error) {
      if (!isCurrent()) return null;
      const status = Number(error?.response?.status || error?.status);
      if (status === 403 || status === 404 || ['SCREEN_NOT_AUTHORIZED', 'ORG_NOT_AUTHORIZED', 'ORG_LAYER_UNCONFIRMED', 'ORG_NOT_DISPLAYABLE'].includes(error?.code)) continue;
      throw error;
    }
  }
  return isCurrent() ? fallback() : null;
}
