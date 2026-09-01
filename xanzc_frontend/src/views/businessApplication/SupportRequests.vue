<template>
  <main v-bp-overflow-tooltip class="bp-crud support-requests" aria-labelledby="support-request-title">
    <header class="page-h">
      <PageTitle id="support-request-title" title="中台支持" />
      <el-button v-if="!isCreateRoute" type="primary" @click="goNew">新建中台支持</el-button>
    </header>

    <section v-if="isCreateRoute" class="card-section support-form-card" aria-label="新建中台支持">
      <div class="section-head">
        <div>
          <h2>新建中台支持</h2>
          <p>明确产品时由产品负责人承接；无法匹配产品时请填写其他需求并选择支持部门。</p>
        </div>
        <el-button @click="goList">返回列表</el-button>
      </div>

      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="inline-error" />
      <el-form ref="formRef" :model="form" label-position="top" @submit.prevent>
        <section class="form-section">
          <h3>来源与客户</h3>
          <div class="form-grid">
            <el-form-item label="来源类型" required>
              <el-radio-group v-model="form.sourceType" :disabled="sourceLocked">
                <el-radio-button value="EXISTING_CUSTOMER">存量客户</el-radio-button>
                <el-radio-button value="TOUCH_TASK">触达任务</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="来源触达任务 ID">
              <el-input v-model="form.sourceTouchTaskId" :disabled="sourceLocked" placeholder="来自触达任务时自动带出" />
            </el-form-item>
            <el-form-item label="客户" required data-field="custId">
              <el-select v-model="form.custId" filterable remote clearable :disabled="sourceLocked"
                         :remote-method="searchCustomers" :loading="customerLoading"
                         placeholder="输入客户名称或客户号" style="width:100%" @change="selectCustomer">
                <el-option v-for="item in customers" :key="item.id" :label="customerLabel(item)" :value="item.id" />
              </el-select>
              <span v-if="sourceLocked" class="field-help">来源触达任务已锁定客户，不可修改。</span>
            </el-form-item>
            <el-form-item label="客户所属机构">
              <el-input :model-value="selectedCustomer.orgName || selectedCustomer.ownerOrgName || '-'" disabled />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <h3>支持内容</h3>
          <el-form-item label="支持产品（可多选）">
            <el-checkbox-group v-model="form.productIds" class="product-grid">
              <el-checkbox v-for="product in products" :key="product.id" :value="product.id" border>
                <span>{{ productName(product) }}</span>
                <small v-if="product.typeName || product.productTypeName">{{ product.typeName || product.productTypeName }}</small>
              </el-checkbox>
            </el-checkbox-group>
            <div v-if="!productsLoading && !products.length" class="field-help">暂无可用支持产品，可填写其他需求并选择支持部门。</div>
          </el-form-item>
          <el-checkbox v-model="manualDemandSelected" class="manual-demand-check">其他 / 手工填写</el-checkbox>
          <el-form-item label="其他需求 / 补充说明" :required="!form.productIds.length">
            <el-input v-model="form.otherDemand" type="textarea" :rows="4" maxlength="2000" show-word-limit
                      placeholder="产品列表无法覆盖时，请描述客户需要的支持内容" />
          </el-form-item>
          <el-form-item label="支持部门" :required="requiresSupportDept">
            <el-select v-model="form.supportDeptId" filterable clearable :disabled="!requiresSupportDept"
                       placeholder="场景 B 必须选择支持部门" style="width:100%">
              <el-option v-for="dept in supportDepartments" :key="dept.code" :label="dept.name" :value="dept.code" />
            </el-select>
          </el-form-item>
        </section>

        <section class="form-section">
          <h3>申请附件</h3>
          <el-upload :http-request="uploadAttachment" :file-list="attachmentFiles" multiple>
            <el-button>上传附件</el-button>
            <template #tip><div class="el-upload__tip">支持现场方案、客户资料等附件；文件最终保存到平台文件中心。</div></template>
          </el-upload>
        </section>
      </el-form>

      <div class="form-footer">
        <el-button @click="goList">取消</el-button>
        <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="saveAndSubmit">创建并提交</el-button>
      </div>
    </section>

    <template v-else>
      <section class="card-section workbench" aria-label="中台支持工作台">
        <nav class="support-tabs" aria-label="中台支持视图">
          <button v-for="tab in tabs" :key="tab.value" type="button" class="support-tab"
                  :aria-current="activeTab === tab.value ? 'page' : undefined"
                  :class="{ active: activeTab === tab.value }" @click="selectTab(tab.value)">
            {{ tab.label }}
          </button>
        </nav>
        <el-form inline class="filter-form" @submit.prevent>
          <el-form-item label="综合查询">
            <el-input v-model="query.keyword" clearable placeholder="申请编号、客户或需求" @keyup.enter="search" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部" style="width:140px">
              <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="SLA">
            <el-select v-model="query.slaStatus" clearable placeholder="全部" style="width:120px">
              <el-option v-for="item in slaOptions" :key="item.value" v-bind="item" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </section>

      <section class="card-section" :aria-label="currentTabLabel + '列表'" :aria-busy="loading">
        <div class="section-head">
          <div>
            <h2>{{ currentTabLabel }}</h2>
            <p v-if="activeTab === 'MINE'">展示本人发起的中台支持申请及其流程状态。</p>
            <p v-else>承接侧待办和已办分别按支持部门、承接人数据范围展示。</p>
          </div>
          <span class="result-count">{{ loading ? '加载中…' : `共 ${total} 条` }}</span>
        </div>
        <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon class="inline-error" />
        <el-table :data="rows" stripe border row-key="id" empty-text="暂无中台支持记录">
          <el-table-column label="申请编号" min-width="180">
            <template #default="{ row }"><el-button link type="primary" @click="openDetail(row.id)">{{ row.requestNo || row.id || '-' }}</el-button></template>
          </el-table-column>
          <el-table-column label="客户" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ customerName(row) }}</template>
          </el-table-column>
          <el-table-column label="产品 / 需求" min-width="220" show-overflow-tooltip>
            <template #default="{ row }"><div>{{ productName(row) || '其他需求' }}</div><small>{{ row.otherDemand || '' }}</small></template>
          </el-table-column>
          <el-table-column label="支持部门" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.supportDeptName || row.supportDeptId || '-' }}</template>
          </el-table-column>
          <el-table-column label="当前节点 / 承接人" min-width="170">
            <template #default="{ row }"><div>{{ row.currentNodeName || row.currentNode || '-' }}</div><small>{{ assignedName(row) }}</small></template>
          </el-table-column>
          <el-table-column label="状态" width="105">
            <template #default="{ row }"><el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="SLA" width="100">
            <template #default="{ row }"><span :class="['sla-badge', slaClass(row)]"><i aria-hidden="true"></i>{{ slaLabel(row) }}</span></template>
          </el-table-column>
          <el-table-column label="发起时间" width="168"><template #default="{ row }">{{ formatTime(row.createdTime) }}</template></el-table-column>
          <el-table-column label="操作" width="220" fixed="right" class-name="operation-cell">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <template v-if="activeTab === 'MINE'">
                <el-button v-if="canSubmit(row)" link type="success" @click="submitRow(row)">提交</el-button>
                <el-button v-if="canWithdraw(row)" link type="danger" @click="withdrawRow(row)">撤回</el-button>
              </template>
              <template v-else-if="activeTab === 'DEPT_TODO'">
                <el-button v-if="canDispatch(row)" link type="primary" @click="openDispatch(row)">派单</el-button>
                <el-button v-if="canHandle(row)" link type="success" @click="openDetail(row.id)">办理</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :total="total"
                         :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" background @change="loadList" />
        </div>
      </section>
    </template>

    <el-drawer v-model="detailVisible" title="中台支持详情" size="min(920px, 96vw)" :close-on-click-modal="false">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="inline-error" />
      <div v-loading="detailLoading" class="detail-body" v-if="detail">
        <section class="detail-section">
          <div class="detail-title"><h2>{{ detail.requestNo || detail.id }}</h2><el-tag :type="statusType(detail.status)" effect="plain">{{ statusLabel(detail.status) }}</el-tag></div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="客户">{{ detail.custName || detail.customerName || detail.custInfo?.name || detail.custId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="产品">{{ productName(detail) || '其他需求' }}</el-descriptions-item>
            <el-descriptions-item label="支持部门">{{ detail.supportDeptName || detail.supportDeptId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="承接人">{{ assignedName(detail) }}</el-descriptions-item>
            <el-descriptions-item label="来源触达任务">{{ detail.sourceTouchTaskId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="同批分组">{{ detail.submitGroupId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发起人">{{ detail.createdByName || detail.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发起时间">{{ formatTime(detail.createdTime) }}</el-descriptions-item>
            <el-descriptions-item label="其他需求" :span="2"><span class="pre-wrap">{{ detail.otherDemand || '-' }}</span></el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <h3>流程进度</h3>
          <el-table :data="processNodes(detail)" size="small" border empty-text="暂无流程节点">
            <el-table-column prop="nodeName" label="节点" min-width="160" />
            <el-table-column prop="status" label="状态" width="110"><template #default="{ row }">{{ processStatusLabel(row.status) }}</template></el-table-column>
            <el-table-column prop="assigneeName" label="处理人" width="140"><template #default="{ row }">{{ row.assigneeName || row.assigneeEmpId || '-' }}</template></el-table-column>
            <el-table-column prop="completedTime" label="处理时间" width="165"><template #default="{ row }">{{ formatTime(row.completedTime || row.endTime) }}</template></el-table-column>
          </el-table>
        </section>

        <section class="detail-section">
          <h3>中台支持过程记录</h3>
          <el-empty v-if="!logs.length" description="暂无过程记录" :image-size="70" />
          <el-timeline v-else>
            <el-timeline-item v-for="log in logs" :key="log.id || log.logId || log.createdTime" :timestamp="formatTime(log.createdTime || log.logTime || log.checkInTime || log.checkinTime)" placement="top">
              <el-card shadow="never">
                <div class="log-head">{{ log.operatorName || log.createdByName || log.createdBy || '-' }} <span v-if="log.result">· {{ resultLabel(log.result) }}</span></div>
                <div class="pre-wrap">{{ log.content || log.summary || log.logContent || '-' }}</div>
                <div v-if="log.operatorLocation || log.locationAddress || log.location" class="muted">定位：{{ log.operatorLocation || log.locationAddress || log.location }}</div>
                <div v-if="log.checkInTime || log.checkinTime" class="muted">打卡：{{ formatTime(log.checkInTime || log.checkinTime) }}</div>
                <div v-if="photoUrls(log).length" class="history-photos"><el-image v-for="url in photoUrls(log)" :key="url" :src="url" :preview-src-list="photoUrls(log)" fit="cover" /></div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </section>

        <section v-if="detailMode === 'DEPT_TODO'" class="detail-section detail-actions-section">
          <h3>办理记录</h3>
          <el-form label-position="top" @submit.prevent>
            <div class="form-grid">
              <el-form-item label="定位地址 / 经纬度" required>
                <div class="location-field">
                  <el-input v-model="logForm.operatorLocation" placeholder="自动定位失败时手工填写地址" @input="geoError = ''" />
                  <el-button size="small" :loading="locating" @click="requestCurrentLocation(true)">重新定位</el-button>
                </div>
                <span v-if="geoError" class="field-error">{{ geoError }}</span>
              </el-form-item>
              <el-form-item label="打卡时间" required><el-date-picker v-model="logForm.checkInTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
            </div>
            <el-form-item label="过程内容" required><el-input v-model="logForm.content" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
            <el-form-item label="现场照片" required>
              <div class="photo-list">
                <span v-for="url in logForm.photoUrls" :key="url" class="photo-item"><el-image :src="url" fit="cover" :preview-src-list="logForm.photoUrls" /><button type="button" @click="removePhoto(url)">×</button></span>
                <el-upload action="#" accept="image/*" :show-file-list="false" :http-request="uploadLogPhoto"><el-button size="small" :loading="uploading">上传照片</el-button></el-upload>
              </div>
              <span class="field-help">过程记录须包含至少一张现场照片。</span>
            </el-form-item>
            <div class="detail-action-buttons">
              <el-button :loading="savingLog" type="primary" @click="addLog">保存过程记录</el-button>
              <el-input v-model="completionSummary" class="completion-summary" placeholder="办理结果摘要（成功/驳回必填）" />
              <el-button :loading="completing" type="success" @click="complete(true)">办理成功</el-button>
              <el-button :loading="completing" type="danger" plain @click="complete(false)">驳回</el-button>
            </div>
          </el-form>
        </section>

        <div v-if="detailMode === 'MINE'" class="detail-action-buttons detail-footer-actions">
          <el-button v-if="canSubmit(detail)" type="primary" :loading="submitting" @click="submitRow(detail)">提交</el-button>
          <el-button v-if="canWithdraw(detail)" type="danger" plain :loading="withdrawing" @click="withdrawRow(detail)">撤回</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="dispatchVisible" title="中台支持派单" width="min(560px, 94vw)" :close-on-click-modal="false">
      <el-alert v-if="dispatchError" :title="dispatchError" type="error" :closable="false" show-icon class="inline-error" />
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="支持部门"><el-input :model-value="dispatchTarget?.supportDeptName || dispatchTarget?.supportDeptId || '-'" disabled /></el-form-item>
        <el-form-item label="承接员工" required>
          <el-select v-model="dispatchForm.assignedEmpId" filterable placeholder="请选择本部门承接人" style="width:100%">
            <el-option v-for="employee in departmentEmployees" :key="employee.id" :label="employeeLabel(employee)" :value="employee.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="派单备注"><el-input v-model="dispatchForm.dispatchRemark" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dispatchVisible=false">取消</el-button><el-button type="primary" :loading="dispatching" @click="dispatch">确认派单</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import PageTitle from '@/components/PageTitle.vue';
import {
  addSupportDeptLog, completeSupportRequest, createSupportRequest, dispatchSupportRequest,
  getSupportDeptRequest, getSupportRequest, listSupportCustomers, listSupportDeptLogs,
  listSupportDeptRequests, listSupportProducts, listSupportRequestLogs, listSupportRequests,
  submitSupportRequest,
  uploadSupportPhoto, withdrawSupportRequest
} from '@/api/supportRequests';
import { getOrgTree, listOrgUsers } from '@/api/orgs';

const route = useRoute();
const router = useRouter();
const SUPPORT_LIST_PATH = '/bizexec/supports';
const tabs = [
  { value: 'MINE', label: '我的申请' },
  { value: 'DEPT_TODO', label: '承接待办' },
  { value: 'DEPT_DONE', label: '承接已办' }
];
const statusOptions = [
  { value: 'DRAFT', label: '草稿' }, { value: 'IN_APPROVAL', label: '审批中' },
  { value: 'IN_PROGRESS', label: '办理中' }, { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '已驳回' }, { value: 'CANCELLED', label: '已撤回' }
];
const slaOptions = [
  { value: 'GREEN', label: '正常' }, { value: 'YELLOW', label: '预警' }, { value: 'RED', label: '超时' }
];
const statusMap = Object.fromEntries(statusOptions.map(item => [item.value, item.label]));

const tabFromRoute = () => {
  const value = String(route.query?.tab || '').toUpperCase();
  if (value === 'TODO' || value === 'PENDING' || value === 'DEPT_TODO') return 'DEPT_TODO';
  if (value === 'DONE' || value === 'PROCESSED' || value === 'DEPT_DONE') return 'DEPT_DONE';
  if (value === 'MINE' || value === 'MY') return 'MINE';
  return 'MINE';
};
const activeTab = ref(tabFromRoute());
const query = reactive({ keyword: '', status: '', slaStatus: '', pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const listError = ref('');
let listGeneration = 0;

const isCreateRoute = computed(() => route.name === 'SupportRequestCreate' || /\/new$/.test(route.path || ''));
const currentTabLabel = computed(() => tabs.find(item => item.value === activeTab.value)?.label || '我的申请');

const emptyForm = () => ({
  sourceType: 'EXISTING_CUSTOMER', sourceTouchTaskId: '', custId: '', productIds: [],
  otherDemand: '', supportDeptId: '', confirmParallel: false, attachmentIds: []
});
const form = reactive(emptyForm());
const formRef = ref();
const formError = ref('');
const saving = ref(false);
const sourceLocked = ref(false);
const manualDemandSelected = ref(false);
const customers = ref([]);
const customerLoading = ref(false);
const products = ref([]);
const productsLoading = ref(false);
const supportDepartments = ref([]);
const attachmentFiles = ref([]);
const selectedCustomer = computed(() => customers.value.find(item => String(item.id) === String(form.custId)) || {});
const requiresSupportDept = computed(() => !form.productIds.length || manualDemandSelected.value || Boolean(String(form.otherDemand || '').trim()));

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detail = ref(null);
const detailMode = ref('MINE');
const logs = ref([]);
const submitting = ref(false);
const withdrawing = ref(false);
const logForm = reactive({
  content: '', operatorLocation: '', checkInTime: '', photoUrls: [], photoFileIds: [], clientUuid: '',
  longitude: null, latitude: null
});
const completionSummary = ref('');
const lastLogContent = ref('');
const savingLog = ref(false);
const completing = ref(false);
const uploading = ref(false);
const locating = ref(false);
const geoError = ref('');
const locationLoadedFor = ref('');

const dispatchVisible = ref(false);
const dispatchTarget = ref(null);
const dispatchForm = reactive({ assignedEmpId: '', dispatchRemark: '' });
const departmentEmployees = ref([]);
const dispatchError = ref('');
const dispatching = ref(false);

function toArray(value) {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.records)) return value.records;
  if (Array.isArray(value?.list)) return value.list;
  if (Array.isArray(value?.content)) return value.content;
  return [];
}

function pageRows(value) {
  const records = toArray(value);
  return { records, total: Number(value?.total ?? records.length) || 0 };
}

function flattenOrgNodes(nodes, result = []) {
  toArray(nodes).forEach(node => {
    const code = node.code ?? node.orgCode ?? node.deptNo;
    const name = node.name ?? node.orgName ?? code;
    if (code != null) result.push({ code: String(code), name: String(name) });
    if (Array.isArray(node.children)) flattenOrgNodes(node.children, result);
  });
  return result;
}

function customerLabel(item) {
  const suffix = item.custNo || item.unifiedCreditCode || item.id;
  return [item.custName || item.customerName || item.name, suffix].filter(Boolean).join('（') + (suffix ? '）' : '');
}
function customerName(row) { return row?.custName || row?.customerName || row?.custInfo?.name || row?.custId || '-'; }
function productName(row) {
  if (!row) return '';
  if (row.productName) return row.productName;
  if (Array.isArray(row.products)) return row.products.map(item => item.name || item.productName || item.id).filter(Boolean).join('、');
  return row.product?.name || row.product?.productName || '';
}
function employeeLabel(item) { return [item.name || item.empName, item.id || item.empId].filter(Boolean).join(' · '); }
function assignedName(row) { return row?.assignedEmpName || row?.assignedName || row?.assignedEmpId || '-'; }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-'; }
function statusLabel(value) { return statusMap[value] || value || '-'; }
function statusType(value) { return ({ DRAFT: 'info', IN_APPROVAL: 'warning', IN_PROGRESS: 'warning', COMPLETED: 'success', REJECTED: 'danger', CANCELLED: 'info' }[value] || 'info'); }
function processStatusLabel(value) { return ({ COMPLETED: '已完成', ACTIVE: '处理中', PENDING: '待处理', REJECTED: '已驳回' }[value] || value || '-'); }
function resultLabel(value) { return ({ SUCCESS: '办理成功', FAILED: '驳回', CANCELLED: '已取消' }[value] || value || '-'); }
function slaValue(row) { return String(row?.slaStatus || row?.sla?.status || row?.slaColor || '').toUpperCase(); }
function slaClass(row) { return ({ GREEN: 'normal', YELLOW: 'warn', RED: 'overdue' }[slaValue(row)] || 'unknown'); }
function slaLabel(row) { return ({ GREEN: '正常', YELLOW: '预警', RED: '超时' }[slaValue(row)] || row?.slaStatus || '-'); }
function processNodes(item) { return item?.processNodes || item?.processMap?.nodes || item?.nodes || []; }
function photoUrls(log) {
  const value = log?.photoUrls || log?.photos || log?.attachments || log?.files || [];
  if (!Array.isArray(value)) return [];
  return value.map(item => {
    if (typeof item === 'string') return item;
    if (item?.url || item?.downloadUrl || item?.fileUrl) return item.url || item.downloadUrl || item.fileUrl;
    return item?.id ? `/api/files/${encodeURIComponent(item.id)}/download` : '';
  }).filter(Boolean);
}

async function loadCustomers(keyword) {
  customerLoading.value = true;
  try {
    const result = await listSupportCustomers({ keyword: String(keyword || '').trim() || undefined, pageNo: 1, pageSize: 20 });
    customers.value = toArray(result).map(item => ({ ...item, id: item.id ?? item.custId }));
  } catch (error) {
    customers.value = [];
    formError.value = error?.message || '客户查询失败';
  } finally { customerLoading.value = false; }
}
async function searchCustomers(keyword) { await loadCustomers(keyword); }
function selectCustomer(id) {
  if (!id) return;
  const item = customers.value.find(row => String(row.id ?? row.custId) === String(id));
  if (item && item.id == null) item.id = item.custId;
}

async function loadCreateOptions() {
  productsLoading.value = true;
  try {
    products.value = toArray(await listSupportProducts({ custId: form.custId || undefined }));
  } catch (error) {
    products.value = [];
    formError.value = error?.message || '支持产品加载失败';
  } finally { productsLoading.value = false; }
  try {
    supportDepartments.value = flattenOrgNodes(await getOrgTree({ strict: true }));
  } catch (error) {
    supportDepartments.value = [];
    formError.value = formError.value || error?.message || '支持部门加载失败';
  }
}

function resetForm() {
  Object.assign(form, emptyForm());
  formError.value = '';
  customers.value = [];
  attachmentFiles.value = [];
  sourceLocked.value = false;
  manualDemandSelected.value = false;
}
async function openCreate() {
  resetForm();
  const sourceTouchTaskId = String(route.query?.sourceTouchTaskId || '').trim();
  const custId = String(route.query?.custId || '').trim();
  Object.assign(form, {
    sourceType: sourceTouchTaskId ? 'TOUCH_TASK' : 'EXISTING_CUSTOMER',
    sourceTouchTaskId, custId
  });
  sourceLocked.value = Boolean(sourceTouchTaskId || custId && sourceTouchTaskId);
  if (custId) {
    customerLoading.value = true;
    try {
      const result = await listSupportCustomers({ custId, pageNo: 1, pageSize: 1 });
      const resultRows = toArray(result);
      customers.value = resultRows.length ? resultRows.map(item => ({ ...item, id: item.id ?? item.custId })) : [{ id: custId, custName: route.query?.custName || custId }];
    } catch (error) {
      customers.value = [{ id: custId, custName: route.query?.custName || custId }];
      formError.value = error?.message || '客户信息反显失败';
    } finally { customerLoading.value = false; }
  }
  await loadCreateOptions();
}

function toUploadUrl(result, file) {
  if (result?.url || result?.downloadUrl) return result.url || result.downloadUrl;
  const id = result?.id || result?.fileId || result?.fileObjectId;
  return id ? `/api/files/${encodeURIComponent(id)}/download#${encodeURIComponent(result.fileName || file?.name || 'photo')}` : '';
}
function clientUuid(prefix = 'SUPPORT_LOG:') {
  return `${prefix}${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`}`;
}
function coordinate(value) {
  if (value === null || value === undefined || value === '') return undefined;
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}
function coordinateLabel(longitude, latitude) {
  return `经度 ${longitude.toFixed(6)}，纬度 ${latitude.toFixed(6)}`;
}
function requestCurrentLocation(force = false) {
  const requestId = detail.value?.id ? String(detail.value.id) : '';
  if (!requestId || (!force && locationLoadedFor.value === requestId)) return;
  locationLoadedFor.value = requestId;
  geoError.value = '';
  const geolocation = globalThis.navigator?.geolocation;
  if (!geolocation?.getCurrentPosition) {
    geoError.value = '无法自动定位，请手工填写定位地址';
    return;
  }
  locating.value = true;
  const success = position => {
    const longitude = coordinate(position?.coords?.longitude);
    const latitude = coordinate(position?.coords?.latitude);
    if (longitude === undefined || latitude === undefined) {
      geoError.value = '自动定位结果无效，请手工填写定位地址';
    } else {
      logForm.longitude = longitude;
      logForm.latitude = latitude;
      if (!String(logForm.operatorLocation || '').trim()) {
        logForm.operatorLocation = coordinateLabel(longitude, latitude);
      }
      geoError.value = '';
    }
    locating.value = false;
  };
  const failure = () => {
    geoError.value = '自动定位失败，请手工填写定位地址';
    locating.value = false;
  };
  try {
    geolocation.getCurrentPosition(success, failure, {
      enableHighAccuracy: true, timeout: 8000, maximumAge: 60000
    });
  } catch (_) {
    failure();
  }
}
async function uploadAttachment(options) {
  try {
    const result = await uploadSupportPhoto(options.file);
    const id = result?.id || result?.fileId || result?.fileObjectId;
    if (!id) throw new Error('上传结果缺少文件 ID');
    if (!form.attachmentIds.includes(id)) form.attachmentIds.push(id);
    options.onSuccess?.(result);
  } catch (error) {
    options.onError?.(error);
    formError.value = error?.message || '附件上传失败';
  }
}
async function uploadLogPhoto(options) {
  uploading.value = true;
  try {
    if (!options.file?.type?.startsWith('image/')) throw new Error('仅支持图片文件');
    const result = await uploadSupportPhoto(options.file);
    const url = toUploadUrl(result, options.file);
    if (!url) throw new Error('上传结果缺少文件地址');
    const fileId = result?.id || result?.fileId || result?.fileObjectId || null;
    if (!logForm.photoUrls.includes(url)) {
      // Keep URL and file ID indexes aligned so removing a photo cannot bind
      // the wrong file.  The current backend consumes fileIds; newer clients
      // can still render the URL when an upload adapter only returns a URL.
      logForm.photoFileIds.push(fileId);
      logForm.photoUrls.push(url);
    }
    options.onSuccess?.(result);
  } catch (error) {
    options.onError?.(error);
    ElMessage.error(error?.message || '照片上传失败');
  } finally { uploading.value = false; }
}
function removePhoto(url) {
  const index = logForm.photoUrls.indexOf(url);
  if (index < 0) return;
  logForm.photoUrls.splice(index, 1);
  logForm.photoFileIds.splice(index, 1);
}

function validateForm() {
  if (!form.custId) return '请选择客户';
  if (!form.productIds.length && !String(form.otherDemand || '').trim()) return '请选择支持产品或填写其他需求';
  if (manualDemandSelected.value && !String(form.otherDemand || '').trim()) return '请填写其他/手工需求';
  if (requiresSupportDept.value && !form.supportDeptId) return '场景 B 必须选择支持部门';
  return '';
}
async function createDraft() {
  const error = validateForm();
  if (error) { ElMessage.warning(error); formError.value = error; return null; }
  saving.value = true;
  formError.value = '';
  const payload = {
    sourceType: form.sourceType, sourceTouchTaskId: form.sourceTouchTaskId || undefined,
    custId: form.custId, productIds: form.productIds, otherDemand: String(form.otherDemand || '').trim(),
    supportDeptId: form.supportDeptId || undefined, confirmParallel: form.confirmParallel,
    attachmentIds: form.attachmentIds
  };
  try {
    return await createSupportRequest(payload);
  } catch (error) {
    if (error?.code === 'BIZ-40907' && !form.confirmParallel) {
      try {
        await ElMessageBox.confirm(
          error.message || '该客户已存在进行中的中台支持，是否仍要继续？',
          '并行流程确认',
          { type: 'warning', confirmButtonText: '继续创建', cancelButtonText: '取消' }
        );
        form.confirmParallel = true;
        return await createSupportRequest({ ...payload, confirmParallel: true });
      } catch (retryError) {
        // 用户取消时不展示错误；第二次创建失败时保留服务端真实原因。
        if (retryError === 'cancel' || retryError === 'close') return null;
        formError.value = retryError?.message || '中台支持创建失败';
        return null;
      }
    }
    formError.value = error?.message || '中台支持创建失败';
    return null;
  } finally { saving.value = false; }
}
async function saveDraft() {
  const result = await createDraft();
  if (!result) return;
  ElMessage.success('中台支持草稿已保存');
  goList();
}
async function saveAndSubmit() {
  const result = await createDraft();
  if (!result) return;
  const requestItems = Array.isArray(result?.requests) ? result.requests : (result?.id ? [result] : []);
  if (!requestItems.length) {
    formError.value = '创建结果缺少申请明细，未提交任何流程';
    return;
  }
  saving.value = true;
  try {
    // 后端按产品拆单后，必须逐条提交，避免其中一条失败时伪造整批成功。
    for (const item of requestItems) await submitSupportRequest(item.id);
    ElMessage.success(requestItems.length > 1 ? `已创建并提交 ${requestItems.length} 条中台支持申请` : '中台支持已提交');
    goList();
  } catch (error) {
    formError.value = error?.message || '中台支持提交失败，请从我的申请继续处理未提交草稿';
  } finally { saving.value = false; }
}

async function loadList() {
  const generation = ++listGeneration;
  loading.value = true;
  listError.value = '';
  try {
    const params = {
      keyword: String(query.keyword || '').trim() || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    };
    const result = activeTab.value === 'MINE'
      ? await listSupportRequests(params)
      : await listSupportDeptRequests(params);
    if (generation !== listGeneration) return;
    const page = pageRows(result);
    let nextRows = page.records;
    if (query.slaStatus) nextRows = nextRows.filter(row => slaValue(row) === query.slaStatus);
    if (activeTab.value === 'DEPT_DONE' && !query.status) {
      nextRows = nextRows.filter(row => ['COMPLETED', 'REJECTED', 'CANCELLED'].includes(row.status));
    }
    if (activeTab.value === 'DEPT_TODO' && !query.status) {
      nextRows = nextRows.filter(row => !['COMPLETED', 'REJECTED', 'CANCELLED'].includes(row.status));
    }
    rows.value = nextRows;
    total.value = query.slaStatus || activeTab.value !== 'MINE' && !query.status ? nextRows.length : page.total;
  } catch (error) {
    if (generation === listGeneration) {
      rows.value = [];
      total.value = 0;
      listError.value = error?.message || '中台支持列表加载失败';
    }
  } finally {
    if (generation === listGeneration) loading.value = false;
  }
}
function selectTab(value) { activeTab.value = value; query.pageNo = 1; loadList(); }
function search() { query.pageNo = 1; loadList(); }
function resetQuery() { Object.assign(query, { keyword: '', status: '', slaStatus: '', pageNo: 1 }); loadList(); }
function goList() { router.push(SUPPORT_LIST_PATH); }
function goNew() { router.push(`${SUPPORT_LIST_PATH}/new`); }

function canSubmit(row) { return row?.canSubmit ?? row?.canOperate?.canSubmit ?? row?.status === 'DRAFT'; }
function canWithdraw(row) { return row?.canCancel ?? row?.canOperate?.canCancel ?? ['IN_APPROVAL', 'IN_PROGRESS'].includes(row?.status); }
function canDispatch(row) { return row?.canDispatch ?? row?.canOperate?.canDispatch ?? (row?.status === 'IN_APPROVAL' && !row?.assignedEmpId); }
function canHandle(row) { return row?.canComplete ?? row?.canOperate?.canComplete ?? ['IN_APPROVAL', 'IN_PROGRESS'].includes(row?.status); }

async function submitRow(row) {
  if (!row?.id || submitting.value) return;
  try {
    await ElMessageBox.confirm(`确认提交中台支持申请 ${row.requestNo || row.id}？`, '提交确认', { type: 'warning' });
  } catch { return; }
  submitting.value = true;
  try {
    await submitSupportRequest(row.id);
    ElMessage.success('中台支持已提交');
    await loadList();
    if (detailVisible.value) await openDetail(row.id, detailMode.value);
  } catch (error) {
    ElMessage.error(error?.message || '中台支持提交失败');
  } finally { submitting.value = false; }
}
async function withdrawRow(row) {
  if (!row?.id || withdrawing.value) return;
  let result;
  try {
    result = await ElMessageBox.prompt('请输入撤回理由（必填）', '撤回中台支持', {
      type: 'warning', inputValidator: value => Boolean(value?.trim()) || '撤回理由不能为空'
    });
  } catch { return; }
  const reason = String(result?.value || '').trim();
  if (!reason) return;
  withdrawing.value = true;
  try {
    await withdrawSupportRequest(row.id, reason);
    ElMessage.success('中台支持已撤回');
    await loadList();
    if (detailVisible.value) await openDetail(row.id, detailMode.value);
  } catch (error) {
    ElMessage.error(error?.message || '中台支持撤回失败');
  } finally { withdrawing.value = false; }
}

async function openDetail(id, mode = activeTab.value) {
  if (!id) return;
  const requestId = String(id);
  if (locationLoadedFor.value !== requestId) {
    Object.assign(logForm, {
      content: '', operatorLocation: '', checkInTime: '', photoUrls: [], photoFileIds: [], clientUuid: '',
      longitude: null, latitude: null
    });
    geoError.value = '';
    locationLoadedFor.value = '';
  }
  detailMode.value = mode;
  detailVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = null;
  logs.value = [];
  try {
    const loaded = mode === 'MINE' ? await getSupportRequest(id) : await getSupportDeptRequest(id);
    detail.value = loaded || {};
    const loadedLogs = mode === 'MINE' ? await listSupportRequestLogs(id) : await listSupportDeptLogs(id);
    logs.value = toArray(loadedLogs);
    if (mode === 'DEPT_TODO') requestCurrentLocation();
  } catch (error) {
    detailError.value = error?.message || '中台支持详情加载失败';
  } finally { detailLoading.value = false; }
}

async function addLog() {
  const id = detail.value?.id;
  if (!id || savingLog.value) return;
  if (!String(logForm.content || '').trim()) { ElMessage.warning('请填写过程内容'); return; }
  const longitude = coordinate(logForm.longitude);
  const latitude = coordinate(logForm.latitude);
  if (!String(logForm.operatorLocation || '').trim() && (longitude === undefined || latitude === undefined)) {
    ElMessage.warning('请填写定位地址或获取当前位置');
    return;
  }
  if (!logForm.checkInTime) {
    ElMessage.warning('请选择打卡时间');
    return;
  }
  if (!logForm.photoFileIds.filter(Boolean).length) {
    ElMessage.warning('请上传至少一张现场照片');
    return;
  }
  savingLog.value = true;
  try {
    const payload = {
      // The current backend contract uses checkinTime/locationAddress/fileIds;
      // the descriptive aliases stay in the payload for newer adapters.
      clientUuid: logForm.clientUuid || (logForm.clientUuid = clientUuid()),
      content: String(logForm.content).trim(),
      operatorLocation: String(logForm.operatorLocation || '').trim() || undefined,
      locationAddress: String(logForm.operatorLocation || '').trim() || undefined,
      checkInTime: logForm.checkInTime || undefined,
      checkinTime: logForm.checkInTime || undefined,
      photoUrls: logForm.photoUrls,
      fileIds: logForm.photoFileIds.filter(Boolean), longitude, latitude
    };
    await addSupportDeptLog(id, payload);
    lastLogContent.value = payload.content;
    completionSummary.value = completionSummary.value || payload.content;
    ElMessage.success('过程记录已保存');
    Object.assign(logForm, {
      content: '', checkInTime: '', photoUrls: [], photoFileIds: [], clientUuid: ''
    });
    await openDetail(id, detailMode.value);
  } catch (error) {
    ElMessage.error(error?.message || '过程记录保存失败');
  } finally { savingLog.value = false; }
}

async function complete(success) {
  const id = detail.value?.id;
  const summary = String(completionSummary.value || lastLogContent.value || '').trim();
  if (!id || completing.value) return;
  if (!summary) { ElMessage.warning('请填写办理结果摘要'); return; }
  try {
    await ElMessageBox.confirm(success ? '确认办理成功并结束中台支持？' : '确认驳回该中台支持申请？', success ? '办理成功' : '驳回确认', { type: 'warning' });
  } catch { return; }
  completing.value = true;
  try {
    await completeSupportRequest(id, {
      success, result: success ? 'SUCCESS' : 'FAILED', summary, handleResult: summary,
      outputAttachmentIds: logForm.photoFileIds.filter(Boolean), fileIds: logForm.photoFileIds.filter(Boolean), photoUrls: logForm.photoUrls,
      operatorLocation: logForm.operatorLocation || undefined,
      locationAddress: logForm.operatorLocation || undefined,
      checkInTime: logForm.checkInTime || undefined,
      checkinTime: logForm.checkInTime || undefined,
      longitude: coordinate(logForm.longitude), latitude: coordinate(logForm.latitude)
    });
    ElMessage.success(success ? '中台支持已完成' : '中台支持已驳回');
    await loadList();
    await openDetail(id, 'DEPT_DONE');
  } catch (error) {
    ElMessage.error(error?.message || '中台支持办理失败');
  } finally { completing.value = false; }
}

async function openDispatch(row) {
  dispatchTarget.value = row;
  dispatchForm.assignedEmpId = row?.assignedEmpId || '';
  dispatchForm.dispatchRemark = '';
  dispatchError.value = '';
  dispatchVisible.value = true;
  departmentEmployees.value = [];
  if (!row?.supportDeptId) { dispatchError.value = '申请缺少支持部门，无法派单'; return; }
  try {
    departmentEmployees.value = toArray(await listOrgUsers(row.supportDeptId, { pageNo: 1, pageSize: 100 }));
  } catch (error) {
    dispatchError.value = error?.message || '承接员工加载失败';
  }
}
async function dispatch() {
  if (!dispatchTarget.value?.id || !dispatchForm.assignedEmpId || dispatching.value) {
    if (!dispatchForm.assignedEmpId) dispatchError.value = '请选择承接员工';
    return;
  }
  dispatching.value = true;
  dispatchError.value = '';
  try {
    await dispatchSupportRequest(dispatchTarget.value.id, { ...dispatchForm });
    ElMessage.success('中台支持已派单');
    dispatchVisible.value = false;
    await loadList();
  } catch (error) {
    dispatchError.value = error?.message || '中台支持派单失败';
  } finally { dispatching.value = false; }
}

watch(() => [route.name, route.path, route.params?.id, route.query?.custId, route.query?.sourceTouchTaskId, route.query?.tab], async () => {
  activeTab.value = tabFromRoute();
  if (isCreateRoute.value) await openCreate();
  else if (route.params?.id) await openDetail(route.params.id, activeTab.value);
  else await loadList();
});

onMounted(async () => {
  if (isCreateRoute.value) await openCreate();
  else if (route.params?.id) await openDetail(route.params.id, activeTab.value);
  else await loadList();
});
</script>

<style scoped lang="scss">
.support-requests { display: flex; flex-direction: column; gap: 16px; }
.page-h, .section-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.section-head h2, .detail-section h3 { margin: 0; font-size: 16px; color: var(--color-text-strong); }
.section-head p { margin: 4px 0 0; color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
.result-count { flex: 0 0 auto; color: var(--color-text-muted); font-size: 12px; }
.card-section { overflow: hidden; border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface); box-shadow: var(--shadow-surface); }
.workbench { padding: 0 16px 12px; }
.support-tabs { display: flex; align-items: center; gap: 24px; min-height: 52px; border-bottom: 1px solid var(--color-border); }
.support-tab { position: relative; min-height: 52px; padding: 0 2px; border: 0; background: transparent; color: var(--color-text-muted); font: inherit; cursor: pointer; }
.support-tab.active { color: var(--color-brand-700); font-weight: 600; }
.support-tab.active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; background: var(--color-brand-700); content: ''; }
.filter-form { display: flex; flex-wrap: wrap; padding-top: 14px; }
.filter-form :deep(.el-form-item) { margin-bottom: 8px; }
.inline-error { margin: 12px 0; }
.card-section > .section-head { padding: 16px; }
.card-section > :deep(.el-table) { width: 100%; }
.pager { display: flex; justify-content: flex-end; padding: 14px 16px 16px; }
.form-section, .detail-section { margin: 0 16px 16px; padding: 14px 16px; border: 1px solid var(--color-border); border-radius: var(--radius-control); }
.support-form-card { padding-top: 16px; }
.support-form-card > .section-head { padding: 0 16px 16px; }
.support-form-card > .inline-error { margin: 0 16px 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.form-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 0 16px 16px; }
.field-help, small, .muted { color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
.product-grid { display: grid; grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 8px; }
.product-grid :deep(.el-checkbox) { display: flex; align-items: flex-start; height: auto; min-height: 44px; margin: 0; padding: 10px; white-space: normal; }
.product-grid small { display: block; margin-top: 2px; }
.manual-demand-check { margin: 0 0 8px; }
.location-field { display: flex; align-items: flex-start; gap: 8px; }
.location-field :deep(.el-input) { min-width: 0; }
.field-error { display: block; margin-top: 4px; color: var(--color-danger-fg); font-size: 12px; line-height: 18px; }
.detail-body { padding-bottom: 20px; }
.detail-section { margin-right: 0; margin-left: 0; border-right: 0; border-left: 0; border-radius: 0; }
.detail-title { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.detail-title h2 { margin: 0; font-size: 17px; color: var(--color-text-strong); }
.pre-wrap { white-space: pre-wrap; word-break: break-word; }
.log-head { margin-bottom: 6px; font-weight: 600; }
.history-photos, .photo-list { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 8px; }
.history-photos :deep(.el-image), .photo-item :deep(.el-image) { width: 64px; height: 64px; border-radius: 4px; }
.photo-item { position: relative; width: 64px; height: 64px; }
.photo-item button { position: absolute; top: -7px; right: -7px; width: 18px; height: 18px; padding: 0; border: 0; border-radius: 50%; background: var(--color-danger-fg); color: #fff; cursor: pointer; }
.detail-action-buttons { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.completion-summary { width: min(300px, 100%); }
.detail-footer-actions { padding: 0 16px; }
.sla-badge { display: inline-flex; align-items: center; gap: 4px; }
.sla-badge i { width: 8px; height: 8px; border-radius: 50%; background: var(--color-success-fg); }
.sla-badge.unknown { color: var(--color-text-muted); }.sla-badge.unknown i { background: var(--color-text-muted); }
.sla-badge.warn { color: var(--color-warning-fg); }.sla-badge.warn i { background: var(--color-warning-fg); }
.sla-badge.overdue { color: var(--color-danger-fg); font-weight: 600; }.sla-badge.overdue i { background: var(--color-danger-fg); }
@media (max-width: 760px) {
  .page-h, .section-head { flex-direction: column; align-items: stretch; }
  .form-grid, .product-grid { grid-template-columns: 1fr; }
  .support-tabs { gap: 14px; overflow-x: auto; }
  .support-tab { flex: 0 0 auto; }
  .form-footer { flex-wrap: wrap; }
}
</style>
