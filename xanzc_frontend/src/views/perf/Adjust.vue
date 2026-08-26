<template>
<main v-bp-overflow-tooltip class="bp-crud perf-adjust-page" aria-labelledby="perf-adjust-page-title" :aria-busy="loading || todoLoading || doneLoading || dlg.saving || dlg.reviewSaving || batchDlg.saving ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="perf-adjust-page-title"><span class="sub">比例之和 = 100% · 单行 ≥ 1% · 同一员工不重复</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="业绩调整操作">
        <el-button :loading="loading || todoLoading || doneLoading" :disabled="loading || todoLoading || doneLoading || dlg.saving || dlg.reviewSaving || batchDlg.saving" @click="reload">刷新</el-button>
        <el-button v-if="activeTab==='mine'" type="primary" :disabled="dlg.saving || dlg.draftSaving" @click="openCreate">新建调整申请</el-button>
      </div>
    </header>

    <el-tabs v-model="activeTab" @tab-change="reload" class="adjust-tabs" aria-label="业绩调整工作区">
      <!-- ============ 我的申请 ============ -->
      <el-tab-pane label="我的申请" name="mine">
        <section class="card-section data-panel filter-bar" aria-label="我的申请筛选">
          <el-form inline size="default" aria-label="我的申请筛选">
            <el-form-item label="关键字">
              <el-input v-model="mineFilters.keyword" placeholder="申请编号 / 客户 ID" clearable
                        style="width:200px" @keyup.enter="onMineFilterChange" />
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="mineFilters.allocDim" clearable placeholder="全部" style="width:140px"
                         @change="onMineFilterChange">
                <el-option v-for="o in allocDimOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="业务类型">
              <el-select v-model="mineFilters.bizKind" clearable placeholder="全部" style="width:160px"
                         @change="onMineFilterChange">
                <el-option v-for="o in bizKindOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="mineFilters.status" clearable placeholder="全部" style="width:140px"
                         @change="onMineFilterChange">
                <el-option value="DRAFT" label="草稿" />
                <el-option value="IN_APPROVAL" label="审批中" />
                <el-option value="APPROVED" label="已通过" />
                <el-option value="REJECTED" label="已驳回" />
                <el-option value="WITHDRAWN" label="已撤回" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请时间">
              <el-date-picker v-model="mineFilters.dateRange" type="daterange" value-format="YYYY-MM-DD"
                              range-separator="~" start-placeholder="开始" end-placeholder="结束"
                              style="width:240px" @change="onMineFilterChange" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onMineFilterChange">查询</el-button>
              <el-button @click="resetMineFilters">重置</el-button>
            </el-form-item>
          </el-form>
        </section>
        <section class="card-section data-panel table" aria-label="我的业绩调整申请" aria-labelledby="perf-adjust-mine-heading"
          aria-describedby="perf-adjust-mine-state" :aria-busy="loading ? 'true' : 'false'">
          <div class="toolbar">
            <div>
              <h2 id="perf-adjust-mine-heading" class="section-title">我的申请</h2>
              <p class="hint">查看申请进度、编辑草稿或撤回尚未完成审批的申请。</p>
            </div>
            <p id="perf-adjust-mine-state" class="table-state" role="status" aria-live="polite">{{ mineState }}</p>
          </div>
          <div v-if="mineError" class="table-error" role="alert"><span>{{ mineError }}</span><el-button link type="primary" @click="reloadMine">重新加载</el-button></div>
          <el-table :data="rows" size="default" :empty-text="mineError ? '加载失败，请重新加载' : '暂无调整申请'" v-loading="loading"
            aria-labelledby="perf-adjust-mine-heading" aria-describedby="perf-adjust-mine-state">
            <el-table-column label="申请编号" width="170">
              <template #default="{row}"><code class="mono">{{ row.applyNo || row.id }}</code></template>
            </el-table-column>
            <el-table-column label="客户" min-width="190" class-name="compact-stack-cell">
              <template #default="{row}">
                <div>{{ row.custName || '-' }}</div>
                <div v-if="row.custId" class="cust-name-sub">{{ row.custId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="维度" width="100">
              <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ allocDimLabel(row.allocDim) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="业务类型" width="160">
              <template #default="{row}">{{ fmtBizKind(row.bizKind) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="160">
              <template #default="{row}">{{ fmtDateTime(row.createdTime || row.time) }}</template>
            </el-table-column>
            <el-table-column label="操作" class-name="operation-cell" width="180" fixed="right">
              <template #default="{row}">
                <BpAdaptiveRowActions>
                  <template #primary><el-button link type="primary" size="small" @click="openSharedView(row)">查看</el-button></template>
                  <template #expanded>
                    <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                    <el-button v-if="canWithdraw(row.status)" link type="danger" size="small" @click="confirmWithdraw(row)">撤回</el-button>
                  </template>
                  <template #compact><el-dropdown v-if="row.status === 'DRAFT' || canWithdraw(row.status)" trigger="click" popper-class="bp-crud-menu"><el-button link size="small" aria-label="更多业绩调整申请操作">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="row.status === 'DRAFT'" @click="openEdit(row)">编辑</el-dropdown-item><el-dropdown-item v-if="canWithdraw(row.status)" divided class="danger-item" @click="confirmWithdraw(row)">撤回</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template>
                </BpAdaptiveRowActions>
              </template>
            </el-table-column>
          </el-table>
          <nav class="pager" aria-label="我的业绩调整申请分页">
            <el-pagination
              v-model:current-page="minePager.pageNo"
              v-model:page-size="minePager.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="minePager.total"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="reloadMine"
              @current-change="reloadMine"
            />
          </nav>
        </section>
      </el-tab-pane>

      <!-- ============ 待我审批 ============ -->
      <el-tab-pane v-if="canApprove" label="待我审批" name="todo">
        <section class="card-section data-panel filter-bar" aria-label="待我审批筛选">
          <el-form inline size="default" aria-label="待我审批筛选">
            <el-form-item label="关键字">
              <el-input v-model="todoFilters.keyword" placeholder="申请编号 / 客户 ID" clearable
                        style="width:200px" @keyup.enter="onTodoFilterChange" />
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="todoFilters.allocDim" clearable placeholder="全部"
                         style="width:140px" @change="onTodoFilterChange">
                <el-option v-for="o in allocDimOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="业务类型">
              <el-select v-model="todoFilters.bizKind" clearable placeholder="全部"
                         style="width:160px" @change="onTodoFilterChange">
                <el-option v-for="o in bizKindOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请时间">
              <el-date-picker v-model="todoFilters.dateRange" type="daterange" value-format="YYYY-MM-DD"
                              range-separator="~" start-placeholder="开始" end-placeholder="结束"
                              style="width:240px" @change="onTodoFilterChange" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onTodoFilterChange">查询</el-button>
              <el-button @click="resetTodoFilters">重置</el-button>
              <el-button type="success" plain :disabled="!todoSelection.length"
                         @click="openBatchReview">批量审批{{ todoSelection.length ? `（${todoSelection.length}）` : '' }}</el-button>
            </el-form-item>
          </el-form>
        </section>
        <section class="card-section data-panel table" aria-label="待我审批列表" aria-labelledby="perf-adjust-todo-heading"
          aria-describedby="perf-adjust-todo-state" :aria-busy="todoLoading ? 'true' : 'false'">
          <div class="toolbar">
            <div>
              <h2 id="perf-adjust-todo-heading" class="section-title">待我审批</h2>
              <p class="hint">先签收候选任务，再提交审批意见；批量审批需选择同一审批环节。</p>
            </div>
            <p id="perf-adjust-todo-state" class="table-state" role="status" aria-live="polite">{{ todoState }}</p>
          </div>
          <div v-if="todoError" class="table-error" role="alert"><span>{{ todoError }}</span><el-button link type="primary" @click="reloadTodo">重新加载</el-button></div>
          <el-table :data="todos" row-key="taskId" size="default" :empty-text="todoError ? '加载失败，请重新加载' : '无符合条件的待审批'" v-loading="todoLoading"
                    aria-labelledby="perf-adjust-todo-heading" aria-describedby="perf-adjust-todo-state"
                    @selection-change="onTodoSelectionChange">
            <el-table-column type="selection" width="48" />
            <el-table-column label="申请编号" width="170">
              <template #default="{row}"><code class="mono">{{ row.applyNo || row.id }}</code></template>
            </el-table-column>
            <el-table-column label="客户" min-width="190" class-name="compact-stack-cell">
              <template #default="{row}">
                <div>{{ row.custName || '-' }}</div>
                <div v-if="row.custId" class="cust-name-sub">{{ row.custId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="发起人" width="160" class-name="compact-stack-cell">
              <template #default="{row}">
                <div>{{ row.startUserName || row.startUserEmpNo || row.startUser || '-' }}</div>
                <div v-if="row.startUserEmpNo" class="sub-id">{{ row.startUserEmpNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="发起机构" width="220" class-name="compact-stack-cell">
              <template #default="{row}">
                <template v-if="row.startOrgName || row.startOrgDeptNo || row.startOrgId">
                  <div>{{ row.startOrgName || '-' }}</div>
                  <div v-if="row.startOrgDeptNo" class="sub-id">{{ row.startOrgDeptNo }}</div>
                </template>
                <template v-else>-</template>
              </template>
            </el-table-column>
            <el-table-column label="发起时间" width="160">
              <template #default="{row}">{{ fmt(row.startTime) }}</template>
            </el-table-column>
            <el-table-column label="任务到达" width="160">
              <template #default="{row}">{{ fmt(row.taskCreateTime) }}</template>
            </el-table-column>
            <el-table-column label="SLA" width="90">
              <template #default="{row}">
                <el-tag :class="slaCls(row.slaStatus)" effect="plain">{{ slaLabel(row.slaStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" class-name="operation-cell" width="100" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openTodoReview(row)">审批</el-button>
              </template>
            </el-table-column>
          </el-table>
          <nav class="pager" aria-label="待我审批分页">
            <el-pagination
              v-model:current-page="todoPager.pageNo"
              v-model:page-size="todoPager.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="todoPager.total"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="reloadTodo"
              @current-change="reloadTodo"
            />
          </nav>
        </section>
      </el-tab-pane>

      <!-- ============ 已审批 ============ -->
      <el-tab-pane v-if="canApprove" label="已审批" name="done">
        <section class="card-section data-panel filter-bar" aria-label="已审批筛选">
          <el-form inline size="default" aria-label="已审批筛选">
            <el-form-item label="关键字">
              <el-input v-model="doneFilters.keyword" placeholder="申请编号 / 客户 ID" clearable
                        style="width:200px" @keyup.enter="onDoneFilterChange" />
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="doneFilters.allocDim" clearable placeholder="全部" style="width:140px"
                         @change="onDoneFilterChange">
                <el-option v-for="o in allocDimOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="业务类型">
              <el-select v-model="doneFilters.bizKind" clearable placeholder="全部" style="width:160px"
                         @change="onDoneFilterChange">
                <el-option v-for="o in bizKindOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请时间">
              <el-date-picker v-model="doneFilters.dateRange" type="daterange" value-format="YYYY-MM-DD"
                              range-separator="~" start-placeholder="开始" end-placeholder="结束"
                              style="width:240px" @change="onDoneFilterChange" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onDoneFilterChange">查询</el-button>
              <el-button @click="resetDoneFilters">重置</el-button>
            </el-form-item>
          </el-form>
        </section>
        <section class="card-section data-panel table" aria-label="已审批列表" aria-labelledby="perf-adjust-done-heading"
          aria-describedby="perf-adjust-done-state" :aria-busy="doneLoading ? 'true' : 'false'">
          <div class="toolbar">
            <div>
              <h2 id="perf-adjust-done-heading" class="section-title">已审批</h2>
              <p class="hint">仅展示已完成审批的申请，可打开查看申请资料和审批流记录。</p>
            </div>
            <p id="perf-adjust-done-state" class="table-state" role="status" aria-live="polite">{{ doneState }}</p>
          </div>
          <div v-if="doneError" class="table-error" role="alert"><span>{{ doneError }}</span><el-button link type="primary" @click="reloadDone">重新加载</el-button></div>
          <el-table :data="dones" size="default" :empty-text="doneError ? '加载失败，请重新加载' : '无符合条件的已审批'" v-loading="doneLoading"
            aria-labelledby="perf-adjust-done-heading" aria-describedby="perf-adjust-done-state">
            <el-table-column label="申请编号" width="170">
              <template #default="{row}"><code class="mono">{{ row.applyNo || row.id }}</code></template>
            </el-table-column>
            <el-table-column label="客户" min-width="190" class-name="compact-stack-cell">
              <template #default="{row}">
                <div>{{ row.custName || '-' }}</div>
                <div v-if="row.custId" class="cust-name-sub">{{ row.custId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="维度" width="100">
              <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ allocDimLabel(row.allocDim) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="业务类型" width="160">
              <template #default="{row}">{{ fmtBizKind(row.bizKind) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请人" width="160" class-name="compact-stack-cell">
              <template #default="{row}">
                <div>{{ row.startUserName || row.createdByName || row.startUserEmpNo || row.createdBy || '-' }}</div>
                <div v-if="row.startUserEmpNo" class="sub-id">{{ row.startUserEmpNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="160">
              <template #default="{row}">{{ fmt(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" class-name="operation-cell" width="120" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openSharedView(row)">查看申请</el-button>
              </template>
            </el-table-column>
          </el-table>
          <nav class="pager" aria-label="已审批分页">
            <el-pagination
              v-model:current-page="donePager.pageNo"
              v-model:page-size="donePager.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="donePager.total"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="reloadDone"
              @current-change="reloadDone"
            />
          </nav>
        </section>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建/查看 弹框 -->
    <el-dialog v-model="dlg.show" :title="dlgTitle" width="900px" :close-on-click-modal="false" @closed="onDlgClosed">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlgRules" label-position="top" size="default">
        <!-- 申请信息条（仅查看模式显示）-->
        <el-descriptions
          v-if="dlg.readOnly"
          class="apply-info-bar"
          :column="3"
          size="small"
          border
        >
          <el-descriptions-item label="申请单号">
            <code class="mono">{{ dlg.applyNo || '-' }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="申请人">
            <span>{{ dlg.createdByName || dlg.createdByUsername || dlg.createdBy || '-' }}</span>
            <span v-if="dlg.createdByUsername" class="sub-id">（{{ dlg.createdByUsername }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="申请机构">
            {{ dlg.createdByOrgName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="申请时间" :span="3">
            {{ fmt(dlg.createdTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户类型" prop="custType" required>
              <el-select v-model="dlg.form.custType" :disabled="dlg.readOnly" style="width:100%" @change="onCustTypeChange">
                <el-option label="对公客户" value="CORP" />
                <el-option label="零售客户" value="RETAIL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户编号" prop="custId" required>
              <el-input v-model="dlg.form.custId" :disabled="dlg.readOnly" placeholder="如 C20260001" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户名称" prop="custName" required>
              <el-input v-model="dlg.form.custName"
                        :disabled="dlg.readOnly || custStat.found"
                        :placeholder="custStat.found ? '' : '请输入客户名称'" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分配维度" prop="allocDim" required>
              <el-select v-model="dlg.form.allocDim" :disabled="dlg.readOnly" style="width:100%"
                         @change="onAllocDimChange">
                <el-option v-for="o in allocDimOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务类型" prop="bizKind" required>
              <el-select v-model="dlg.form.bizKind"
                         :disabled="dlg.readOnly || dlg.form.allocDim === 'ACCOUNT'"
                         multiple style="width:100%"
                         :placeholder="dlg.form.allocDim === 'ACCOUNT' ? '按账号分配固定为存款' : '请选择业务类型（存款/贷款，可多选）'">
                <el-option v-for="o in bizKindFormOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="账号" prop="accountNo" :required="dlg.form.allocDim === 'ACCOUNT'">
              <el-input v-model="dlg.form.accountNo"
                        :disabled="dlg.readOnly || dlg.form.allocDim !== 'ACCOUNT'"
                        :placeholder="dlg.form.allocDim === 'ACCOUNT' ? '请输入账号（必填）' : '仅按账号分配时可输入'" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 客户主档(CUST_MASTER)查不到该客户编号时告警（查到则不显示）-->
        <el-alert v-if="dlg.form.custId && custStat.queried && !custStat.found"
                  type="warning" :closable="false" style="margin-bottom:12px"
                  title="未查询到该客户，请填写客户名称" />

        <!-- 余额概览（label 方式展示）：按业务类型动态显隐——选含存款→存款余额(MC_001-004)，选含贷款→贷款余额(MC_005-008) -->
        <!-- 存款余额：仅业务类型选含存款时显示 -->
        <template v-if="showDepositBal">
          <div class="bal-sub">存款余额</div>
          <el-row :gutter="16" style="margin-bottom:4px">
            <el-col :span="6"><el-form-item label="当前余额"><span class="bal-val">{{ fmtAmt(custIdx.MC_001) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="较上日余额"><span class="bal-val">{{ fmtAmt(custIdx.MC_002) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="年均余额"><span class="bal-val">{{ fmtAmt(custIdx.MC_003) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="较上年均余额"><span class="bal-val">{{ fmtAmt(custIdx.MC_004) }}</span></el-form-item></el-col>
          </el-row>
        </template>
        <!-- 贷款余额：仅业务类型选含贷款时显示 -->
        <template v-if="showLoanBal">
          <div class="bal-sub">贷款余额</div>
          <el-row :gutter="16" style="margin-bottom:4px">
            <el-col :span="6"><el-form-item label="当前余额"><span class="bal-val">{{ fmtAmt(custIdxLoan.MC_005) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="较上日余额"><span class="bal-val">{{ fmtAmt(custIdxLoan.MC_006) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="年均余额"><span class="bal-val">{{ fmtAmt(custIdxLoan.MC_007) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="较上年均余额"><span class="bal-val">{{ fmtAmt(custIdxLoan.MC_008) }}</span></el-form-item></el-col>
          </el-row>
        </template>

        <!-- 原业绩分配：RULE/ACCOUNT 显示；NEW 不依赖原分配关系；自动查到历史审批通过分配→只读展示；查不到→手工录入（除账号外必填，至少 1 条）-->
        <div v-if="showOriginalAllocation" class="preview-section" v-loading="preview.loading">
          <div class="card-h">
            <div class="title">原业绩分配</div>
            <el-button v-if="!dlg.readOnly && !hasOriginalOwners" size="small" type="primary" plain @click="addOriginalRow">添加原业绩分配</el-button>
          </div>
          <!-- 历史审批通过分配（自动查到）：只读 -->
          <el-table v-if="hasOriginalOwners" :data="preview.data.allocList || []" size="small" border style="margin-bottom:12px" empty-text="暂无审批通过的分配记录">
            <el-table-column prop="acctNo" label="账号" min-width="150" show-overflow-tooltip>
              <template #default="{row}">{{ row.acctNo || '-' }}</template>
            </el-table-column>
            <el-table-column label="员工名称" min-width="150" show-overflow-tooltip>
              <template #default="{row}">{{ row.username || '-' }}{{ row.empChnName ? '（' + row.empChnName + '）' : '' }}</template>
            </el-table-column>
            <el-table-column label="所属机构" min-width="180" show-overflow-tooltip>
              <template #default="{row}">{{ originalOrgDisplay(row) }}</template>
            </el-table-column>
            <el-table-column prop="ratio" label="分配比例" width="100">
              <template #default="{row}">{{ row.ratio != null && row.ratio !== '' ? row.ratio + '%' : '-' }}</template>
            </el-table-column>
          </el-table>
          <!-- 查不到 → 手工录入（新建/编辑可增删）；查看/审批时只读展示已保存原业绩分配 -->
          <el-table v-else :data="dlg.form.originalItems" size="small" border style="margin-bottom:12px" empty-text="未查到原业绩分配，请手工录入至少 1 条（除账号外必填）">
            <el-table-column label="账号" min-width="140">
              <template #default="{row}">
                <el-input v-if="!dlg.readOnly" v-model="row.acctNo" size="small" placeholder="选填" clearable />
                <span v-else>{{ row.acctNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="员工名称" min-width="200">
              <template #default="{row}">
                <el-autocomplete v-if="!dlg.readOnly" v-model="row.empLabel" size="small" style="width:100%"
                  value-key="label" :fetch-suggestions="queryEmpSuggest" :trigger-on-focus="false" clearable
                  placeholder="输入工号/姓名搜索" @select="(item) => onOrigEmpSelect(row, item)" @input="(v) => onOrigEmpInput(row, v)" />
                <span v-else>{{ row.username || row.empId || '-' }}{{ row.empChnName ? '（' + row.empChnName + '）' : '' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="所属机构" min-width="220">
              <template #default="{row}">
                <el-autocomplete v-if="!dlg.readOnly" v-model="row.orgLabel" size="small" style="width:100%"
                  value-key="label" :fetch-suggestions="queryOrgSuggest" :trigger-on-focus="false" clearable
                  placeholder="输入机构号/名称搜索" @select="(item) => onOrigOrgSelect(row, item)" @input="(v) => onOrigOrgInput(row, v)" />
                <span v-else>{{ originalOrgDisplay(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分配比例" width="120">
              <template #default="{row}">
                <el-input-number v-if="!dlg.readOnly" v-model="row.ratio" :min="0" :max="100" :precision="2" size="small" controls-position="right" style="width:100%" />
                <span v-else>{{ row.ratio != null && row.ratio !== '' ? row.ratio + '%' : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!dlg.readOnly" label="操作" class-name="operation-cell" width="70" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="removeOriginalRow($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="card-h">
          <div class="title">分配明细</div>
          <span class="weight-sum" :class="{ ok: totalPct === 100 }">
            合计：{{ totalPct }}% {{ totalPct === 100 ? '✓' : '' }}
          </span>
        </div>
        <el-table :data="dlg.form.items" size="default" border>
          <el-table-column label="员工号" min-width="220">
            <template #default="{row}">
              <el-autocomplete
                v-model="row.empLabel"
                :disabled="dlg.readOnly"
                size="small"
                style="width:100%"
                value-key="label"
                :fetch-suggestions="queryEmpSuggest"
                :trigger-on-focus="false"
                clearable
                placeholder="输入工号/登录名/中文名搜索"
                @select="(item) => onEmpSelect(row, item)"
                @change="(val) => onEmpInput(row, val)">
                <template #default="{ item }">
                  <span>{{ item.username }}</span>
                  <span v-if="item.empChnName" style="color:#909399;margin-left:8px">{{ item.empChnName }}</span>
                </template>
              </el-autocomplete>
            </template>
          </el-table-column>
          <el-table-column label="承担比例 %" width="140">
            <template #default="{row}">
              <el-input-number v-model="row.pct" :disabled="dlg.readOnly" :min="0" :max="100" :precision="0" :controls="false" size="small" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="240">
            <template #default="{row}">
              <el-input v-model="row.remark" :disabled="dlg.readOnly" size="small" />
            </template>
          </el-table-column>
          <el-table-column v-if="!dlg.readOnly" label="操作" class-name="operation-cell" width="80" align="center" fixed="right">
            <template #default="{$index}">
              <el-button link type="danger" size="small" @click="dlg.form.items.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
          <el-button v-if="!dlg.readOnly" plain @click="addItemRow" style="margin-top:10px">添加分配人</el-button>

        <el-form-item label="申请原因" prop="reason" required style="margin-top:14px">
          <el-input v-model="dlg.form.reason" :disabled="dlg.readOnly" type="textarea" :rows="2" maxlength="500" show-word-limit
            placeholder="请说明本次调整原因（必填，将记入审批日志）" />
        </el-form-item>
      </el-form>

      <!-- 审批流记录（仅查看模式展示）-->
      <template v-if="dlg.readOnly">
        <div class="card-h">
          <div class="title">审批流记录</div>
          <span class="sub-tip">按时间倒序 · 最新在上</span>
        </div>
        <div v-loading="dlg.approvalLoading" class="approval-wrap">
          <el-empty v-if="!dlg.approvalLoading && (!dlg.approvalLogs || dlg.approvalLogs.length === 0)"
            description="暂无审批记录" :image-size="60" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="(log, idx) in dlg.approvalLogs"
              :key="idx"
              :timestamp="fmt(log.operateTime)"
              placement="top"
              :type="actionTimelineType(log.action)"
              :hollow="idx !== 0">
              <div class="approval-line">
                <el-tag :class="actionCls(log.action)" effect="plain" size="small">
                  {{ actionLabel(log.action) }}
                </el-tag>
                <span class="node">{{ log.nodeName || log.nodeKey || '-' }}</span>
              </div>
              <div class="approval-meta">
                <span class="meta-key">审核人：</span>
                <span>{{ log.operatorName || log.operatorEmpNo || log.operator || '-' }}</span>
                <span v-if="log.operatorEmpNo" class="sub-id">（{{ log.operatorEmpNo }}）</span>
                <span class="meta-sep">·</span>
                <span class="meta-key">机构：</span>
                <span>{{ log.operatorOrgName || '-' }}</span>
              </div>
              <div v-if="log.action !== 'SUBMIT'" class="approval-opinion">意见：{{ log.opinion || '（未填写）' }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>

      <!-- 审批模式：评审意见 + 通过/驳回 -->
      <template v-if="dlg.reviewMode">
        <el-form label-position="top" size="default" style="margin-top:12px">
          <el-form-item label="评审意见" required>
            <el-input v-model="dlg.reviewOpinion" type="textarea" :rows="3" maxlength="500" show-word-limit
              placeholder="请填写评审意见（必填）" />
          </el-form-item>
          <el-form-item v-if="dlg.reviewBranches.length >= 2" label="下一步走向">
            <el-radio-group v-model="dlg.reviewBranchIdx">
              <el-radio v-for="(b, i) in dlg.reviewBranches" :key="i" :value="i"
                        :disabled="isOwnerBranch(b) && !canRouteOwner">
                {{ b.outputName }}{{ isOwnerBranch(b) && !canRouteOwner ? '（无原业绩分配，不可选）' : '' }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="dlg.show = false">{{ dlg.reviewMode ? '关闭' : dlg.readOnly ? '关闭' : '取消' }}</el-button>
        <template v-if="dlg.reviewMode">
          <el-button type="danger" :loading="dlg.reviewSaving" @click="onDlgReviewAction('REJECT')">驳回</el-button>
          <el-button type="primary" :loading="dlg.reviewSaving" @click="onDlgReviewAction('APPROVE')">通过</el-button>
        </template>
        <el-button v-if="!dlg.readOnly && !dlg.reviewMode" :loading="dlg.draftSaving"
                   @click="onSaveDraft">保存为草稿</el-button>
        <el-button v-if="!dlg.readOnly && !dlg.reviewMode" type="primary" :loading="dlg.saving"
                   @click="onSubmit">提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 经办审批专用对话框（biz_dept_review / finance_review 节点，含路由选择） -->
    <el-dialog v-model="approveDlg.show" :title="approveDlgTitle" width="520px" :close-on-click-modal="false">
      <el-form label-position="top" size="default">
        <el-form-item label="审批意见">
          <el-input v-model="approveDlg.opinion" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="请填写审批意见（可空）" />
        </el-form-item>
        <el-form-item label="下一步走向">
          <el-radio-group v-model="approveDlg.branchIdx">
            <el-radio v-for="(b, i) in approveDlg.branches" :key="i" :value="i"
                      :disabled="isOwnerBranch(b) && !approveDlg.hasOwners">
              {{ b.outputName }}{{ isOwnerBranch(b) && !approveDlg.hasOwners ? '（无原业绩分配，不可选）' : '' }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="approveDlg.saving" @click="onApproveSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 批量审批对话框（待我审批 tab 勾选后批量通过/驳回） -->
    <el-dialog v-model="batchDlg.show" :title="`批量审批（共 ${batchDlg.rows.length} 条）`" width="540px" :close-on-click-modal="false">
      <el-form label-position="top" size="default">
        <el-form-item label="审批意见" required>
          <el-input v-model="batchDlg.opinion" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="请填写审批意见（必填，所有勾选申请共用）" />
        </el-form-item>
        <el-form-item v-if="batchDlg.branches.length >= 2" label="下一步走向">
          <el-radio-group v-model="batchDlg.branchIdx">
            <el-radio v-for="(b, i) in batchDlg.branches" :key="i" :value="i">{{ b.outputName }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDlg.show = false">取消</el-button>
        <el-button type="danger" :loading="batchDlg.saving" @click="onBatchReviewAction('REJECT')">驳回</el-button>
        <el-button type="primary" :loading="batchDlg.saving" @click="onBatchReviewAction('APPROVE')">通过</el-button>
      </template>
    </el-dialog>

    <!-- 查看调整申请（共享只读组件，与业绩分配查询页共用） -->
    <AllocAdjustViewDialog v-model="viewDialog.show" :apply-id="viewDialog.applyId" />
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import AllocAdjustViewDialog from '@/components/AllocAdjustViewDialog.vue';
import BpAdaptiveRowActions from '@/components/BpAdaptiveRowActions.vue';
import { useRoute } from 'vue-router';
import { fmtDateTime } from '@/utils/datetime';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  submitAdjust, saveDraftAdjust, submitDraftAdjust, withdrawAdjust, getAdjustDetail,
  getAdjustApprovalHistory, listMyAdjustTodos, listMyAdjustApplies, listMyAdjustDones,
  getAllocPreview, getCustMasterName, getCustIndexValues, suggestEmployees, suggestOrgs
} from '@/api/perf';
import { approveTask, rejectTask, claimTask, getTaskDetail } from '@/api/workflow';
import { getMyPermissions } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import { listDictItems } from '@/api/system';

const route = useRoute();

const STATUS_LABEL = {
  DRAFT: '草稿', IN_APPROVAL: '审批中', APPROVED: '已通过',
  REJECTED: '已驳回', WITHDRAWN: '已撤回'
};
const statusLabel = (s) => STATUS_LABEL[s] || s || '-';
const statusCls = (s) => ({
  APPROVED: 'tag-success', IN_APPROVAL: 'tag-warning',
  DRAFT: 'tag-info', REJECTED: 'tag-danger', WITHDRAWN: 'tag-info'
}[s] || 'tag-info');
const canWithdraw = (s) => s === 'IN_APPROVAL' || s === 'DRAFT';
const inferCustType = (custType, bizKind) => {
  if (custType) return custType;
  const bk = typeof bizKind === 'string' ? bizKind : (Array.isArray(bizKind) ? bizKind[0] || '' : '');
  if (bk.startsWith('CORP')) return 'CORP';
  if (bk.startsWith('RETAIL') || bk.startsWith('PER') || bk.startsWith('FEE')) return 'RETAIL';
  return '';
};
const doneRowStatus = (row) => {
  // 撤回优先：撤回也会把流程置为 CANCELLED，需先按申请状态区分「撤回」与「驳回」
  if (row.status === 'WITHDRAWN') return 'WITHDRAWN';
  if (row.approvalResult === 'APPROVE' || row.processStatus === 'COMPLETED') return 'APPROVED';
  if (row.approvalResult === 'REJECT' || row.processStatus === 'CANCELLED') return 'REJECTED';
  if (row.status) return row.status;
  return 'IN_APPROVAL';
};

const SLA_LABEL = { GREEN: '正常', YELLOW: '预警', RED: '超时' };
const slaLabel = (s) => SLA_LABEL[s] || s || '-';
const slaCls = (s) => ({ GREEN: 'tag-success', YELLOW: 'tag-warning', RED: 'tag-danger' }[s] || 'tag-info');

// 流程实例状态映射（来自 biz_process_map.process_status）
// RUNNING=审批中（绿）/ COMPLETED=完结(业务通过)（蓝）/ CANCELLED=驳回(业务拒绝)（橙）
const PROC_STATUS_LABEL = { RUNNING: '审批中', COMPLETED: '完结', CANCELLED: '驳回' };
const processStatusLabel = (s) => PROC_STATUS_LABEL[s] || s || '-';
const processStatusCls = (s) => ({ RUNNING: 'tag-success', COMPLETED: 'tag-info', CANCELLED: 'tag-warning' }[s] || 'tag-info');

const ACTION_LABEL = {
  SUBMIT: '提交', APPROVE: '通过', REJECT: '驳回', CLAIM: '签收', TRANSFER: '转办'
};
const actionLabel = (a) => ACTION_LABEL[a] || a || '-';
const actionCls = (a) => ({
  APPROVE: 'tag-success', REJECT: 'tag-danger',
  SUBMIT: 'tag-info', CLAIM: 'tag-warning', TRANSFER: 'tag-warning'
}[a] || 'tag-info');
const actionTimelineType = (a) => ({
  APPROVE: 'success', REJECT: 'danger',
  SUBMIT: 'primary', CLAIM: 'warning', TRANSFER: 'warning'
}[a] || 'info');

const fmt = (s) => {
  if (!s) return '-';
  // 后端可能给 ISO 字符串或本地字符串，统一截到分钟
  const str = String(s).replace('T', ' ');
  return str.length >= 16 ? str.substring(0, 16) : str;
};

const userStore = useUserStore();

// ============ tab 状态 ============
const activeTab = ref('mine');

// 是否有"工作流任务/审批"相关 API 权限。没有则隐藏"待我审批"+"已审批"两个 tab。
// 判断口径：用户 resourceUrls 含任一 /api/workflow/tasks(*) URL，即视为有审批资格。
// SYS_ADMIN（isSystemAdmin=true）一律放行。
const canApprove = ref(false);

async function loadCanApprove() {
  try {
    const p = await getMyPermissions();
    if (p?.isSystemAdmin) { canApprove.value = true; return; }
    const urls = p?.resourceUrls || p?.resources || [];
    canApprove.value = Array.isArray(urls) && urls.some(
      u => typeof u === 'string' && u.startsWith('/api/workflow/tasks')
    );
  } catch {
    canApprove.value = false;
  }
}

// ============ 我的申请 ============
const rows = ref([]);
const loading = ref(false);
const mineError = ref('');
const mineFilters = reactive({
  keyword: '',
  allocDim: '',
  bizKind: '',
  status: '',
  dateRange: null,
});
const minePager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
});
async function reloadMine() {
  loading.value = true;
  mineError.value = '';
  try {
    const params = {
      keyword: mineFilters.keyword || undefined,
      allocDim: mineFilters.allocDim || undefined,
      bizKind: mineFilters.bizKind || undefined,
      status: mineFilters.status || undefined,
      dateFrom: mineFilters.dateRange?.[0] || undefined,
      dateTo: mineFilters.dateRange?.[1] || undefined,
      pageNo: minePager.pageNo,
      pageSize: minePager.pageSize,
    };
    const r = await listMyAdjustApplies(params);
    rows.value = r.records || [];
    minePager.total = r.total || 0;
  } catch (err) {
    console.error('[reloadMine] failed', err);
    rows.value = [];
    minePager.total = 0;
    mineError.value = `我的申请加载失败：${err?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}
function resetMineFilters() {
  mineFilters.keyword = '';
  mineFilters.allocDim = '';
  mineFilters.bizKind = '';
  mineFilters.status = '';
  mineFilters.dateRange = null;
  minePager.pageNo = 1;
  reloadMine();
}
function onMineFilterChange() {
  minePager.pageNo = 1;
  reloadMine();
}

// ============ 待我审批 ============
const todos = ref([]);
const todoLoading = ref(false);
const todoError = ref('');
const todoFilters = reactive({
  keyword: '',
  allocDim: '',
  bizKind: '',
  dateRange: null,  // [startDate, endDate] from el-date-picker daterange
});
const todoPager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
});
async function reloadTodo() {
  todoLoading.value = true;
  todoError.value = '';
  try {
    const params = {
      keyword: todoFilters.keyword || undefined,
      allocDim: todoFilters.allocDim || undefined,
      bizKind: todoFilters.bizKind || undefined,
      dateFrom: todoFilters.dateRange?.[0] || undefined,
      dateTo: todoFilters.dateRange?.[1] || undefined,
      pageNo: todoPager.pageNo,
      pageSize: todoPager.pageSize,
    };
    const r = await listMyAdjustTodos(params);
    // r 是 PageResult 对象 { records, total, pageNo, pageSize }
    todos.value = r.records || [];
    todoPager.total = r.total || 0;
  } catch (err) {
    console.error('[reloadTodo] failed', err);
    todos.value = [];
    todoPager.total = 0;
    todoError.value = `待我审批加载失败：${err?.message || '请稍后重试'}`;
  } finally {
    todoLoading.value = false;
  }
}
function resetTodoFilters() {
  todoFilters.keyword = '';
  todoFilters.allocDim = '';
  todoFilters.bizKind = '';
  todoFilters.dateRange = null;
  todoPager.pageNo = 1;
  reloadTodo();
}
function onTodoFilterChange() {
  todoPager.pageNo = 1;
  reloadTodo();
}

// ============ 已审批（已办） ============
const dones = ref([]);
const doneLoading = ref(false);
const doneError = ref('');
const doneFilters = reactive({
  keyword: '',
  allocDim: '',
  bizKind: '',
  dateRange: null,
});
const donePager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
});
async function reloadDone() {
  doneLoading.value = true;
  doneError.value = '';
  try {
    const params = {
      keyword: doneFilters.keyword || undefined,
      allocDim: doneFilters.allocDim || undefined,
      bizKind: doneFilters.bizKind || undefined,
      dateFrom: doneFilters.dateRange?.[0] || undefined,
      dateTo: doneFilters.dateRange?.[1] || undefined,
      pageNo: donePager.pageNo,
      pageSize: donePager.pageSize,
    };
    const r = await listMyAdjustDones(params);
    dones.value = r.records || [];
    donePager.total = r.total || 0;
  } catch (err) {
    console.error('[reloadDone] failed', err);
    dones.value = [];
    donePager.total = 0;
    doneError.value = `已审批加载失败：${err?.message || '请稍后重试'}`;
  } finally {
    doneLoading.value = false;
  }
}
function resetDoneFilters() {
  doneFilters.keyword = '';
  doneFilters.allocDim = '';
  doneFilters.bizKind = '';
  doneFilters.dateRange = null;
  donePager.pageNo = 1;
  reloadDone();
}
function onDoneFilterChange() {
  donePager.pageNo = 1;
  reloadDone();
}

// ============ 统一刷新（按 tab 路由） ============
function reload() {
  if (activeTab.value === 'todo') reloadTodo();
  else if (activeTab.value === 'done') reloadDone();
  else reloadMine();
}

const mineState = computed(() => loading.value
  ? '我的申请加载中'
  : mineError.value
    ? '我的申请加载失败'
    : rows.value.length
      ? `共 ${minePager.total} 条申请`
      : '暂无我的申请');
const todoState = computed(() => todoLoading.value
  ? '待我审批加载中'
  : todoError.value
    ? '待我审批加载失败'
    : todos.value.length
      ? `共 ${todoPager.total} 条待审批任务`
      : '暂无待审批任务');
const doneState = computed(() => doneLoading.value
  ? '已审批加载中'
  : doneError.value
    ? '已审批加载失败'
    : dones.value.length
      ? `共 ${donePager.total} 条已审批记录`
      : '暂无已审批记录');

async function openTodoDetail(row) {
  // businessKey 形如 ALLOC_ADJUST:{applyId}
  const applyId = (row.businessKey || '').split(':')[1] || row.bizId;
  if (!applyId) return ElMessage.warning('无法识别申请 ID');
  try {
    const d = await getAdjustDetail(applyId);
    // 复用查看弹框
    dlg.readOnly = true;
    dlg.viewingId = applyId;
    dlg.approvalLogs = [];
    Object.assign(dlg.form, {
      custType: inferCustType(d.custType, d.bizKind), custId: d.custId || '',
      custName: d.custName || '',
      allocDim: d.allocDim,
      bizKind: d.bizKind ? (typeof d.bizKind === 'string' ? d.bizKind.split(',') : d.bizKind) : [],
      accountNo: d.accountNo,
      ownerOrgId: d.ownerOrgId, reason: d.reason || d.remark,
      items: (d.items || []).filter(it => (it.itemKind || 'NEW') === 'NEW').map(it => ({
        empId: it.empId || '', username: it.username || '', empChnName: it.empChnName || '',
        pct: it.pct ?? it.ratio, remark: it.remark, empLabel: empLabelOf(it),
        _selectedEmpLabel: selectedEmpLabelOf(it)
      })),
      originalItems: originalItemsFromDetail(d)
    });
    // 余额概览读快照（存款 MC_001..004 列复用：currBal=当前 / mAvgBal=较上日 / qAvgBal=年均 / yAvgBal=较上年均）
    custIdx.MC_001 = d.currBal ?? null;
    custIdx.MC_002 = d.mAvgBal ?? null;
    custIdx.MC_003 = d.qAvgBal ?? null;
    custIdx.MC_004 = d.yAvgBal ?? null;
    // 贷款余额读快照（MC_005..008，与存款同步落库，查看/审批直接读，不再实时取数）
    custIdxLoan.MC_005 = d.loanCurrBal ?? null;
    custIdxLoan.MC_006 = d.loanMAvgBal ?? null;
    custIdxLoan.MC_007 = d.loanQAvgBal ?? null;
    custIdxLoan.MC_008 = d.loanYAvgBal ?? null;
    // 申请信息条所需的 dlg 顶层字段（之前漏赋值导致 todo/done tab 查看时申请单号/申请人/机构/时间 全空）
    dlg.applyNo = d.applyNo || '';
    dlg.createdBy = d.createdBy || '';
    dlg.createdByName = d.createdByName || '';
    dlg.createdByUsername = d.createdByUsername || '';
    dlg.createdByOrgName = d.createdByOrgName || '';
    dlg.createdTime = d.createdTime || null;
    dlg.show = true;
    loadApprovalHistory(applyId);
    // 查看/审批页面：Statis_Dt = 申请日期 - 1
    if (d.createdTime) {
      const applyDate = new Date(d.createdTime);
      applyDate.setDate(applyDate.getDate() - 1);
      loadPreview(applyDate.toISOString().slice(0, 10));
    }
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '查看失败');
  }
}

// 候选任务（assignee=null, claimable=true）走 approve/reject 前必须先 claim 成为受理人
async function ensureClaimed(row) {
  if (row.claimable) {
    await claimTask(row.taskId);
  }
}

// 业务部门经办（biz_dept_review）节点专用审批对话框 state
// 该节点表单含 needsOriginalOwnerApprove CHECKBOX，由经办勾选决定是否走原业绩所属人审批分支
const approveDlg = reactive({
  show: false, saving: false, row: null,
  opinion: '同意', nodeKey: '', hasOwners: false,
  // 设计器流程「下一步走向」分支选项（来自后端 outgoingBranches：当前节点命名出边），
  // branchIdx 为所选分支下标；分支标签/路由变量全部由设计器流转连线动态驱动，不再硬编码
  branches: [], branchIdx: 0
});
const approveDlgTitle = computed(
  () => `审批通过：${approveDlg.row?.title || approveDlg.row?.businessKey || ''}`
);

/**
 * 拉取任务详情中的「下一步走向」分支选项（设计器当前节点的命名出边）。
 * 失败/无返回时回空数组，调用方据 length 决定是否展示分支单选。
 */
async function loadOutgoingBranches(taskId) {
  if (!taskId) return [];
  try {
    const detail = await getTaskDetail(taskId);
    return Array.isArray(detail?.outgoingBranches) ? detail.outgoingBranches : [];
  } catch {
    return [];
  }
}

/** 该分支是否「交原业绩所属人审批」（routeVariables 任一值为 OWNER），用于无原业绩分配时禁用。 */
function isOwnerBranch(b) {
  return b && b.routeVariables && Object.values(b.routeVariables).some(v => v === 'OWNER');
}

/** 选定首个可选分支下标（跳过因无原业绩分配而禁用的 OWNER 分支）。 */
function firstSelectableBranch(branches, hasOwners) {
  const idx = branches.findIndex(b => !(isOwnerBranch(b) && !hasOwners));
  return idx >= 0 ? idx : 0;
}

async function openApprove(row) {
  // 设计器流程：先拉当前节点的命名出边作为「下一步走向」选项
  const branches = await loadOutgoingBranches(row.taskId);
  if (branches.length >= 2) {
    approveDlg.row = row;
    approveDlg.opinion = '同意';
    approveDlg.nodeKey = row.nodeKey;
    approveDlg.saving = false;
    // 原业绩分配为空时禁用「交原业绩所属人审批」分支：拉取该客户当前维度的原业绩分配判断有无所属人
    approveDlg.hasOwners = false;
    if (row.allocDim !== 'NEW') {
      try {
        const p = await getAllocPreview({ custNo: row.custId, allocDim: row.allocDim });
        approveDlg.hasOwners = !!(p && p.allocList && p.allocList.length);
      } catch { approveDlg.hasOwners = false; }
    }
    approveDlg.branches = branches;
    approveDlg.branchIdx = firstSelectableBranch(branches, approveDlg.hasOwners);
    approveDlg.show = true;
    return;
  }
  // 单分支/无分支节点：沿用简单意见输入（无走向可选）
  let opinion;
  try {
    const r = await ElMessageBox.prompt('请填写审批意见（可空）', `审批通过：${row.title || row.businessKey}`, {
      type: 'success', inputValue: '同意'
    });
    opinion = r.value;
  } catch { return; }
  try {
    await ensureClaimed(row);
    await approveTask(row.taskId, opinion);
    ElMessage.success('已通过');
    reloadTodo();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '审批失败');
  }
}

async function onApproveSubmit() {
  if (!approveDlg.row) return;
  const chosen = approveDlg.branches[approveDlg.branchIdx];
  if (!chosen) return ElMessage.warning('请选择下一步走向');
  approveDlg.saving = true;
  try {
    await ensureClaimed(approveDlg.row);
    // formData 直接取所选分支的 routeVariables（如 {corpRouteTo:'LEADER'}），驱动设计器排他网关路由
    await approveTask(approveDlg.row.taskId, approveDlg.opinion, { ...(chosen.routeVariables || {}) });
    ElMessage.success('已通过');
    approveDlg.show = false;
    reloadTodo();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '审批失败');
  } finally {
    approveDlg.saving = false;
  }
}

async function openReject(row) {
  let opinion;
  try {
    const r = await ElMessageBox.prompt('请填写驳回原因（必填）', `驳回：${row.title || row.businessKey}`, {
      type: 'warning', inputPattern: /\S+/, inputErrorMessage: '驳回原因必填'
    });
    opinion = r.value;
  } catch { return; }
  try {
    await ensureClaimed(row);
    await rejectTask(row.taskId, opinion);
    ElMessage.success('已驳回');
    reloadTodo();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '驳回失败');
  }
}

// ============ 待我审批 - 审批弹窗（复用查看弹窗 dlg + reviewMode） ============
async function openTodoReview(row) {
  await openTodoDetail(row);
  dlg.reviewMode = true;
  dlg.reviewRow = row;
  dlg.reviewOpinion = '';
  dlg.reviewSaving = false;
  // 设计器流程：拉当前节点命名出边作为「下一步走向」选项（canRouteOwner 决定 OWNER 分支可选性）
  dlg.reviewBranches = await loadOutgoingBranches(row.taskId);
  dlg.reviewBranchIdx = firstSelectableBranch(dlg.reviewBranches, canRouteOwner.value);
}
async function onDlgReviewAction(action) {
  if (!dlg.reviewOpinion || !dlg.reviewOpinion.trim()) {
    return ElMessage.warning('请填写评审意见');
  }
  if (!dlg.reviewRow?.taskId) {
    return ElMessage.error('任务 ID 缺失');
  }
  dlg.reviewSaving = true;
  try {
    await ensureClaimed(dlg.reviewRow);
    if (action === 'APPROVE') {
      // 设计器流程：formData 取所选「下一步走向」分支的 routeVariables；无分支节点则不带
      const chosen = dlg.reviewBranches[dlg.reviewBranchIdx];
      const formData = chosen ? { ...(chosen.routeVariables || {}) } : undefined;
      await approveTask(dlg.reviewRow.taskId, dlg.reviewOpinion, formData);
      ElMessage.success('已通过');
    } else {
      await rejectTask(dlg.reviewRow.taskId, dlg.reviewOpinion);
      ElMessage.success('已驳回');
    }
    dlg.show = false;
    reloadTodo();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '审批失败');
  } finally {
    dlg.reviewSaving = false;
  }
}

// ============ 待我审批 - 批量审批 ============
const todoSelection = ref([]);
function onTodoSelectionChange(rows) { todoSelection.value = rows || []; }

// 批量审批弹窗 state（沿用单个审批的路由流转语义；processData 同单个）
const batchDlg = reactive({ show: false, saving: false, opinion: '', nodeKey: '', rows: [], branches: [], branchIdx: 0 });

async function openBatchReview() {
  const sel = todoSelection.value;
  if (!sel.length) return ElMessage.warning('请先勾选要审批的申请');
  // 必须同一审批环节：不同环节(节点)的"下一步走向"分支选项不同，混选无法统一处理
  const nodeKeys = [...new Set(sel.map(r => r.nodeKey))];
  if (nodeKeys.length > 1) {
    return ElMessage.warning('请选择同一审批环节的任务再批量审批');
  }
  batchDlg.rows = [...sel];
  batchDlg.nodeKey = nodeKeys[0] || '';
  batchDlg.opinion = '';
  batchDlg.saving = false;
  // 同一环节 → 分支选项一致，取首条任务的命名出边作为共用「下一步走向」选项
  batchDlg.branches = await loadOutgoingBranches(sel[0]?.taskId);
  batchDlg.branchIdx = 0;
  batchDlg.show = true;
}

async function onBatchReviewAction(action) {
  if (!batchDlg.opinion || !batchDlg.opinion.trim()) {
    return ElMessage.warning('请填写审批意见');
  }
  batchDlg.saving = true;
  let ok = 0;
  const fails = [];
  try {
    // 逐条处理：claim → 通过(带 corp/finRouteTo 流转)/驳回，与单个审批一致；单条失败不阻断其余
    for (const row of batchDlg.rows) {
      try {
        if (!row.taskId) throw new Error('任务 ID 缺失');
        await ensureClaimed(row);
        if (action === 'APPROVE') {
          // 共用所选分支的 routeVariables 应用到每条任务（同环节分支一致）
          const chosen = batchDlg.branches[batchDlg.branchIdx];
          const formData = chosen ? { ...(chosen.routeVariables || {}) } : undefined;
          await approveTask(row.taskId, batchDlg.opinion, formData);
        } else {
          await rejectTask(row.taskId, batchDlg.opinion);
        }
        ok++;
      } catch (e) {
        fails.push(`${row.applyNo || row.taskId}：${e?.bizMsg || e?.message || '失败'}`);
      }
    }
    if (fails.length === 0) {
      ElMessage.success(`已${action === 'APPROVE' ? '通过' : '驳回'} ${ok} 条`);
    } else {
      ElMessage.warning(`成功 ${ok} 条，失败 ${fails.length} 条：${fails.slice(0, 3).join('；')}${fails.length > 3 ? ' …' : ''}`);
    }
    batchDlg.show = false;
    todoSelection.value = [];
    reloadTodo();
  } finally {
    batchDlg.saving = false;
  }
}

// ============ 业务类型字典 ============
const ALLOC_DIM_FALLBACK = [
  { value: 'RULE', label: '按规则分配' },
  { value: 'ACCOUNT', label: '按账号分配' },
  { value: 'NEW', label: '新开户' }
];
// 分配维度是字典配置项，列表筛选、列表标签和表单下拉共用这一组数据。
// 字典接口不可用时保留三项基础能力，避免页面因配置中心短暂故障而无法操作。
const allocDimOptions = ref(ALLOC_DIM_FALLBACK.map(item => ({ ...item })));
const allocDimMap = computed(() => Object.fromEntries(allocDimOptions.value.map(item => [item.value, item.label])));
function normalizeAllocDimLabel(value, label) {
  if (value === 'ACCOUNT') return '按账号分配';
  if (value === 'NEW') return '新开户';
  return label || value;
}
function allocDimLabel(value) {
  return allocDimMap.value[value] || value || '-';
}
async function loadAllocDimDict() {
  try {
    const items = await listDictItems('PERF_ALLOC_DIM');
    const merged = new Map(ALLOC_DIM_FALLBACK.map(item => [item.value, item.label]));
    for (const item of (Array.isArray(items) ? items : [])) {
      const value = item?.dictCode || item?.dictValue || item?.value;
      const label = item?.dictLabel || item?.label || value;
      if (value) merged.set(value, normalizeAllocDimLabel(value, label));
    }
    allocDimOptions.value = [...merged].map(([value, label]) => ({ value, label }));
  } catch {
    allocDimOptions.value = ALLOC_DIM_FALLBACK.map(item => ({ ...item }));
  }
}

const bizKindOptions = ref([]);
const bizKindMap = computed(() => {
  const m = {};
  for (const o of bizKindOptions.value) m[o.value] = o.label;
  return m;
});
// 业务类型「存款 / 贷款」对应的字典编码（PERF_BIZ_KIND）
const BIZ_DEPOSIT = 'CORP_DEPOSIT';
const BIZ_LOAN = 'CORP_LOAN';
// 新建申请弹框里业务类型可选项：
// - 只读查看：展示全部（保证历史含中收/结构性等也能正常显示标签）
// - 按账号分配(ACCOUNT)：仅「存款」（且默认固定为存款、不可改）
// - 按规则分配(RULE)：全部 PERF_BIZ_KIND 字典项可选（默认选中存款+贷款，可多选可改）
const bizKindFormOptions = computed(() => {
  if (dlg.readOnly) return bizKindOptions.value;
  if (dlg.form.allocDim === 'ACCOUNT') return bizKindOptions.value.filter(o => o.value === BIZ_DEPOSIT);
  return bizKindOptions.value;
});
const BIZ_KIND_FALLBACK = {
  CORP_DEPOSIT: '对公存款', CORP_LOAN: '对公贷款', CORP_FOREX: '对公外汇',
  CORP_LARGE_CD: '大额存单', FEE_BIZ: '中间业务',
  PER_DEP: '个人存款', PER_LOAN: '个人贷款',
};
function fmtBizKind(val) {
  if (!val) return '-';
  return val.split(',').map(k => bizKindMap.value[k] || BIZ_KIND_FALLBACK[k] || k).join('、');
}
// 余额概览按业务类型动态显隐：业务类型(可多选)的标签含「存」→显示存款余额；含「贷」→显示贷款余额；其余不显示
function bizKindLabel(k) {
  return bizKindMap.value[k] || BIZ_KIND_FALLBACK[k] || k || '';
}
const showDepositBal = computed(() => (dlg.form.bizKind || []).some(k => bizKindLabel(k).includes('存')));
const showLoanBal = computed(() => (dlg.form.bizKind || []).some(k => bizKindLabel(k).includes('贷')));
async function loadBizKindDict() {
  try {
    const items = await listDictItems('PERF_BIZ_KIND');
    bizKindOptions.value = (Array.isArray(items) ? items : []).map(d => ({ label: d.dictLabel, value: d.dictCode }));
  } catch { bizKindOptions.value = []; }
}

// ============ 新建/查看 弹框 ============
const dlgFormRef = ref(null);
const custNameDisplay = ref('');
// XAN_M98_CUST_STAT_SHOW3 反显：客户余额（当前/月日均/季日均/年日均）
// queried=是否已完成一次按编号查询；found=是否查到该客户的统计行
const custStat = reactive({ currBal: null, mAvgBal: null, qAvgBal: null, yAvgBal: null, queried: false, found: false });
function resetCustStat() {
  custStat.currBal = custStat.mAvgBal = custStat.qAvgBal = custStat.yAvgBal = null;
  custStat.queried = false;
  custStat.found = false;
}
// 余额概览 4 项：按客户编号 + 昨日日期在 CUST_INDEX_RESULT 取 MC_001/MC_002/MC_003/MC_004 指标值，
// 分别对应 当前余额 / 较上日余额 / 年均余额 / 较上年均余额；查无数据为 null → 页面显示 '-'
const custIdx = reactive({ MC_001: null, MC_002: null, MC_003: null, MC_004: null });
// 贷款余额（MC_005-008，固定槽位 5-8：当前/较上日/年均/较上年均贷款余额）
const custIdxLoan = reactive({ MC_005: null, MC_006: null, MC_007: null, MC_008: null });
function resetCustIdxLoan() {
  custIdxLoan.MC_005 = custIdxLoan.MC_006 = custIdxLoan.MC_007 = custIdxLoan.MC_008 = null;
}
function resetCustIdx() {
  custIdx.MC_001 = custIdx.MC_002 = custIdx.MC_003 = custIdx.MC_004 = null;
  resetCustIdxLoan();
}
// 按客户编号 + 统计日期取 CUST_INDEX_RESULT 的贷款余额（MC_005-008，独立失败兜底 '-'）
async function loadCustIdxLoan(custId) {
  if (!custId) { resetCustIdxLoan(); return; }
  try {
    const m = await getCustIndexValues(custId, custStatStatisDt(), ['MC_005', 'MC_006', 'MC_007', 'MC_008']);
    custIdxLoan.MC_005 = m?.MC_005 ?? null;
    custIdxLoan.MC_006 = m?.MC_006 ?? null;
    custIdxLoan.MC_007 = m?.MC_007 ?? null;
    custIdxLoan.MC_008 = m?.MC_008 ?? null;
  } catch { resetCustIdxLoan(); }
}
// XAN_M98_CUST_STAT_SHOW3 统计日期 STATIS_DT：
// 查看/审批模式（dlg.createdTime 有值）取 申请日期-1；新建模式取 昨日。均 yyyy-MM-dd。
function custStatStatisDt() {
  const base = dlg.createdTime ? new Date(dlg.createdTime) : new Date(Date.now() - 86400000);
  if (dlg.createdTime) base.setDate(base.getDate() - 1);
  return base.toISOString().slice(0, 10);
}
const preview = reactive({ loaded: false, loading: false, data: null });
// 本次申请明细表是否存在 item_kind=ORIGIN 记录（手工录入的原业绩分配，openTodoDetail 已拆入 dlg.form.originalItems）
const hasOriginItems = computed(() => (dlg.form.originalItems?.length || 0) > 0);
// 新开户不依赖原分配关系；详情中已有 ORIGIN 行时，以已保存的行作为权威来源，
// 避免后续预览请求命中当前关系后把草稿编辑态切回只读预览表。
const showOriginalAllocation = computed(() => dlg.form.allocDim !== 'NEW');
// 原业绩分配是否有数据（决定审批时能否选"交原业绩所属人审批"）。
// 详情已经返回 ORIGIN 时优先展示 originalItems（草稿可编辑、查看只读），不被 preview 覆盖。
const hasOriginalOwners = computed(() => showOriginalAllocation.value
  && !hasOriginItems.value
  && (preview.data && preview.data.allocList && preview.data.allocList.length) > 0);
// 能否走"交原业绩所属人审批"：当前关系预览 OR 申请内已保存的 ORIGIN 快照；
// 后端在有申请快照时以快照为准，新建未回传自动预览时才读取当前关系。
const canRouteOwner = computed(() => showOriginalAllocation.value && (hasOriginalOwners.value || hasOriginItems.value));
function fmtAmt(v) {
  if (v == null) return '-';
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
async function loadPreview(statisDt) {
  const f = dlg.form;
  // NEW 不需要原分配关系；详情已有 ORIGIN 行时也不再以当前关系覆盖申请快照。
  if (f.allocDim === 'NEW' || hasOriginItems.value || !f.custId) {
    preview.loaded = false;
    preview.loading = false;
    preview.data = null;
    return;
  }
  preview.loaded = true;
  preview.loading = true;
  // 新建时传昨日，查看/审批时由调用方传入申请日期-1
  const dt = statisDt || new Date(Date.now() - 86400000).toISOString().slice(0, 10);
  try {
    preview.data = await getAllocPreview({
      custType: f.custType, custNo: f.custId, allocDim: f.allocDim, accountNo: f.accountNo || undefined,
      statisDt: dt
    });
  } catch { preview.data = null; }
  finally { preview.loading = false; }
}
// 查看调整申请：统一走共享只读组件（与业绩分配查询页内容一致）；审批仍用原表单弹框，不改动任何现有函数
const viewDialog = reactive({ show: false, applyId: '' });
function openSharedView(row) {
  const fromBk = (row.businessKey || '').split(':')[1];
  const applyId = fromBk || row.id || row.applyNo || row.bizId;
  if (!applyId) { ElMessage.warning('无法识别申请 ID'); return; }
  viewDialog.applyId = String(applyId);
  viewDialog.show = true;
}
const dlg = reactive({
  show: false, readOnly: false, saving: false, draftSaving: false, viewingId: null, editingId: null,
  reviewMode: false, reviewRow: null, reviewOpinion: '', reviewSaving: false,
  reviewBranches: [], reviewBranchIdx: 0,
  approvalLogs: [], approvalLoading: false,
  applyNo: '', createdBy: '', createdByName: '', createdByOrgName: '', createdTime: null,
  form: {
    custType: 'CORP', custId: '', custName: '', allocDim: 'RULE', bizKind: 'CORP_DEPOSIT',
    accountNo: '', ownerOrgId: '', reason: '',
    items: [{ empId: '', pct: 100, remark: '', empLabel: '', _selectedEmpLabel: '' }],
    originalItems: []
  }
});
const dlgTitle = computed(() => dlg.reviewMode ? '审批调整申请' : dlg.readOnly ? '查看调整申请' : dlg.editingId ? '编辑调整申请' : '新建调整申请');
const totalPct = computed(() => dlg.form.items.reduce((s, x) => s + (Number(x.pct) || 0), 0));
const dlgRules = {
  custType:   [{ required: true, message: '请选择客户类型' }],
  custId:     [{ required: true, message: '请填写客户编号' }],
  custName:   [{ required: true, message: '请填写客户名称' }],
  allocDim:   [{ required: true, message: '请选择分配维度' }],
  accountNo:  [{ validator: (rule, val, cb) => {
                  if (dlg.form.allocDim === 'ACCOUNT' && !(val && String(val).trim())) {
                    cb(new Error('按账号分配时请填写账号'));
                  } else { cb(); }
                }, trigger: ['blur', 'change'] }],
  bizKind:    [{ required: true, message: '请选择业务类型' }],
  // ownerOrgId 已隐藏，不再必填
  reason:     [
    { required: true, message: '请填写申请原因（必填，将记入审批日志）' },
    { max: 500, message: '申请原因不超过 500 字' }
  ]
};

function defaultItem() { return { empId: '', pct: 0, remark: '', empLabel: '', _selectedEmpLabel: '' }; }
function addItemRow() { dlg.form.items.push(defaultItem()); }

// 分配明细员工号自动补齐：按工号/登录名/中文名模糊匹配 PT_USER
// 下拉项 label = "username（中文名）"，作为输入框显示内容；empId 另存真实主键用于提交
async function queryEmpSuggest(queryString, cb) {
  const kw = (queryString || '').trim();
  if (!kw) { cb([]); return; }
  try {
    const list = await suggestEmployees(kw);
    const arr = Array.isArray(list) ? list : [];
    cb(arr.map(u => ({
      ...u,
      label: u.empChnName
        ? `${u.username || u.empId}（${u.empChnName}）`
        : (u.username || u.empId)
    })));
  } catch { cb([]); }
}
// 选中下拉项：输入框显示 username/姓名，empId 存真实用户主键（供提交/校验/后端解析）
function onEmpSelect(row, item) {
  row.empId = item.empId || '';
  row.username = item.username || '';
  row.empChnName = item.empChnName || item.displayName || '';
  row.empLabel = item.label || empDisplayLabel(item, row.empId);
  row._selectedEmpLabel = row.empLabel;
}
// 自由输入未从下拉选择时清空主键，避免把 username 当作 empId；已选项原样回填时保持选择结果。
function onEmpInput(row, val) {
  if (row.empId && String(val || '') === String(row._selectedEmpLabel || '')) return;
  clearEmpSelection(row);
}
// 明细员工号显示文案：优先 username（中文名），无则回退工号（查看/审批页详情已带 username/empChnName）
function empLabelOf(it) {
  if (it && it.username) return it.username + (it.empChnName ? '（' + it.empChnName + '）' : '');
  return (it && it.empId) || '';
}
function selectedEmpLabelOf(it) {
  return it?.empId ? empLabelOf(it) : '';
}
function empDisplayLabel(item, fallbackEmpId = '') {
  const username = item?.username || '';
  const name = item?.empChnName || item?.displayName || '';
  return username ? `${username}${name ? `（${name}）` : ''}` : (fallbackEmpId || item?.empId || '');
}
function clearEmpSelection(row) {
  row.empId = '';
  row._selectedEmpLabel = '';
  if ('username' in row) row.username = '';
  if ('empChnName' in row) row.empChnName = '';
  if ('orgCode' in row) {
    row.orgCode = '';
    row.orgName = '';
    row.orgLabel = '';
  }
}

// === 原业绩分配（手工录入）===
function origRow() {
  return {
    acctNo: '', empId: '', empLabel: '', _selectedEmpLabel: '', username: '', empChnName: '',
    orgCode: '', orgName: '', orgLabel: '', ratio: 0
  };
}
function addOriginalRow() { dlg.form.originalItems.push(origRow()); }
function removeOriginalRow(i) { dlg.form.originalItems.splice(i, 1); }
// 原业绩分配的所属机构只展示名称；orgCode 仍保留在数据模型中用于提交。
function originalOrgDisplay(row) {
  return row?.orgName || '-';
}
// 原业绩分配-员工下拉选中（empId 存真实用户主键，username/empChnName 留展示快照）
function onOrigEmpSelect(row, item) {
  row.empId = item.empId || '';
  row.username = item.username || '';
  row.empChnName = item.empChnName || item.displayName || '';
  row.empLabel = item.label || empDisplayLabel(item, row.empId);
  row._selectedEmpLabel = row.empLabel;
  // emp-suggest 返回员工主机构快照；回填后机构仍可通过下方机构联想框改选。
  const mainOrgCode = item.mainOrgCode || item.orgCode || item.deptNo || '';
  const mainOrgName = item.mainOrgName || item.orgName || '';
  if (mainOrgCode) row.orgCode = mainOrgCode;
  if (mainOrgName) row.orgName = mainOrgName;
  if (row.orgCode || row.orgName) {
    row.orgLabel = row.orgName || '';
  }
}
function onOrigEmpInput(row, val) {
  if (row.empId && String(val || '') === String(row._selectedEmpLabel || '')) return;
  clearEmpSelection(row);
}
// 原业绩分配-机构下拉联想（按机构号/部门号/名称模糊匹配，只展示机构名称）
async function queryOrgSuggest(queryString, cb) {
  const kw = (queryString || '').trim();
  if (!kw) { cb([]); return; }
  try {
    const list = await suggestOrgs(kw);
    const arr = Array.isArray(list) ? list : [];
    cb(arr.map(o => ({ ...o, label: o.orgName || '' })));
  } catch { cb([]); }
}
function onOrigOrgSelect(row, item) {
  // 存部门号(DEPT_NO)作为「所属机构号」用于提交，输入框只展示机构名称。
  row.orgCode = item.deptNo || item.orgCode || '';
  row.orgName = item.orgName || '';
  row.orgLabel = row.orgName || '';
}
function onOrigOrgInput(row, val) {
  if (!val) { row.orgCode = ''; row.orgName = ''; return; }
  if (!String(val).includes('（')) { row.orgCode = String(val).trim(); }
}
// 查看/审批：从详情 items 拆出原业绩分配（item_kind=ORIGIN）
function originalItemsFromDetail(d) {
  return (d.items || []).filter(it => (it.itemKind || 'NEW') === 'ORIGIN').map(it => ({
    acctNo: it.acctNo || '', empId: it.empId || '',
    username: it.username || '', empChnName: it.empChnName || '',
    orgCode: it.orgCode || '', orgName: it.orgName || '',
    empLabel: empLabelOf(it),
    _selectedEmpLabel: selectedEmpLabelOf(it),
    orgLabel: it.orgName || '',
    ratio: it.ratio
  }));
}
function onDlgClosed() {
  dlg.viewingId = null;
  dlg.editingId = null;
  dlg.readOnly = false;
  dlg.reviewMode = false;
  dlg.reviewRow = null;
  dlg.reviewOpinion = '';
  dlg.reviewSaving = false;
  dlg.approvalLogs = [];
  dlg.approvalLoading = false;
  dlg.applyNo = '';
  dlg.createdBy = '';
  dlg.createdByName = '';
  dlg.createdByOrgName = '';
  dlg.createdTime = null;
}

// 拉审批流记录
// 弹窗顶部已独立展示"申请单号 / 申请人 / 机构 / 时间"，时间线里不再重复 SUBMIT 节点。
async function loadApprovalHistory(applyId) {
  if (!applyId) {
    dlg.approvalLogs = [];
    return;
  }
  dlg.approvalLoading = true;
  try {
    const list = await getAdjustApprovalHistory(applyId);
    const logs = Array.isArray(list) ? list : [];
    dlg.approvalLogs = logs.filter(log => log.action !== 'SUBMIT');
  } catch (err) {
    // 审批流拉取失败不阻塞主流程，仅清空 + 控制台告警
    console.warn('[Adjust] 审批流记录加载失败', err);
    dlg.approvalLogs = [];
  } finally {
    dlg.approvalLoading = false;
  }
}

function onCustTypeChange(val) {
  // 零售客户默认按账号分配，但分配维度下拉仍由字典提供 RULE/ACCOUNT/NEW，用户可继续改选。
  // 详情回显使用 Object.assign，不调用此事件，避免加载已有零售草稿时误覆盖其维度。
  if (!dlg.readOnly && val === 'RETAIL') {
    dlg.form.allocDim = 'ACCOUNT';
    onAllocDimChange('ACCOUNT');
  }
}
function onAllocDimChange(val) {
  if (val === 'ACCOUNT') {
    // 按账号分配：业务类型固定为「存款」且不可修改
    dlg.form.bizKind = [BIZ_DEPOSIT];
  } else {
    dlg.form.accountNo = '';
    // 按规则分配：业务类型默认「存款 + 贷款」（可改、可多选，选项为全部 PERF_BIZ_KIND）
    dlg.form.bizKind = [BIZ_DEPOSIT, BIZ_LOAN];
  }
}
function openCreate() {
  dlg.readOnly = false;
  dlg.viewingId = null;
  dlg.editingId = null;
  custNameDisplay.value = '';
  resetCustStat();
  resetCustIdx();
  preview.loaded = false;
  preview.data = null;
  Object.assign(dlg.form, {
    // 默认按规则分配 → 业务类型默认存款+贷款
    custType: 'CORP', custId: '', custName: '', allocDim: 'RULE', bizKind: [BIZ_DEPOSIT, BIZ_LOAN],
    accountNo: '', ownerOrgId: '', reason: '',
    items: [{ empId: '', pct: 100, remark: '', empLabel: '', _selectedEmpLabel: '' }],
    originalItems: []
  });
  dlg.show = true;
}
async function openView(row) {
  dlg.readOnly = true;
  dlg.editingId = null;
  dlg.viewingId = row.id || row.applyNo;
  dlg.approvalLogs = [];
  Object.assign(dlg.form, {
    custType: inferCustType(row.custType, row.bizKind), custId: row.custId || '',
    custName: row.custName || '',
    allocDim: row.allocDim || 'RULE',
    bizKind: row.bizKind ? (typeof row.bizKind === 'string' ? row.bizKind.split(',') : row.bizKind) : [],
    accountNo: row.accountNo || '',
    ownerOrgId: row.ownerOrgId || '',
    reason: row.reason || row.remark || '',
    items: row.items?.length ? row.items.map(it => ({
      empId: it.empId || '', username: it.username || '', empChnName: it.empChnName || '',
      pct: it.pct ?? it.ratio, remark: it.remark || '', empLabel: empLabelOf(it),
      _selectedEmpLabel: selectedEmpLabelOf(it)
    })) : [{ empId: '', pct: 100, remark: '', empLabel: '', _selectedEmpLabel: '' }]
  });
  // 余额概览读快照（列复用：currBal=当前 / mAvgBal=较上日 / qAvgBal=年均 / yAvgBal=较上年均）
  custIdx.MC_001 = row.currBal ?? null;
  custIdx.MC_002 = row.mAvgBal ?? null;
  custIdx.MC_003 = row.qAvgBal ?? null;
  custIdx.MC_004 = row.yAvgBal ?? null;
  custIdxLoan.MC_005 = row.loanCurrBal ?? null;
  custIdxLoan.MC_006 = row.loanMAvgBal ?? null;
  custIdxLoan.MC_007 = row.loanQAvgBal ?? null;
  custIdxLoan.MC_008 = row.loanYAvgBal ?? null;
  // list 接口已有的申请人字段先塞进去，detail 接口再覆盖一次以拿到 createdByName/OrgName
  dlg.applyNo = row.applyNo || '';
  dlg.createdBy = row.createdBy || '';
  dlg.createdByName = row.createdByName || '';
  dlg.createdByOrgName = row.createdByOrgName || '';
  dlg.createdTime = row.createdTime || null;
  dlg.show = true;
  try {
    const d = await getAdjustDetail(dlg.viewingId);
    if (d?.id) {
      Object.assign(dlg.form, {
        custType: d.custType || '', custId: d.custId, allocDim: d.allocDim,
        bizKind: d.bizKind ? (typeof d.bizKind === 'string' ? d.bizKind.split(',') : d.bizKind) : [],
        accountNo: d.accountNo, ownerOrgId: d.ownerOrgId, reason: d.reason || d.remark || '',
        items: (d.items || []).filter(it => (it.itemKind || 'NEW') === 'NEW').map(it => ({
          empId: it.empId || '', username: it.username || '', empChnName: it.empChnName || '',
          pct: it.pct ?? it.ratio, remark: it.remark || '', empLabel: empLabelOf(it),
          _selectedEmpLabel: selectedEmpLabelOf(it)
        })),
        originalItems: originalItemsFromDetail(d)
      });
      // 客户名称 + 余额概览以 detail 快照为准
      if (d.custName) dlg.form.custName = d.custName;
      custIdx.MC_001 = d.currBal ?? null;
      custIdx.MC_002 = d.mAvgBal ?? null;
      custIdx.MC_003 = d.qAvgBal ?? null;
      custIdx.MC_004 = d.yAvgBal ?? null;
      custIdxLoan.MC_005 = d.loanCurrBal ?? null;
      custIdxLoan.MC_006 = d.loanMAvgBal ?? null;
      custIdxLoan.MC_007 = d.loanQAvgBal ?? null;
      custIdxLoan.MC_008 = d.loanYAvgBal ?? null;
      dlg.applyNo = d.applyNo || dlg.applyNo;
      dlg.createdBy = d.createdBy || dlg.createdBy;
      dlg.createdByName = d.createdByName || dlg.createdByName;
      dlg.createdByOrgName = d.createdByOrgName || dlg.createdByOrgName;
      dlg.createdTime = d.createdTime || dlg.createdTime;
      // 查看页面：Statis_Dt = 申请日期 - 1
      const ct = d.createdTime || dlg.createdTime;
      if (ct) {
        const applyDate = new Date(ct);
        applyDate.setDate(applyDate.getDate() - 1);
        loadPreview(applyDate.toISOString().slice(0, 10));
      }
    }
  } catch {}
  loadApprovalHistory(dlg.viewingId);
}

// 编辑草稿：复用新建弹窗（非只读），从详情反显已录入信息。仅 DRAFT 行可进入。
async function openEdit(row) {
  dlg.readOnly = false;
  dlg.reviewMode = false;
  dlg.editingId = row.id || row.applyNo;
  dlg.viewingId = null;
  dlg.approvalLogs = [];
  custNameDisplay.value = '';
  resetCustStat();
  resetCustIdx();
  preview.loaded = false;
  preview.data = null;
  // 先用列表行预填
  Object.assign(dlg.form, {
    custType: inferCustType(row.custType, row.bizKind), custId: row.custId || '',
    custName: row.custName || '', allocDim: row.allocDim || 'RULE',
    bizKind: row.bizKind ? (typeof row.bizKind === 'string' ? row.bizKind.split(',') : row.bizKind) : [],
    accountNo: row.accountNo || '', ownerOrgId: row.ownerOrgId || '',
    reason: row.reason || row.remark || '',
    items: [{ empId: '', pct: 100, remark: '', empLabel: '', _selectedEmpLabel: '' }],
    originalItems: []
  });
  dlg.applyNo = row.applyNo || '';
  dlg.show = true;
  try {
    const d = await getAdjustDetail(dlg.editingId);
    if (d?.id) {
      Object.assign(dlg.form, {
        custType: d.custType || 'CORP', custId: d.custId, allocDim: d.allocDim || 'RULE',
        bizKind: d.bizKind ? (typeof d.bizKind === 'string' ? d.bizKind.split(',') : d.bizKind) : [],
        accountNo: d.accountNo || '', ownerOrgId: d.ownerOrgId || '', reason: d.reason || d.remark || '',
        items: (d.items || []).filter(it => (it.itemKind || 'NEW') === 'NEW').map(it => ({
          empId: it.empId || '', username: it.username || '', empChnName: it.empChnName || '',
          pct: it.pct ?? it.ratio, remark: it.remark || '', empLabel: empLabelOf(it),
          _selectedEmpLabel: selectedEmpLabelOf(it)
        })),
        originalItems: originalItemsFromDetail(d)
      });
      if (!dlg.form.items.length) dlg.form.items = [{ empId: '', pct: 100, remark: '', empLabel: '', _selectedEmpLabel: '' }];
      if (d.custName) dlg.form.custName = d.custName;
      // 余额概览快照反显（列复用）
      custIdx.MC_001 = d.currBal ?? null;
      custIdx.MC_002 = d.mAvgBal ?? null;
      custIdx.MC_003 = d.qAvgBal ?? null;
      custIdx.MC_004 = d.yAvgBal ?? null;
      custIdxLoan.MC_005 = d.loanCurrBal ?? null;
      custIdxLoan.MC_006 = d.loanMAvgBal ?? null;
      custIdxLoan.MC_007 = d.loanQAvgBal ?? null;
      custIdxLoan.MC_008 = d.loanYAvgBal ?? null;
      dlg.applyNo = d.applyNo || dlg.applyNo;
      dlg.createdTime = d.createdTime || dlg.createdTime;
      // 编辑态客户编号是反显的，依赖 custId watch 不可靠（防抖/同值）；
      // 这里按草稿创建日-1 主动触发一次原业绩分配数据拉取（statisDt 与查看一致）
      const ct = d.createdTime || dlg.createdTime;
      const statisDt = ct
        ? new Date(new Date(ct).getTime() - 86400000).toISOString().slice(0, 10)
        : undefined;
      loadPreview(statisDt);
    }
  } catch {}
}

async function onSubmit() {
  try { await dlgFormRef.value.validate(); } catch { return; }
  if (dlg.form.allocDim === 'ACCOUNT' && !(dlg.form.accountNo && dlg.form.accountNo.trim())) {
    return ElMessage.warning('按账号分配时请填写账号');
  }
  if (!dlg.form.items.length) return ElMessage.warning('至少添加 1 条分配明细');
  for (let i = 0; i < dlg.form.items.length; i++) {
    const it = dlg.form.items[i];
    if (!it.empId) return ElMessage.warning(`第 ${i + 1} 行：请填写员工号`);
    if (!(it.pct >= 1)) return ElMessage.warning(`第 ${i + 1} 行：承担比例须 ≥ 1%`);
  }
  const seen = new Set();
  for (const it of dlg.form.items) {
    if (seen.has(it.empId)) return ElMessage.warning(`员工 ${it.empId} 出现多次，请合并`);
    seen.add(it.empId);
  }
  if (totalPct.value !== 100) return ElMessage.warning(`分配比例合计须为 100%，当前 ${totalPct.value}%`);

  // 原业绩分配校验：NEW 不要求；自动查到历史分配则免手工；查不到则要求手工至少 1 条（除账号外必填）
  if (showOriginalAllocation.value && !hasOriginalOwners.value) {
    const origs = dlg.form.originalItems || [];
    if (!origs.length) return ElMessage.warning('未查到原业绩分配，请手工录入至少 1 条原业绩分配记录');
    for (let i = 0; i < origs.length; i++) {
      const o = origs[i];
      if (!o.empId) return ElMessage.warning(`原业绩分配第 ${i + 1} 行：请选择员工`);
      if (!o.orgCode) return ElMessage.warning(`原业绩分配第 ${i + 1} 行：请选择所属机构`);
      if (!(Number(o.ratio) > 0)) return ElMessage.warning(`原业绩分配第 ${i + 1} 行：请填写分配比例`);
    }
  }

  dlg.saving = true;
  try {
    const payload = buildAdjustPayload();
    if (dlg.editingId) {
      // 编辑草稿后提交：先存草稿持久化本次编辑，再走草稿提交审批（DRAFT→IN_APPROVAL）
      await saveDraftAdjust({ ...payload, id: dlg.editingId });
      await submitDraftAdjust(dlg.editingId);
    } else {
      // 新建直接提交审批
      await submitAdjust(payload);
    }
    ElMessage.success('已提交审批');
    dlg.show = false;
    reloadMine();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '提交失败');
  } finally { dlg.saving = false; }
}

/**
 * 组装提交/草稿入库 payload（提交审批与保存草稿共用）。
 * 余额概览快照随保存入库（列复用 currBal/mAvgBal/... + loan*），审批/查看直接读。
 */
function buildAdjustPayload() {
  return {
    custType:   dlg.form.custType,
    custId:     dlg.form.custId,
    custName:   dlg.form.custName || null,
    currBal:    custIdx.MC_001,
    mAvgBal:    custIdx.MC_002,
    qAvgBal:    custIdx.MC_003,
    yAvgBal:    custIdx.MC_004,
    loanCurrBal: custIdxLoan.MC_005,
    loanMAvgBal: custIdxLoan.MC_006,
    loanQAvgBal: custIdxLoan.MC_007,
    loanYAvgBal: custIdxLoan.MC_008,
    allocDim:   dlg.form.allocDim,
    bizKind:    Array.isArray(dlg.form.bizKind) ? dlg.form.bizKind.join(',') : dlg.form.bizKind,
    accountNo:  dlg.form.accountNo || undefined,
    ownerOrgId: dlg.form.ownerOrgId || userStore.user?.mainOrgCode || userStore.user?.orgCode || '',
    reason:     dlg.form.reason,
    // 仅送已填员工号的明细行（草稿可能含空行）
    items: (dlg.form.items || []).filter(it => it.empId).map(it => ({ empId: it.empId, ratio: Number(it.pct), remark: it.remark || '' })),
    // NEW 不需要原分配；详情带 ORIGIN 时优先送可编辑快照，避免预览关系覆盖草稿修改。
    originalAllocList: dlg.form.allocDim === 'NEW' || hasOriginalOwners.value
      ? []
      : (dlg.form.originalItems || []).filter(o => o.empId).map(o => ({
        acctNo: o.acctNo || null, empId: o.empId,
        username: o.username || null, empChnName: o.empChnName || null,
        orgCode: o.orgCode, orgName: o.orgName || null, ratio: Number(o.ratio)
      }))
  };
}

/**
 * 保存为草稿：宽松校验（仅需客户编号），把已录入信息落库为 DRAFT，不进入审批流程。
 * 新建草稿成功后记下 id，便于继续编辑/直接提交。
 */
async function onSaveDraft() {
  if (!dlg.form.custId || !String(dlg.form.custId).trim()) {
    return ElMessage.warning('请先填写客户编号再保存草稿');
  }
  dlg.draftSaving = true;
  try {
    const resp = await saveDraftAdjust({ ...buildAdjustPayload(), id: dlg.editingId || undefined });
    if (resp && resp.id && !dlg.editingId) dlg.editingId = resp.id;
    ElMessage.success('已保存为草稿');
    dlg.show = false;
    reloadMine();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '保存草稿失败');
  } finally { dlg.draftSaving = false; }
}

async function onWithdraw(row) {
  let reason;
  try {
    const r = await ElMessageBox.prompt('请填写撤回原因', '撤回申请', {
      type: 'warning', inputPattern: /\S+/, inputErrorMessage: '撤回原因必填'
    });
    reason = r.value;
  } catch { return; }
  try {
    await withdrawAdjust(row.id || row.applyNo, reason);
    ElMessage.success('已撤回');
    reloadMine();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '撤回失败');
  }
}

async function confirmWithdraw(row) {
  try {
    await ElMessageBox.confirm(`确认撤回申请 ${row.applyNo || row.id}？`, '确认撤回', {
      type: 'warning', confirmButtonText: '确认撤回', cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  await onWithdraw(row);
}

// 客户编号变化时查询客户名称
let custNoTimer = null;
watch(() => dlg.form.custId, (val) => {
  clearTimeout(custNoTimer);
  // 查看/审批：客户名称与余额概览直接读提交时的快照，不再按编号实时取数
  if (dlg.readOnly) return;
  if (!val || val.length < 2) { dlg.form.custName = ''; resetCustStat(); resetCustIdx(); preview.loaded = false; return; }
  custNoTimer = setTimeout(async () => {
    // 记录上次是否"查到并锁定"——查不到时仅清掉这种锁定名，避免冲掉用户手输
    const prevFound = custStat.found;
    try {
      // 客户名称改从客户主档 CUST_MASTER 按客户编号反显（后端 CustomerQueryApi.getCustomerByCustNo）
      const m = await getCustMasterName(val);
      if (m && m.found) {
        // 客户主档命中：反显客户名称并锁定（输入框 disabled）
        dlg.form.custName = m.custName || '';
        custStat.found = true;
      } else {
        // 客户主档查不到：允许手工输入；若此前是查到锁定的名称则清空
        if (prevFound) dlg.form.custName = '';
        resetCustStat();
      }
      custStat.queried = true;
    } catch { if (prevFound) dlg.form.custName = ''; resetCustStat(); custStat.queried = true; }
    // 余额概览 4 项：按客户编号 + 昨日日期在 CUST_INDEX_RESULT 取 MC_001..004 指标值（独立失败兜底 '-'）
    try {
      const m = await getCustIndexValues(val, custStatStatisDt(), ['MC_001', 'MC_002', 'MC_003', 'MC_004']);
      custIdx.MC_001 = m?.MC_001 ?? null;
      custIdx.MC_002 = m?.MC_002 ?? null;
      custIdx.MC_003 = m?.MC_003 ?? null;
      custIdx.MC_004 = m?.MC_004 ?? null;
    } catch { resetCustIdx(); }
    // 贷款余额（MC_005-008）独立取数
    loadCustIdxLoan(val);
  }, 500);
});
// 客户编号 / 分配维度变化时触发原业绩分配预览（ACCOUNT 维度只查按账号分配的最后一条）
let previewTimer = null;
watch(
  () => [dlg.form.custId, dlg.form.allocDim],
  () => {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(() => {
      const f = dlg.form;
      if (f.custId && f.custId.length >= 2) {
        loadPreview();
      } else {
        preview.loaded = false;
      }
    }, 600);
  }
);

onMounted(async () => {
  loadAllocDimDict();
  loadBizKindDict();
  await loadCanApprove();
  // 没审批资格强制回到"我的申请"，避免 URL/路由复用残留 activeTab='todo' 的边角
  if (!canApprove.value && activeTab.value !== 'mine') activeTab.value = 'mine';

  // 读 query.tab 切 activeTab（仅当有该 tab 权限）
  const queryTab = route.query.tab;
  if (queryTab && ['mine', 'todo', 'done'].includes(queryTab)
      && (canApprove.value || queryTab === 'mine')) {
    activeTab.value = queryTab;
  }

  // 自动弹审批：query 含 tab=todo + action=open + taskId 三者齐 + 有审批权限
  const isAutoOpen = queryTab === 'todo' && route.query.action === 'open'
                     && route.query.taskId && canApprove.value;
  // 工作台已办「详情」跳转：tab=done + action=view → 已审批 tab + 弹只读详情
  const isAutoViewDone = queryTab === 'done' && route.query.action === 'view'
                         && (route.query.taskId || route.query.bizKey) && canApprove.value;
  if (isAutoOpen) {
    await reloadTodo();
    const row = todos.value.find(t => t.taskId === route.query.taskId);
    if (row) {
      openTodoReview(row);
    } else {
      ElMessage.warning('任务已处理或不在当前页');
    }
  } else if (isAutoViewDone) {
    await reloadDone();
    const tid = route.query.taskId, bk = route.query.bizKey;
    // 优先按 taskId/businessKey 命中当前页；不在当前页则用 businessKey 直接构造行拉详情
    let row = dones.value.find(t => (tid && t.taskId === tid) || (bk && t.businessKey === bk));
    if (!row && bk) row = { businessKey: bk };
    if (row) {
      openTodoDetail(row);
    } else {
      ElMessage.warning('申请不在当前页或已变更');
    }
  } else {
    reload();
  }
});
</script>

<style lang="scss" scoped>
.cust-name-label {
  display: inline-block;
  line-height: 32px;
  color: $text-1;
  font-weight: 500;
}
.cust-name-sub {
  font-size: 12px;
  color: #909399;
  line-height: 18px;
}
.preview-section {
  margin-bottom: 12px;
  .bal-card {
    border: 1px solid $border-2; border-radius: 4px; padding: 12px;
    .bal-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
    .bal-table { width: 100%; font-size: 13px;
      td { padding: 4px 0; }
      .num { text-align: right; font-weight: 500; font-family: ui-monospace, monospace; }
    }
  }
}
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.adjust-tabs { :deep(.el-tabs__nav-wrap)::after { background: $border-1; } }
.table { padding: 0; padding-bottom: 12px; }
.toolbar { align-items: flex-start; display: flex; justify-content: space-between; gap: var(--space-4); padding: var(--space-4) var(--space-4) var(--space-3); }
.section-title { color: var(--color-text-strong); font-size: 16px; font-weight: 600; line-height: 24px; margin: 0; }
.hint { color: var(--color-text-muted); font-size: 12px; line-height: 18px; margin: 4px 0 0; }
.table-state { color: var(--color-text-muted); font-size: 12px; margin: 2px 0 0; white-space: nowrap; }
.table-error { align-items: center; background: var(--color-danger-bg); border-left: 3px solid var(--color-danger-fg); color: var(--color-danger-fg); display: flex; font-size: 12px; gap: var(--space-3); justify-content: space-between; margin: 0 var(--space-4) var(--space-3); padding: var(--space-2) var(--space-3); }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.sub-id { font-size: 12px; color: $text-3; margin-left: 4px; }

/* 余额概览：分组小标题(存款/贷款余额) + label 方式金额 */
.bal-sub {
  font-size: 13px; font-weight: 600; color: $text-2;
  margin: 4px 0 6px;
}
.bal-val {
  display: inline-block;
  font-size: 14px; font-weight: 600; color: $text-1;
  line-height: 32px;
}
.card-h {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0 10px;
  border-bottom: 1px solid $border-1;
  margin: 8px 0 14px;
  .title { font-size: 14px; font-weight: 600; color: $text-1; flex: 1; }
  .weight-sum {
    font-size: 13px; color: $text-3; font-weight: 500;
    &.ok { color: $success; font-weight: 700; }
  }
  .sub-tip { font-size: 12px; color: $text-3; }
}

.approve-checkbox-tip {
  margin-left: 24px;
  font-size: 12px;
  color: $text-3;
  margin-top: 2px;
  line-height: 1.4;
}

.apply-info-bar {
  margin: 4px 0 16px;
  :deep(.el-descriptions__label) { width: 90px; }
  code.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
}
.approval-wrap {
  padding: 4px 0 4px 6px;
  min-height: 80px;
  .approval-line {
    display: flex; align-items: center; gap: 8px;
    font-size: 13px;
    .node { font-weight: 600; color: $text-1; }
  }
  .approval-meta {
    margin-top: 4px;
    font-size: 12px; color: $text-2;
    .meta-key { color: $text-3; }
    .meta-sep { margin: 0 8px; color: $text-3; }
  }
  .approval-opinion {
    margin-top: 4px;
    font-size: 12px; color: $text-2;
    background: $bg-soft;
    padding: 6px 8px; border-radius: 4px;
    word-break: break-all;
  }
}
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
@media (prefers-reduced-motion: reduce) {
  :where(.perf-adjust-page) :deep(*) { transition-duration: 0.01ms !important; animation-duration: 0.01ms !important; }
}
</style>
