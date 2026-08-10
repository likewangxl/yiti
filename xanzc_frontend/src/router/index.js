import { createRouter, createWebHashHistory } from 'vue-router';
import DefaultLayout from '@/layouts/DefaultLayout.vue';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';
import { usePermissionStore } from '@/stores/permission';
import http from '@/api/http';
import { ElMessage } from 'element-plus';
import { createRouteProgress } from './routeProgress';
import { hasRouteAccessRequirements, resolveRouteAccess } from './access';

const routes = [
  // 登录页：顶层路由，不进 DefaultLayout（无 sidebar / header）
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Index.vue'),
    meta: { title: '登录', public: true }
  },
  // 经营大屏：顶层全屏路由（不进 DefaultLayout，无 sidebar/header；仍走登录守卫）
  {
    path: '/screen/:screenCode',
    name: 'ScreenView',
    component: () => import('@/views/screen/ScreenView.vue'),
    meta: { title: '经营大屏' }
  },
  {
    path: '/no-access',
    name: 'NoAccess',
    component: () => import('@/views/NoAccess.vue'),
    meta: { title: '暂无访问权限' }
  },
  // 红色引擎（党建）：独立登录页复用平台 Session，业务页面保持独立红色布局
  {
    path: '/redengine/login',
    name: 'RedEngineLogin',
    component: () => import('@/views/redengine/login/LoginView.vue'),
    meta: { title: '红色引擎-登录', public: true }
  },
  {
    path: '/redengine',
    component: () => import('@/views/redengine/layout/RedEngineLayout.vue'),
    redirect: '/redengine/dashboard',
    meta: { requiredMenu: '/redengine/dashboard' },
    children: [
      { path: 'dashboard', name: 'RedEngineDashboard', component: () => import('@/views/redengine/dashboard/DashboardView.vue'), meta: { title: '工作台' } },
      { path: 'report', name: 'RedEngineReport', component: () => import('@/views/redengine/report/JointView.vue'), meta: { title: '四大维度材料上报', requiredResource: '/api/re/submits' } },
      { path: 'records', name: 'RedEngineRecords', component: () => import('@/views/redengine/records/RecordsView.vue'), meta: { title: '上报记录', requiredResource: '/api/re/submits/my' } },
      { path: 'branch-review', name: 'RedEngineBranchReview', component: () => import('@/views/redengine/branch-review/BranchReviewView.vue'), meta: { title: '支部审核工作台', requiredResource: '/api/re/reviews/**' } },
      { path: 'cockpit', name: 'RedEngineCockpit', component: () => import('@/views/redengine/cockpit/CockpitView.vue'), meta: { title: '全局数据驾驶舱', requiredResource: '/api/re/cockpit/**' } },
      { path: 'warning', name: 'RedEngineWarning', component: () => import('@/views/redengine/warning/WarningView.vue'), meta: { title: '红黄牌预警池', requiredResource: '/api/re/cockpit/**' } },
      { path: 'review', name: 'RedEngineReview', component: () => import('@/views/redengine/review/ReviewView.vue'), meta: { title: '沉浸式审核工作台', requiredResource: '/api/re/reviews/**' } },
      { path: 'archive', name: 'RedEngineArchive', component: () => import('@/views/redengine/archive/ArchiveView.vue'), meta: { title: '年度考核归档', requiredResource: '/api/re/cockpit/**' } },
      { path: 'export', name: 'RedEngineExport', component: () => import('@/views/redengine/export/ExportView.vue'), meta: { title: '数据导出', requiredResource: '/api/re/export/*' } },
      // Task 15 新增：党组织管理（org-manage，Task 13 起菜单数组已声明该项但路由此前未接）+ 用户党组织映射（user-map，新建）
      { path: 'org-manage', name: 'RedEngineOrgManage', component: () => import('@/views/redengine/system/OrgManageView.vue'), meta: { title: '党组织管理', requiredResource: '/api/re/orgs' } },
      { path: 'user-map', name: 'RedEngineUserMap', component: () => import('@/views/redengine/system/UserMapView.vue'), meta: { title: '用户党组织映射', requiredResource: '/api/re/user-party-maps' } }
    ]
  },
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/workspace',
    children: [
      // 工作台
      {
        path: 'workspace',
        name: 'Workspace',
        component: () => import('@/views/workspace/Index.vue'),
        meta: {
          title: '工作台',
          icon: '🏠',
          requiredMenu: '/workspace',
          fallbackToAuthorizedMenu: true
        }
      },
      { path: 'workspace/announcements', name: 'AnnouncementList', component: () => import('@/views/workspace/AnnouncementList.vue'), meta: { title: '公告列表', group: '工作台' } },
      { path: 'announcement/:id', name: 'AnnouncementDetail', component: () => import('@/views/system/AnnouncementDetail.vue'), meta: { title: '公告详情' } },
      { path: 'workspace/notifications', name: 'NotificationList', component: () => import('@/views/workspace/NotificationList.vue'), meta: { title: '通知列表', group: '工作台' } },

      // 信息聚合
      { path: 'info/nav',          name: 'InfoNav',        component: () => import('@/views/info/NavHub.vue'),      meta: { title: '网址导航',     group: '信息聚合' } },
      { path: 'info/address-book', name: 'InfoAddressBook', component: () => import('@/views/info/AddressBook.vue'), meta: { title: '通讯录',       group: '信息聚合' } },
      { path: 'info/products',     name: 'InfoProducts',   component: () => import('@/views/info/ProductLib.vue'),  meta: { title: '产品资料库',   group: '信息聚合' } },
      { path: 'info/documents',    name: 'InfoDocuments',  component: () => import('@/views/info/DocCenter.vue'),   meta: { title: '常用文档',     group: '信息聚合' } },

      // 绩效与考核
      { path: 'perf/metrics',     name: 'PerfMetrics',   component: () => import('@/views/perf/Metrics.vue'),   meta: { title: '指标库', group: '绩效与考核' } },
      { path: 'perf/kpi-rules',   name: 'PerfKpiRules',  component: () => import('@/views/perf/KpiRules.vue'),  meta: { title: 'KPI 规则', group: '绩效与考核' } },
      { path: 'perf/targets',     name: 'PerfTargets',   component: () => import('@/views/perf/Targets.vue'),   meta: { title: '目标管理', group: '绩效与考核' } },
      { path: 'perf/target-values', name: 'PerfTargetValues', component: () => import('@/views/perf/TargetValues.vue'), meta: { title: '目标值管理', group: '绩效与考核' } },
      { path: 'perf/import',      name: 'PerfImport',    component: () => import('@/views/perf/Import.vue'),    meta: { title: '数据导入', group: '绩效与考核' } },
      { path: 'perf/adjust',      name: 'PerfAdjust',    component: () => import('@/views/perf/Adjust.vue'),    meta: { title: '业绩调整', group: '绩效与考核' } },
      { path: 'perf/compute',     name: 'PerfCompute',   component: () => import('@/views/perf/Compute.vue'),   meta: { title: '考核计算', group: '绩效与考核' } },
      { path: 'perf/task-monitor', name: 'PerfTaskMonitor', component: () => import('@/views/perf/TaskMonitor.vue'), meta: { title: '任务监控', group: '绩效与考核' } },
      { path: 'perf/kpi-score-detail', name: 'PerfKpiScoreDetail', component: () => import('@/views/perf/KpiScoreDetail.vue'), meta: { title: 'KPI计算结果详情', group: '绩效与考核', hideInMenu: true } },

      // 内部评价
      { path: 'eval/tags',      name: 'EvalTags',     component: () => import('@/views/eval/Tags.vue'),     meta: { title: '标签管理', group: '内部评价' } },
      { path: 'eval/user-tags', name: 'EvalUserTags', component: () => import('@/views/eval/UserTags.vue'), meta: { title: '人员标签', group: '内部评价' } },
      { path: 'eval/rules',     name: 'EvalRules',    component: () => import('@/views/eval/Rules.vue'),    meta: { title: '评价规则', group: '内部评价' } },
      { path: 'eval/tasks',     name: 'EvalTasks',    component: () => import('@/views/eval/Tasks.vue'),    meta: { title: '评价任务', group: '内部评价' } },
      { path: 'eval/my-tasks',  name: 'EvalMyTasks',  component: () => import('@/views/eval/MyTasks.vue'),  meta: { title: '待处理任务', group: '内部评价' } },

      // 报表分析
      { path: 'report',           redirect: '/report/dynamic' },
      { path: 'report/dynamic',   name: 'ReportDynamic', component: () => import('@/views/report/Dynamic.vue'),   meta: { title: '动态指标查询', group: '报表分析' } },
      { path: 'report/dashboard', name: 'ReportDash',    component: () => import('@/views/report/Dashboard.vue'), meta: { title: '行长仪表盘',   group: '报表分析' } },
      { path: 'report/presets',   name: 'ReportPresets', component: () => import('@/views/report/Presets.vue'),   meta: { title: '预置报表',     group: '报表分析' } },
      { path: 'report/free',      name: 'ReportFree',    component: () => import('@/views/report/FreeReport.vue'), meta: { title: '自由报表', group: '报表分析' } },
      { path: 'report/free/:batchId', name: 'ReportFreeDetail', component: () => import('@/views/report/FreeReportDetail.vue'), meta: { title: '报表详情', group: '报表分析' } },
      { path: 'report/sql',       name: 'ReportSql',     component: () => import('@/views/report/Sql.vue'),       meta: { title: 'SQL 探查',     group: '报表分析' } },
      { path: 'report/amas-approvals', name: 'ReportAmasApprovals', component: () => import('@/views/report/AmasApprovals.vue'), meta: { title: '业绩分配查询', group: '报表分析' } },
      { path: 'report/amas-approvals/:perfAdjustNo', name: 'ReportAmasApprovalDetail', component: () => import('@/views/report/AmasApprovalDetail.vue'), meta: { title: '业绩分配审批详情', group: '报表分析', hideInMenu: true } },
      { path: 'screen-admin/datasources', name: 'ScreenAdminDs',       component: () => import('@/views/screen/admin/Datasources.vue'), meta: { title: '大屏数据源', group: '报表分析' } },
      // fullBleed:设计器需要整块内容区(去 padding),高度契约见 DefaultLayout .content--full
      { path: 'screen-admin/designer',    name: 'ScreenAdminDesigner', component: () => import('@/views/screen/designer/DesignerV2.vue'),    meta: { title: '大屏设计器', group: '报表分析', fullBleed: true } },

      // 历史数据查询
      { path: 'guarantee/query',  name: 'GuaranteeQuery', component: () => import('@/views/guarantee/Query.vue'), meta: { title: '担保查询', group: '历史数据查询' } },
      { path: 'guarantee/data-import', name: 'HistoryDataImport', component: () => import('@/views/guarantee/DataImport.vue'), meta: { title: '数据导入查询', group: '历史数据查询' } },
      { path: 'guarantee/notice', name: 'HistoryNotice', component: () => import('@/views/guarantee/Notice.vue'), meta: { title: '公告查询', group: '历史数据查询' } },
      { path: 'history/price-approval', name: 'HistoryPriceApproval', component: () => import('@/views/history/PriceApproval.vue'), meta: { title: '定价审批查询', group: '历史数据查询' } },
      { path: 'history/price-approval/:priceApprId', name: 'HistoryPriceApprovalDetail', component: () => import('@/views/history/PriceApprovalDetail.vue'), meta: { title: '定价审批详情', group: '历史数据查询', hideInMenu: true } },
      { path: 'history/perf-adjust', name: 'HistoryPerfAdjust', component: () => import('@/views/history/PerfAdjustQuery.vue'), meta: { title: '业绩调整查询', group: '历史数据查询' } },

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
      { path: 'system/files',      name: 'SysFiles',      component: () => import('@/views/system/Files.vue'),      meta: { title: '文件管理', group: '系统设置' } },
      { path: 'system/timeout-rules', name: 'SysTimeoutRules', component: () => import('@/views/system/TimeoutRules.vue'), meta: { title: '超时规则', group: '系统设置' } },
      { path: 'system/announcements', name: 'SysAnnouncements', component: () => import('@/views/system/Announcements.vue'), meta: { title: '公告管理', group: '系统设置' } },
      { path: 'system/announcements/:id', name: 'SysAnnouncementDetail', component: () => import('@/views/system/AnnouncementDetail.vue'), meta: { title: '公告详情', group: '系统设置' } },
      { path: 'system/workflow-flows', name: 'SysWorkflowFlows', component: () => import('@/views/system/FlowList.vue'), meta: { title: '审批流程', group: '系统设置' } },
      { path: 'system/workflow-flows/:id', name: 'SysWorkflowFlowEdit', component: () => import('@/views/system/FlowEdit.vue'), meta: { title: '审批流程编辑', group: '系统设置' } },
      { path: 'system/workflow-monitor', name: 'SysWorkflowMonitor', component: () => import('@/views/system/WorkflowMonitor.vue'), meta: { title: '审批流监控', group: '系统设置' } },
      { path: 'system/person-tags', name: 'SysPersonTags', component: () => import('@/views/system/PersonTags.vue'), meta: { title: '人员标签', group: '系统设置' } }
    ]
  }
];

const router = createRouter({ history: createWebHashHistory(), routes });

const progress = createRouteProgress({
  onTimeout() {
    ElMessage.error('页面加载超时，请刷新后重试');
  }
});

function checkDeclaredRouteAccess(to) {
  if (!hasRouteAccessRequirements(to)) return true;
  return resolveRouteAccess(to, useMenuStore(), usePermissionStore());
}

// 全局守卫：未登录访问业务路由 → 跳 /login？redirect=...
// store 没 user 时先试一次 /api/auth/current-user：
//   - 200 → 后端 session 还在（UIAS 回调 / F5 刷新 sessionStorage 清空场景）→ setUser 后放行
//   - 401 → 真未登录 → 跳 login
router.beforeEach(async (to) => {
  progress.start();
  const store = useUserStore();
  if (to.meta?.public) return true;
  if (store.isLoggedIn) {
    return checkDeclaredRouteAccess(to);
  }

  try {
    const user = await http.get('/api/auth/current-user');
    if (user && user.empId) {
      store.setUser(user);
      return checkDeclaredRouteAccess(to);
    }
  } catch (_) {
    // http.js 401 拦截器自己会清 sessionStorage；这里不重复
  }
  return { path: '/login', query: { redirect: to.fullPath } };
});

router.afterEach(() => {
  progress.done();
});

router.onError((error) => {
  const wasActive = progress.fail();
  console.error('[Router] 路由加载失败', error);
  if (wasActive) {
    ElMessage.error('页面加载失败，请刷新后重试');
  }
});

export default router;
