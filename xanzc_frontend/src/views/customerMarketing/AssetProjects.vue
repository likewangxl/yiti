<template>
  <main v-bp-overflow-tooltip class="bp-crud asset-projects" aria-labelledby="asset-project-title">
    <div class="page-h">
      <PageTitle id="asset-project-title" title="资产立项" />
      <el-button type="primary" @click="openCreate">新建资产立项</el-button>
    </div>

    <section class="card-section workbench" aria-label="资产立项工作台">
      <el-tabs v-model="query.tab" @tab-change="search">
        <el-tab-pane label="我的申请" name="MY" />
        <el-tab-pane label="待办" name="PENDING" />
        <el-tab-pane label="已办" name="PROCESSED" />
      </el-tabs>
      <el-form inline class="filter-form" @submit.prevent>
        <el-form-item label="综合查询"><el-input v-model="query.keyword" clearable placeholder="申请编号、客户或项目" @keyup.enter="search" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目标签">
          <el-select v-model="query.tag" clearable placeholder="全部" style="width: 130px">
            <el-option label="加急项目" value="URGENT" /><el-option label="重点项目" value="KEY" />
          </el-select>
        </el-form-item>
        <el-form-item label="发起日期">
          <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </section>

    <section class="card-section" :aria-busy="loading">
      <div class="section-head">
        <div><h2>资产立项列表</h2><p>金额单位：万元；待办和已办由工作流参与记录实时过滤。</p></div>
        <span>{{ loading ? '加载中…' : `共 ${total} 条` }}</span>
      </div>
      <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon class="inline-error" />
      <el-table :data="rows" stripe border row-key="id" empty-text="暂无资产立项记录">
        <el-table-column label="申请编号" min-width="180"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row.id)">{{ row.applyNo }}</el-button></template></el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="项目贷款金额" width="140" align="right"><template #default="{ row }">{{ money(row.projectLoanAmount) }}</template></el-table-column>
        <el-table-column label="授信金额" width="130" align="right"><template #default="{ row }">{{ money(row.creditAmount) }}</template></el-table-column>
        <el-table-column label="标签" width="150"><template #default="{ row }"><el-tag v-if="row.urgent" type="danger" effect="plain">加急</el-tag><el-tag v-if="row.keyProject" type="warning" effect="plain">重点</el-tag><span v-if="!row.urgent && !row.keyProject">-</span></template></el-table-column>
        <el-table-column label="当前节点 / 处理人" min-width="170"><template #default="{ row }"><div>{{ row.currentNode || '-' }}</div><small>{{ row.currentProcessor || '' }}</small></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="最后处理时间" width="168"><template #default="{ row }">{{ time(row.updatedTime) }}</template></el-table-column>
        <el-table-column label="操作" width="255" fixed="right" class-name="operation-cell">
          <template #default="{ row }">
            <BpAdaptiveRowActions>
              <template #primary><el-button link type="primary" @click="openDetail(row.id)">详情</el-button></template>
              <template #expanded>
                <el-button v-if="row.canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="row.canSubmit" link type="success" @click="submitRow(row)">提交</el-button>
                <el-button v-if="row.canDelete" link type="danger" @click="deleteRow(row)">删除</el-button>
                <el-button v-if="row.canCancel" link type="danger" @click="cancelRow(row)">撤回</el-button>
                <el-button v-if="row.canApplyUrgent" link type="warning" @click="openUrgent(row)">申请加急</el-button>
              </template>
              <template #compact><el-dropdown trigger="click" popper-class="bp-crud-menu"><el-button link>更多</el-button><template #dropdown><el-dropdown-menu>
                <el-dropdown-item v-if="row.canEdit" @click="openEdit(row)">编辑</el-dropdown-item>
                <el-dropdown-item v-if="row.canSubmit" @click="submitRow(row)">提交</el-dropdown-item>
                <el-dropdown-item v-if="row.canDelete" class="danger-item" @click="deleteRow(row)">删除</el-dropdown-item>
                <el-dropdown-item v-if="row.canCancel" class="danger-item" @click="cancelRow(row)">撤回</el-dropdown-item>
                <el-dropdown-item v-if="row.canApplyUrgent" @click="openUrgent(row)">申请加急</el-dropdown-item>
              </el-dropdown-menu></template></el-dropdown></template>
            </BpAdaptiveRowActions>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" background @change="load" /></div>
    </section>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑资产立项草稿' : '新建资产立项'" width="min(920px, 94vw)" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="inline-error" />
        <section class="form-section"><h3>客户信息</h3><div class="form-grid">
          <el-form-item label="客户" prop="custId">
            <el-select v-model="form.custId" filterable remote :remote-method="searchCustomers" :loading="customerLoading" placeholder="输入客户名称或客户号" style="width:100%" @change="selectCustomer">
              <el-option v-for="item in customers" :key="item.id" :label="`${item.custName}（${item.custNo || item.id}）`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="客户号"><el-input v-model="form.custNo" disabled /></el-form-item>
          <el-form-item label="统一社会信用代码"><el-input v-model="form.unifiedCreditCode" disabled /></el-form-item>
          <el-form-item label="主办人 / 主办机构"><el-input :model-value="[form.mainManagerName || form.mainManagerId, form.mainOrgName || form.mainOrgId].filter(Boolean).join(' / ')" disabled /></el-form-item>
        </div></section>
        <section class="form-section"><h3>触达来源（可选）</h3><div class="form-grid">
          <el-form-item label="来源触达任务ID"><el-input v-model="form.sourceTouchTaskId" :disabled="sourceLocked" /></el-form-item>
          <el-form-item label="来源工作日志ID"><el-input v-model="form.sourceWorklogId" :disabled="sourceLocked" /></el-form-item>
        </div></section>
        <section class="form-section"><h3>项目概况</h3><div class="form-grid">
          <el-form-item label="项目名称" prop="projectName"><el-input v-model="form.projectName" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="项目类型" prop="projectType"><el-select v-model="form.projectType" style="width:100%"><el-option v-for="item in projectTypes" :key="item.value" v-bind="item" /></el-select></el-form-item>
          <el-form-item label="业务类型" prop="bizType"><el-select v-model="form.bizType" style="width:100%"><el-option v-for="item in bizTypes" :key="item.value" v-bind="item" /></el-select></el-form-item>
          <el-form-item label="项目总投资（万元）" prop="projectTotalInvestment"><el-input-number v-model="form.projectTotalInvestment" :min="0" :precision="2" controls-position="right" /></el-form-item>
          <el-form-item label="项目贷款金额（万元）" prop="projectLoanAmount"><el-input-number v-model="form.projectLoanAmount" :min="0" :precision="2" controls-position="right" /></el-form-item>
        </div><el-alert v-if="amountWarning" :title="amountWarning" type="warning" :closable="false" show-icon /></section>
        <section class="form-section"><h3>授信与担保</h3><div class="form-grid">
          <el-form-item label="授信金额（万元）" prop="creditAmount"><el-input-number v-model="form.creditAmount" :min="0" :precision="2" controls-position="right" /></el-form-item>
          <el-form-item label="授信敞口（万元）" prop="creditExposureAmount"><el-input-number v-model="form.creditExposureAmount" :min="0" :max="form.creditAmount ?? undefined" :precision="2" controls-position="right" /></el-form-item>
          <el-form-item label="主要担保方式" prop="guaranteeType"><el-select v-model="form.guaranteeType" style="width:100%"><el-option v-for="item in guaranteeTypes" :key="item.value" v-bind="item" /></el-select></el-form-item>
        </div></section>
        <section class="form-section"><h3>标签与附件</h3><div class="checks"><el-checkbox v-model="form.urgent">启动时加急</el-checkbox><el-checkbox v-model="form.keyProject">重点项目</el-checkbox></div>
          <el-upload :http-request="uploadFile" :file-list="fileList" :on-remove="removeFile" multiple><el-button>上传附件</el-button><template #tip><div class="el-upload__tip">附件保存到平台文件中心并与本申请关联，单文件不超过 50MB。</div></template></el-upload>
        </section>
      </el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button :loading="saving" @click="save(false)">保存草稿</el-button><el-button type="primary" :loading="saving" @click="save(true)">保存并提交</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="资产立项详情" size="min(900px, 94vw)" :close-on-click-modal="false">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="inline-error" />
      <div v-loading="detailLoading" class="detail-body" v-if="detail">
        <section><h3>申请信息</h3><el-descriptions :column="2" border><el-descriptions-item label="申请编号">{{ detail.applyNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item><el-descriptions-item label="客户">{{ detail.customerName }}</el-descriptions-item><el-descriptions-item label="项目">{{ detail.projectName || '-' }}</el-descriptions-item><el-descriptions-item label="项目总投资">{{ money(detail.projectTotalInvestment) }}</el-descriptions-item><el-descriptions-item label="项目贷款金额">{{ money(detail.projectLoanAmount) }}</el-descriptions-item><el-descriptions-item label="授信金额">{{ money(detail.creditAmount) }}</el-descriptions-item><el-descriptions-item label="授信敞口">{{ money(detail.creditExposureAmount) }}</el-descriptions-item></el-descriptions></section>
        <section><h3>来源触达</h3><p>任务：{{ detail.sourceTouchTaskNo || detail.sourceTouchTaskId || '-' }}；工作日志：{{ detail.sourceWorklogNo || detail.sourceWorklogId || '-' }}</p></section>
        <section><h3>附件</h3><LeadAttachmentPreview :attachments="detail.attachments || []" /><el-empty v-if="!detail.attachments?.length" description="暂无附件" /></section>
        <section><h3>流程进度</h3><el-table :data="detail.processNodes || []" size="small" border><el-table-column prop="nodeName" label="节点" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="assigneeName" label="处理人" width="120" /></el-table></section>
        <section><h3>审批记录</h3><el-table :data="detail.approvalLogs || []" size="small" border><el-table-column prop="nodeName" label="节点" /><el-table-column prop="operatorName" label="处理人" width="120" /><el-table-column prop="opinion" label="意见" /></el-table></section>
        <section><h3>加急记录</h3><el-table :data="detail.urgentApplies || []" size="small" border><el-table-column prop="urgentApplyNo" label="加急申请编号" /><el-table-column prop="requestedAtNodeKey" label="发起节点" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="applyReason" label="加急原因" /></el-table></section>
      </div>
    </el-drawer>

    <el-dialog v-model="urgentVisible" title="申请中途加急" width="560px" :close-on-click-modal="false">
      <el-alert v-if="urgentError" :title="urgentError" type="error" :closable="false" show-icon class="inline-error" />
      <el-descriptions v-if="urgentContext" :column="1" border><el-descriptions-item label="申请编号">{{ urgentContext.applyNo }}</el-descriptions-item><el-descriptions-item label="客户 / 项目">{{ urgentContext.customerName }} / {{ urgentContext.projectName }}</el-descriptions-item><el-descriptions-item label="当前节点">{{ urgentContext.currentNodeName }}（{{ urgentContext.currentTaskId }}）</el-descriptions-item></el-descriptions>
      <el-alert v-if="urgentContext && !urgentContext.allowed" :title="urgentContext.unavailableReason" type="warning" :closable="false" />
      <el-form label-position="top"><el-form-item label="加急原因" required><el-input v-model="urgentReason" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="urgentVisible=false">取消</el-button><el-button type="primary" :disabled="!urgentContext?.allowed || !urgentReason.trim()" @click="submitUrgent">提交加急审批</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import PageTitle from '@/components/PageTitle.vue';
import BpAdaptiveRowActions from '@/components/BpAdaptiveRowActions.vue';
import LeadAttachmentPreview from '@/components/LeadAttachmentPreview.vue';
import {
  cancelAssetProject, createAssetProject, deleteAssetProject, getAssetProject,
  getAssetProjectCustomer, getAssetProjectUrgentContext, listAssetProjectCustomers, listAssetProjects,
  requestAssetProjectUrgent, submitAssetProject, updateAssetProject, uploadAssetProjectAttachment
} from '@/api/assetProjects';

const route = useRoute();
const query = reactive({ tab: 'MY', keyword: '', status: '', tag: '', dateRange: [], pageNo: 1, pageSize: 20 });
const rows = ref([]); const total = ref(0); const loading = ref(false);
const listError = ref('');
const formVisible = ref(false); const formRef = ref(); const saving = ref(false); const sourceLocked = ref(false);
const customers = ref([]); const customerLoading = ref(false); const fileList = ref([]);
const formError = ref('');
const detailVisible = ref(false); const detailLoading = ref(false); const detail = ref(null);
const detailError = ref('');
const urgentVisible = ref(false); const urgentContext = ref(null); const urgentReason = ref('');
const urgentError = ref('');
const emptyForm = () => ({ id: null, custId: null, custNo: '', customerName: '', unifiedCreditCode: '', mainManagerId: '', mainManagerName: '', mainOrgId: '', mainOrgName: '', sourceTouchTaskId: null, sourceWorklogId: null, projectName: '', projectType: '', bizType: '', guaranteeType: '', projectTotalInvestment: null, projectLoanAmount: null, creditAmount: null, creditExposureAmount: null, urgent: false, keyProject: false, lockVersion: 0, attachmentIds: [] });
const form = reactive(emptyForm());
const required = (message) => [{ required: true, message, trigger: 'change' }];
const rules = { custId: required('请选择客户'), projectName: required('请输入项目名称'), projectType: required('请选择项目类型'), bizType: required('请选择业务类型'), guaranteeType: required('请选择担保方式'), projectTotalInvestment: required('请输入项目总投资'), projectLoanAmount: required('请输入项目贷款金额'), creditAmount: required('请输入授信金额'), creditExposureAmount: required('请输入授信敞口') };
const statusOptions = [{ label:'草稿',value:'DRAFT' },{ label:'审批中',value:'IN_APPROVAL' },{ label:'已完成',value:'COMPLETED' },{ label:'已驳回',value:'REJECTED' },{ label:'已撤回',value:'CANCELLED' }];
const projectTypes = [{label:'固定资产项目',value:'FIXED_ASSET'},{label:'流动资金项目',value:'WORKING_CAPITAL'},{label:'并购项目',value:'MERGER'}];
const bizTypes = [{label:'项目贷款',value:'PROJECT_LOAN'},{label:'流动资金贷款',value:'WORKING_CAPITAL_LOAN'},{label:'综合授信',value:'COMPREHENSIVE_CREDIT'}];
const guaranteeTypes = [{label:'信用',value:'CREDIT'},{label:'保证',value:'GUARANTEE'},{label:'抵押',value:'MORTGAGE'},{label:'质押',value:'PLEDGE'}];
const statusMap = Object.fromEntries(statusOptions.map(item => [item.value,item.label]));
const amountWarning = computed(() => form.projectLoanAmount!=null&&form.projectTotalInvestment!=null&&Number(form.projectLoanAmount)>Number(form.projectTotalInvestment)
  ? '项目贷款金额高于项目总投资，请核对业务口径（当前仅提示，不阻断保存或提交）'
  : '');
const statusLabel = value => statusMap[value] || value || '-';
const statusType = value => ({DRAFT:'info',IN_APPROVAL:'warning',COMPLETED:'success',REJECTED:'danger',CANCELLED:'info'}[value] || 'info');
const time = value => value ? String(value).replace('T',' ').slice(0,19) : '-';
const money = value => value == null ? '-' : `${Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})} 万元`;

async function load(){ loading.value=true; listError.value=''; try { const params={ tab:query.tab,keyword:query.keyword||undefined,status:query.status||undefined,urgent:query.tag==='URGENT'?true:undefined,keyProject:query.tag==='KEY'?true:undefined,startDate:query.dateRange?.[0],endDate:query.dateRange?.[1],pageNo:query.pageNo,pageSize:query.pageSize }; const page=await listAssetProjects(params); rows.value=page.records||[]; total.value=page.total||0; } catch(error) { listError.value=error?.message||'资产立项列表加载失败'; } finally { loading.value=false; } }
function search(){ query.pageNo=1; load(); }
function reset(){ Object.assign(query,{keyword:'',status:'',tag:'',dateRange:[],pageNo:1}); load(); }
function resetForm(){ Object.assign(form,emptyForm()); customers.value=[]; fileList.value=[]; sourceLocked.value=false; formError.value=''; }
function fillCustomer(item){
  if(!item)return;
  Object.assign(form,{
    custId:item.id,
    customerName:item.custName||item.customerName||'',
    custNo:item.custNo||'',
    unifiedCreditCode:item.unifiedCreditCode||'',
    mainManagerId:item.mainManagerId||'',
    mainManagerName:item.mainManagerName||'',
    mainOrgId:item.mainOrgId||'',
    mainOrgName:item.mainOrgName||''
  });
}
async function openCreate(){
  resetForm();
  const custId=route.query.custId;
  Object.assign(form,{
    custId:custId?Number(custId):null,
    customerName:String(route.query.custName||''),
    sourceTouchTaskId:route.query.sourceTouchTaskId?Number(route.query.sourceTouchTaskId):null,
    sourceWorklogId:route.query.sourceWorklogId?Number(route.query.sourceWorklogId):null
  });
  sourceLocked.value=Boolean(form.sourceTouchTaskId||form.sourceWorklogId);
  formVisible.value=true;
  if(!form.custId)return;
  customerLoading.value=true;
  try{
    const customer=await getAssetProjectCustomer(form.custId);
    customers.value=customer?[customer]:[];
    fillCustomer(customer);
  }catch(error){
    formError.value=error?.message||'客户信息反显失败，请重新选择客户';
  }finally{customerLoading.value=false;}
}
async function openEdit(row){
  resetForm();
  formError.value='';
  formVisible.value=true;
  try{
    const full=await getAssetProject(row.id);
    Object.assign(form,full,{attachmentIds:(full.attachments||[]).map(file=>file.id)});
    customers.value=[{id:full.custId,custName:full.customerName,custNo:full.custNo,unifiedCreditCode:full.unifiedCreditCode,mainManagerId:full.mainManagerId,mainManagerName:full.mainManagerName,mainOrgId:full.mainOrgId,mainOrgName:full.mainOrgName}];
    fileList.value=(full.attachments||[]).map(file=>({name:file.fileName||file.originalName||file.id,url:file.url,id:file.id}));
    sourceLocked.value=Boolean(full.sourceTouchTaskId||full.sourceWorklogId);
  }catch(error){formError.value=error?.message||'资产立项草稿加载失败';}
}
async function searchCustomers(keyword){ customerLoading.value=true; try{ customers.value=await listAssetProjectCustomers({keyword:String(keyword||'').trim()||undefined,pageNo:1,pageSize:20}); }catch(error){formError.value=error?.message||'客户列表加载失败';customers.value=[];}finally{customerLoading.value=false;} }
function selectCustomer(id){ const item=customers.value.find(row=>String(row.id)===String(id)); if(item){fillCustomer(item);formError.value='';} }
async function uploadFile({file,onSuccess,onError}){ try{ if(Number(file?.size||0)>50*1024*1024)throw new Error('单个附件不能超过50MB'); const result=await uploadAssetProjectAttachment(file); const id=result?.id||result?.fileObjectId; if(!id)throw new Error('上传结果缺少文件ID'); if(!form.attachmentIds.includes(id))form.attachmentIds.push(id); formError.value=''; onSuccess?.(result); }catch(error){formError.value=error?.message||'附件上传失败';onError?.(error);} }
function removeFile(file){ const id=file.id||file.response?.id||file.response?.fileObjectId; form.attachmentIds=form.attachmentIds.filter(item=>item!==id); }
function amountError(){
  if(form.creditExposureAmount!=null&&form.creditAmount!=null&&Number(form.creditExposureAmount)>Number(form.creditAmount))return '授信敞口不能超过授信金额';
  return '';
}
async function save(andSubmit){
  formError.value='';
  if(!form.custId){ ElMessage.warning('请选择客户'); await formRef.value?.validateField('custId').catch(()=>{}); return; }
  if(andSubmit) await formRef.value?.validate();
  const invalidAmount=amountError();
  if(invalidAmount){formError.value=invalidAmount;ElMessage.warning(invalidAmount);return;}
  saving.value=true;
  try{
    const payload={...form,sourceTouchTaskId:form.sourceTouchTaskId?Number(form.sourceTouchTaskId):null,sourceWorklogId:form.sourceWorklogId?Number(form.sourceWorklogId):null};
    const saved=form.id?await updateAssetProject(form.id,payload):await createAssetProject(payload);
    if(andSubmit)await submitAssetProject(saved.id);
    ElMessage.success(andSubmit?'资产立项已提交':'草稿已保存');
    formVisible.value=false;
    await load();
  }catch(error){formError.value=error?.message||(andSubmit?'资产立项提交失败':'草稿保存失败');}
  finally{saving.value=false;}
}
async function submitRow(row){ await ElMessageBox.confirm(`确认提交 ${row.applyNo}？`,'提交确认'); await submitAssetProject(row.id); ElMessage.success('已提交审批'); load(); }
async function deleteRow(row){ const {value}=await ElMessageBox.prompt(`请输入删除草稿 ${row.applyNo} 的原因`,'删除确认',{type:'warning',inputValidator:value=>Boolean(value?.trim())||'删除原因不能为空'}); await deleteAssetProject(row.id,row.lockVersion,value); ElMessage.success('草稿已删除'); load(); }
async function cancelRow(row){ const {value}=await ElMessageBox.prompt('请输入撤回原因','撤回资产立项',{inputValidator:value=>Boolean(value?.trim())||'撤回原因不能为空'}); await cancelAssetProject(row.id,value); ElMessage.success('已撤回'); load(); }
async function openDetail(id){ detailVisible.value=true; detailLoading.value=true; detail.value=null; detailError.value=''; try{detail.value=await getAssetProject(id);}catch(error){detailError.value=error?.message||'资产立项详情加载失败';}finally{detailLoading.value=false;} }
async function openUrgent(row){ urgentReason.value=''; urgentContext.value=null; urgentError.value=''; urgentVisible.value=true; try{urgentContext.value=await getAssetProjectUrgentContext(row.id);}catch(error){urgentError.value=error?.message||'加急上下文加载失败';} }
async function submitUrgent(){ urgentError.value=''; try{await requestAssetProjectUrgent(urgentContext.value.assetProjectId,urgentReason.value); ElMessage.success('加急申请已提交'); urgentVisible.value=false; await load();}catch(error){urgentError.value=error?.message||'加急申请提交失败';} }

onMounted(async()=>{ if(['MY','PENDING','PROCESSED'].includes(String(route.query.tab||'').toUpperCase())) query.tab=String(route.query.tab).toUpperCase(); await load(); if(route.name==='AssetProjectCreate'||route.query.sourceTouchTaskId)await openCreate(); else if(route.params?.id)await openDetail(route.params.id); });
</script>

<style scoped lang="scss">
.asset-projects{display:flex;flex-direction:column;gap:16px}.page-h,.section-head{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.section-head h2{margin:0;font-size:16px}.section-head p{margin:4px 0;color:var(--color-text-muted);font-size:12px}.filter-form{display:flex;flex-wrap:wrap}.inline-error{margin:12px 0}.pager{display:flex;justify-content:flex-end;margin-top:16px}.form-section,.detail-body section{margin-bottom:16px;padding:14px 16px;border:1px solid var(--color-border);border-radius:var(--radius-control)}.form-section h3,.detail-body h3{margin:0 0 12px;font-size:15px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.checks{display:flex;gap:24px;margin-bottom:12px}.el-input-number{width:100%}small{color:var(--color-text-muted)}@media(max-width:760px){.form-grid{grid-template-columns:1fr}.page-h,.section-head{align-items:stretch;flex-direction:column}}
</style>
