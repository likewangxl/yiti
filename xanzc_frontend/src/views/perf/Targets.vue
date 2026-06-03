<template>
  <div>
    <div class="page-h">
      <h1>目标管理 <span class="sub">方案级管理：新增方案 / 进入子页维护目标值</span></h1>
      <div class="actions">
        <el-button @click="reload" :loading="loadingPlans || todoLoading || doneLoading">刷新</el-button>
        <el-button v-if="activeTab==='plans' && canCreatePlan" type="primary" @click="openCreatePlan">+ 新增方案</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="targets-tabs">
      <!-- ============ 目标方案 ============ -->
      <el-tab-pane label="目标方案" name="plans">
        <!-- 筛选栏（4 列：方案搜索 / 关联KPI方案 / 维度 / 状态）。表格基于 f 即时过滤 -->
        <div class="card-section filter-grid">
          <div>
            <div class="lab">方案搜索</div>
            <el-input v-model="f.keyword" clearable placeholder="方案编码 / 名称"
              style="width:100%" @keyup.enter="reload" />
          </div>
          <div>
            <div class="lab">关联KPI方案</div>
            <el-input v-model="f.kpiKeyword" clearable placeholder="KPI 方案编码 / 名称"
              style="width:100%" @keyup.enter="reload" />
          </div>
          <div>
            <div class="lab">目标维度</div>
            <el-select v-model="f.targetDim" clearable placeholder="全部" style="width:100%">
              <el-option v-for="o in BASE_DIMS" :key="o.v" :value="o.v" :label="o.l" />
            </el-select>
          </div>
          <div>
            <div class="lab">状态</div>
            <el-select v-model="f.status" clearable placeholder="全部" style="width:100%">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </div>
          <div class="filter-actions">
            <el-button type="primary" @click="reload">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>
        </div>

        <!-- 主表（方案级，每行 1 个方案） -->
        <div class="card-section table">
          <el-table :data="pagedPlans" size="default" empty-text="暂无目标方案" v-loading="loadingPlans">
            <el-table-column label="方案编码" prop="planCode" width="160" />
            <el-table-column label="方案名称" prop="planName" min-width="200" />
            <el-table-column label="关联 KPI 方案" min-width="200">
              <template #default="{row}">{{ kpiLabelOf(row.kpiSchemeId) }}</template>
            </el-table-column>
            <el-table-column label="维度" width="80">
              <template #default="{row}">{{ dimLabel(row.targetDim) }}</template>
            </el-table-column>
            <el-table-column label="起止日期" min-width="200">
              <template #default="{row}">
                <span v-if="row.startDate || row.endDate">
                  {{ row.startDate || '…' }} ~ {{ row.endDate || '…' }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="创建人" min-width="180">
              <template #default="{row}">{{ userMap.get(row.createdBy) || row.createdBy || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{row}">
                <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openValues(row)">目标值</el-button>
                <!-- 仅创建人可编辑：业务规则 - 资财人员可看全行方案（ALL scope），但只能改自己的 -->
                <el-button v-if="row.createdBy === userStore.user?.empId" link type="primary" size="small" @click="openEditPlan(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="pager.pageNo"
              v-model:page-size="pager.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="filteredPlans.length"
              background
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>

          <el-alert type="info" :closable="false" show-icon style="margin-top:14px"
            title="点击「管理目标值」进入子页面维护方案下的员工 / 机构目标值；修正审批通过后将触发 KPI 历史回算（生成新批次 CALC-YYMMDD-xxx）。" />
        </div>
      </el-tab-pane>

      <!-- ============ 待我审批（TARGET_ADJUST 流程任务） ============ -->
      <el-tab-pane v-if="canApprove" label="待我审批" name="todo">
        <div class="card-section table">
          <div class="tab-actions">
            <el-button type="primary" @click="openBatchReview">
              批量审批{{ todoSelection.length ? `（已选 ${todoSelection.length}）` : '' }}
            </el-button>
          </div>
          <el-table :data="pagedTodos" size="default" empty-text="暂无待审批任务" v-loading="todoLoading"
                    @selection-change="onTodoSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column label="方案编号" min-width="150">
              <template #default="{row}"><code class="mono">{{ planOfTodo(row)?.planCode || '-' }}</code></template>
            </el-table-column>
            <el-table-column label="方案名称" min-width="180">
              <template #default="{row}">{{ planOfTodo(row)?.planName || '-' }}</template>
            </el-table-column>
            <el-table-column label="关联KPI方案" min-width="180">
              <template #default="{row}">{{ planOfTodo(row) ? kpiLabelOf(planOfTodo(row).kpiSchemeId) : '-' }}</template>
            </el-table-column>
            <el-table-column label="维度" width="80">
              <template #default="{row}">{{ dimLabel(planOfTodo(row)?.targetDim) }}</template>
            </el-table-column>
            <el-table-column label="当前节点" width="160">
              <template #default="{row}">{{ row.taskName || row.nodeKey || '-' }}</template>
            </el-table-column>
            <el-table-column label="发起人" width="140">
              <template #default="{row}">{{ row.startUserName || row.startUser || '-' }}</template>
            </el-table-column>
            <el-table-column label="提交时间" width="170">
              <template #default="{row}">{{ fmtDateTime(row.startTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openReview(row)">审批</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="todoPager.pageNo"
              v-model:page-size="todoPager.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="todos.length"
              background
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- ============ 已审批（从业务表查 APPROVED/REJECTED，不含 IN_APPROVAL） ============ -->
      <el-tab-pane v-if="canApprove" label="已审批" name="done">
        <div class="card-section table">
          <el-table :data="pagedDones" size="default" empty-text="暂无已审批记录" v-loading="doneLoading">
            <el-table-column label="方案编号" min-width="150">
              <template #default="{row}"><code class="mono">{{ planOfDone(row)?.planCode || '-' }}</code></template>
            </el-table-column>
            <el-table-column label="方案名称" min-width="180">
              <template #default="{row}">{{ planOfDone(row)?.planName || '-' }}</template>
            </el-table-column>
            <el-table-column label="关联KPI方案" min-width="180">
              <template #default="{row}">{{ planOfDone(row) ? kpiLabelOf(planOfDone(row).kpiSchemeId) : '-' }}</template>
            </el-table-column>
            <el-table-column label="维度" width="80">
              <template #default="{row}">{{ dimLabel(planOfDone(row)?.targetDim || row.subjectType) }}</template>
            </el-table-column>
            <el-table-column label="发起人" width="160">
              <template #default="{row}">{{ userMap.get(row.createdBy) || row.createdByName || row.createdBy || '-' }}</template>
            </el-table-column>
            <el-table-column label="申请时间" width="170">
              <template #default="{row}">{{ fmtDateTime(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column label="审批时间" width="170">
              <template #default="{row}">{{ fmtDateTime(row.updatedTime) }}</template>
            </el-table-column>
            <el-table-column label="结果" width="80">
              <template #default="{row}">
                <el-tag v-if="row.status==='APPROVED'" class="tag-success" effect="plain">通过</el-tag>
                <el-tag v-else-if="row.status==='REJECTED'" class="tag-danger" effect="plain">驳回</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="donePager.pageNo"
              v-model:page-size="donePager.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="dones.length"
              background
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 审批弹窗（仅"待我审批"tab 用：通过 / 驳回 + 审批意见） -->
    <el-dialog v-model="reviewDlg.show" :title="reviewTitle" width="560px" :close-on-click-modal="false">
      <div class="review-meta">
        <div><span class="lab">申请编号：</span><code>{{ reviewDlg.row?.businessKey || reviewDlg.row?.bizId || '-' }}</code></div>
        <div><span class="lab">发起人：</span>{{ reviewDlg.row?.startUserName || reviewDlg.row?.startUser || '-' }}</div>
        <div><span class="lab">提交时间：</span>{{ fmtDateTime(reviewDlg.row?.startTime) }}</div>
      </div>
      <!-- 修正详情（从 PERF_TARGET_ADJUST_APPLY.remark JSON 解析） -->
      <div class="review-detail" v-if="reviewDlg.detail" v-loading="reviewDlg.detailLoading">
        <div v-for="(adj, i) in reviewDlg.detail.adjustments" :key="i" class="adj-item">
          <div><span class="lab">指标：</span>{{ metricLabel(adj.metricCode) }}</div>
          <div>
            <span class="lab">原目标值：</span><strong>{{ fmtNum(adj.oldValue) }}</strong>
            <span class="sep">→</span>
            <span class="lab">新目标值：</span><strong class="new-val">{{ fmtNum(adj.newValue) }}</strong>
          </div>
          <div v-if="adj.oldBaseValue != null || adj.newBaseValue != null">
            <span class="lab">原基础值：</span><strong>{{ adj.oldBaseValue != null ? fmtNum(adj.oldBaseValue) : '-' }}</strong>
            <span class="sep">→</span>
            <span class="lab">新基础值：</span><strong class="new-val">{{ adj.newBaseValue != null ? fmtNum(adj.newBaseValue) : '-' }}</strong>
          </div>
        </div>
        <div><span class="lab">修正原因：</span>{{ reviewDlg.detail.reason || '-' }}</div>
      </div>
      <div v-else-if="reviewDlg.detailLoading" v-loading="true" style="height:60px"></div>
      <el-form :model="reviewDlg" label-position="top" size="default" style="margin-top:12px">
        <el-form-item label="审批意见" required>
          <el-input v-model="reviewDlg.opinion" type="textarea" :rows="3"
                    placeholder="请填写审批意见（必填，将记入审批日志）" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
                title="通过 → 触发目标值更新；驳回 → 申请记录置为驳回，目标值不变。" />
      <template #footer>
        <el-button @click="reviewDlg.show = false">取消</el-button>
        <el-button type="danger" :loading="reviewDlg.saving" @click="submitReview('REJECT')">驳回</el-button>
        <el-button type="primary" :loading="reviewDlg.saving" @click="submitReview('APPROVE')">通过</el-button>
      </template>
    </el-dialog>

    <!-- 批量审批弹窗：对所选的多条待审批记录统一通过 / 驳回 -->
    <el-dialog v-model="batchDlg.show" title="批量审批" width="520px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
                :title="`已选 ${todoSelection.length} 条记录，将对全部所选记录统一处理。`" />
      <el-form :model="batchDlg" label-position="top" size="default">
        <el-form-item label="审批意见" required>
          <el-input v-model="batchDlg.opinion" type="textarea" :rows="3"
                    placeholder="请填写审批意见（必填，将记入每条审批日志）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDlg.show = false">取消</el-button>
        <el-button type="danger" :loading="batchDlg.saving" @click="submitBatchReview('REJECT')">驳回</el-button>
        <el-button type="primary" :loading="batchDlg.saving" @click="submitBatchReview('APPROVE')">通过</el-button>
      </template>
    </el-dialog>

    <!-- 已审批详情弹窗（只读） -->
    <el-dialog v-model="detailDlg.show" :title="detailTitle" width="580px" :close-on-click-modal="true">
      <div class="review-meta">
        <div><span class="lab">申请编号：</span><code>{{ detailDlg.row?.businessKey || detailDlg.row?.id || '-' }}</code></div>
        <div><span class="lab">发起人：</span>{{ userMap.get(detailDlg.row?.createdBy) || detailDlg.row?.createdByName || detailDlg.row?.createdBy || '-' }}</div>
        <div><span class="lab">申请时间：</span>{{ fmtDateTime(detailDlg.row?.createdTime) }}</div>
        <div><span class="lab">审批结果：</span>
          <el-tag v-if="detailDlg.row?.status==='APPROVED'" class="tag-success" effect="plain">通过</el-tag>
          <el-tag v-else-if="detailDlg.row?.status==='REJECTED'" class="tag-danger" effect="plain">驳回</el-tag>
          <span v-else>-</span>
        </div>
      </div>
      <!-- 修正详情 -->
      <div class="review-detail" v-if="detailDlg.detail" v-loading="detailDlg.loading">
        <div v-for="(adj, i) in detailDlg.detail.adjustments" :key="i" class="adj-item">
          <div><span class="lab">指标：</span>{{ metricLabel(adj.metricCode) }}</div>
          <div>
            <span class="lab">原目标值：</span><strong>{{ fmtNum(adj.oldValue) }}</strong>
            <span class="sep">→</span>
            <span class="lab">新目标值：</span><strong class="new-val">{{ fmtNum(adj.newValue) }}</strong>
          </div>
          <div v-if="adj.oldBaseValue != null || adj.newBaseValue != null">
            <span class="lab">原基础值：</span><strong>{{ adj.oldBaseValue != null ? fmtNum(adj.oldBaseValue) : '-' }}</strong>
            <span class="sep">→</span>
            <span class="lab">新基础值：</span><strong class="new-val">{{ adj.newBaseValue != null ? fmtNum(adj.newBaseValue) : '-' }}</strong>
          </div>
        </div>
        <div><span class="lab">修正原因：</span>{{ detailDlg.detail.reason || '-' }}</div>
      </div>
      <!-- 审批记录（按时间倒序，申请提交节点显示"申请人"、审批节点显示"审批人"） -->
      <div class="review-history" v-if="detailDlg.history.length" style="margin-top:12px">
        <div class="history-title">审批记录</div>
        <div v-for="(log, i) in sortedHistory" :key="i" class="history-item">
          <span class="lab">{{ log.action === 'SUBMIT' ? '申请人' : '审批人' }}：</span>{{ log.operatorName || log.operator || '-' }}
          <span class="sep">|</span>
          <span class="lab">节点：</span>{{ log.nodeName || log.nodeKey || '-' }}
          <span class="sep">|</span>
          <span class="lab">时间：</span>{{ fmtDateTime(log.operateTime) }}
          <div v-if="log.opinion"><span class="lab">意见：</span>{{ log.opinion }}</div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailDlg.show = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增方案对话框（资财部限定，由 canCreatePlan 控制可见） -->
    <el-dialog v-model="planDlg.show" :title="planDlg.editing ? '编辑目标方案' : '新增目标方案'" width="560px" :close-on-click-modal="false">
      <el-form ref="planFormRef" :model="planDlg.form" :rules="planRules" label-width="120px" size="default">
        <el-form-item label="方案编码" prop="planCode">
          <el-input v-model="planDlg.form.planCode" :disabled="!!planDlg.editing" placeholder="大写字母开头，如 TP_2026_Q2" />
        </el-form-item>
        <el-form-item label="方案名称" prop="planName">
          <el-input v-model="planDlg.form.planName" placeholder="如 2026 年度目标方案" />
        </el-form-item>
        <el-form-item label="关联 KPI 方案" prop="kpiSchemeId">
          <el-select v-model="planDlg.form.kpiSchemeId" filterable placeholder="选择 KPI 方案" style="width:100%"
                     :disabled="planDlg.editing && planDlg.hasValues">
            <el-option v-for="s in kpiSchemeOptions" :key="s.id"
                       :label="`${s.schemeCode || s.code || '-'} · ${s.schemeName || s.name || '-'}`"
                       :value="s.id" />
          </el-select>
          <div v-if="planDlg.editing && planDlg.hasValues" class="lock-hint">该方案已存在目标值，关联 KPI 方案不可修改</div>
        </el-form-item>
        <el-form-item label="目标维度" prop="targetDim">
          <el-radio-group v-model="planDlg.form.targetDim" :disabled="planDlg.editing && planDlg.hasValues">
            <el-radio value="EMP">人员（EMP）</el-radio>
            <el-radio value="ORG">机构（ORG）</el-radio>
          </el-radio-group>
          <div v-if="planDlg.editing && planDlg.hasValues" class="lock-hint">该方案已存在目标值，目标维度不可修改</div>
        </el-form-item>
        <!-- 生效日期已隐藏：保存时由起始日期自动填充（onSavePlan 里 effectiveDate = startDate） -->
        <el-form-item label="起始日期" prop="startDate" required>
          <el-date-picker v-model="planDlg.form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="截止日期" prop="endDate">
          <el-date-picker v-model="planDlg.form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDlg.show = false">取消</el-button>
        <template v-if="planDlg.editing">
          <el-button v-if="planDlg.form._status==='ACTIVE'" type="warning" @click="togglePlanStatus(planDlg.editing,'DISABLED')">禁用</el-button>
          <el-button v-else type="success" @click="togglePlanStatus(planDlg.editing,'ACTIVE')">启用</el-button>
        </template>
        <el-button type="primary" :loading="planDlg.saving" @click="onSavePlan">保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listTargets, createTargetPlan, updateTargetPlan, listKpiRules, getTargetAdjust, listTargetAdjusts, getTargetAdjustApprovalHistory, listTargetValues, listMetrics } from '@/api/perf';
import { listUsers } from '@/api/users';
import { listTodoTasks, listDoneTasks, approveTask, rejectTask, claimTask } from '@/api/workflow';
import { getMyPermissions } from '@/api/auth';
import { useUserStore } from '@/stores/user';

const route  = useRoute();
const router = useRouter();
const userStore = useUserStore();

// 新增方案按钮可见性：仅资材部用户（资财部经办 BACK_FINANCE / 资财部负责人 FINANCE_LEADER）。
// 按业务要求"只有资材部用户可以使用"，不再放行系统管理员(R_ADMIN/SYS_ADMIN)等其它角色。
const canCreatePlan = computed(() => {
  const roles = userStore.user?.roles || [];
  const codes = roles.map(r => (typeof r === 'string' ? r : (r.roleId || r.roleCode)));
  return codes.some(c => c === 'R_BACK_FINANCE' || c === 'R_FIN_LEAD'
                          || c === 'BACK_FINANCE' || c === 'FINANCE_LEADER');
});

// 资财部负责人(FINANCE_LEADER)：可查看全部目标方案，不受"仅本人创建"限制
const isFinanceLeader = computed(() => {
  const roles = userStore.user?.roles || [];
  const codes = roles.map(r => (typeof r === 'string' ? r : (r.roleId || r.roleCode)));
  return codes.some(c => c === 'FINANCE_LEADER' || c === 'R_FIN_LEAD');
});

// === 维度 / 周期 / 状态 字典 ===
const BASE_DIMS = [
  { v: 'EMP', l: '员工' },
  { v: 'ORG', l: '机构' }
];
const dimLabel    = (t) => ({ EMP: '员工', ORG: '机构' }[t] || (t || '-'));
const statusLabel = (s) => ({ ACTIVE: '启用', DISABLED: '停用' }[s] || (s || '-'));
const statusCls   = (s) => ({ ACTIVE: 'tag-success', DISABLED: 'tag-info' }[s] || 'tag-info');

// === KPI 方案下拉 + id→label 映射（用于表格"关联 KPI 方案"列翻译） ===
const kpiSchemeOptions = ref([]);
const kpiMap = ref(new Map());
async function loadKpiSchemeOptions() {
  try {
    const r = await listKpiRules({ pageSize: 100 });
    const arr = Array.isArray(r) ? r : (r?.records || []);
    // 后端 TargetPlanService.create 要求关联 KPI 方案必须 status='ACTIVE'，
    // 选 DISABLED/DRAFT/TRIAL_RUN/INACTIVE 都会被 PERF-42200 拒绝；前端过滤掉非 ACTIVE。
    kpiSchemeOptions.value = arr.filter(s => s.status === 'ACTIVE');
    const m = new Map();
    // kpiMap 保留全部（含非 ACTIVE）用于列表展示历史方案的名称翻译，不影响下拉过滤
    for (const s of arr) {
      const code = s.schemeCode || s.code || '';
      const name = s.schemeName || s.name || '';
      m.set(s.id, `${code} · ${name}`.replace(/^ · /, '').replace(/ · $/, ''));
    }
    kpiMap.value = m;
  } catch { /* 列表仍可显示 ID 兜底 */ }
}
const kpiLabelOf = (id) => kpiMap.value.get(id) || id || '-';

// 指标编号 → 指标名称映射（审批详情里把"指标：M_0268"显示成"M_0268 · 名称"）
const metricNameMap = ref(new Map());
async function loadMetricMap() {
  try {
    const r = await listMetrics({ pageSize: 1000 });
    const arr = Array.isArray(r) ? r : (r?.records || []);
    const m = new Map();
    for (const x of arr) {
      if (x.metricCode) m.set(x.metricCode, x.metricName || '');
    }
    metricNameMap.value = m;
  } catch { /* 取不到名称时回退仅显示编号 */ }
}
// 展示成 "M_0268 · 指标名称"；无名称或未命中时仅显示编号
const metricLabel = (code) => {
  if (!code) return '-';
  const name = metricNameMap.value.get(code);
  return name ? `${code} · ${name}` : code;
};

// ===== 审批列表展示方案信息（方案编号/名称/关联KPI方案/维度）=====
// planId → 方案对象（来自已加载的目标方案列表 plans）
const planById = computed(() => {
  const m = new Map();
  for (const p of plans.value) if (p.id != null) m.set(String(p.id), p);
  return m;
});
// 目标修正申请索引：businessKey / id → apply，供"待我审批"(workflow task) 反查 planId
const applyByKey = ref(new Map());
async function loadApplyIndex() {
  try {
    const r = await listTargetAdjusts({ pageSize: 100 });
    const arr = Array.isArray(r) ? r : (r?.records || []);
    const m = new Map();
    for (const a of arr) {
      if (a.businessKey) m.set(a.businessKey, a);
      if (a.id) m.set(String(a.id), a);
    }
    applyByKey.value = m;
  } catch { /* 索引取不到则方案列回退显示 '-' */ }
}
// 待我审批行(workflow task) → 方案；先用 businessKey 反查 apply 再取 planId
const planOfTodo = (row) => {
  const a = applyByKey.value.get(row.businessKey) || applyByKey.value.get(row.bizId);
  return a ? planById.value.get(String(a.planId)) : null;
};
// 已审批行(apply) → 方案（apply 自带 planId）
const planOfDone = (row) => planById.value.get(String(row.planId));

// === 方案列表 ===
// f = 筛选条件（双向绑定到控件）。filteredPlans 直接读 f，输入即时过滤。
const f = reactive({ keyword: '', kpiKeyword: '', targetDim: '', status: '' });

// 重置查询条件：清空筛选项（表格基于 f 即时过滤，清空后自动恢复全量）
function resetFilters() {
  f.keyword = '';
  f.kpiKeyword = '';
  f.targetDim = '';
  f.status = '';
}
const plans = ref([]);
const loadingPlans = ref(false);

// user_id → "username (中文名)" 映射，用于列表"创建人"列展示
const userMap = ref(new Map());
async function loadUserMap() {
  try {
    const r = await listUsers({ pageSize: 200 });
    const list = Array.isArray(r) ? r : (r?.records || []);
    const m = new Map();
    for (const u of list) {
      const id = u.userId || u.empId;
      if (!id) continue;
      const uname = u.username || '';
      const cn    = u.userchnname || '';
      // 形如 "finance_zhou (周八(资财))"；若任一为空则只显示有的部分
      const label = uname && cn ? `${uname} (${cn})` : (uname || cn || id);
      m.set(id, label);
    }
    userMap.value = m;
  } catch {
    // listUsers 403 等异常时静默——列表降级显示原始 user_id 不阻塞页面
  }
}

async function loadPlans() {
  loadingPlans.value = true;
  try {
    const r = await listTargets({ pageSize: 100 });
    // listTargets 走 unwrapPage：分页响应返回 { records: [...], total } 形态；
    // 非分页直接 Array。两种都要兼容，否则前端永远显示空白。
    plans.value = Array.isArray(r) ? r : (r?.records || []);
  } catch {
    plans.value = [];
  } finally {
    loadingPlans.value = false;
  }
}

// 即时过滤：f 任一字段变化都会触发 computed 重算，无需点"查询"
const filteredPlans = computed(() => {
  let arr = plans.value;
  // 目标方案：仅展示创建人=本人的记录；资财部负责人不受限制，可看全部
  const myEmpId = userStore.user?.empId;
  if (myEmpId && !isFinanceLeader.value) arr = arr.filter(p => p.createdBy === myEmpId);
  if (f.keyword) {
    const kw = String(f.keyword).toLowerCase();
    arr = arr.filter(p => (p.planCode || '').toLowerCase().includes(kw)
                       || (p.planName || '').toLowerCase().includes(kw));
  }
  if (f.kpiKeyword) {
    const kw = String(f.kpiKeyword).toLowerCase();
    // 按列表显示的「关联 KPI 方案」文案（编码 · 名称）模糊匹配
    arr = arr.filter(p => String(kpiLabelOf(p.kpiSchemeId) || '').toLowerCase().includes(kw));
  }
  if (f.targetDim) arr = arr.filter(p => p.targetDim === f.targetDim);
  if (f.status)    arr = arr.filter(p => p.status === f.status);
  return arr;
});

// === 分页（前端 client-side：filteredPlans → slice 给表格） ===
const pager = reactive({ pageNo: 1, pageSize: 10 });
const pagedPlans = computed(() => {
  const start = (pager.pageNo - 1) * pager.pageSize;
  return filteredPlans.value.slice(start, start + pager.pageSize);
});

// 「🔄 刷新」按钮的处理：重拉一次方案数据 + 回第 1 页（筛选条件由 filteredPlans 即时生效，不需再 apply）
async function onSearch() {
  pager.pageNo = 1;
  await loadPlans();
}

// === Tabs 状态：目标方案 / 待我审批 / 已审批 ===
const activeTab = ref('plans');

// 「审批资格」权限 gate：与 Adjust.vue 同口径，
// 用户 resourceUrls 含任一 /api/workflow/tasks(*) URL 即视为有审批资格；SYS_ADMIN 一律放行
const canApprove = ref(false);
async function loadCanApprove() {
  try {
    const p = await getMyPermissions();
    if (p?.isSystemAdmin) { canApprove.value = true; return; }
    const urls = p?.resourceUrls || p?.resources || [];
    canApprove.value = Array.isArray(urls) && urls.some(
      u => typeof u === 'string' && u.startsWith('/api/workflow/tasks')
    );
  } catch { canApprove.value = false; }
}

// 时间格式化（仅 tabs 用，避免新引入 dayjs）
function fmtDateTime(v) {
  if (!v) return '-';
  const d = new Date(v);
  if (isNaN(d.getTime())) return String(v);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// === 待我审批（TARGET_ADJUST bizType） ===
const todos = ref([]);
const todoLoading = ref(false);
const todoPager = reactive({ pageNo: 1, pageSize: 10 });
const pagedTodos = computed(() => {
  const start = (todoPager.pageNo - 1) * todoPager.pageSize;
  return todos.value.slice(start, start + todoPager.pageSize);
});
async function loadTodos() {
  todoLoading.value = true;
  try {
    const r = await listTodoTasks({ pageSize: 50, bizType: 'TARGET_ADJUST' });
    // listTodoTasks 经 unwrapPage 返回 {records,total}（非数组），必须取 records
    todos.value = Array.isArray(r) ? r : (r?.records || []);
    todoPager.pageNo = 1;
    // 刷新申请索引，保证待审批行能按 businessKey 反查到方案信息
    loadApplyIndex();
  } catch { todos.value = []; }
  finally { todoLoading.value = false; }
}

// === 已审批（TARGET_ADJUST bizType） ===
const dones = ref([]);
const doneLoading = ref(false);
const donePager = reactive({ pageNo: 1, pageSize: 10 });
const pagedDones = computed(() => {
  const start = (donePager.pageNo - 1) * donePager.pageSize;
  return dones.value.slice(start, start + donePager.pageSize);
});
async function loadDones() {
  doneLoading.value = true;
  try {
    // 从业务表拉已完结的修正申请（APPROVED / REJECTED），不从 workflow done-tasks 拉
    // 避免未审批完的中间节点 task 混入"已审批"列表
    const r = await listTargetAdjusts({ pageSize: 100 });
    // listTargetAdjusts 经 unwrapPage 返回 {records,total}（非数组），必须取 records
    const all = Array.isArray(r) ? r : (r?.records || []);
    dones.value = all.filter(d => d.status === 'APPROVED' || d.status === 'REJECTED');
    donePager.pageNo = 1;
  } catch { dones.value = []; }
  finally { doneLoading.value = false; }
}
// === 已审批详情弹窗（只读） ===
const detailDlg = reactive({ show: false, loading: false, row: null, detail: null, history: [] });
const detailTitle = computed(() => {
  if (!detailDlg.row) return '审批详情';
  const st = detailDlg.row.status === 'APPROVED' ? '已通过' : detailDlg.row.status === 'REJECTED' ? '已驳回' : '';
  return `审批详情${st ? ' · ' + st : ''}`;
});
const sortedHistory = computed(() =>
  [...detailDlg.history].sort((a, b) => {
    const ta = new Date(a.operateTime || 0).getTime();
    const tb = new Date(b.operateTime || 0).getTime();
    return tb - ta;
  })
);
async function openDetail(row) {
  detailDlg.row = row;
  detailDlg.detail = null;
  detailDlg.history = [];
  detailDlg.loading = true;
  detailDlg.show = true;
  const applyId = row.id;
  try {
    const [adj, hist] = await Promise.all([
      getTargetAdjust(applyId).catch(() => null),
      getTargetAdjustApprovalHistory(applyId).catch(() => [])
    ]);
    if (adj) {
      const remark = typeof adj.remark === 'string' ? JSON.parse(adj.remark) : (adj.remark || {});
      detailDlg.detail = { adjustments: remark.adjustments || [], reason: remark.reason || '' };
    }
    detailDlg.history = Array.isArray(hist) ? hist : [];
  } catch { /* 兜底 */ }
  detailDlg.loading = false;
}


// 「🔄 刷新」全局按钮：按当前 tab 路由
async function reload() {
  if (activeTab.value === 'plans') return onSearch();
  if (activeTab.value === 'todo')  return loadTodos();
  if (activeTab.value === 'done')  return loadDones();
}
function onTabChange(name) {
  if (name === 'todo' && !todos.value.length) loadTodos();
  if (name === 'done') loadDones();
}

// === 审批弹窗 ===
const reviewDlg = reactive({ show: false, saving: false, row: null, opinion: '', detail: null, detailLoading: false });
const reviewTitle = computed(() => {
  if (!reviewDlg.row) return '审批';
  return `审批 · ${reviewDlg.row.title || reviewDlg.row.businessKey || ''}`;
});
const fmtNum = (v) => (v == null || v === '') ? '-' : Number(v).toLocaleString();
async function openReview(row) {
  reviewDlg.row = row;
  reviewDlg.opinion = '';
  reviewDlg.detail = null;
  reviewDlg.detailLoading = true;
  reviewDlg.show = true;
  // 根据 bizId（= applyId）拉修正申请详情，解析 remark JSON 得到 adjustments + reason
  const applyId = (row.businessKey || '').split(':')[1] || row.bizId;
  if (applyId) {
    try {
      const d = await getTargetAdjust(applyId);
      const remark = typeof d?.remark === 'string' ? JSON.parse(d.remark) : (d?.remark || {});
      reviewDlg.detail = {
        adjustments: remark.adjustments || [],
        reason: remark.reason || d?.reason || ''
      };
    } catch { reviewDlg.detail = null; }
  }
  reviewDlg.detailLoading = false;
}
// 候选组任务（assignee=null, claimable=true）必须先 claim 才能 approve/reject
async function ensureClaimed(row) {
  if (row.claimable && row.taskId) {
    await claimTask(row.taskId);
  }
}
async function submitReview(action) {
  if (!reviewDlg.opinion || !reviewDlg.opinion.trim()) {
    return ElMessage.warning('请填写审批意见');
  }
  if (!reviewDlg.row?.taskId) {
    return ElMessage.error('任务 ID 缺失，无法提交');
  }
  reviewDlg.saving = true;
  try {
    await ensureClaimed(reviewDlg.row);
    if (action === 'APPROVE') {
      await approveTask(reviewDlg.row.taskId, reviewDlg.opinion);
      ElMessage.success('已通过');
    } else {
      await rejectTask(reviewDlg.row.taskId, reviewDlg.opinion);
      ElMessage.success('已驳回');
    }
    reviewDlg.show = false;
    await loadTodos();
    dones.value = [];
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '审批失败');
  } finally {
    reviewDlg.saving = false;
  }
}

// === 批量审批：多选 + 统一通过/驳回 ===
const todoSelection = ref([]);
function onTodoSelectionChange(rows) {
  todoSelection.value = rows || [];
}
const batchDlg = reactive({ show: false, saving: false, opinion: '' });
// 点击「批量审批」：必须已选 ≥1 条，否则报错；通过后弹出批量审批弹窗
function openBatchReview() {
  if (!todoSelection.value.length) {
    return ElMessage.error('请至少选择一条待审批记录');
  }
  batchDlg.opinion = '';
  batchDlg.show = true;
}
// 对所选全部记录统一通过/驳回；审批意见必填；逐条提交，统计成功/失败
async function submitBatchReview(action) {
  if (!batchDlg.opinion || !batchDlg.opinion.trim()) {
    return ElMessage.warning('请填写审批意见');
  }
  const rows = todoSelection.value.filter(r => r && r.taskId);
  if (!rows.length) {
    return ElMessage.error('请至少选择一条待审批记录');
  }
  batchDlg.saving = true;
  let ok = 0;
  let fail = 0;
  for (const row of rows) {
    try {
      await ensureClaimed(row);
      if (action === 'APPROVE') {
        await approveTask(row.taskId, batchDlg.opinion);
      } else {
        await rejectTask(row.taskId, batchDlg.opinion);
      }
      ok++;
    } catch (e) {
      fail++;
    }
  }
  batchDlg.saving = false;
  batchDlg.show = false;
  const verb = action === 'APPROVE' ? '通过' : '驳回';
  if (fail === 0) {
    ElMessage.success(`批量${verb}成功：${ok} 条`);
  } else {
    ElMessage.warning(`批量${verb}完成：成功 ${ok} 条，失败 ${fail} 条`);
  }
  todoSelection.value = [];
  await loadTodos();
  dones.value = [];
}

// === 跳子页（带 planId 给 TargetValues.vue 预选方案） ===
function openValues(row) {
  router.push({
    name: 'PerfTargetValues',
    query: { planId: row.id || row.planCode }
  });
}

// === 新增方案对话框 ===
const planFormRef = ref(null);
const planDlg = reactive({
  show: false, saving: false, editing: null, hasValues: false,
  form: { planCode: '', planName: '', kpiSchemeId: '', targetDim: 'EMP',
          effectiveDate: '', startDate: '', endDate: '' }
});
// 生效日期不再在 UI 暴露：onSavePlan 提交前自动用 startDate 兜底，因此校验改放在 startDate
const planRules = {
  planCode:      [{ required: true, message: '请填写方案编码' },
                  { pattern: /^[A-Z][A-Z0-9_]*$/, message: '方案编码必须以大写字母开头，仅含大写字母/数字/下划线' }],
  planName:      [{ required: true, message: '请填写方案名称' }],
  kpiSchemeId:   [{ required: true, message: '请选择关联 KPI 方案' }],
  targetDim:     [{ required: true, message: '请选择目标维度' }],
  startDate:     [{ required: true, message: '请选择起始日期（同时作为生效日期）' }],
};
function openCreatePlan() {
  planDlg.editing = null;
  planDlg.hasValues = false;
  Object.assign(planDlg.form, {
    planCode: '', planName: '', kpiSchemeId: '', targetDim: 'EMP',
    effectiveDate: '', startDate: '', endDate: ''
  });
  planDlg.show = true;
}
async function openEditPlan(row) {
  planDlg.editing = row.id || row.planCode;
  planDlg.hasValues = false;
  // 先确保 KPI 方案下拉的 options 已加载，否则 el-select 拿到 kpiSchemeId 也无 option 匹配显示空白
  if (!kpiSchemeOptions.value.length) {
    try { await loadKpiSchemeOptions(); } catch {}
  }
  Object.assign(planDlg.form, {
    planCode: row.planCode || '', planName: row.planName || '',
    kpiSchemeId: row.kpiSchemeId || '', targetDim: row.targetDim || 'EMP',
    effectiveDate: row.effectiveDate || '', startDate: row.startDate || '', endDate: row.endDate || '',
    _status: row.status || 'ACTIVE'
  });
  planDlg.show = true;
  // 该方案是否已存在目标值：有则禁止修改关联 KPI 方案 / 目标维度（避免与已录目标值口径冲突）
  try {
    const tv = await listTargetValues({ planId: row.id, planCode: row.planCode, pageSize: 1 });
    planDlg.hasValues = ((tv?.total ?? tv?.records?.length ?? 0) > 0);
  } catch { planDlg.hasValues = false; }
}
async function onSavePlan() {
  try { await planFormRef.value.validate(); } catch { return; }
  if (planDlg.form.startDate && planDlg.form.endDate
      && planDlg.form.startDate > planDlg.form.endDate) {
    return ElMessage.warning('起始日期不能晚于截止日期');
  }
  planDlg.saving = true;
  try {
    let targetCycle = 'QUARTER';
    if (planDlg.form.startDate && planDlg.form.endDate) {
      const days = (new Date(planDlg.form.endDate) - new Date(planDlg.form.startDate)) / 86400000;
      if (days > 92) targetCycle = 'YEAR';
    }
    planDlg.form.effectiveDate = planDlg.form.startDate;
    // _status 是前端内部状态（用于"启用/禁用"按钮显示），后端 DTO 无此字段，提交前必须剔除
    const { _status, ...basePayload } = planDlg.form;
    if (planDlg.editing) {
      const { planCode, ...updatePayload } = basePayload;
      await updateTargetPlan(planDlg.editing, { ...updatePayload, targetCycle });
      ElMessage.success('方案更新成功');
    } else {
      await createTargetPlan({ ...basePayload, targetCycle });
      ElMessage.success('方案创建成功');
    }
    planDlg.show = false;
    loadPlans();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '保存失败');
  } finally {
    planDlg.saving = false;
  }
}
async function togglePlanStatus(idOrRow, newStatus) {
  const id = typeof idOrRow === 'string' ? idOrRow : (idOrRow.id || idOrRow.planCode);
  try {
    await updateTargetPlan(id, { status: newStatus });
    ElMessage.success(newStatus === 'ACTIVE' ? '已启用' : '已禁用');
    planDlg.show = false;
    loadPlans();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '操作失败');
  }
}

onMounted(async () => {
  await Promise.all([loadPlans(), loadKpiSchemeOptions(), loadCanApprove(), loadUserMap(), loadMetricMap(), loadApplyIndex()]);

  // 从工作台跳转：?tab=todo&taskId=xxx → 切到待我审批 tab + 自动弹审批窗
  const queryTab = route.query.tab;
  const queryTaskId = route.query.taskId;
  if (queryTab === 'todo' && canApprove.value) {
    activeTab.value = 'todo';
    await loadTodos();
    if (queryTaskId) {
      const row = todos.value.find(t => t.taskId === queryTaskId);
      if (row) openReview(row);
    }
  } else if (queryTab === 'done' && canApprove.value) {
    // 工作台已办「详情」跳转：?tab=done&bizKey=TARGET_ADJUST:{applyId} → 已审批 tab + 弹只读详情
    activeTab.value = 'done';
    await loadDones();
    const applyId = (route.query.bizKey || '').split(':')[1];
    const row = dones.value.find(d => (applyId && d.id === applyId) || (queryTaskId && d.taskId === queryTaskId));
    if (row) openDetail(row);
    else ElMessage.warning('申请不在已审批列表或已变更');
  }
});
</script>

<style lang="scss" scoped>
.lock-hint { font-size: 12px; color: $text-4; margin-top: 4px; line-height: 1.4; }
.page-h h1 .sub {
  font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400;
  em { color: $text-4; font-style: normal; }
}
.filter-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;
  .lab { font-size: 13px; color: $text-2; margin-bottom: 6px; }
  .filter-actions { display: flex; align-items: flex-end; }
}
.table { padding: 14px 16px 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.tab-actions { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.targets-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }
.review-meta {
  background: rgba(64, 158, 255, 0.04);
  border-left: 3px solid #409eff;
  padding: 10px 14px; border-radius: 4px;
  font-size: 13px; line-height: 1.9;
  .lab { color: $text-3; margin-right: 4px; }
}
.review-detail {
  margin-top: 10px; padding: 10px 14px; border-radius: 4px;
  background: rgba(245, 158, 11, 0.06);
  border-left: 3px solid #f59e0b;
  font-size: 13px; line-height: 1.9;
  .lab { color: $text-3; margin-right: 4px; }
  .sep { margin: 0 6px; color: $text-4; }
  .new-val { color: #e65100; }
  .adj-item { margin-bottom: 2px; }
}
.review-history {
  .history-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; color: $text-1; }
  .history-item {
    padding: 8px 12px; margin-bottom: 6px; border-radius: 4px;
    background: rgba(0, 0, 0, 0.02); font-size: 13px; line-height: 1.8;
    .lab { color: $text-3; margin-right: 4px; }
    .sep { margin: 0 6px; color: $text-4; }
  }
}
</style>
