import { createRouter, createWebHashHistory } from 'vue-router';
import DefaultLayout from '@/layouts/DefaultLayout.vue';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';
import { usePermissionStore } from '@/stores/permission';
import http from '@/api/http';
import { ElMessage } from 'element-plus';
import { createRouteProgress } from './routeProgress';
import { hasRouteAccessRequirements, resolveRouteAccess } from './access';
import {
  normalizeRoleCodes,
  RED_ENGINE_RESOURCE_URLS
} from '@/views/redengine/layout/canSee';

const routes = [
  // 登录页：顶层路由，不进 DefaultLayout（无 sidebar / header）
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Index.vue'),
    meta: { title: '登录', public: true }
  },
  // 本地视觉验收入口：只在 Vite 开发环境编译进路由，生产包没有该入口。
  ...(import.meta.env.DEV ? [{
    path: '/screen-preview',
    name: 'ScreenPreview',
    component: () => import('@/views/screen/panorama/PanoramaPreview.vue'),
    meta: { title: '本地演示预览', public: true, hideInMenu: true }
  }, {
    path: '/screen-preview/corporate',
    name: 'CorporateScreenPreview',
    component: () => import('@/views/screen/panorama/CorporatePreview.vue'),
    meta: { title: '对公经营本地预览', public: true, hideInMenu: true }
  }, {
    path: '/screen-preview/retail',
    name: 'RetailScreenPreview',
    component: () => import('@/views/screen/panorama/RetailPreview.vue'),
    meta: { title: '零售经营本地预览', public: true, hideInMenu: true }
  }] : []),
  // 经营大屏：顶层全屏路由（不进 DefaultLayout，无 sidebar/header；仍走登录守卫）
  {
    path: '/screen/:screenCode',
    name: 'ScreenView',
    component: () => import('@/views/screen/ScreenView.vue'),
    meta: { title: '经营大屏', requiredResource: '/api/screen/view/*' }
  },
  // 代码化大屏只接受 ScreenCenter 后端目录确认过的固定模板；页面自身仍复核目录授权。
  {
    path: '/screen-pages/:template',
    name: 'CodeScreenPage',
    component: () => import('@/views/screen/CodeScreenPage.vue'),
    meta: { title: '代码化大屏', requiredResource: '/api/screen/view/*', hideInMenu: true }
  },
  // 大屏管理：历史 designer 路径继续兼容，但入口改为绑定/发布管理页，不再暴露为菜单。
  // 保留原路径和权限资源，刷新、直达和会话恢复仍经全局守卫 Fail Close。
  {
    path: '/screen-admin/designer',
    name: 'ScreenAdminDesigner',
    component: () => import('@/views/screen/panorama/PanoramaBindings.vue'),
    meta: {
      title: '大屏管理',
      group: '报表分析',
      fullBleed: true,
      hideInMenu: true,
      requiredResource: '/api/screen/admin/screens'
    }
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
      {
        path: 'dashboard',
        name: 'RedEngineDashboard',
        component: () => import('@/views/redengine/dashboard/DashboardView.vue'),
        meta: {
          title: '工作台',
          requiredRoleCodes: ['R_RE_REPORT', 'R_RE_SECR', 'R_RE_ORGREV', 'SYS_ADMIN']
        }
      },
      {
        path: 'report',
        name: 'RedEngineReport',
        component: () => import('@/views/redengine/report/JointView.vue'),
        meta: {
          title: '四大维度材料上报',
          requiredResource: RED_ENGINE_RESOURCE_URLS.REPORT,
          requiredRoleCodes: ['R_RE_REPORT', 'SYS_ADMIN']
        }
      },
      {
        path: 'records',
        name: 'RedEngineRecords',
        component: () => import('@/views/redengine/records/RecordsView.vue'),
        meta: {
          title: '任务处理',
          requiredResource: RED_ENGINE_RESOURCE_URLS.RECORDS,
          requiredRoleCodes: ['R_RE_REPORT', 'SYS_ADMIN']
        }
      },
      {
        path: 'branch-review',
        name: 'RedEngineBranchReview',
        component: () => import('@/views/redengine/branch-review/BranchReviewView.vue'),
        meta: {
          title: '任务处理',
          requiredResource: RED_ENGINE_RESOURCE_URLS.REVIEW,
          requiredRoleCodes: ['R_RE_SECR']
        }
      },
      {
        path: 'cockpit',
        name: 'RedEngineCockpit',
        component: () => import('@/views/redengine/cockpit/CockpitView.vue'),
        meta: {
          title: '全局数据驾驶舱',
          requiredResource: RED_ENGINE_RESOURCE_URLS.COCKPIT,
          requiredRoleCodes: ['R_RE_ORGREV', 'SYS_ADMIN']
        }
      },
      {
        path: 'warning',
        name: 'RedEngineWarning',
        component: () => import('@/views/redengine/warning/WarningView.vue'),
        meta: {
          title: '红黄牌预警池',
          requiredResource: RED_ENGINE_RESOURCE_URLS.HOME,
          requiredRoleCodes: ['R_RE_REPORT', 'R_RE_SECR', 'R_RE_ORGREV', 'SYS_ADMIN']
        }
      },
      {
        path: 'review',
        name: 'RedEngineReview',
        component: () => import('@/views/redengine/review/ReviewView.vue'),
        meta: {
          title: '工作台',
          requiredResource: RED_ENGINE_RESOURCE_URLS.REVIEW,
          requiredRoleCodes: ['R_RE_ORGREV']
        }
      },
      {
        path: 'task-management',
        name: 'RedEngineTaskManagement',
        component: () => import('@/views/redengine/tasks/TaskManagementView.vue'),
        meta: {
          title: '任务管理',
          requiredResource: RED_ENGINE_RESOURCE_URLS.TASK_COLLECTION,
          requiredRoleCodes: ['R_RE_ORGREV', 'SYS_ADMIN']
        }
      },
      {
        path: 'task-management/new',
        name: 'RedEngineTaskNew',
        component: () => import('@/views/redengine/tasks/NewTaskView.vue'),
        meta: {
          title: '新增任务',
          hideInMenu: true,
          requiredResource: RED_ENGINE_RESOURCE_URLS.TASK_COLLECTION,
          requiredRoleCodes: ['R_RE_ORGREV', 'SYS_ADMIN']
        }
      },
      {
        path: 'task-management/:taskId',
        name: 'RedEngineTaskDetail',
        component: () => import('@/views/redengine/tasks/TaskDetailView.vue'),
        meta: {
          title: '任务详情',
          hideInMenu: true,
          requiredResource: RED_ENGINE_RESOURCE_URLS.TASK_DETAIL,
          requiredRoleCodes: ['R_RE_ORGREV', 'SYS_ADMIN']
        }
      },
      {
        path: 'task-entry',
        name: 'RedEngineTaskEntry',
        component: () => import('@/views/redengine/records/TemporaryTaskEntryView.vue'),
        meta: {
          title: '临时任务填报',
          hideInMenu: true,
          requiredResource: RED_ENGINE_RESOURCE_URLS.TASK_ASSIGNMENT,
          requiredRoleCodes: ['R_RE_REPORT']
        }
      },
      // 党组织管理、用户党组织映射只对组织审核员和系统管理员开放。
      {
        path: 'org-manage',
        name: 'RedEngineOrgManage',
        component: () => import('@/views/redengine/system/OrgManageView.vue'),
        meta: {
          title: '党组织管理',
          requiredResource: RED_ENGINE_RESOURCE_URLS.ORG_TREE,
          requiredRoleCodes: ['R_RE_ORGREV', 'SYS_ADMIN']
        }
      },
      {
        path: 'user-map',
        name: 'RedEngineUserMap',
        component: () => import('@/views/redengine/system/UserMapView.vue'),
        meta: {
          title: '用户党组织映射',
          requiredResource: RED_ENGINE_RESOURCE_URLS.USER_MAP,
          requiredRoleCodes: ['R_RE_ORGREV', 'SYS_ADMIN']
        }
      }
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
      {
        path: 'screens',
        name: 'ScreenCenter',
        component: () => import('@/views/screen/ScreenCenter.vue'),
        meta: {
          title: '大屏中心',
          requiredResource: '/api/screen/view/*'
        }
      },
      { path: 'workspace/announcements', name: 'AnnouncementList', component: () => import('@/views/workspace/AnnouncementList.vue'), meta: { title: '公告列表', group: '工作台' } },
      { path: 'announcement/:id', name: 'AnnouncementDetail', component: () => import('@/views/system/AnnouncementDetail.vue'), meta: { title: '公告详情' } },
      { path: 'workspace/notifications', name: 'NotificationList', component: () => import('@/views/workspace/NotificationList.vue'), meta: { title: '通知列表', group: '工作台' } },

      // 客户营销：资产立项正式入口。
      {
        path: 'marketing/asset-projects',
        name: 'AssetProjects',
        component: () => import('@/views/customerMarketing/AssetProjects.vue'),
        meta: {
          title: '资产立项',
          group: '客户营销',
          requiredMenu: '/marketing/asset-projects',
          requiredResource: '/api/marketing/asset-projects'
        }
      },
      {
        path: 'marketing/asset-projects/new',
        name: 'AssetProjectCreate',
        component: () => import('@/views/customerMarketing/AssetProjects.vue'),
        meta: {
          title: '新建资产立项',
          group: '客户营销',
          hideInMenu: true,
          requiredMenu: '/marketing/asset-projects',
          requiredResource: '/api/marketing/asset-projects'
        }
      },
      {
        path: 'marketing/asset-projects/:id',
        name: 'AssetProjectDetail',
        component: () => import('@/views/customerMarketing/AssetProjects.vue'),
        meta: {
          title: '资产立项详情',
          group: '客户营销',
          hideInMenu: true,
          requiredMenu: '/marketing/asset-projects',
          requiredResource: '/api/marketing/asset-projects'
        }
      },

      // 中台支持：正式菜单资源来自 /bizexec/supports；创建/详情页隐藏于同一菜单下。
      {
        path: 'bizexec/supports',
        name: 'SupportRequests',
        component: () => import('@/views/businessApplication/SupportRequests.vue'),
        meta: {
          title: '中台支持',
          group: '客户营销',
          requiredMenu: '/bizexec/supports',
          requiredResource: '/api/support-requests'
        }
      },
      {
        path: 'bizexec/supports/new',
        name: 'SupportRequestCreate',
        component: () => import('@/views/businessApplication/SupportRequests.vue'),
        meta: {
          title: '新建中台支持',
          group: '客户营销',
          hideInMenu: true,
          requiredMenu: '/bizexec/supports',
          requiredResource: '/api/support-requests'
        }
      },
      {
        path: 'bizexec/supports/:id',
        name: 'SupportRequestDetail',
        component: () => import('@/views/businessApplication/SupportRequests.vue'),
        meta: {
          title: '中台支持详情',
          group: '客户营销',
          hideInMenu: true,
          requiredMenu: '/bizexec/supports',
          requiredResource: '/api/support-requests'
        }
      },
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

      // 客户营销
      { path: 'customers/manage', name: 'MarketingCustomerList', component: () => import('@/views/customerMarketing/MarketingCustomerList.vue'), meta: { title: '营销客户列表', group: '客户营销', requiredResource: '/api/marketing/customers' } },
      { path: 'customers/list', name: 'CustomerList', component: () => import('@/views/customerMarketing/MyCustomers.vue'), meta: { title: '我的客户', group: '客户营销', requiredResource: '/api/marketing/customers/mine' } },
      { path: 'customers/leads/new', name: 'LeadEntry', component: () => import('@/views/customerMarketing/MarketingLeadEntry.vue'), meta: { title: '线索录入', group: '客户营销', requiredResource: '/api/marketing/leads' } },
      { path: 'customers/leads/approval', name: 'LeadApproval', component: () => import('@/views/customerMarketing/MarketingLeadApproval.vue'), meta: { title: '线索审批', group: '客户营销', requiredResource: '/api/marketing/lead-approvals/**' } },
      { path: 'customers/pool/available', name: 'CustomerPoolAvailable', component: () => import('@/views/customerMarketing/AvailablePool.vue'), meta: { title: '待认领客户', group: '客户营销' } },
      { path: 'customers/tags', name: 'CustomerTags', component: () => import('@/views/customerMarketing/MarketingCustomerTags.vue'), meta: { title: '营销客户标签', group: '客户营销', requiredResource: '/api/marketing/customer-tags' } },
      { path: 'customers/tags/approval', name: 'CustomerTagApproval', component: () => import('@/views/customerMarketing/MarketingTagCustomerApproval.vue'), meta: { title: '标签客户审核', group: '客户营销', requiredResource: '/api/marketing/customer-tag-approvals/**' } },
      { path: 'customers/touch-limits', name: 'CustomerTouchLimits', component: () => import('@/views/customerMarketing/TouchLimitManagement.vue'), meta: { title: '客户触达周期管理', group: '客户营销', requiredResource: '/api/touch-limit-rules' } },
      { path: 'customers/cross-org', name: 'CrossOrgMarketing', component: () => import('@/views/customerMarketing/CrossOrgMarketing.vue'), meta: { title: '跨机构营销申请', group: '客户营销' } },
      { path: 'customers/transfer-log', name: 'CustomerTransfers', component: () => import('@/views/customerMarketing/CustomerTransfers.vue'), meta: { title: '客户转交记录', group: '客户营销' } },
      { path: 'customers/pool/claimed', name: 'CustomerPoolClaimed', component: () => import('@/views/customerMarketing/ClaimedPool.vue'), meta: { title: '已认领客户', group: '客户营销' } },
      { path: 'touches/mine', name: 'MyTouchTasks', component: () => import('@/views/customerMarketing/MyTouches.vue'), meta: { title: '我的触达任务', group: '客户营销' } },
      { path: 'touches/overview', name: 'TouchOverview', component: () => import('@/views/customerMarketing/TouchOverview.vue'), meta: { title: '触达任务一览', group: '客户营销' } },

      // 浦爱云盾
      { path: 'yundun/accountability-violations', name: 'YundunAccountabilityViolations', component: () => import('@/views/yundun/ViolationManagement.vue'), meta: { title: '人员违规信息', group: '浦爱云盾', violationKind: 'accountability' } },
      { path: 'yundun/credit-violations', name: 'YundunCreditViolations', component: () => import('@/views/yundun/ViolationManagement.vue'), meta: { title: '信贷风险信息', group: '浦爱云盾', violationKind: 'credit' } },

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
      { path: 'screen-admin/datasources', name: 'ScreenAdminDs',       component: () => import('@/views/screen/admin/Datasources.vue'), meta: { title: '大屏数据源', group: '报表分析', requiredResource: '/api/screen/admin/datasources' } },
      { path: 'screen-admin/org-profiles', name: 'ScreenAdminOrgProfiles', component: () => import('@/views/screen/admin/OrgProfiles.vue'), meta: { title: '机构经营画像', group: '报表分析', requiredResource: '/api/admin/org-profiles' } },
      { path: 'screen-admin/org-groups', name: 'ScreenAdminOrgGroups', component: () => import('@/views/screen/admin/OrgGroups.vue'), meta: { title: '命名机构组', group: '报表分析', requiredResource: '/api/admin/org-groups' } },
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

/**
 * 红色引擎页面除平台资源外再按角色做一层路由门禁，避免仅隐藏菜单而允许直接输入 URL。
 * SYS_ADMIN 仅按各路由显式白名单生效，不能借系统管理员身份访问组织审核员专属工作台。
 */
export function checkRouteRoleAccess(to, userStore = useUserStore()) {
  const metas = to?.matched?.length
    ? to.matched.map((record) => record.meta || {})
    : [to?.meta || {}];
  const requiredRoleCodes = [...new Set(
    metas.flatMap((meta) => {
      const declared = meta.requiredRoleCodes;
      return Array.isArray(declared) ? declared : declared ? [declared] : [];
    })
  )];
  if (!requiredRoleCodes.length) return true;
  const roleCodes = normalizeRoleCodes(userStore.roles, userStore.isSystemAdmin);
  return requiredRoleCodes.some((roleCode) => roleCodes.has(roleCode))
    ? true
    : { path: '/no-access' };
}

function checkDeclaredRouteAccess(to) {
  const roleAccess = checkRouteRoleAccess(to);
  if (roleAccess !== true) return roleAccess;
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
