import { createRouter, createWebHashHistory } from 'vue-router';
import DefaultLayout from '@/layouts/DefaultLayout.vue';
import { useUserStore } from '@/stores/user';
import http from '@/api/http';

const routes = [
  // 登录页：顶层路由，不进 DefaultLayout（无 sidebar / header）
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/workspace',
    children: [
      // 工作台
      { path: 'workspace', name: 'Workspace', component: () => import('@/views/workspace/Index.vue'), meta: { title: '工作台', icon: '🏠' } },

      // 绩效与考核
      { path: 'perf/metrics',     name: 'PerfMetrics',   component: () => import('@/views/perf/Metrics.vue'),   meta: { title: '指标库', group: '绩效与考核' } },
      { path: 'perf/kpi-rules',   name: 'PerfKpiRules',  component: () => import('@/views/perf/KpiRules.vue'),  meta: { title: 'KPI 规则', group: '绩效与考核' } },
      { path: 'perf/targets',     name: 'PerfTargets',   component: () => import('@/views/perf/Targets.vue'),   meta: { title: '目标管理', group: '绩效与考核' } },
      { path: 'perf/target-values', name: 'PerfTargetValues', component: () => import('@/views/perf/TargetValues.vue'), meta: { title: '目标值管理', group: '绩效与考核' } },
      { path: 'perf/import',      name: 'PerfImport',    component: () => import('@/views/perf/Import.vue'),    meta: { title: '数据导入', group: '绩效与考核' } },
      { path: 'perf/adjust',      name: 'PerfAdjust',    component: () => import('@/views/perf/Adjust.vue'),    meta: { title: '业绩调整', group: '绩效与考核' } },
      { path: 'perf/compute',     name: 'PerfCompute',   component: () => import('@/views/perf/Compute.vue'),   meta: { title: '考核计算', group: '绩效与考核' } },

      // 报表分析
      { path: 'report',           redirect: '/report/dynamic' },
      { path: 'report/dynamic',   name: 'ReportDynamic', component: () => import('@/views/report/Dynamic.vue'),   meta: { title: '动态指标查询', group: '报表分析' } },
      { path: 'report/dashboard', name: 'ReportDash',    component: () => import('@/views/report/Dashboard.vue'), meta: { title: '行长仪表盘',   group: '报表分析' } },
      { path: 'report/presets',   name: 'ReportPresets', component: () => import('@/views/report/Presets.vue'),   meta: { title: '预置报表',     group: '报表分析' } },
      { path: 'report/sql',       name: 'ReportSql',     component: () => import('@/views/report/Sql.vue'),       meta: { title: 'SQL 探查',     group: '报表分析' } },

      // 系统设置
      { path: 'system/users',      name: 'SysUsers',      component: () => import('@/views/system/Users.vue'),      meta: { title: '用户管理', group: '系统设置' } },
      { path: 'system/roles',      name: 'SysRoles',      component: () => import('@/views/system/Roles.vue'),      meta: { title: '角色管理', group: '系统设置' } },
      { path: 'system/resources',  name: 'SysResources',  component: () => import('@/views/system/Resources.vue'),  meta: { title: '资源/菜单', group: '系统设置' } },
      { path: 'system/permission', name: 'SysPermission', component: () => import('@/views/system/Permission.vue'), meta: { title: '权限配置', group: '系统设置' } },
      { path: 'system/dict',       name: 'SysDict',       component: () => import('@/views/system/Dict.vue'),       meta: { title: '字典管理', group: '系统设置' } },
      { path: 'system/calendar',   name: 'SysCalendar',   component: () => import('@/views/system/Calendar.vue'),   meta: { title: '工作日历', group: '系统设置' } },
      { path: 'system/jobs',       name: 'SysJobs',       component: () => import('@/views/system/Jobs.vue'),       meta: { title: '任务调度', group: '系统设置' } },
      { path: 'system/audit',      name: 'SysAudit',      component: () => import('@/views/system/Audit.vue'),      meta: { title: '审计日志', group: '系统设置' } },
      { path: 'system/notifications', name: 'SysNotifications', component: () => import('@/views/system/Notifications.vue'), meta: { title: '通知消息', group: '系统设置' } },
      { path: 'system/config',     name: 'SysConfig',     component: () => import('@/views/system/Config.vue'),     meta: { title: '系统配置', group: '系统设置' } },
      { path: 'system/files',      name: 'SysFiles',      component: () => import('@/views/system/Files.vue'),      meta: { title: '文件管理', group: '系统设置' } }
    ]
  }
];

const router = createRouter({ history: createWebHashHistory(), routes });

// 全局守卫：未登录访问业务路由 → 跳 /login？redirect=...
// store 没 user 时先试一次 /api/auth/current-user：
//   - 200 → 后端 session 还在（UIAS 回调 / F5 刷新 sessionStorage 清空场景）→ setUser 后放行
//   - 401 → 真未登录 → 跳 login
router.beforeEach(async (to) => {
  const store = useUserStore();
  if (to.meta?.public) return true;
  if (store.isLoggedIn) return true;

  try {
    const user = await http.get('/api/auth/current-user');
    if (user && user.empId) {
      store.setUser(user);
      return true;
    }
  } catch (_) {
    // http.js 401 拦截器自己会清 sessionStorage；这里不重复
  }
  return { path: '/login', query: { redirect: to.fullPath } };
});

export default router;
