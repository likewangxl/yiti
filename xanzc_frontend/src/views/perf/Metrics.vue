<template>
  <div>
    <div class="page-h">
      <h1>指标库</h1>
      <span class="desc"></span>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button @click="triggerImport">📥 导入指标</el-button>
        <el-button @click="downloadTemplate">📄 下载模板</el-button>
        <input ref="fileInputRef" type="file" accept=".xlsx,.xls" style="display:none" @change="onFileSelected" />
        <el-button type="primary" @click="openCreate">+ 新增指标</el-button>
      </div>
    </div>

    <div class="layout">
      <!-- 左：分类树 -->
      <div class="card-section tree-col">
        <div class="card-h-mini">指标层级</div>
        <el-input
          v-model="treeKeyword"
          placeholder="搜索：指标名称 / 编号 / 分类"
          size="small"
          clearable
          :prefix-icon="Search"
          class="tree-search"
        />
        <el-tree
          ref="treeRef"
          :data="treeData"
          node-key="id"
          :default-expanded-keys="expandedKeys"
          :expand-on-click-node="false"
          :highlight-current="true"
          :current-node-key="picked"
          :filter-node-method="filterTreeNode"
          @node-click="onTreeClick"
          @node-expand="onNodeExpand"
          @node-collapse="onNodeCollapse"
          empty-text="暂无指标"
        >
          <template #default="{ node, data }">
            <span class="tree-node">
              <span v-if="!data.isMetric" class="ico">📁</span>
              <span v-if="data.isMetric && data.raw?.metricLevel != null"
                    class="lvl-badge" :class="'lvl-' + data.raw.metricLevel">{{ data.raw.metricLevel }}</span>
              <span :class="{ 'tree-leaf': data.isMetric }">{{ node.label }}</span>
              <el-tag v-if="data.isMetric" :class="statusCls(data.status)" effect="plain" size="small" class="tree-tag">
                {{ statusLabel(data.status) }}
              </el-tag>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右：详情 + 同分类 -->
      <div class="card-section detail-col" v-loading="detailLoading">
        <div v-if="!detail.metricCode" class="empty-pane">← 在左侧选择任一指标查看详情</div>
        <template v-else>
          <div class="card-h">
            <div class="title">指标详情 · {{ detail.metricName }}</div>
            <el-tag :class="statusCls(detail.status)" effect="plain">{{ statusLabel(detail.status) }}</el-tag>
            <span class="version">{{ detail.version || `v${detail.metricLevel || 1}` }}</span>
          </div>

          <table class="meta-table">
            <tr>
              <td class="lab">编码</td><td class="val"><code class="mono">{{ detail.metricCode }}</code></td>
              <td class="lab">分类</td><td class="val">{{ (detail.metricCategory && String(detail.metricCategory).trim()) || resolveCategory(detail) }}</td>
            </tr>
            <tr>
              <td class="lab">维度</td><td class="val">{{ { EMP:'员工', ORG:'机构', CUST:'客户' }[detail.baseDim] || detail.baseDim || '-' }}</td>
              <td class="lab">层级</td><td class="val">{{ detail.metricLevel != null ? detail.metricLevel + ' 级' : '-' }}</td>
            </tr>
            <tr>
              <td class="lab">宽表槽位</td>
              <td class="val">{{ detail.valSlot != null ? detail.valSlot : '-' }}</td>
              <td class="lab">计算方式</td><td class="val">{{ { AUTO:'自动', MANUAL:'手动' }[detail.calcMode] || detail.calcMode || '-' }}</td>
            </tr>
            <tr>
              <td class="lab">计算逻辑</td>
              <td class="val">
                <el-tag :class="logicCls(detail.calcLogicType)" effect="plain">{{ { SQL:'SQL', EXPR:'Groovy' }[detail.calcLogicType] || detail.calcLogicType || '-' }}</el-tag>
              </td>
              <td class="lab">创建人</td><td class="val">{{ personLabel(detail.createdByUsername, detail.createdByName, detail.createdBy) }}</td>
            </tr>
            <tr>
              <td class="lab">更新人</td><td class="val">{{ personLabel(detail.updatedByUsername, detail.updatedByName, detail.updatedBy) }}</td>
              <td class="lab">更新时间</td><td class="val">{{ fmtTime(detail.updatedTime || detail.createdTime) }}</td>
            </tr>
            <tr v-if="detail.description">
              <td class="lab">详细描述</td><td class="val" colspan="3">{{ detail.description }}</td>
            </tr>
          </table>

          <!-- 计算公式：SQL / EXPR / SUMMARY 三选一；若后端字段为空，显示占位块（不让模板看起来缺一栏） -->
          <template v-if="detail.calcLogicType === 'SQL'">
            <div class="block-h">SQL 表达式</div>
            <pre class="code">{{ detail.sqlText || '-- 暂未配置 SQL，可点【编辑】补充' }}</pre>
          </template>
          <template v-else-if="detail.calcLogicType === 'EXPR'">
            <div class="block-h">Groovy 表达式</div>
            <pre class="code">{{ detail.exprDisplay || detail.exprText || '// 暂未配置表达式，可点【编辑】补充' }}</pre>
          </template>
          <template v-else-if="detail.calcLogicType === 'SUMMARY'">
            <div class="block-h">SUMMARY 汇总规则</div>
            <pre class="code">{{ detail.summaryRule || '/* 暂未配置汇总规则 */' }}</pre>
          </template>
          <template v-else>
            <div class="block-h">计算逻辑</div>
            <pre class="code">{{ '/* 计算方式未指定 */' }}</pre>
          </template>

          <div class="acts">
            <el-button type="primary" :disabled="isDisabled" @click="openEdit(detail)">编辑</el-button>
            <el-button :disabled="isDisabled" @click="onExecute">⚡立即执行</el-button>
            <el-button @click="onShowVersions">查看版本历史</el-button>
            <el-button @click="onViewAudit">📋 查看审计</el-button>
            <el-button v-if="!isDisabled" type="warning" plain @click="onChangeStatus('DISABLED')">停用</el-button>
            <el-button v-else type="success" plain @click="onChangeStatus('ACTIVE')">启用</el-button>
          </div>
          <div class="audit-hint">
            ⚠ 立即执行 / 编辑 / 状态变更 均属高危动作，会自动写入「系统设置 → 审计日志」
          </div>

          <!-- 详情侧"试运行"结果区（仅在按过试运行后才出现） -->
          <div v-if="detailTrial.status" class="trial-detail">
            <div class="trial-row">
              <span class="trial-title">试运行结果</span>
              <el-tag v-if="detailTrial.status === 'SUCCESS'" class="tag-success" effect="plain">
                成功 · {{ detailTrial.totalRows ?? detailTrial.rows.length }} 行 · {{ ((detailTrial.cost || 0) / 1000).toFixed(1) }}s
              </el-tag>
              <el-tag v-else-if="detailTrial.status === 'FAILED'" class="tag-warning" effect="plain">
                失败 · {{ ((detailTrial.cost || 0) / 1000).toFixed(1) }}s
              </el-tag>
            </div>
            <!-- SQL 类指标：样本行表格；EXPR 类指标：单值（exprResult） -->
            <el-table v-if="detailTrial.rows.length" :data="detailTrial.rows" size="small" border style="margin-top: 8px">
              <el-table-column v-for="col in detailTrial.cols" :key="col" :prop="col" :label="col" min-width="140" show-overflow-tooltip />
            </el-table>
            <div v-else-if="detailTrial.status === 'SUCCESS' && detailTrial.exprResult != null"
                 class="trial-expr" style="margin-top:8px">
              EXPR 单值结果：<code class="mono">{{ detailTrial.exprResult }}</code>
            </div>
            <div v-else-if="detailTrial.status === 'FAILED'" class="trial-error" style="margin-top:8px">
              ✗ {{ detailTrial.errorMsg || '试运行失败，请检查 SQL/EXPR 是否合法' }}
            </div>
            <div v-else class="trial-empty" style="margin-top:8px; color:#999">
              （无样本数据）
            </div>
          </div>

          <div class="block-h">同分类指标</div>
          <el-table :data="sameCategory" size="default" border empty-text="—">
            <el-table-column label="编码" prop="metricCode" width="100">
              <template #default="{row}"><code class="mono">{{ row.metricCode }}</code></template>
            </el-table-column>
            <el-table-column label="名称" prop="metricName" min-width="160" show-overflow-tooltip />
            <el-table-column label="计算" width="90">
              <template #default="{row}"><el-tag :class="logicCls(row.calcLogicType)" effect="plain" size="small">{{ { SQL:'SQL', EXPR:'Groovy' }[row.calcLogicType] || row.calcLogicType }}</el-tag></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{row}"><el-tag :class="statusCls(row.status)" effect="plain" size="small">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="版本" width="60" align="center">
              <template #default>v1</template>
            </el-table-column>
            <el-table-column label="更新" width="110" align="center">
              <template #default>{{ today }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="onPick(row.metricCode)">查看</el-button>
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </div>

    <!-- 编辑/新增 弹框 -->
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑指标 · ' + dlg.form.metricName : '新增指标'"
      width="780px" top="5vh" @closed="dlg.editing = null" @opened="onExprDialogOpened">
      <el-form ref="formRef" :model="dlg.form" :rules="formRules" label-position="top" size="default">
        <div class="form-grid">
          <el-form-item label="编码" prop="metricCode" required>
            <el-input v-model="dlg.form.metricCode" :disabled="!!dlg.editing" placeholder="如 M_0001" maxlength="20" />
          </el-form-item>
          <el-form-item label="名称" prop="metricName" required>
            <el-input v-model="dlg.form.metricName" maxlength="100" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="dlg.form._category" style="width:100%">
              <el-option v-for="c in CATEGORY_OPTIONS" :key="c" :value="c" :label="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="基础维度" prop="baseDim" required>
            <el-select v-model="dlg.form.baseDim" style="width:100%">
              <el-option value="EMP" label="员工" />
              <el-option value="ORG" label="机构" />
              <el-option value="CUST" label="客户" />
            </el-select>
          </el-form-item>
          <el-form-item label="指标层级" prop="metricLevel" required>
            <el-select v-model="dlg.form.metricLevel" style="width:100%">
              <el-option :value="1" label="1 级" />
              <el-option :value="2" label="2 级" />
              <el-option :value="3" label="3 级" />
            </el-select>
          </el-form-item>
          <el-form-item label="计算方式" prop="calcMode" required>
            <el-select v-model="dlg.form.calcMode" style="width:100%">
              <el-option value="AUTO" label="自动" />
              <el-option value="MANUAL" label="手动" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="指标详细描述">
          <el-input v-model="dlg.form.description" type="textarea" :rows="2"
                    placeholder="指标的业务含义、计算口径、数据来源等详细描述" maxlength="500" show-word-limit />
        </el-form-item>

        <el-form-item label="计算逻辑" prop="calcLogicType">
          <el-radio-group v-model="dlg.form.calcLogicType" disabled>
            <el-radio value="SQL">SQL</el-radio>
            <el-radio value="EXPR">Groovy</el-radio>
          </el-radio-group>
          <!-- EXPR 模式下，指标下拉 + 添加按钮与计算逻辑同行：选中指标点【添加】插入到表达式光标处 -->
          <template v-if="dlg.form.calcLogicType === 'EXPR'">
            <el-select v-model="exprPickCode" filterable clearable placeholder="选择指标插入表达式"
                       style="width:240px;margin-left:16px">
              <el-option v-for="m in parentMetricOptions" :key="m.metricCode"
                         :value="m.metricCode" :label="`${m.metricName}（${m.metricCode}）`" />
            </el-select>
            <!-- 取值时间：为该次引用指定取历史哪一天的指标结果（今日/昨日/上月末/上季末/上年末） -->
            <el-select v-model="exprPickTime" style="width:110px;margin-left:8px" title="取值时间">
              <el-option v-for="t in VALUE_TIME_OPTIONS" :key="t.value" :value="t.value" :label="t.label" />
            </el-select>
            <el-button type="primary" style="margin-left:8px" :disabled="!exprPickCode"
                       @click="insertMetricChip">添加</el-button>
            <el-button style="margin-left:8px" title="插入安全除法：除数为 0 时结果取 0"
                       @click="insertDiv">÷ 安全除</el-button>
          </template>
          <div v-if="dlg.form.metricLevel === 1" style="font-size:12px;color:#999;margin-top:2px">1级指标仅支持SQL</div>
        </el-form-item>

        <template v-if="dlg.form.calcLogicType === 'EXPR'">
          <el-form-item label="Groovy 表达式">
            <div class="expr-edit-wrap">
              <!-- 可编辑表达式区：指标以标签插入（可删除），运算符/数字/括号可直接键入 -->
              <div ref="exprEditorRef" class="expr-editor" contenteditable="true"
                   @input="syncExprText" @click="onExprEditorClick"
                   data-placeholder="从上方选择指标点【添加】插入指标标签；运算符（+ - * / ( )）与数字可直接键入，例如：指标A + 指标B * 2"></div>
              <div class="expr-hint">指标以标签形式嵌入，点标签上的 × 可删除；其余位置可自由编辑运算符与数字。</div>
            </div>
          </el-form-item>
        </template>
        <el-form-item v-else :label="'SQL 表达式 (支持 #{slot} 占位符)'">
          <el-input
            ref="sqlInputRef"
            v-model="dlg.form.sqlText" type="textarea" :rows="6"
            placeholder="SELECT cust_id, AVG(bal) FROM t_xxx WHERE dt=:dataDate"
          />
        </el-form-item>

        <el-form-item v-if="dlg.form.calcLogicType === 'SQL'" label="">
          <div class="sql-date-macros">
            <div class="hint-title">可用日期变量（点击插入到 SQL 光标处；后端按 dataDate 自动计算注入）</div>
            <table class="hint-table">
              <tr><th style="width:180px">SQL 占位符</th><th>含义</th></tr>
              <tr v-for="m in DATE_MACROS" :key="m.token">
                <td>
                  <code class="macro-btn" @click="insertMacro(m.token)" :title="`点击插入 ${m.token}`">{{ m.token }}</code>
                </td>
                <td>{{ m.desc }}</td>
              </tr>
            </table>
            <div class="hint-foot">用法：<code>WHERE stat_date = :datePrevMonthEnd</code>。结果列必须含 <code>base_key</code> + <code>metric_value</code>。</div>
          </div>
        </el-form-item>

        <el-form-item label="试运行">
          <div class="trial-row">
            <el-date-picker v-model="dlg.trialDate" type="date" value-format="YYYY-MM-DD"
              placeholder="数据日期（传给 SQL :dataDate）" style="flex:1; min-width:220px" />
            <!-- 对象值：普通输入框，输入什么传什么，直接映射 SQL :objectId（不做联想） -->
            <el-input
              v-model="dlg.trialSubject"
              clearable
              :placeholder="dlg.form.baseDim === 'EMP' ? '对象值：员工工号 → :objectId' : (dlg.form.baseDim === 'ORG' ? '对象值：机构编号 → :objectId' : '对象值 → :objectId')"
              style="flex:1; min-width:240px" />
            <el-button type="primary" @click="onTrialFromDialog" :loading="dlg.trialing"
                       :disabled="metricEditBlocked" :title="metricEditBlocked ? '指标数据加载完成后可用' : ''">▶ 试运行</el-button>
            <span v-if="metricEditBlocked" style="margin-left:8px; color:#e6a23c; font-size:12px">指标数据加载中…</span>
            <el-tag v-if="dlg.trial.status === 'SUCCESS'" class="tag-success" effect="plain">
              成功 · {{ dlg.trial.exprResult != null ? ('结果 ' + dlg.trial.exprResult) : ((dlg.trial.totalRows ?? dlg.trial.rows.length) + ' 行') }} · {{ ((dlg.trial.cost || 0) / 1000).toFixed(1) }}s
            </el-tag>
            <el-tag v-else-if="dlg.trial.status === 'FAILED'" class="tag-warning" effect="plain">
              失败 · {{ ((dlg.trial.cost || 0) / 1000).toFixed(1) }}s
            </el-tag>
          </div>

          <!-- 试运行结果：SQL 类→样本行表格；EXPR(Groovy) 类→单值（exprResult） -->
          <el-table v-if="dlg.trial.rows.length" :data="dlg.trial.rows" size="small" border style="margin-top: 12px">
            <el-table-column v-for="col in dlg.trial.cols" :key="col" :prop="col" :label="col" min-width="140" show-overflow-tooltip />
          </el-table>
          <div v-else-if="dlg.trial.status === 'SUCCESS' && dlg.trial.exprResult != null"
               class="trial-expr" style="margin-top: 12px">
            <div>计算结果：<code class="mono">{{ dlg.trial.exprResult }}</code></div>
            <!-- 列出 Groovy 计算用到的用户指标数据（含命中数据版本），方便核对结果为何是该值 -->
            <div v-if="exprVarList(dlg.trial.exprVars).length" class="trial-vars" style="margin-top: 8px">
              <div style="color:#909399; margin-bottom:4px">
                Groovy 用到的指标取值<span v-if="dlg.trial.dataVersion">（数据版本 {{ dlg.trial.dataVersion }}）</span>：
              </div>
              <div v-for="v in exprVarList(dlg.trial.exprVars)" :key="v.code" style="line-height:1.9">
                <code class="mono">{{ v.code }}</code><span v-if="v.name" style="color:#909399"> · {{ v.name }}</span>
                ＝ <code class="mono">{{ v.value }}</code>
                <span v-if="Number(v.value) === 0" style="color:#e6a23c">（该对象/日期宽表中无此指标数据，取 0）</span>
              </div>
            </div>
          </div>
          <div v-else-if="dlg.trial.status === 'FAILED'" class="trial-error">
            ✗ {{ dlg.trial.errorMsg || '试运行失败，请检查 SQL/Groovy 表达式是否合法' }}
          </div>
        </el-form-item>

        <!-- 隐含字段（不让用户暴露太多复杂度） -->
      </el-form>
      <template #footer>
        <span v-if="metricEditBlocked" style="margin-right:12px; color:#e6a23c; font-size:12px">指标数据加载中，请稍候…</span>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button :loading="dlg.saving" :disabled="metricEditBlocked"
                   :title="metricEditBlocked ? '指标数据加载完成后可用' : ''" @click="onSave('DRAFT')">保存为草稿</el-button>
        <el-button type="primary" :loading="dlg.saving" :disabled="metricEditBlocked"
                   :title="metricEditBlocked ? '指标数据加载完成后可用' : ''" @click="onSave('ACTIVE')">发布</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史（复用审计日志） -->
    <el-dialog v-model="versionDlg.show" title="版本历史" width="720px">
      <el-table :data="versionDlg.list" size="default" empty-text="暂无变更记录" v-loading="versionDlg.loading">
        <el-table-column label="版本" prop="version" width="70" align="center" />
        <el-table-column label="动作" width="120">
          <template #default="{row}">
            <el-tag :class="bizActionCls(row.bizAction)" effect="plain">{{ bizActionLabel(row.bizAction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更人" min-width="120">
          <template #default="{row}">{{ row.empName || row.empId || '—' }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="180" show-overflow-tooltip>
          <template #default="{row}">{{ row.reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="变更时间" prop="createdTime" width="170" :formatter="fmtDateTimeCol" />
      </el-table>
    </el-dialog>

    <!-- 立即执行对话框：日期 + 原因合并到一页 -->
    <el-dialog v-model="execDlg.show" title="立即执行" width="520px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="指标编码">
          <el-input v-model="execDlg.metricCode" readonly />
        </el-form-item>
        <el-form-item label="数据日期" required>
          <el-date-picker
            v-model="execDlg.dataDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择数据日期（不能大于今天）"
            :disabled-date="execDlg.disabledDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="业绩分配日期">
          <el-date-picker
            v-model="execDlg.allocDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="SQL 的 :allocDate（留空默认=数据日期）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="执行原因" required>
          <el-input v-model="execDlg.reason" type="textarea" :rows="3" placeholder="高危操作，必填执行原因" />
        </el-form-item>
        <div class="audit-hint" style="font-size:12px; color:#999; margin-left:100px">
          ⚠ 将写入宽表 + run_task，自动记入审计日志
        </div>
      </el-form>
      <template #footer>
        <el-button @click="execDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="execDlg.submitting" @click="confirmExecute">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { Search } from '@element-plus/icons-vue';
import { fmtDateTimeCol } from '@/utils/datetime';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listMetrics, listMetricCategories, getMetricDetail,
  createMetric, updateMetric, deleteMetric,
  changeMetricStatus, trialRunMetric, executeMetric,
  uploadImportFile
} from '@/api/perf';
import { listAuditLogs } from '@/api/system';

const router = useRouter();

// === 常量 ===
const CATEGORY_OPTIONS = ['规模类', '效益类', '质量类', '合规类'];
const SLOT_TYPES = ['DATE', 'STRING', 'STRING[]', 'INTEGER', 'DECIMAL'];
const today = new Date().toISOString().slice(0, 10);

// 后端真实枚举：ACTIVE / DRAFT / DISABLED；INACTIVE 是历史遗留兼容
const statusCls = (s) => ({ ACTIVE: 'tag-success', DRAFT: 'tag-info', DISABLED: 'tag-warning', INACTIVE: 'tag-warning' }[s] || 'tag-info');
const statusLabel = (s) => ({ ACTIVE: '已发布', DRAFT: '草稿', DISABLED: '停用', INACTIVE: '停用' }[s] || s || '-');
const logicCls = (t) => ({ SQL: 'tag-info', EXPR: 'tag-warning', SUMMARY: 'tag-success' }[t] || 'tag-info');
const bizActionCls = (a) => {
  if (a === 'DELETE' || a === 'METRIC_EXECUTE') return 'tag-danger';
  if (a === 'STATUS_CHANGE' || a === 'METRIC_TRIAL_RUN') return 'tag-warning';
  if (a === 'CREATE' || a === 'UPDATE') return 'tag-success';
  return 'tag-info';
};
// V1.6 版本历史动作中文化
const BIZ_ACTION_LABEL = {
  CREATE:           '新增',
  UPDATE:           '编辑',
  DELETE:           '删除',
  STATUS_CHANGE:    '状态变更',
  METRIC_TRIAL_RUN: '试运行',
  METRIC_EXECUTE:   '立即执行',
  SLOT_RELEASE:     '释放槽位',
  PUBLISH:          '发布',
  READ:             '查看',
  LIST:             '查询',
  EXPORT:           '导出',
  IMPORT:           '导入',
  CONFIG:           '配置',
  RECALC:           '重算',
  PERMISSION_CHANGE: '权限变更',
  EXECUTE_SQL:      'SQL 执行',
  TRANSFER:         '转移',
  APPROVE:          '审批通过',
  REJECT:           '审批驳回',
  EXECUTE:          '执行'
};
const bizActionLabel = (a) => BIZ_ACTION_LABEL[a] || a || '—';

// === 数据加载 ===
const UNCATEGORIZED_LABEL = '未分类';
const allMetrics = ref([]);
const categories = ref([]);  // [{value, label}] 来自 GET /api/perf/metrics/categories
// 指标列表加载态：加载中 / 是否已成功加载过。编辑指标的保存/发布/试运行都依赖 allMetrics
// 解析引用指标标签与校验上级指标，未加载完成时禁止这些操作，避免按"空列表"误判引用非法。
const metricsLoading = ref(false);
const metricsLoaded = ref(false);
async function reload() {
  metricsLoading.value = true;
  try {
    const [r, cs] = await Promise.all([
      listMetrics({ pageSize: 100 }),
      listMetricCategories()
    ]);
    // 停用项保留在树里（按用户反馈），通过按钮 disable 限制操作即可
    if (Array.isArray(r)) allMetrics.value = r;
    categories.value = Array.isArray(cs) ? cs : [];
    metricsLoaded.value = true;
    // 初始/刷新/新增：默认展开全部维度与分类（编辑保存不走 reload，故不会重置用户已调整的展开态）
    expandedKeys.value = collectFolderKeys();
    // 默认选中第一个指标
    if (allMetrics.value.length && !picked.value) onPick(allMetrics.value[0].metricCode);
  } catch {
    // 加载失败保持 metricsLoaded 原值（首次失败则仍为 false，继续禁止编辑保存）
  } finally {
    metricsLoading.value = false;
  }
}
// 编辑指标弹框里"保存/发布/试运行"是否应禁用：列表加载中或尚未成功加载
const metricEditBlocked = computed(() => metricsLoading.value || !metricsLoaded.value);
// 列表晚于弹框加载完成时，重渲染表达式编辑器，把已存 exprText 里的指标编号还原成标签
watch(metricsLoaded, (v) => {
  if (v && dlg.show && dlg.form.calcLogicType === 'EXPR') nextTick(renderExprEditor);
});

// === 分类规则：优先 metricDesc(JSON)._category，其次按名称关键词推断 ===
function categoryOf(m) {
  const meta = parseMeta(m);
  if (meta && meta._category) {
    const [g, sub] = meta._category.split('/');
    return [g, sub || null];
  }
  const n = (m.metricName || '') + ' ' + (m.metricDesc || '');
  if (/不良|风险/.test(n)) return ['风险指标', '风险类'];
  if (/中收|手续费|fee/i.test(n) || m.metricCode === 'M_FEE') return ['业务指标', '中收类'];
  if (/存款/.test(n)) return ['业务指标', '存款类'];
  if (/贷款/.test(n)) return ['业务指标', '贷款类'];
  if (/客户|cust/i.test(n) || m.baseDim === 'CUST') return ['客户指标', null];
  return ['综合指标', null];
}
function resolveCategory(m) {
  const [g, sub] = categoryOf(m);
  return sub ? `${g}/${sub}` : g;
}

// === 树结构：三级层次「维度 - 指标分类 - 指标」===
// 维度展示名 + 排序（EMP/ORG/CUST 优先，维度无关型排最后）
const DIM_LABEL = { EMP: '员工', ORG: '机构', CUST: '客户', NONE: '维度无关' };
const DIM_ORDER = ['EMP', 'ORG', 'CUST'];
const treeData = computed(() => {
  // 维度节点 Map；每个维度节点内再用 _catMap 暂存「分类label → 分类节点」
  const dimMap = new Map();
  const getDim = (dimKey) => {
    let d = dimMap.get(dimKey);
    if (!d) {
      d = { id: 'dim-' + dimKey, label: DIM_LABEL[dimKey] || dimKey, isDim: true, children: [], _catMap: new Map() };
      dimMap.set(dimKey, d);
    }
    return d;
  };
  const getCat = (dimNode, dimKey, cat) => {
    let c = dimNode._catMap.get(cat);
    if (!c) {
      c = { id: `dim-${dimKey}-cat-${cat}`, label: cat, children: [] };
      dimNode._catMap.set(cat, c);
      dimNode.children.push(c);
    }
    return c;
  };
  // 逐条指标挂到 维度 → 分类 → 指标；空维度归 NONE，空分类归"未分类"
  for (const m of allMetrics.value) {
    const dimKey = (m?.baseDim && String(m.baseDim).trim()) || 'NONE';
    const cat = (m?.metricCategory && String(m.metricCategory).trim()) || UNCATEGORIZED_LABEL;
    const catNode = getCat(getDim(dimKey), dimKey, cat);
    catNode.children.push({
      id: m.metricCode, label: m.metricName,
      isMetric: true, status: m.status, raw: m
    });
  }
  // 维度排序：EMP/ORG/CUST 在前，未知维度居中，维度无关(NONE)最后
  const dims = Array.from(dimMap.values());
  const rank = (node) => {
    const k = node.id.replace(/^dim-/, '');
    if (k === 'NONE') return 999;
    const i = DIM_ORDER.indexOf(k);
    return i === -1 ? 500 : i;
  };
  dims.sort((a, b) => rank(a) - rank(b));
  // 同一分类下的指标按「指标名称」中文升序排列（叶子节点 label = metricName）
  dims.forEach(d => {
    (d.children || []).forEach(cat => {
      if (Array.isArray(cat.children)) {
        cat.children.sort((a, b) =>
          String(a.label || '').localeCompare(String(b.label || ''), 'zh'));
      }
    });
    delete d._catMap; // 清掉临时索引，避免污染节点数据
  });
  return dims;
});

// 树展开态：用受控 default-expanded-keys 取代 default-expand-all，
// 否则 treeData 每次重算（如编辑保存原地改 allMetrics）都会被强制全展开、丢失用户折叠态。
const expandedKeys = ref([]);
function onNodeExpand(data) {
  if (data?.id != null && !expandedKeys.value.includes(data.id)) expandedKeys.value.push(data.id);
}
function onNodeCollapse(data) {
  if (data?.id == null) return;
  const i = expandedKeys.value.indexOf(data.id);
  if (i > -1) expandedKeys.value.splice(i, 1);
}
// 收集全部「维度/分类」文件夹节点 key（叶子=指标不入展开态）
function collectFolderKeys() {
  const keys = [];
  for (const dim of treeData.value) {
    if (dim?.id != null) keys.push(dim.id);
    for (const cat of (dim.children || [])) if (cat?.id != null) keys.push(cat.id);
  }
  return keys;
}

// === 详情 ===
const picked = ref('');
const detail = ref({});
// 创建人/更新人展示：优先 "username（中文名）"，缺中文名退化为 username，再缺退回原始 empId
function personLabel(username, chnName, raw) {
  if (username) return chnName ? `${username}（${chnName}）` : username;
  return raw || '-';
}
// LocalDateTime ISO 串（2026-05-31T18:30:00）转友好展示
function fmtTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 19);
}
// 详情侧"试运行"按钮的结果展示（与编辑对话框里的 dlg.trial 独立，避免互相覆盖）
const detailTrial = reactive({ status: '', cost: 0, totalRows: 0, errorMsg: '', rows: [], cols: [], loading: false });
function resetDetailTrial() {
  Object.assign(detailTrial, { status: '', cost: 0, totalRows: 0, errorMsg: '', rows: [], cols: [], loading: false });
}
// V1.6 修复 Bug3：停用态下"编辑/试运行/立即执行"按钮 disabled，前端先拦截，后端兜底校验
const isDisabled = computed(() => detail.value.status === 'DISABLED' || detail.value.status === 'INACTIVE');
const sameCategory = computed(() => {
  if (!detail.value.metricCode) return [];
  const [g] = categoryOf(detail.value);
  return allMetrics.value.filter(m => categoryOf(m)[0] === g && m.metricCode !== detail.value.metricCode).slice(0, 10);
});

const detailLoading = ref(false);
async function onPick(code) {
  if (!code) return;
  picked.value = code;
  resetDetailTrial();
  detailLoading.value = true;
  try {
    const r = await getMetricDetail(code);
    if (r) detail.value = r;
  } catch {}
  finally { detailLoading.value = false; }
}
function onTreeClick(node) {
  if (node.isMetric) onPick(node.id);
}

// === 槽位声明 / 分类元数据：从 metricDesc(JSON) 解析，否则按 SQL 文本兜底 ===
function parseMeta(m) {
  const txt = m.metricDesc || m.description || '';
  if (typeof txt === 'string' && txt.trim().startsWith('{')) {
    try { return JSON.parse(txt); } catch {}
  }
  return null;
}
function resolveSlots(m) {
  const meta = parseMeta(m);
  if (meta && Array.isArray(meta._slots)) return meta._slots;
  // 兼容：旧 description 直接存数组的情况
  const txt = m.metricDesc || m.description || '';
  if (typeof txt === 'string' && txt.trim().startsWith('[')) {
    try { return JSON.parse(txt); } catch {}
  }
  // 默认槽位（DAY 类 SQL 指标常见）
  if (m.calcLogicType === 'SQL' && /#\{datadate\}|#\{period_/i.test(m.sqlText || '')) {
    return [
      { name: 'period_start', type: 'DATE',     required: true,  desc: '起始日期' },
      { name: 'period_end',   type: 'DATE',     required: true,  desc: '截止日期' },
      { name: 'org_scope',    type: 'STRING[]', required: false, desc: '机构范围 · 缺省全行' }
    ];
  }
  return [];
}
function guessDataSource(m) {
  if (m.calcLogicType === 'SUMMARY') return '指标汇总（' + (m.summaryRule || '?') + '）';
  if (m.calcLogicType === 'EXPR')    return '复合指标（' + (m.refMetricCodes || '?') + '）';
  return 'EDW · 业务表';
}

// === 左树模糊搜索 ===
const treeRef = ref(null);
const treeKeyword = ref('');
watch(treeKeyword, v => treeRef.value?.filter(v ?? ''));
function filterTreeNode(value, data) {
  if (!value) return true;
  const v = String(value).trim().toLowerCase();
  if (!v) return true;
  const label = String(data.label || '').toLowerCase();
  const code = String(data.raw?.metricCode || data.id || '').toLowerCase();
  return label.includes(v) || code.includes(v);
}

// === 编辑/新增 弹框 ===
const formRef = ref(null);
const sqlInputRef = ref(null);

// 后端 MetricTrialService.runSql 自动注入的 10 个 SQL 命名参数；点击下方变量符插入到光标位置
const DATE_MACROS = [
  { token: ':dataDate',           desc: '数据日期（=dateToday，由调度/试运行传入）' },
  { token: ':allocDate',          desc: '业绩分配日期（定时任务=T-1；手动执行由弹窗"业绩分配日期"指定，留空默认=数据日期）' },
  { token: ':objectId',           desc: '对象id（试运行的"对象值"输入框映射；员工=工号/机构=机构号；真实执行为 null，建议写 (:objectId IS NULL OR x=:objectId)）' },
  { token: ':version',            desc: 'sys_control 当前版本' },
  { token: ':dateToday',          desc: '当前日期 T' },
  { token: ':dateYesterday',      desc: 'T-1 上一日期' },
  { token: ':dateMonthEnd',       desc: '本月最后一天' },
  { token: ':datePrevMonthEnd',   desc: '上月最后一天' },
  { token: ':dateQuarterEnd',     desc: '本季度最后一天' },
  { token: ':datePrevQuarterEnd', desc: '上季度最后一天' },
  { token: ':dateYearEnd',        desc: '本年最后一天' },
  { token: ':datePrevYearEnd',    desc: '上年最后一天（去年 12-31）' }
];

// 把变量符插到 SQL textarea 当前光标位置；未聚焦时附加到末尾
function insertMacro(token) {
  const elInput = sqlInputRef.value;
  const ta = elInput?.textarea || elInput?.input || elInput?.$el?.querySelector?.('textarea');
  const cur = dlg.form.sqlText || '';
  if (!ta) {
    dlg.form.sqlText = cur + token;
    return;
  }
  const start = ta.selectionStart ?? cur.length;
  const end = ta.selectionEnd ?? start;
  dlg.form.sqlText = cur.slice(0, start) + token + cur.slice(end);
  nextTick(() => {
    ta.focus();
    const pos = start + token.length;
    ta.setSelectionRange(pos, pos);
  });
}
const dlg = reactive({
  show: false, editing: null, saving: false,
  trialDate: null, trialing: false,
  trialSubject: '',
  trial: { status: '', cost: 0, totalRows: 0, errorMsg: '', rows: [], cols: [] },
  slots: [],
  form: {
    metricCode: '', metricName: '', baseDim: 'EMP', metricLevel: 1,
    calcFreq: 'DAY', calcLogicType: 'SQL', calcMode: 'AUTO',
    sqlText: '', exprText: '', exprDisplay: '', summaryRule: '',
    unit: '', decimalPlaces: 2, valSlot: 1, description: '',
    _category: '规模类'
  }
});
watch(() => dlg.form.metricLevel, (lvl) => {
  if (lvl === 1) dlg.form.calcLogicType = 'SQL';
  else if (lvl >= 2) dlg.form.calcLogicType = 'EXPR';
  dlg.form.exprText = '';
  exprPickCode.value = '';
  // 切换层级后清空表达式编辑器（DOM 渲染后执行）
  nextTick(renderExprEditor);
});
const parentMetricOptions = computed(() => {
  const lvl = dlg.form.metricLevel;
  if (!lvl || lvl <= 1) return [];
  const dim = dlg.form.baseDim;
  return allMetrics.value.filter(m =>
    m.metricLevel === lvl - 1
    && m.status === 'ACTIVE'
    && (!dim || m.baseDim === dim)
  );
});

// ====== Groovy 表达式标签编辑器（contenteditable，指标=可删除标签，运算符/数字可自由编辑）======
const exprEditorRef = ref(null);
const exprPickCode = ref('');
// 取值时间：为每次引用指定取历史哪一天的指标结果。
// value=下拉选中值（今日用非空哨兵 'TODAY'，规避 el-select 空串 option 选中/复位失灵的坑）；
// suffix=真实落库 token 后缀（今日为空串）。
const exprPickTime = ref('TODAY');
const VALUE_TIME_OPTIONS = [
  { label: '今日', value: 'TODAY', suffix: '' },
  { label: '昨日', value: '__D1', suffix: '__D1' },
  { label: '上月末', value: '__PME', suffix: '__PME' },
  { label: '上季末', value: '__PQE', suffix: '__PQE' },
  { label: '上年末', value: '__PYE', suffix: '__PYE' }
];
// 系统保留后缀（与后端 MetricValueTimeEnum / MetricRefTokenParser 一致）
const RESERVED_SUFFIXES = ['__D1', '__PME', '__PQE', '__PYE'];
// 当前下拉选中值 → 真实 token 后缀（今日→''）
function selectedSuffix() {
  const opt = VALUE_TIME_OPTIONS.find(t => t.value === exprPickTime.value);
  return opt ? opt.suffix : '';
}
// 把完整 token 拆成 { baseCode, suffix, timeLabel }：只按真实保留后缀 __xxx 判定，避免 'TODAY' 之类误伤。
// 无后缀=今日，也返回 timeLabel='今日'，让 chip 统一显示 @取值时间（避免用户误以为没生效）。
function splitToken(token) {
  for (const suf of RESERVED_SUFFIXES) {
    if (token.endsWith(suf)) {
      const opt = VALUE_TIME_OPTIONS.find(t => t.suffix === suf);
      return { baseCode: token.slice(0, -suf.length), suffix: suf, timeLabel: opt ? opt.label : '' };
    }
  }
  const today = VALUE_TIME_OPTIONS.find(t => t.suffix === '');
  return { baseCode: token, suffix: '', timeLabel: today ? today.label : '今日' };
}
// 保存编辑器内最近一次光标 Range：点 el-select / 添加按钮会让编辑器失焦，需用它定位插入点
let savedRange = null;

// 监听全局 selectionchange，把落在编辑器内的光标 Range 暂存
function onSelectionChange() {
  const editor = exprEditorRef.value;
  if (!editor) return;
  const sel = window.getSelection();
  if (sel && sel.rangeCount > 0) {
    const r = sel.getRangeAt(0);
    if (editor.contains(r.startContainer)) savedRange = r.cloneRange();
  }
}
onMounted(() => document.addEventListener('selectionchange', onSelectionChange));
onBeforeUnmount(() => document.removeEventListener('selectionchange', onSelectionChange));

// 构造一个指标标签节点（显示 指标编号·指标名称[@取值时间] + 删除按钮）；data-code 存完整 token
function makeChip(token) {
  const { baseCode, timeLabel } = splitToken(token);
  const m = allMetrics.value.find(x => x.metricCode === baseCode);
  const name = m ? (m.metricName || '') : '';
  const span = document.createElement('span');
  span.className = 'metric-chip';
  span.setAttribute('contenteditable', 'false');
  span.setAttribute('data-code', token);
  const base = name ? `${baseCode}·${name}` : baseCode;
  const label = timeLabel ? `${base} @${timeLabel}` : base;
  span.innerHTML = `<span class="chip-text">${label}</span><span class="chip-del" title="删除">×</span>`;
  return span;
}

// 把选中指标作为标签插入到表达式光标处
function insertMetricChip() {
  // 完整 token = 指标编号 + 取值时间后缀（今日为空后缀）
  const code = exprPickCode.value ? exprPickCode.value + selectedSuffix() : '';
  const editor = exprEditorRef.value;
  if (!code || !editor) return;
  editor.focus();
  const sel = window.getSelection();
  let range;
  if (savedRange && editor.contains(savedRange.startContainer)) {
    range = savedRange.cloneRange();
  } else {
    range = document.createRange();
    range.selectNodeContents(editor);
    range.collapse(false); // 末尾
  }
  sel.removeAllRanges();
  sel.addRange(range);
  range.deleteContents();
  const chip = makeChip(code);
  const after = document.createTextNode(' '); // 标签后补一个空格，便于继续键入运算符
  range.insertNode(after);
  range.insertNode(chip);
  // 光标移到空格之后
  range.setStartAfter(after);
  range.collapse(true);
  sel.removeAllRanges();
  sel.addRange(range);
  savedRange = range.cloneRange();
  exprPickCode.value = '';
  syncExprText();
}

// 插入安全除法模板 div( , ) —— 除数为 0 时后端返回 0，避免整表达式报错
function insertDiv() {
  const editor = exprEditorRef.value;
  if (!editor) return;
  editor.focus();
  const sel = window.getSelection();
  let range;
  if (savedRange && editor.contains(savedRange.startContainer)) {
    range = savedRange.cloneRange();
  } else {
    range = document.createRange();
    range.selectNodeContents(editor);
    range.collapse(false);
  }
  sel.removeAllRanges();
  sel.addRange(range);
  range.deleteContents();
  const tpl = document.createTextNode('div( , )');
  range.insertNode(tpl);
  range.setStartAfter(tpl);
  range.collapse(true);
  sel.removeAllRanges();
  sel.addRange(range);
  savedRange = range.cloneRange();
  syncExprText();
}

// 点击编辑器：处理标签上的 × 删除（事件委托）
function onExprEditorClick(e) {
  const del = e.target.closest && e.target.closest('.chip-del');
  if (del) {
    const chip = del.closest('.metric-chip');
    if (chip) { chip.remove(); syncExprText(); }
    e.preventDefault();
  }
}

// 把编辑器内容序列化为后端 Groovy 表达式字符串：标签→指标编号，文本→原样
function syncExprText() {
  const editor = exprEditorRef.value;
  if (!editor) return;
  let out = '';
  let displayOut = '';
  editor.childNodes.forEach((node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      out += node.textContent;
      displayOut += node.textContent;
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      if (node.classList && node.classList.contains('metric-chip')) {
        const c = node.getAttribute('data-code');
        const txt = node.querySelector('.chip-text');
        out += ' ' + c + ' ';
        displayOut += ' ' + (txt ? txt.textContent : c) + ' ';
      } else {
        out += node.textContent;
        displayOut += node.textContent;
      }
    }
  });
  dlg.form.exprText = out.replace(/ /g, ' ').replace(/\s+/g, ' ').trim();
  dlg.form.exprDisplay = displayOut.replace(/\s+/g, ' ').trim();
}

// 校验 Groovy 表达式里"相邻两个操作数之间缺运算符"——两个指标标签/数字仅用空格相连时，
// Groovy 会把前者当类名报 "unable to resolve class M_xxxx"。这里提前给出可读提示，命中返回错误文案，否则 null。
function checkExprOperator(expr) {
  const e = expr || '';
  // M_xxx 紧跟 M_xxx / 数字，或 数字 紧跟 M_xxx，中间只有空白（无 + - * / 等运算符）
  const m = e.match(/(\bM_[A-Za-z0-9_]+\b)\s+(\bM_[A-Za-z0-9_]+\b|\d+(?:\.\d+)?)/)
        || e.match(/(\b\d+(?:\.\d+)?)\s+(\bM_[A-Za-z0-9_]+\b)/);
  if (m) {
    const disp = (c) => (allMetrics.value.find(x => x.metricCode === c)?.metricName)
      ? `${c}·${allMetrics.value.find(x => x.metricCode === c).metricName}` : c;
    return `表达式里「${disp(m[1])}」与「${disp(m[2])}」之间缺少运算符（如 + - * / 等），请补全后再试`;
  }
  return null;
}

// 把后端返回的 exprVars(对象: 指标编号→值) 转为带指标名的列表，供试运行结果展示"Groovy 用到的用户指标数据"
function exprVarList(vars) {
  if (!vars || typeof vars !== 'object') return [];
  return Object.keys(vars).map((code) => {
    const m = allMetrics.value.find((x) => x.metricCode === code);
    return { code, name: m ? (m.metricName || '') : '', value: vars[code] };
  });
}

// 把已存在的 exprText 反序列化渲染回编辑器（编辑场景）：命中已知指标编号→标签，其余→文本
function renderExprEditor() {
  const editor = exprEditorRef.value;
  if (!editor) return;
  editor.innerHTML = '';
  const text = dlg.form.exprText || '';
  if (!text) return;
  const codes = allMetrics.value.map(m => m.metricCode).filter(Boolean)
    .sort((a, b) => b.length - a.length); // 长编号优先，避免前缀误匹配
  const isWord = (ch) => !!ch && /[A-Za-z0-9_]/.test(ch);
  let i = 0;
  while (i < text.length) {
    let matched = null;
    for (const c of codes) {
      if (!text.startsWith(c, i) || isWord(text[i - 1])) continue;
      const afterIdx = i + c.length;
      // 编号后是词边界 → 今日 token
      if (!isWord(text[afterIdx])) { matched = c; break; }
      // 编号后紧跟保留后缀（__D1/__PME/...）→ 历史档 token（编号 + 后缀整体识别为一个标签）
      for (const suf of RESERVED_SUFFIXES) {
        if (text.startsWith(suf, afterIdx) && !isWord(text[afterIdx + suf.length])) {
          matched = c + suf;
          break;
        }
      }
      if (matched) break;
    }
    if (matched) {
      editor.appendChild(makeChip(matched));
      editor.appendChild(document.createTextNode(' '));
      i += matched.length;
    } else {
      const last = editor.lastChild;
      if (last && last.nodeType === Node.TEXT_NODE) last.textContent += text[i];
      else editor.appendChild(document.createTextNode(text[i]));
      i++;
    }
  }
  syncExprText();
}

// 读取编辑器内当前使用到的指标编号（用于保存前校验）
function usedMetricCodes() {
  const editor = exprEditorRef.value;
  if (!editor) return [];
  // data-code 存的是完整 token（可能含取值时间后缀）；引用校验只关心底层指标编号，去重返回 baseCode
  const bases = Array.from(editor.querySelectorAll('.metric-chip'))
    .map(n => splitToken(n.getAttribute('data-code') || '').baseCode);
  return [...new Set(bases)];
}
function resetTrial() {
  dlg.trial = { status: '', cost: 0, totalRows: 0, errorMsg: '', rows: [], cols: [], exprResult: null, exprVars: null, dataVersion: '' };
}
const formRules = {
  metricCode: [
    { required: true, message: '编码必填' },
    { pattern: /^[A-Z0-9_]+$/, message: '指标编号只允许大写字母、数字和下划线（与编辑规则一致）' }
  ],
  metricName: [{ required: true, message: '名称必填' }],
  baseDim: [{ required: true, message: '基础维度必选' }],
  metricLevel: [{ required: true, message: '指标层级必选' }],
  calcFreq: [{ required: true, message: '计算频率必选' }],
  calcMode: [{ required: true, message: '计算方式必选' }],
  calcLogicType: [{ required: true, message: '计算逻辑必选' }]
};

function defaultForm() {
  return {
    metricCode: '', metricName: '', baseDim: 'EMP', metricLevel: 1,
    calcFreq: 'DAY', calcLogicType: 'SQL', calcMode: 'AUTO',
    sqlText: '', exprText: '', exprDisplay: '', summaryRule: '',
    unit: '', decimalPlaces: 2, valSlot: 1, description: '',
    _category: '规模类'
  };
}
function openCreate() {
  dlg.editing = null;
  Object.assign(dlg.form, defaultForm());
  dlg.slots = [
    { name: 'period_start', type: 'DATE', required: true, desc: '起始日期' },
    { name: 'period_end',   type: 'DATE', required: true, desc: '截止日期' }
  ];
  dlg.trialDate = null; dlg.trialSubject = ''; resetTrial();
  exprPickCode.value = '';
  exprPickTime.value = 'TODAY';
  dlg.show = true;
  // 指标列表若未加载成功（首屏失败/仍在途），开窗时补一次拉取，避免弹框永久禁用保存
  if (!metricsLoaded.value && !metricsLoading.value) reload();
  nextTick(renderExprEditor);
}
function openEdit(row) {
  dlg.editing = row.metricCode;
  Object.assign(dlg.form, {
    metricCode: row.metricCode, metricName: row.metricName,
    baseDim: row.baseDim, metricLevel: row.metricLevel,
    calcFreq: row.calcFreq, calcLogicType: row.calcLogicType, calcMode: row.calcMode,
    sqlText: row.sqlText || '', exprText: row.exprText || '', exprDisplay: row.exprDisplay || '', summaryRule: row.summaryRule || '',
    unit: row.unit || '', decimalPlaces: row.decimalPlaces ?? 2, valSlot: row.valSlot ?? 1,
    description: row.description || '',
    // 分类优先取数据表 metric_category 列（与下拉选项 CATEGORY_OPTIONS 同词表）；为空再按启发式兜底
    _category: (row.metricCategory && String(row.metricCategory).trim()) || resolveCategory(row)
  });
  dlg.slots = resolveSlots(row);
  dlg.trialDate = null; dlg.trialSubject = ''; resetTrial();
  exprPickCode.value = '';
  exprPickTime.value = 'TODAY';
  dlg.show = true;
  // 指标列表若未加载成功，开窗时补一次拉取（编辑场景还需靠它把已存 exprText 还原成指标标签）
  if (!metricsLoaded.value && !metricsLoading.value) reload();
  // 弹框渲染后，把已存的 exprText 还原成标签 + 文本
  nextTick(renderExprEditor);
}
// el-dialog 首次打开时其内容（含 contenteditable 表达式编辑器）才异步挂载完成；
// openEdit 里的单次 nextTick 此刻 exprEditorRef 仍为 null → renderExprEditor 空跑，
// 导致首次打开 Groovy 表达式渲染不出来。改由 @opened（过渡结束、DOM 就绪后触发）兜底渲染。
function onExprDialogOpened() {
  if (dlg.form.calcLogicType === 'EXPR') nextTick(renderExprEditor);
}
function addSlot() {
  dlg.slots.push({ name: '', type: 'DATE', required: false, desc: '' });
}

async function onSave(targetStatus) {
  // 指标数据未加载完成时禁止保存/发布：否则引用指标标签解析、上级指标校验都基于空列表，会误判
  if (metricEditBlocked.value) {
    return ElMessage.warning('指标数据尚未加载完成，请稍候再保存/发布');
  }
  try { await formRef.value.validate(); } catch { return; }
  // 提交前把编辑器内容同步到 exprText（防止最后一次键入未触发 input）
  if (dlg.form.calcLogicType === 'EXPR') syncExprText();
  if (dlg.form.calcLogicType === 'EXPR') {
    const opErr = checkExprOperator(dlg.form.exprText || '');
    if (opErr) { ElMessage.warning(opErr); return; }
  }
  if (dlg.form.calcLogicType === 'EXPR') {
    // Groovy 表达式可以为空；非空时至少需要引用一个指标
    const expr = (dlg.form.exprText || '').trim();
    if (expr) {
      const codes = usedMetricCodes();
      if (codes.length < 1) {
        return ElMessage.warning('Groovy 表达式不为空时至少需要一个指标');
      }
      // 发布(ACTIVE)时，引用的指标必须都在上级已发布指标列表中
      if (targetStatus === 'ACTIVE') {
        for (const c of codes) {
          if (!parentMetricOptions.value.some(m => m.metricCode === c)) {
            return ElMessage.warning(`指标 ${c} 不在上级已发布指标列表中`);
          }
        }
      }
    }
  }
  dlg.saving = true;
  // V1.6 修复 Bug5：保存失败（如后端返回 400 / 业务错）时**绝不**关闭弹框，
  // 让用户能继续修正字段。错误消息由 http.js 拦截器统一 ElMessage 抛出。
  // 后端 DTO 严格白名单：unit/decimalPlaces/valSlot/status/description 都不接受。
  // 把这些前端附加字段（含分类、槽位声明）打包写入 metricDesc 里，避免 Jackson 报 Unrecognized field。
  const meta = {
    _slots: dlg.slots, _unit: dlg.form.unit,
    _decimal: dlg.form.decimalPlaces, _category: dlg.form._category
  };
  const basePayload = {
    metricCode: dlg.form.metricCode,
    metricName: dlg.form.metricName,
    metricDesc: JSON.stringify(meta),
    // 指标详细描述：完全按输入框内容提交，后端存 description 列，不再塞 metricDesc JSON
    description: dlg.form.description || null,
    // V1.9 metric_category 独立列（前端分类下拉/树聚合靠它，不能只塞 metricDesc JSON）
    metricCategory: dlg.form._category || null,
    calcFreq: dlg.form.calcFreq,
    calcMode: dlg.form.calcMode,
    calcLogicType: dlg.form.calcLogicType,
    sqlText: dlg.form.calcLogicType === 'SQL' ? (dlg.form.sqlText || null) : null,
    exprText: dlg.form.calcLogicType === 'EXPR' ? (dlg.form.exprText || null) : null,
    // 含标签展示串(exprDisplay)已废弃：不再提交，后端按 exprText 实时派生用于查看显示
    summaryRule: dlg.form.calcLogicType === 'SUMMARY' ? (dlg.form.summaryRule || null) : null
  };
  try {
    if (dlg.editing) {
      // Update DTO 含 metricLevel（允许编辑层级落库，后端 val_slot 不变）；
      // 不含 baseDim/preferredSlot/status，也不含 metricCategory（仅 create 时落到独立列）
      const { metricCategory, ...rest } = basePayload;
      const updatePayload = { ...rest, metricLevel: dlg.form.metricLevel };
      await updateMetric(dlg.editing, updatePayload);
      if (targetStatus !== detail.value.status) {
        await changeMetricStatus(dlg.editing, targetStatus, '编辑保存');
      }
      ElMessage.success(targetStatus === 'ACTIVE' ? '已发布' : '已保存为草稿');
    } else {
      // Create DTO 额外接 baseDim / metricLevel / status；
      // preferredSlot 故意不传——已有指标占满 slot=1 必触发 METRIC_SLOT_CONFLICT，
      // 后端 MetricSlotService.allocSlot 在 preferredSlot=null 时自动找下一个空闲槽位。
      await createMetric({
        ...basePayload,
        baseDim: dlg.form.baseDim,
        metricLevel: dlg.form.metricLevel,
        status: targetStatus   // 一步直接落 ACTIVE / DRAFT，避免两步切换状态机抖动
      });
      ElMessage.success(targetStatus === 'ACTIVE' ? '已新增并发布' : '已保存为草稿');
    }
    dlg.show = false;
    if (dlg.editing) {
      // 编辑保存：只刷新被编辑的指标，不整树重载——原地更新该指标在列表里的名称/状态，
      // 树是 allMetrics 派生的 computed，仅这一个叶子节点的标签/状态标签会变（编辑不改维度/分类，
      // el-tree node-key=id 保留展开与选中状态）；右侧详情单独按编号重新拉取。
      const edited = allMetrics.value.find(m => m.metricCode === dlg.editing);
      if (edited) {
        edited.metricName = dlg.form.metricName;
        edited.status = targetStatus;
        // 层级可编辑：同步新层级，派生树 computed 会把该指标重新归到对应层级组
        edited.metricLevel = dlg.form.metricLevel;
      }
      onPick(dlg.editing);
    } else {
      // 新增：需要后端分配的槽位/编号，整列表重载后定位到新指标
      await reload();
      onPick(dlg.form.metricCode);
    }
  } catch {} finally { dlg.saving = false; }
}

async function onTrialFromDialog() {
  // 指标数据未加载完成时禁止试运行：EXPR 引用指标需靠 allMetrics 解析编号，空列表会取不到引用值
  if (metricEditBlocked.value) {
    return ElMessage.warning('指标数据尚未加载完成，请稍候再试运行');
  }
  // 直接取当前 SQL/Groovy 表达式执行，无需先保存指标
  const logic = dlg.form.calcLogicType;
  if (logic === 'EXPR') syncExprText();
  const sqlText = logic === 'SQL' ? (dlg.form.sqlText || '').trim() : '';
  const exprText = logic === 'EXPR' ? (dlg.form.exprText || '').trim() : '';
  if (logic === 'SQL' && !sqlText) return ElMessage.warning('请先填写 SQL 表达式');
  if (logic === 'EXPR' && !exprText) return ElMessage.warning('请先填写 Groovy 表达式');
  if (logic !== 'SQL' && logic !== 'EXPR') return ElMessage.warning('该计算逻辑暂不支持试运行');
  if (logic === 'EXPR') {
    const opErr = checkExprOperator(exprText);
    if (opErr) return ElMessage.warning(opErr);
  }
  const yesterday = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);
  // 单日期模式：dlg.trialDate 是 'YYYY-MM-DD' 字符串，作为后端 :dataDate 占位符的值
  const dataDate = dlg.trialDate || yesterday;
  // 对象值：普通输入框手输原值，直接映射后端 SQL :objectId（始终下发，未填则 null）
  const objectId = (dlg.trialSubject || '').trim() || null;
  // Groovy 引用了其它指标(M_xxxx)时必须有对象值，才能按维度+日期+对象值从宽表取数；否则引用指标全为 0
  if (logic === 'EXPR' && /\bM_[A-Za-z0-9_]+\b/.test(exprText) && !objectId) {
    return ElMessage.warning('Groovy 引用了指标，请先填写【对象值】（按维度+数据日期定位宽表中该对象的指标值）');
  }
  resetTrial();
  dlg.trialing = true;
  const t0 = Date.now();
  try {
    // metricCode 仅作路由占位（命中已注册的 /{metricCode}/trial-run 资源）；实际执行用传入的表达式文本
    const codeForPath = dlg.editing || dlg.form.metricCode || '_PREVIEW_';
    const r = await trialRunMetric(codeForPath, {
      dataDate, sampleSize: 10, params: { objectId },
      calcLogicType: logic,
      baseDim: dlg.form.baseDim,
      sqlText: sqlText || undefined,
      exprText: exprText || undefined
    });
    // 后端 MetricTrialRespDTO: { status, totalRows, executionMillis, errorMsg, sampleRows, exprResult }
    const rows = r?.sampleRows || [];
    const cols = rows.length ? Object.keys(rows[0]) : ['cust_id', 'org_id', 'metric_value'];
    dlg.trial = {
      status: r?.status || (rows.length || r?.exprResult != null ? 'SUCCESS' : 'FAILED'),
      cost: r?.executionMillis ?? (Date.now() - t0),
      totalRows: r?.totalRows ?? rows.length,
      errorMsg: r?.errorMsg || '',
      // EXPR(Groovy) 单值结果：用于成功后反显「计算结果：xxx」
      exprResult: r?.exprResult,
      // EXPR 引用指标取值 + 命中数据版本：列出 Groovy 计算用到的用户指标数据
      exprVars: r?.exprVars || null,
      dataVersion: r?.dataVersion || '',
      rows, cols
    };
  } catch (err) {
    // 业务错误（PERF-42201 SQL 语法错等）：显示失败 tag + 错误文本，方便排查
    dlg.trial = {
      status: 'FAILED', cost: Date.now() - t0, totalRows: 0,
      errorMsg: err?.message || '试运行失败（SQL/EXPR 执行异常）',
      rows: [], cols: []
    };
  } finally { dlg.trialing = false; }
}

// === 详情侧动作 ===
async function onTrialRun() {
  // dataDate 默认昨天（T-1 是 perf 模块习惯）
  const yesterday = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);
  resetDetailTrial();
  detailTrial.loading = true;
  const t0 = Date.now();
  try {
    const r = await trialRunMetric(detail.value.metricCode, { dataDate: yesterday, sampleSize: 10 });
    // 后端 MetricTrialRespDTO: { status, totalRows, executionMillis, errorMsg, sampleRows, exprResult }
    const rows = r?.sampleRows || [];
    const cols = rows.length ? Object.keys(rows[0]) : ['baseKey', 'metricValue'];
    Object.assign(detailTrial, {
      status: r?.status || (rows.length ? 'SUCCESS' : (r?.exprResult != null ? 'SUCCESS' : 'FAILED')),
      cost: r?.executionMillis ?? (Date.now() - t0),
      totalRows: r?.totalRows ?? rows.length,
      errorMsg: r?.errorMsg || '',
      exprResult: r?.exprResult,
      rows, cols
    });
  } catch (err) {
    Object.assign(detailTrial, {
      status: 'FAILED', cost: Date.now() - t0, totalRows: 0,
      errorMsg: err?.message || '试运行失败（SQL/EXPR 执行异常）',
      rows: [], cols: []
    });
  } finally { detailTrial.loading = false; }
}

// 立即执行：打开 execDlg 让用户在同一页选日期 (el-date-picker) + 填原因
function onExecute() {
  execDlg.metricCode = detail.value.metricCode;
  execDlg.dataDate = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);  // 默认昨日
  execDlg.allocDate = '';   // 业绩分配日期（留空默认=数据日期，由后端兜底）
  execDlg.reason = '';
  execDlg.submitting = false;
  execDlg.show = true;
}

// execDlg "确认执行" 按钮：校验 dataDate / reason 后真正调用后端
async function confirmExecute() {
  const today = new Date().toISOString().slice(0, 10);
  if (!execDlg.dataDate)                              return ElMessage.warning('数据日期必填');
  if (execDlg.dataDate > today)                       return ElMessage.warning(`数据日期不能大于今天（${today}）`);
  if (!execDlg.reason || !execDlg.reason.trim())      return ElMessage.warning('执行原因必填');

  execDlg.submitting = true;
  try {
    await executeMetric(execDlg.metricCode, {
      dataDate: execDlg.dataDate,
      // 业绩分配日期：填了就传，留空不传（后端默认=数据日期）
      allocDate: execDlg.allocDate || undefined,
      cascade: true,
      async: true,
      reason: execDlg.reason.trim()
    });
    ElMessage.success('已触发执行（异步任务），已写入审计日志');
    execDlg.show = false;
  } catch {
    // executeMetric 内部已 ElMessage.error，这里不重复
  } finally {
    execDlg.submitting = false;
  }
}

// 跳到审计日志页面，预填筛选 = 本指标的操作流水
function onViewAudit() {
  router.push({
    path: '/system/audit',
    query: {
      bizType: 'PERF_CONFIG',
      keyword: detail.value.metricCode
    }
  });
}
async function onChangeStatus(status) {
  let reason;
  try {
    const r = await ElMessageBox.prompt(`确认将 ${detail.value.metricCode} 切换为 ${status}？请填写原因`, '提示',
      { inputPattern: /\S+/, inputErrorMessage: '原因必填' });
    reason = r.value;
  } catch { return; }
  try {
    await changeMetricStatus(detail.value.metricCode, status, reason);
    ElMessage.success('状态已更新');
    onPick(detail.value.metricCode); reload();
  } catch {}
}
async function onDelete(row) {
  let reason;
  try {
    const r = await ElMessageBox.prompt(`确认删除 ${row.metricCode} (${row.metricName})？请填写删除原因`, '高危',
      { type: 'warning', inputPattern: /\S+/, inputErrorMessage: '原因必填' });
    reason = r.value;
  } catch { return; }
  try {
    await deleteMetric(row.metricCode, reason);
    ElMessage.success('已删除');
    detail.value = {}; picked.value = '';
    reload();
  } catch {}
}

// === 版本历史：复用审计日志，按 metricCode 拉本指标的所有变更（CREATE/UPDATE/STATUS_CHANGE/DELETE） ===
const versionDlg = reactive({ show: false, loading: false, list: [] });

// 立即执行对话框：日期（el-date-picker）+ 执行原因 合并一页
const execDlg = reactive({
  show: false,
  metricCode: '',
  dataDate: '',
  allocDate: '',   // 业绩分配日期（SQL :allocDate；留空默认=数据日期）
  reason: '',
  submitting: false,
  // el-date-picker disabled-date：禁选今天之后的日期（含 0 点比较）
  disabledDate: (d) => {
    const t = new Date(); t.setHours(0, 0, 0, 0);
    return d.getTime() > t.getTime();
  }
});
async function onShowVersions() {
  versionDlg.show = true;
  versionDlg.loading = true;
  versionDlg.list = [];
  try {
    const r = await listAuditLogs({
      bizType: 'PERF_CONFIG',
      keyword: detail.value.metricCode,
      pageSize: 50
    });
    if (Array.isArray(r)) {
      // 倒序 + 过滤掉非本指标的命中（keyword 是 LIKE 模糊匹配）
      versionDlg.list = r
        .filter(x => (x.resourceUrl || '').includes(detail.value.metricCode))
        .map((x, i, arr) => ({ ...x, version: 'v' + (arr.length - i) }));
    }
  } catch {} finally { versionDlg.loading = false; }
}

// === 导入指标（直接上传 METRIC_DEF 文件） ===
const fileInputRef = ref(null);
function triggerImport() {
  fileInputRef.value?.click();
}
async function onFileSelected(e) {
  const file = e.target?.files?.[0];
  if (!file) return;
  try {
    const resp = await uploadImportFile('METRIC_DEF', file);
    const errRows = resp?.errorRows || 0;
    if (errRows > 0) {
      ElMessage.warning(`导入完成，${errRows} 行有错误：${resp.errorSummary || '请检查数据'}`);
    } else {
      ElMessage.success(`导入成功，新增 ${resp?.insertedRows ?? '-'} 条，更新 ${resp?.updatedRows ?? '-'} 条`);
    }
    reload();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '导入失败');
  } finally {
    fileInputRef.value.value = '';
  }
}
function downloadTemplate() {
  window.open('/templates/指标表上传模板.xlsx', '_blank');
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.expr-edit-wrap {
  width: 100%;
  .expr-editor {
    min-height: 64px;
    width: 100%;
    border: 1px solid var(--el-border-color, #dcdfe6);
    border-radius: 4px;
    padding: 8px 10px;
    font-size: 14px;
    line-height: 28px;
    background: #fff;
    outline: none;
    word-break: break-all;
    &:focus { border-color: var(--el-color-primary, #409eff); }
    &:empty::before {
      content: attr(data-placeholder);
      color: #b6bcc4;
    }
  }
  .expr-hint { font-size: 12px; color: #999; margin-top: 4px; }
  // 指标标签：编号·名称 + 删除按钮
  :deep(.metric-chip) {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin: 0 2px;
    padding: 1px 4px 1px 8px;
    border-radius: 4px;
    background: var(--el-color-primary-light-9, #ecf5ff);
    border: 1px solid var(--el-color-primary-light-5, #a0cfff);
    color: var(--el-color-primary, #409eff);
    font-size: 13px;
    line-height: 20px;
    user-select: none;
    white-space: nowrap;
    .chip-del {
      cursor: pointer;
      color: #909399;
      font-weight: bold;
      padding: 0 2px;
      &:hover { color: var(--el-color-danger, #f56c6c); }
    }
  }
}
.layout {
  display: grid;
  grid-template-columns: 370px 1fr;
  gap: 12px;
}
.tree-col {
  padding: 16px;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
  overflow-x: auto;
  &::-webkit-scrollbar { width: 6px; height: 6px; }
  &::-webkit-scrollbar-thumb { background: #c0c4cc; border-radius: 3px; }
  &::-webkit-scrollbar-track { background: transparent; }
  :deep(.el-tree) { min-width: max-content; }
  :deep(.el-tree-node__content) { white-space: nowrap; }
}
.card-h-mini {
  font-size: 14px; font-weight: 600;
  padding: 0 0 12px;
  border-bottom: 1px solid $border-1;
  margin-bottom: 10px;
  color: $text-1;
}
.tree-search { margin-bottom: 10px; }
.tree-node {
  display: flex; align-items: center; gap: 6px;
  flex: 1; min-width: 0;
  .ico { font-size: 13px; }
  .tree-leaf { color: $text-1; }
  .tree-tag { margin-left: auto; }
  // 指标层级数字徽标（1/2/3），按层级配色
  .lvl-badge {
    flex: none;
    display: inline-flex; align-items: center; justify-content: center;
    width: 16px; height: 16px; border-radius: 50%;
    font-size: 11px; font-weight: 700; line-height: 1; color: #fff;
    background: #909399;
    &.lvl-1 { background: #409eff; }
    &.lvl-2 { background: #67c23a; }
    &.lvl-3 { background: #e6a23c; }
  }
}
.detail-col {
  padding: 18px 22px;
  max-height: calc(100vh - 200px);
  overflow: auto;
}
.empty-pane { padding: 80px 20px; text-align: center; color: $text-3; font-size: 13px; }
.card-h {
  display: flex; align-items: center; gap: 10px;
  padding: 0 0 14px; border-bottom: 1px solid $border-1; margin-bottom: 16px;
  .title { font-size: 16px; font-weight: 600; flex: 1; }
  .version { font-size: 12px; color: $text-3; }
}
.meta-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-bottom: 8px;
  td {
    padding: 10px 14px;
    border: 1px solid $border-1;
  }
  .lab { background: $bg-soft; color: $text-2; width: 110px; }
  .val { color: $text-1; }
}
.block-h {
  font-size: 13px; font-weight: 600; margin: 22px 0 10px; color: $text-1;
}
.code {
  background: #1e293b; color: #f1f5f9; border-radius: 4px; padding: 12px 14px;
  font-family: ui-monospace, monospace; font-size: 12.5px; line-height: 1.6;
  white-space: pre-wrap; overflow: auto; margin: 0;
}
.acts { margin: 22px 0 8px; display: flex; gap: 8px; flex-wrap: wrap; }
.audit-hint {
  margin-top: 6px; padding: 8px 12px;
  background: #fffbeb; border: 1px solid #fed7aa; border-radius: 4px;
  color: #92400e; font-size: 12.5px; line-height: 1.6;
}
.req { color: #16A34A; font-weight: 600; }
.opt { color: $text-3; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }

.form-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px;
}
.trial-row {
  display: flex; gap: 10px; align-items: center; flex-wrap: wrap;
}
.trial-error {
  margin-top: 12px; padding: 10px 14px;
  background: #fef2f2; border: 1px solid #fecaca; border-radius: 4px;
  color: $danger; font-size: 13px; line-height: 1.6;
}
.trial-detail {
  margin-top: 14px; padding: 12px 14px;
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 4px;
}
.trial-title { font-size: 13px; font-weight: 600; color: $text-1; }
.trial-expr  { font-size: 13px; color: $text-1; }
.sql-date-macros {
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
}
.sql-date-macros .hint-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.sql-date-macros .hint-table {
  border-collapse: collapse;
  width: 100%;
}
.sql-date-macros .hint-table th,
.sql-date-macros .hint-table td {
  border: 1px solid #ebeef5;
  padding: 4px 8px;
  text-align: left;
  vertical-align: top;
}
.sql-date-macros .hint-table th {
  background: #fafafa;
  color: #606266;
  font-weight: 500;
}
.sql-date-macros code {
  background: #fff5e6;
  color: #b87600;
  padding: 0 4px;
  border-radius: 2px;
}
.sql-date-macros code.macro-btn {
  cursor: pointer;
  user-select: none;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}
.sql-date-macros code.macro-btn:hover {
  background: #ffd591;
  color: #874d00;
  box-shadow: 0 0 0 1px #fa8c16;
}
.sql-date-macros code.macro-btn:active {
  background: #fa8c16;
  color: #fff;
}
.sql-date-macros .hint-foot {
  margin-top: 8px;
  color: #909399;
}
</style>
