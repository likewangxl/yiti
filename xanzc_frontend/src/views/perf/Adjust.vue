<template>
  <div>
    <div class="page-h">
      <h1>业绩调整 <span class="sub">比例之和 = 100% · 单行 ≥ 1% · 同一员工不重复</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button v-if="activeTab==='mine'" type="primary" @click="openCreate">+ 新建调整申请</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false"
      title="线下调整后将触发 KPI 历史回算。'我的申请' = 当前账号提交的；'待我审批' = 流程任务派到我的。"
      style="margin-bottom:12px" />

    <el-tabs v-model="activeTab" @tab-change="reload" class="adjust-tabs">
      <!-- ============ 我的申请 ============ -->
      <el-tab-pane label="我的申请" name="mine">
        <div class="card-section">
          <el-form inline size="default">
            <el-form-item label="关键字">
              <el-input v-model="mineFilters.keyword" placeholder="申请编号 / 客户 ID" clearable
                        style="width:200px" @keyup.enter="onMineFilterChange" />
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="mineFilters.allocDim" clearable placeholder="全部" style="width:140px"
                         @change="onMineFilterChange">
                <el-option value="RULE" label="按规则" />
                <el-option value="ACCOUNT" label="按账户" />
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
        </div>
        <div class="card-section table">
          <el-table :data="rows" size="default" empty-text="暂无调整申请" v-loading="loading">
            <el-table-column label="申请编号" width="170">
              <template #default="{row}"><code class="mono">{{ row.applyNo || row.id }}</code></template>
            </el-table-column>
            <el-table-column label="客户编号" min-width="160">
              <template #default="{row}">{{ row.custNo || row.custId || '-' }}</template>
            </el-table-column>
            <el-table-column label="维度" width="100">
              <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ { RULE: '按规则', ACCOUNT: '按账户' }[row.allocDim] || row.allocDim || '-' }}</el-tag></template>
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
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openView(row)">查看</el-button>
                <el-popconfirm
                  v-if="canWithdraw(row.status)"
                  :title="`确认撤回申请 ${row.applyNo || row.id}？`"
                  @confirm="onWithdraw(row)">
                  <template #reference>
                    <el-button link type="danger" size="small">撤回</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
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
          </div>
        </div>
      </el-tab-pane>

      <!-- ============ 待我审批 ============ -->
      <el-tab-pane v-if="canApprove" label="待我审批" name="todo">
        <div class="card-section">
          <el-form inline size="default">
            <el-form-item label="关键字">
              <el-input v-model="todoFilters.keyword" placeholder="申请编号 / 客户 ID" clearable
                        style="width:200px" @keyup.enter="onTodoFilterChange" />
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="todoFilters.allocDim" clearable placeholder="全部"
                         style="width:140px" @change="onTodoFilterChange">
                <el-option value="RULE" label="按规则" />
                <el-option value="ACCOUNT" label="按账户" />
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
            </el-form-item>
          </el-form>
        </div>
        <div class="card-section table">
          <el-table :data="todos" size="default" empty-text="无符合条件的待审批" v-loading="todoLoading">
            <el-table-column label="标题" min-width="220">
              <template #default="{row}"><code class="mono">{{ row.title || row.businessKey }}</code></template>
            </el-table-column>
            <el-table-column label="当前节点" width="140" prop="taskName" />
            <el-table-column label="发起人" width="160">
              <template #default="{row}">
                {{ row.startUserName || '-' }}
                <span v-if="row.startUser" class="sub-id">({{ row.startUser }})</span>
              </template>
            </el-table-column>
            <el-table-column label="发起机构" width="220">
              <template #default="{row}">
                <template v-if="row.startOrgName || row.startOrgId">
                  {{ row.startOrgId || '-' }}<span v-if="row.startOrgName"> · {{ row.startOrgName }}</span>
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
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openTodoReview(row)">审批</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
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
          </div>
        </div>
      </el-tab-pane>

      <!-- ============ 已审批 ============ -->
      <el-tab-pane v-if="canApprove" label="已审批" name="done">
        <div class="card-section">
          <el-form inline size="default">
            <el-form-item label="关键字">
              <el-input v-model="doneFilters.keyword" placeholder="申请编号 / 客户 ID" clearable
                        style="width:200px" @keyup.enter="onDoneFilterChange" />
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="doneFilters.allocDim" clearable placeholder="全部" style="width:140px"
                         @change="onDoneFilterChange">
                <el-option value="RULE" label="按规则" />
                <el-option value="ACCOUNT" label="按账户" />
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
        </div>
        <div class="card-section table">
          <el-table :data="dones" size="default" empty-text="无符合条件的已审批" v-loading="doneLoading">
            <el-table-column label="申请编号" width="170">
              <template #default="{row}"><code class="mono">{{ row.applyNo || row.id }}</code></template>
            </el-table-column>
            <el-table-column label="客户编号" min-width="160">
              <template #default="{row}">{{ row.custId || '-' }}</template>
            </el-table-column>
            <el-table-column label="维度" width="100">
              <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ { RULE: '按规则', ACCOUNT: '按账户' }[row.allocDim] || row.allocDim || '-' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="业务类型" width="160">
              <template #default="{row}">{{ fmtBizKind(row.bizKind) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请人" width="160">
              <template #default="{row}">
                {{ row.createdByName || row.startUserName || row.createdBy || '-' }}
                <span v-if="row.createdBy" class="sub-id">({{ row.createdBy }})</span>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="160">
              <template #default="{row}">{{ fmt(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openTodoDetail(row)">查看申请</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
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
          </div>
        </div>
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
            <span>{{ dlg.createdByName || '-' }}</span>
            <span v-if="dlg.createdBy" class="sub-id">（{{ dlg.createdBy }}）</span>
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
              <el-select v-model="dlg.form.custType" :disabled="dlg.readOnly" style="width:100%">
                <el-option label="对公客户" value="CORP" />
                <el-option label="零售客户" value="RETAIL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户编号" prop="custNo" required>
              <el-input v-model="dlg.form.custNo" :disabled="dlg.readOnly" placeholder="如 C20260001" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户名称">
              <span class="cust-name-label">{{ custNameDisplay || '-' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分配维度" prop="allocDim" required>
              <el-select v-model="dlg.form.allocDim" :disabled="dlg.readOnly" style="width:100%"
                         @change="onAllocDimChange">
                <el-option label="按规则分配" value="RULE" />
                <el-option label="按账户分配" value="ACCOUNT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务类型" prop="bizKind" required>
              <el-select v-model="dlg.form.bizKind" :disabled="dlg.readOnly" multiple style="width:100%">
                <el-option v-for="o in bizKindOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="账号" prop="accountNo" :required="dlg.form.allocDim === 'ACCOUNT'">
              <el-input v-model="dlg.form.accountNo"
                        :disabled="dlg.readOnly || dlg.form.allocDim !== 'ACCOUNT'"
                        :placeholder="dlg.form.allocDim === 'ACCOUNT' ? '请输入账号（必填）' : '仅按账户分配时可输入'" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 查不到该客户的余额统计时告警（查到则不显示）-->
        <el-alert v-if="dlg.form.custNo && custStat.queried && !custStat.found"
                  type="warning" :closable="false" style="margin-bottom:12px"
                  title="未查询到该客户/账号的余额数据，不允许提交审批" />

        <!-- 余额概览（默认显示，数据来自 XAN_M98_CUST_STAT_SHOW3，按客户编号反显）-->
        <div class="card-h"><div class="title">余额概览</div></div>
        <el-row :gutter="16" style="margin-bottom:4px">
          <el-col :span="6">
            <el-form-item label="当前余额">
              <el-input :model-value="custStat.currBal != null ? custStat.currBal : '-'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="月日均余额">
              <el-input :model-value="custStat.mAvgBal != null ? custStat.mAvgBal : '-'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="季日均余额">
              <el-input :model-value="custStat.qAvgBal != null ? custStat.qAvgBal : '-'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="年日均余额">
              <el-input :model-value="custStat.yAvgBal != null ? custStat.yAvgBal : '-'" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 原业绩分配（来自 getAllocPreview，仅展示当前分配，存贷款余额预览模块已删除）-->
        <div v-if="preview.loaded && preview.data" class="preview-section" v-loading="preview.loading">
          <div class="card-h"><div class="title">原业绩分配</div></div>
          <el-table :data="preview.data.allocList || []" size="small" border style="margin-bottom:12px" empty-text="暂无审批通过的分配记录">
            <el-table-column prop="acctNo" label="账号" min-width="150" show-overflow-tooltip>
              <template #default="{row}">{{ row.acctNo || '-' }}</template>
            </el-table-column>
            <el-table-column label="员工名称" min-width="150" show-overflow-tooltip>
              <template #default="{row}">
                {{ row.username || '-' }}{{ row.empChnName ? '（' + row.empChnName + '）' : '' }}
              </template>
            </el-table-column>
            <el-table-column label="所属机构" min-width="180" show-overflow-tooltip>
              <template #default="{row}">
                {{ row.orgName || '-' }}{{ row.orgCode ? '（' + row.orgCode + '）' : '' }}
              </template>
            </el-table-column>
            <el-table-column prop="ratio" label="分配比例" width="100">
              <template #default="{row}">{{ row.ratio != null && row.ratio !== '' ? row.ratio + '%' : '-' }}</template>
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
          <el-table-column v-if="!dlg.readOnly" label="操作" width="80" align="center">
            <template #default="{$index}">
              <el-button link type="danger" size="small" @click="dlg.form.items.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button v-if="!dlg.readOnly" plain @click="addItemRow" style="margin-top:10px">+ 添加分配人</el-button>

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
                <span>{{ log.operatorName || log.operator || '-' }}</span>
                <span v-if="log.operator && log.operatorName" class="sub-id">({{ log.operator }})</span>
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
          <el-form-item v-if="dlg.reviewRow?.nodeKey === 'biz_dept_review' || dlg.reviewRow?.nodeKey === 'finance_review'" label="下一步审批">
            <el-radio-group v-model="dlg.reviewRouteTo">
              <template v-if="dlg.reviewRow?.nodeKey === 'biz_dept_review'">
                <el-radio value="LEADER">{{ dlg.reviewFlowType === 'RETAIL' ? '交零售部负责人审批' : '交公司部负责人审批' }}</el-radio>
                <el-radio value="OWNER" :disabled="!hasOriginalOwners">交原业绩所属人审批{{ hasOriginalOwners ? '' : '（无原业绩分配，不可选）' }}</el-radio>
              </template>
              <template v-else-if="dlg.reviewRow?.nodeKey === 'finance_review'">
                <el-radio value="LEADER">交资财部负责人审批</el-radio>
                <el-radio value="END">审批结束</el-radio>
              </template>
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
        <el-button v-if="!dlg.readOnly && !dlg.reviewMode" type="primary" :loading="dlg.saving"
                   :disabled="custStat.queried && !custStat.found"
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
        <el-form-item label="下一步审批">
          <el-radio-group v-model="approveDlg.routeTo">
            <template v-if="approveDlg.nodeKey === 'biz_dept_review'">
              <el-radio value="LEADER">{{ approveDlg.flowType === 'RETAIL' ? '交零售部负责人审批' : '交公司部负责人审批' }}</el-radio>
              <el-radio value="OWNER" :disabled="!approveDlg.hasOwners">交原业绩所属人审批{{ approveDlg.hasOwners ? '' : '（无原业绩分配，不可选）' }}</el-radio>
            </template>
            <template v-else-if="approveDlg.nodeKey === 'finance_review'">
              <el-radio value="LEADER">交资财部负责人审批</el-radio>
              <el-radio value="END">审批结束</el-radio>
            </template>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="approveDlg.saving" @click="onApproveSubmit">提交</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { fmtDateTime } from '@/utils/datetime';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  submitAdjust, withdrawAdjust, getAdjustDetail,
  getAdjustApprovalHistory, listMyAdjustTodos, listMyAdjustApplies, listMyAdjustDones,
  getAllocPreview, getCustStat, suggestEmployees
} from '@/api/perf';
import { approveTask, rejectTask, claimTask } from '@/api/workflow';
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
      custType: inferCustType(d.custType, d.bizKind), custNo: d.custNo || d.custId || '',
      allocDim: d.allocDim,
      bizKind: d.bizKind ? (typeof d.bizKind === 'string' ? d.bizKind.split(',') : d.bizKind) : [],
      accountNo: d.accountNo,
      ownerOrgId: d.ownerOrgId, reason: d.reason || d.remark,
      items: (d.items || []).map(it => ({ empId: it.empId, pct: it.pct ?? it.ratio, remark: it.remark, empLabel: empLabelOf(it) }))
    });
    // 申请信息条所需的 dlg 顶层字段（之前漏赋值导致 todo/done tab 查看时申请单号/申请人/机构/时间 全空）
    dlg.applyNo = d.applyNo || '';
    dlg.createdBy = d.createdBy || '';
    dlg.createdByName = d.createdByName || '';
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
  opinion: '同意', routeTo: 'LEADER', nodeKey: '', flowType: 'CORP', hasOwners: false
});
const approveDlgTitle = computed(
  () => `审批通过：${approveDlg.row?.title || approveDlg.row?.businessKey || ''}`
);

async function openApprove(row) {
  if (row.nodeKey === 'biz_dept_review' || row.nodeKey === 'finance_review') {
    approveDlg.row = row;
    approveDlg.opinion = '同意';
    approveDlg.routeTo = 'LEADER';
    approveDlg.nodeKey = row.nodeKey;
    approveDlg.flowType = (row.processDefinitionKey || '').includes('retail') ? 'RETAIL' : 'CORP';
    approveDlg.saving = false;
    // 原业绩分配为空时禁用"交原业绩所属人审批"：拉取该客户当前维度的原业绩分配判断有无所属人
    approveDlg.hasOwners = false;
    if (row.nodeKey === 'biz_dept_review') {
      try {
        const p = await getAllocPreview({ custNo: row.custId || row.custNo, allocDim: row.allocDim });
        approveDlg.hasOwners = !!(p && p.allocList && p.allocList.length);
      } catch { approveDlg.hasOwners = false; }
      if (!approveDlg.hasOwners && approveDlg.routeTo === 'OWNER') approveDlg.routeTo = 'LEADER';
    }
    approveDlg.show = true;
    return;
  }
  // 其他节点：沿用简单意见输入
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
  approveDlg.saving = true;
  try {
    await ensureClaimed(approveDlg.row);
    const varName = approveDlg.nodeKey === 'finance_review' ? 'finRouteTo' : 'corpRouteTo';
    await approveTask(approveDlg.row.taskId, approveDlg.opinion, {
      [varName]: approveDlg.routeTo
    });
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
  dlg.reviewRouteTo = 'LEADER';
  dlg.reviewFlowType = (row.processDefinitionKey || '').includes('retail') ? 'RETAIL' : 'CORP';
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
      let formData;
      if (dlg.reviewRow.nodeKey === 'biz_dept_review') {
        formData = { corpRouteTo: dlg.reviewRouteTo };
      } else if (dlg.reviewRow.nodeKey === 'finance_review') {
        formData = { finRouteTo: dlg.reviewRouteTo };
      }
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

// ============ 业务类型字典 ============
const bizKindOptions = ref([]);
const bizKindMap = computed(() => {
  const m = {};
  for (const o of bizKindOptions.value) m[o.value] = o.label;
  return m;
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
// XAN_M98_CUST_STAT_SHOW3 统计日期 STATIS_DT：
// 查看/审批模式（dlg.createdTime 有值）取 申请日期-1；新建模式取 昨日。均 yyyy-MM-dd。
function custStatStatisDt() {
  const base = dlg.createdTime ? new Date(dlg.createdTime) : new Date(Date.now() - 86400000);
  if (dlg.createdTime) base.setDate(base.getDate() - 1);
  return base.toISOString().slice(0, 10);
}
const preview = reactive({ loaded: false, loading: false, data: null });
// 原业绩分配是否有数据（决定审批时能否选"交原业绩所属人审批"）
const hasOriginalOwners = computed(() => (preview.data && preview.data.allocList && preview.data.allocList.length) > 0);
function fmtAmt(v) {
  if (v == null) return '-';
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
async function loadPreview(statisDt) {
  const f = dlg.form;
  // 原业绩分配按客户编号取 RULE/ACCOUNT 审批通过的最后一条，故只要有客户编号即可加载
  if (!f.custNo) return;
  preview.loaded = true;
  preview.loading = true;
  // 新建时传昨日，查看/审批时由调用方传入申请日期-1
  const dt = statisDt || new Date(Date.now() - 86400000).toISOString().slice(0, 10);
  try {
    preview.data = await getAllocPreview({
      custType: f.custType, custNo: f.custNo, allocDim: f.allocDim, accountNo: f.accountNo || undefined,
      statisDt: dt
    });
  } catch { preview.data = null; }
  finally { preview.loading = false; }
}
const dlg = reactive({
  show: false, readOnly: false, saving: false, viewingId: null,
  reviewMode: false, reviewRow: null, reviewOpinion: '', reviewSaving: false, reviewRouteTo: 'LEADER', reviewFlowType: 'CORP',
  approvalLogs: [], approvalLoading: false,
  applyNo: '', createdBy: '', createdByName: '', createdByOrgName: '', createdTime: null,
  form: {
    custType: 'CORP', custNo: '', allocDim: 'RULE', bizKind: 'CORP_DEPOSIT',
    accountNo: '', ownerOrgId: '', reason: '',
    items: [{ empId: '', pct: 100, remark: '', empLabel: '' }]
  }
});
const dlgTitle = computed(() => dlg.reviewMode ? '审批调整申请' : dlg.readOnly ? '查看调整申请' : '新建调整申请');
const totalPct = computed(() => dlg.form.items.reduce((s, x) => s + (Number(x.pct) || 0), 0));
const dlgRules = {
  custType:   [{ required: true, message: '请选择客户类型' }],
  custNo:     [{ required: true, message: '请填写客户编号' }],
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

function defaultItem() { return { empId: '', pct: 0, remark: '', empLabel: '' }; }
function addItemRow() { dlg.form.items.push(defaultItem()); }

// 分配明细员工号自动补齐：按工号/登录名/中文名模糊匹配 PT_USER
// 下拉项 label = "username（中文名）"，作为输入框显示内容；empId 另存干净值用于提交
async function queryEmpSuggest(queryString, cb) {
  const kw = (queryString || '').trim();
  if (!kw) { cb([]); return; }
  try {
    const list = await suggestEmployees(kw);
    const arr = Array.isArray(list) ? list : [];
    cb(arr.map(u => ({ ...u, label: u.empChnName ? `${u.username}（${u.empChnName}）` : u.username })));
  } catch { cb([]); }
}
// 选中下拉项：输入框显示 label，empId 存登录名（干净值，供提交/校验/原业绩分配解析）
function onEmpSelect(row, item) {
  row.empId = item.username || item.empId || '';
  row.empLabel = item.label || row.empId;
}
// 自由输入（未从下拉选择）时同步 empId；已选项 label 含「（」则跳过，避免覆盖干净值
function onEmpInput(row, val) {
  if (!val) { row.empId = ''; return; }
  if (!String(val).includes('（')) { row.empId = String(val).trim(); }
}
// 明细员工号显示文案：优先 username（中文名），无则回退工号（查看/审批页详情已带 username/empChnName）
function empLabelOf(it) {
  if (it && it.username) return it.username + (it.empChnName ? '（' + it.empChnName + '）' : '');
  return (it && it.empId) || '';
}
function onDlgClosed() {
  dlg.viewingId = null;
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

function onAllocDimChange(val) {
  if (val !== 'ACCOUNT') {
    dlg.form.accountNo = '';
  }
}
function openCreate() {
  dlg.readOnly = false;
  dlg.viewingId = null;
  custNameDisplay.value = '';
  resetCustStat();
  preview.loaded = false;
  preview.data = null;
  Object.assign(dlg.form, {
    custType: 'CORP', custNo: '', allocDim: 'RULE', bizKind: [],
    accountNo: '', ownerOrgId: '', reason: '',
    items: [{ empId: '', pct: 100, remark: '', empLabel: '' }]
  });
  dlg.show = true;
}
async function openView(row) {
  dlg.readOnly = true;
  dlg.viewingId = row.id || row.applyNo;
  dlg.approvalLogs = [];
  Object.assign(dlg.form, {
    custType: inferCustType(row.custType, row.bizKind), custNo: row.custNo || row.custId || '',
    allocDim: row.allocDim || 'RULE',
    bizKind: row.bizKind ? (typeof row.bizKind === 'string' ? row.bizKind.split(',') : row.bizKind) : [],
    accountNo: row.accountNo || '',
    ownerOrgId: row.ownerOrgId || '',
    reason: row.reason || row.remark || '',
    items: row.items?.length ? row.items.map(it => ({ empId: it.empId, pct: it.pct ?? it.ratio, remark: it.remark || '', empLabel: empLabelOf(it) })) : [{ empId: '', pct: 100, remark: '' }]
  });
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
        custType: d.custType || '', custNo: d.custNo || d.custId, allocDim: d.allocDim,
        bizKind: d.bizKind ? (typeof d.bizKind === 'string' ? d.bizKind.split(',') : d.bizKind) : [],
        accountNo: d.accountNo, ownerOrgId: d.ownerOrgId, reason: d.reason || d.remark || '',
        items: (d.items || []).map(it => ({ empId: it.empId, pct: it.pct ?? it.ratio, remark: it.remark || '', empLabel: empLabelOf(it) }))
      });
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

  dlg.saving = true;
  try {
    await submitAdjust({
      custType:   dlg.form.custType,
      custNo:     dlg.form.custNo,
      allocDim:   dlg.form.allocDim,
      bizKind:    Array.isArray(dlg.form.bizKind) ? dlg.form.bizKind.join(',') : dlg.form.bizKind,
      accountNo:  dlg.form.accountNo || undefined,
      ownerOrgId: dlg.form.ownerOrgId || userStore.user?.mainOrgCode || userStore.user?.orgCode || '',
      reason:     dlg.form.reason,
      items: dlg.form.items.map(it => ({ empId: it.empId, ratio: Number(it.pct), remark: it.remark || '' }))
    });
    ElMessage.success('已提交审批');
    dlg.show = false;
    reloadMine();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '提交失败');
  } finally { dlg.saving = false; }
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

// 客户编号变化时查询客户名称
let custNoTimer = null;
watch(() => dlg.form.custNo, (val) => {
  clearTimeout(custNoTimer);
  if (!val || val.length < 2) { custNameDisplay.value = ''; resetCustStat(); preview.loaded = false; return; }
  custNoTimer = setTimeout(async () => {
    try {
      // 客户名称 + 余额均从 XAN_M98_CUST_STAT_SHOW3 按客户编号反显（列名为 DB 大写）
      // 统计日期 STATIS_DT：新建=昨日 / 查看·审批=申请日期-1
      const s = await getCustStat(val, custStatStatisDt());
      if (s) {
        custNameDisplay.value = s.CUST_NAME || s.cust_name || '';
        custStat.currBal = s.CURR_BAL ?? null;
        custStat.mAvgBal = s.M_AVG_BAL ?? null;
        custStat.qAvgBal = s.Q_AVG_BAL ?? null;
        custStat.yAvgBal = s.Y_AVG_BAL ?? null;
        custStat.found = true;
      } else {
        custNameDisplay.value = '';
        resetCustStat();
      }
      custStat.queried = true;
    } catch { custNameDisplay.value = ''; resetCustStat(); custStat.queried = true; }
  }, 500);
});
// 客户编号 / 分配维度变化时触发原业绩分配预览（ACCOUNT 维度只查按账号分配的最后一条）
let previewTimer = null;
watch(
  () => [dlg.form.custNo, dlg.form.allocDim],
  () => {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(() => {
      const f = dlg.form;
      if (f.custNo && f.custNo.length >= 2) {
        loadPreview();
      } else {
        preview.loaded = false;
      }
    }, 600);
  }
);

onMounted(async () => {
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
  if (isAutoOpen) {
    await reloadTodo();
    const row = todos.value.find(t => t.taskId === route.query.taskId);
    if (row) {
      openTodoReview(row);
    } else {
      ElMessage.warning('任务已处理或不在当前页');
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
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.sub-id { font-size: 12px; color: $text-3; margin-left: 4px; }

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
</style>
