<template>
  <main v-bp-overflow-tooltip class="page bp-crud marketing-lead-entry" aria-labelledby="marketing-lead-entry-title">
    <header class="page-head">
      <div>
        <PageTitle id="marketing-lead-entry-title" />
        <span>展示当前登录人的线索录入记录；支持单条录入和批量导入，存量客户会反显客户主档信息。</span>
      </div>
      <div class="page-actions">
        <el-button @click="openImport">批量导入</el-button>
        <el-button type="primary" @click="openCreate">录入线索</el-button>
      </div>
    </header>
    <el-tabs v-model="activeTab" @tab-change="loadActive">
      <el-tab-pane label="线索录入记录" name="manual">
        <div class="lead-stat-grid" aria-label="线索状态筛选">
          <button
            v-for="item in leadStatCards"
            :key="item.status"
            type="button"
            class="lead-stat-card"
            :class="[`tone-${item.tone}`, { 'is-selected': leadQuery.status === item.status }]"
            :aria-pressed="leadQuery.status === item.status"
            @click="toggleStatusCard(item.status)"
          >
            <span class="stat-card-top"><span>{{ item.label }}</span><i /></span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.hint }}</small>
          </button>
        </div>
        <el-card shadow="never" class="filter-card">
          <div class="toolbar">
            <el-form inline class="filter-form" @submit.prevent>
              <el-form-item label="关键词"><el-input v-model="leadQuery.keyword" clearable placeholder="企业名称 / 统一社会信用代码" /></el-form-item>
              <el-form-item label="状态"><el-select v-model="leadQuery.status" clearable style="width:140px" @change="filterByDropdown"><el-option v-for="item in leadStatuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
              <el-form-item class="filter-actions">
                <el-button type="primary" @click="searchLeads">查询</el-button>
                <el-button @click="resetLeadFilters">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>
        <el-table :data="leads" v-loading="leadLoading" border stripe class="lead-entry-table">
          <el-table-column prop="custName" label="企业名称" min-width="180" />
          <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190" />
          <el-table-column prop="leadSource" label="线索来源" width="110"><template #default="{row}">{{ leadSourceLabel(row.leadSource) }}</template></el-table-column>
          <el-table-column prop="entryEmpId" label="录入人" width="110" />
          <el-table-column prop="entryTime" label="录入时间" min-width="165"><template #default="{row}">{{ formatTime(row.entryTime) }}</template></el-table-column>
          <el-table-column prop="leadStatus" label="状态" width="110"><template #default="{row}"><el-tag :type="statusTagType(row.leadStatus)">{{ statusLabel(row.leadStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="230" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="showLead(row)">详情</el-button><el-button v-if="row.leadStatus === 'DRAFT'" link type="primary" @click="editLead(row)">编辑</el-button><el-button v-if="row.leadStatus === 'DRAFT'" link type="success" @click="submitLead(row)">提交审批</el-button><el-button v-if="row.leadStatus === 'REJECTED'" link type="danger" @click="editLead(row)">重新编辑</el-button></template></el-table-column>
        </el-table>
        <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="leadTotal" v-model:current-page="leadQuery.pageNo" v-model:page-size="leadQuery.pageSize" :page-sizes="[10, 20, 50, 100]" @change="loadLeads" /></div>
      </el-tab-pane>

      <el-tab-pane label="导入记录" name="imports">
        <el-card shadow="never" class="filter-card">
          <div class="toolbar">
            <el-form inline @submit.prevent><el-form-item label="导入文件名"><el-input v-model="batchQuery.keyword" clearable /></el-form-item><el-form-item label="导入状态"><el-select v-model="batchQuery.status" clearable style="width:150px"><el-option label="待确认" value="WAITING_CONFIRM" /><el-option label="成功" value="COMPLETED" /><el-option label="失败" value="ALL_FAILED" /></el-select></el-form-item><el-button type="primary" @click="searchBatches">查询</el-button></el-form>
          </div>
        </el-card>
        <el-table :data="batches" v-loading="batchLoading" border stripe class="lead-entry-table">
          <el-table-column prop="sourceFileName" label="导入文件名" min-width="220" />
          <el-table-column prop="importTime" label="导入时间" min-width="165"><template #default="{row}">{{ formatTime(row.importTime) }}</template></el-table-column>
          <el-table-column prop="importEmpId" label="导入人" width="110" />
          <el-table-column label="导入情况" width="120"><template #default="{row}"><el-tag :type="batchType(row.importStatus)">{{ batchLabel(row.importStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="统计" min-width="190"><template #default="{row}">总数 {{ row.totalCount || 0 }} / 成功 {{ row.validCount || 0 }} / 失败 {{ failureCount(row) }}</template></el-table-column>
          <el-table-column label="操作" width="120" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="openBatch(row)">详情</el-button></template></el-table-column>
        </el-table>
        <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="batchTotal" v-model:current-page="batchQuery.pageNo" v-model:page-size="batchQuery.pageSize" :page-sizes="[10, 20, 50, 100]" @change="loadBatches" /></div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer
      v-model="formVisible"
      :title="form.id ? '编辑客户线索' : '录入客户线索'"
      size="min(1080px, 92vw)"
      :close-on-click-modal="false"
      destroy-on-close
      class="lead-form-drawer"
    >
      <el-alert title="带 * 的字段将参与保存校验；存量客户信息会按客户名称或统一社会信用代码自动反显。" type="info" :closable="false" show-icon />
      <el-alert
        v-if="matchedCustomer"
        :title="`已匹配客户主档：${matchedCustomer.custName || ''}，开户状态：${matchedCustomer.isAccountOpened === 1 ? '已开户' : '未开户'}，主办：${matchedCustomer.mainManagerId ? `${matchedCustomer.mainManagerName || matchedCustomer.mainManagerId}（${matchedCustomer.mainManagerId}）` : '无'}`"
        type="warning"
        :closable="false"
        show-icon
        class="matched-customer-alert"
      />
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="lead-form">
        <section class="form-section">
          <div class="section-heading"><div><span>01</span><h3>基础信息</h3></div><p>用于客户识别和线索幂等校验</p></div>
          <div class="form-grid">
            <el-form-item label="线索类型" prop="leadType"><el-select v-model="form.leadType" style="width:100%"><el-option label="新客户开户线索" value="NEW_ACCOUNT" /><el-option label="存量客户营销线索" value="EXISTING_MARKETING" /></el-select></el-form-item>
            <el-form-item label="客户名称" prop="custName"><el-input v-model="form.custName" maxlength="200" show-word-limit @blur="lookupCustomerByName" /></el-form-item>
            <el-form-item label="统一社会信用代码" prop="unifiedCreditCode"><el-input v-model="form.unifiedCreditCode" maxlength="18" placeholder="18 位数字或大写字母" @input="normalizeCreditCode" @blur="lookupCustomerByCreditCode" /></el-form-item>
            <el-form-item label="是否开户" prop="isAccountOpenedSnapshot"><el-radio-group v-model="form.isAccountOpenedSnapshot"><el-radio :value="1">是</el-radio><el-radio :value="0">否</el-radio></el-radio-group></el-form-item>
            <el-form-item label="客户号"><el-input v-model="form.custNo" :disabled="form.isAccountOpenedSnapshot !== 1" maxlength="50" placeholder="已开户客户可填写" /></el-form-item>
            <el-form-item label="所属行业" prop="industry"><el-select v-model="form.industry" filterable clearable style="width:100%" placeholder="请选择"><el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="section-heading"><div><span>02</span><h3>经营属性</h3></div><p>均为选填，新开户企业可暂不填写</p></div>
          <div class="form-grid">
            <el-form-item label="所属集团类型" prop="groupType"><el-select v-model="form.groupType" clearable style="width:100%" placeholder="请选择"><el-option v-for="item in groupTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
            <el-form-item label="所属集团名称"><el-input v-model="form.groupName" :disabled="form.groupType === 'SINGLE'" maxlength="100" placeholder="非单一客户时填写" /></el-form-item>
            <el-form-item label="客户类型" prop="customerType"><el-select v-model="form.customerType" clearable style="width:100%" placeholder="请选择"><el-option v-for="item in customerTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
            <el-form-item label="是否基石客户" prop="isKeystone"><el-radio-group v-model="form.isKeystone"><el-radio :value="1">是</el-radio><el-radio :value="0">否</el-radio></el-radio-group></el-form-item>
            <el-form-item label="企业类型" prop="enterpriseType"><el-select v-model="form.enterpriseType" clearable style="width:100%" placeholder="请选择"><el-option v-for="item in enterpriseTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
            <el-form-item label="客户标签"><el-select v-model="form.tagIds" multiple filterable collapse-tags collapse-tags-tooltip style="width:100%" placeholder="可选"><el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.tagName || tag.name || tag.id" :value="tag.id" /></el-select></el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="section-heading"><div><span>03</span><h3>分配信息</h3></div><p>存量客户查到主办权后固定由主办人承接</p></div>
          <div class="ownership-result" :class="{ 'is-success': ownerCandidate }">
            <div><strong>{{ ownerCandidate ? '已查询到存量客户主办权' : '尚未查询到主办权' }}</strong><span>{{ ownerCandidate ? '分配方式已固定为主办专属，不可切换为公开或指定范围。' : '可按客户名称或 18 位统一社会信用代码查询客户主档。' }}</span></div>
            <div v-if="ownerCandidate" class="owner-identity"><small>主办客户经理</small><strong>{{ ownerCandidate.name }}（{{ ownerCandidate.id }}）</strong><span>{{ ownerCandidate.orgName || '-' }}</span></div>
          </div>
          <el-form-item label="分配方式" prop="distributionMode">
            <el-radio-group v-model="form.distributionMode">
              <el-radio-button value="PUBLIC" :disabled="Boolean(ownerCandidate)">全行公开认领</el-radio-button>
              <el-radio-button value="SCOPE" :disabled="Boolean(ownerCandidate)">指定客户经理范围</el-radio-button>
              <el-radio-button v-if="ownerCandidate" value="OWNER">主办专属</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.distributionMode === 'SCOPE'" label="指定客户经理范围" prop="managerEmpIds">
            <el-select v-model="form.managerEmpIds" multiple filterable remote reserve-keyword :remote-method="searchManagers" :loading="managerLoading" style="width:100%" placeholder="按姓名或工号搜索">
              <el-option v-for="person in managerOptions" :key="person.id" :label="`${person.name || person.id}（${person.id} · ${person.org || person.orgCode || '-'}）`" :value="person.id" />
            </el-select>
          </el-form-item>
          <div v-if="form.distributionMode === 'OWNER' && ownerCandidate" class="owner-readonly"><span>主办专属客户经理</span><strong>{{ ownerCandidate.name }}（{{ ownerCandidate.id }}）</strong><small>{{ ownerCandidate.orgName || '-' }}（由客户主办权自动带出）</small></div>
          <div class="distribution-outcome"><strong>审批后流向</strong><span>{{ distributionHint }}</span></div>
        </section>

        <section class="form-section">
          <div class="section-heading"><div><span>04</span><h3>补充资料</h3></div><p>金额单位为万元，附件在保存线索前上传</p></div>
          <el-form-item label="是否触达限制" prop="touchRestricted">
            <el-radio-group v-model="form.touchRestricted"><el-radio :value="1">是</el-radio><el-radio :value="0">否</el-radio></el-radio-group>
          </el-form-item>
          <el-form-item label="客户说明"><el-input v-model="form.customerDesc" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
          <div class="form-grid">
            <el-form-item label="授信金额（万元）"><el-input-number v-model="form.creditAmount" :min="0" :precision="2" :controls="false" style="width:100%" /></el-form-item>
            <el-form-item label="授信敞口金额（万元）"><el-input-number v-model="form.creditExposureAmount" :min="0" :precision="2" :controls="false" style="width:100%" /></el-form-item>
          </div>
          <el-form-item label="附件">
            <el-upload v-model:file-list="fileList" :auto-upload="false" multiple :limit="10" :on-change="syncAttachments" :on-remove="syncAttachments">
              <el-button>选择文件</el-button>
              <template #tip><div class="el-upload__tip">保存时统一上传，单文件不超过 10MB，最多 10 个。</div></template>
            </el-upload>
          </el-form-item>
        </section>
      </el-form>
      <template #footer><div class="drawer-footer"><span>{{ editingLeadStatus === 'REJECTED' ? '已退回线索修改后可重新提交审批' : form.id ? '正在编辑草稿线索' : '新建线索将先保存草稿' }}</span><div><el-button @click="formVisible=false">取消</el-button><el-button :loading="saving" @click="saveLead(false)">保存草稿</el-button><el-button type="primary" :loading="saving" @click="saveLead(true)">{{ editingLeadStatus === 'REJECTED' ? '重新提交审批' : '提交审批' }}</el-button></div></div></template>
    </el-drawer>

    <el-drawer v-model="leadDetailVisible" title="线索详情" size="min(900px, 95vw)"><MarketingLeadReadonlyDetail :detail="leadDetail" /></el-drawer>
    <MarketingLeadImportDetailDrawer v-model="batchDrawerVisible" :batch="selectedBatch" @confirm-action="confirmBatch" />

    <el-dialog v-model="importDialogVisible" title="批量导入" width="520px" :close-on-click-modal="false" @closed="resetImport">
      <div class="import-dialog-content">
        <div class="import-template-row">
          <span>请先下载模板，按模板填写后选择文件上传。</span>
          <el-button link type="primary" tag="a" href="/templates/lead-import-template.xlsx" download="lead-import-template.xlsx">下载模板</el-button>
        </div>
        <el-upload
          v-model:file-list="importFileList"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls,.csv"
          :on-change="importFile"
          :on-remove="clearImportFile"
          class="import-uploader"
        >
          <el-button>选择文件</el-button>
          <template #tip><div class="el-upload__tip">支持 xlsx、xls、csv，单个文件不超过 10MB。</div></template>
        </el-upload>
        <div class="import-dialog-help">
          <p>客户标签填写标签名称，多个用分号分隔；指定客户经理范围填写工号，多个用分号分隔。</p>
          <p>附件无法随表格导入，需在线索生成后单独补充。</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitImport">上传</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import PageTitle from '@/components/PageTitle.vue';
import MarketingLeadReadonlyDetail from '@/components/MarketingLeadReadonlyDetail.vue';
import MarketingLeadImportDetailDrawer from '@/components/MarketingLeadImportDetailDrawer.vue';
import { searchEmployees } from '@/api/employees';
import { useDict } from '@/composables/useDict';
import {
  confirmLeadImportBatch, createLeadImportBatch, createMarketingLead,
  downloadLeadImportSourceFile, getMarketingLead,
  listLeadEntries, listLeadImportBatches, listMarketingCustomerTags,
  lookupMarketingCustomer, submitMarketingLead, updateMarketingLead,
  uploadMarketingLeadAttachment
} from '@/api/marketingManagement';

const activeTab = ref('manual');
const PROCESS_VALID = 'PROCESS_VALID';
const ABANDON_REIMPORT = 'ABANDON_REIMPORT';
const leadQuery = reactive({ keyword: '', status: '', pageNo: 1, pageSize: 20 });
const batchQuery = reactive({ keyword: '', status: '', pageNo: 1, pageSize: 20 });
const leads = ref([]); const leadTotal = ref(0); const leadLoading = ref(false);
const leadStats = reactive({DRAFT:0,IN_APPROVAL:0,APPROVED:0,REJECTED:0});
const batches = ref([]); const batchTotal = ref(0); const batchLoading = ref(false); const uploading = ref(false);
const formVisible = ref(false); const saving = ref(false); const formRef = ref(null); const form = reactive(emptyForm()); const matchedCustomer = ref(null);
const editingLeadStatus = ref('');
const tagOptions = ref([]); const managerOptions = ref([]); const managerLoading = ref(false);
const fileList = ref([]); const pendingFiles = ref([]);
const importDialogVisible = ref(false); const importFileList = ref([]); const pendingImportFile = ref(null);
const leadDetailVisible = ref(false); const leadDetail = ref(null);
const batchDrawerVisible = ref(false); const selectedBatch = ref(null);
const IMPORT_FILE_MAX_SIZE = 10 * 1024 * 1024;
const IMPORT_FILE_EXTENSIONS = new Set(['xlsx', 'xls', 'csv']);
const leadStatuses = [{label:'草稿',value:'DRAFT'},{label:'待审批',value:'IN_APPROVAL'},{label:'已通过',value:'APPROVED'},{label:'已退回',value:'REJECTED'}];
const leadStatCards = computed(()=>[
  {status:'DRAFT',label:'草稿',value:leadStats.DRAFT,hint:'可继续补充',tone:'primary'},
  {status:'IN_APPROVAL',label:'待审批',value:leadStats.IN_APPROVAL,hint:'提交后待审核',tone:'warning'},
  {status:'APPROVED',label:'已通过',value:leadStats.APPROVED,hint:'审批已完成',tone:'success'},
  {status:'REJECTED',label:'已退回',value:leadStats.REJECTED,hint:'可修改后重提',tone:'danger'}
]);
const { options:industryOptions } = useDict('INDUSTRY');
const { options:groupTypeOptions } = useDict('GROUP_TYPE');
const { options:customerTypeOptions } = useDict('CUSTOMER_TYPE');
const { options:enterpriseTypeOptions } = useDict('ENTERPRISE_TYPE');

function emptyForm(){ return { id:null, leadType:'NEW_ACCOUNT', custName:'', unifiedCreditCode:'', isAccountOpenedSnapshot:0, custNo:'', industry:'', groupType:'', groupName:'', customerType:'', isKeystone:null, enterpriseType:'', tagIds:[], distributionMode:'PUBLIC', mainManagerId:'', mainOrgId:'', managerEmpIds:[], customerDesc:'', creditAmount:null, creditExposureAmount:null, attachmentIds:[], touchRestricted:1 }; }
const formRules = {
  leadType: [{ required:true, message:'请选择线索类型', trigger:'change' }],
  custName: [{ required:true, message:'请输入客户名称', trigger:'blur' }],
  unifiedCreditCode: [
    { required:true, message:'请输入统一社会信用代码', trigger:'blur' },
    { pattern:/^[0-9A-Z]{18}$/, message:'统一社会信用代码须为18位大写字母或数字', trigger:'blur' }
  ],
  isAccountOpenedSnapshot: [{ required:true, message:'请选择是否开户', trigger:'change' }],
  touchRestricted: [{ required:true, message:'请选择是否触达限制', trigger:'change' }],
  distributionMode: [{ required:true, message:'请选择分配方式', trigger:'change' }],
  managerEmpIds: [{ validator:(_,value,done)=>form.distributionMode==='SCOPE'&&!value?.length?done(new Error('请选择至少一名客户经理')):done(), trigger:'change' }]
};
const ownerCandidate = computed(() => {
  const id = matchedCustomer.value?.mainManagerId;
  if (!id) return null;
  return {
    id,
    name: matchedCustomer.value?.mainManagerName || id,
    orgName: matchedCustomer.value?.mainOrgName || matchedCustomer.value?.mainOrgId || ''
  };
});
const distributionHint = computed(() => ({
  PUBLIC:'审批通过后进入全行公开待认领客户池。',
  SCOPE:'审批通过后仅指定范围内的客户经理可见并认领。',
  OWNER:'审批通过后由存量客户主办客户经理承接。'
}[form.distributionMode]));
const pageRows = result => result?.records || result?.list || [];
const formatTime = value => value ? String(value).replace('T',' ').slice(0,19) : '-';
const statusLabel = value => ({DRAFT:'草稿',SUBMITTED:'已提交',IN_APPROVAL:'待审批',APPROVED:'已通过',REJECTED:'已退回',CANCELLED:'已取消'}[value] || value || '-');
const statusTagType = value => ({DRAFT:'primary',SUBMITTED:'warning',IN_APPROVAL:'warning',APPROVED:'success',REJECTED:'danger',CANCELLED:'info'}[value] || 'info');
const leadSourceLabel = value => ({MANUAL:'手工录入',LEAD_IMPORT:'批量导入'}[value] || value || '-');
const batchLabel = value => ({WAITING_CONFIRM:'待确认',COMPLETED:'成功',ALL_FAILED:'失败',ABANDONED:'已放弃',IMPORTING:'处理中'}[value] || value || '-');
const batchType = value => ({COMPLETED:'success',WAITING_CONFIRM:'warning',ALL_FAILED:'danger',ABANDONED:'info'}[value] || 'info');
const failureCount = row => Number(row.rejectedCount || 0) + Number(row.errorCount || 0) + Number(row.warningCount || 0);

async function loadLeads(){ leadLoading.value=true; try { const result=await listLeadEntries(leadQuery); leads.value=pageRows(result); leadTotal.value=Number(result?.total||0); } catch (error) { ElMessage.error(`线索录入记录加载失败：${error?.message||'请稍后重试'}`); } finally { leadLoading.value=false; } }
async function loadLeadStats(){
  try{
    const statuses=Object.keys(leadStats);
    const results=await Promise.all(statuses.map(status=>listLeadEntries({status,pageNo:1,pageSize:1})));
    statuses.forEach((status,index)=>{leadStats[status]=Number(results[index]?.total||0);});
  }catch(error){ElMessage.error(`线索状态统计加载失败：${error?.message||'请稍后重试'}`);}
}
async function refreshManualView(){await Promise.all([loadLeads(),loadLeadStats()]);}
async function loadBatches(){ batchLoading.value=true; try { const result=await listLeadImportBatches(batchQuery); batches.value=pageRows(result); batchTotal.value=Number(result?.total||0); } catch (error) { ElMessage.error(`导入记录加载失败：${error?.message||'请稍后重试'}`); } finally { batchLoading.value=false; } }
function loadActive(){ activeTab.value==='manual' ? refreshManualView() : loadBatches(); }
function searchLeads(){ leadQuery.pageNo=1; loadLeads(); } function searchBatches(){ batchQuery.pageNo=1; loadBatches(); }
function toggleStatusCard(status){leadQuery.status=leadQuery.status===status?'':status;leadQuery.pageNo=1;loadLeads();}
function filterByDropdown(){leadQuery.pageNo=1;loadLeads();}
function resetLeadFilters(){leadQuery.keyword='';leadQuery.status='';leadQuery.pageNo=1;loadLeads();}
function openImport(){ importDialogVisible.value=true; }
async function ensureTagOptions(){
  if(tagOptions.value.length)return;
  const result=await listMarketingCustomerTags({status:'ENABLED',approvalStatus:'APPROVED',pageNo:1,pageSize:100});
  tagOptions.value=pageRows(result);
}
function resetLeadForm(){ Object.assign(form,emptyForm()); editingLeadStatus.value=''; matchedCustomer.value=null; managerOptions.value=[]; fileList.value=[]; pendingFiles.value=[]; }
async function openCreate(){ resetLeadForm(); await ensureTagOptions(); formVisible.value=true; }
async function editLead(row){
  resetLeadForm(); await ensureTagOptions();
  const detail=await getMarketingLead(row.id); const lead=detail?.lead||row;
  editingLeadStatus.value=lead.leadStatus||row.leadStatus||'';
  const managerEmpIds=detail?.managerEmpIds||lead.managerEmpIds||(lead.managerScopes||[]).map(item=>item.managerEmpId);
  const tagIds=detail?.tagIds||lead.tagIds||(lead.tags||[]).map(tag=>tag.tagId??tag.id);
  const attachments=lead.attachments||detail?.attachments||[];
  Object.assign(form,emptyForm(),lead,{
    id:lead.id||row.id,
    custNo:lead.custNo??lead.custNoSnapshot??'',
    mainManagerId:lead.mainManagerId??lead.mainManagerIdSnapshot??'',
    mainOrgId:lead.mainOrgId??lead.mainOrgIdSnapshot??'',
    managerEmpIds:[...managerEmpIds], tagIds:[...tagIds],
    attachmentIds:attachments.map(file=>file.id||file.fileId).filter(Boolean),
    touchRestricted:lead.touchRestricted??1
  });
  matchedCustomer.value=detail?.currentCustomer||null;
  if(matchedCustomer.value?.mainManagerId){form.distributionMode='OWNER';form.mainManagerId=matchedCustomer.value.mainManagerId;form.mainOrgId=matchedCustomer.value.mainOrgId||'';form.managerEmpIds=[];}
  else if(form.distributionMode==='OWNER'){form.distributionMode='PUBLIC';form.mainManagerId='';form.mainOrgId='';}
  managerOptions.value=(lead.managerScopes||[]).map(item=>({id:item.managerEmpId,name:item.managerName,org:item.managerOrgName,orgCode:item.managerOrgId}));
  fileList.value=attachments.map(file=>({name:file.fileName||file.name||file.id,url:`/api/files/${file.id||file.fileId}/download`,status:'success',fileId:file.id||file.fileId}));
  formVisible.value=true;
}
function normalizeCreditCode(){ form.unifiedCreditCode=String(form.unifiedCreditCode||'').replace(/\s+/g,'').toUpperCase(); }
function clearPreviousMatchedSnapshot(preserveCreditCode=false){
  if(!matchedCustomer.value)return false;
  matchedCustomer.value=null;
  Object.assign(form,{
    unifiedCreditCode:preserveCreditCode?form.unifiedCreditCode:'',custNo:'',mainManagerId:'',mainOrgId:'',
    managerEmpIds:[],leadType:'NEW_ACCOUNT',isAccountOpenedSnapshot:0,industry:'',groupType:'',groupName:'',
    customerType:'',isKeystone:null,enterpriseType:'',customerDesc:'',creditAmount:null,creditExposureAmount:null
  });
  form.distributionMode='PUBLIC';
  return true;
}
function reflectMatchedCustomer(customer){
  matchedCustomer.value=customer||null;
  if(!customer){if(form.distributionMode==='OWNER')form.distributionMode='PUBLIC';return;}
  ['custName','industry','groupType','groupName','customerType','enterpriseType','isKeystone','customerDesc','creditAmount','creditExposureAmount'].forEach(key=>{if(customer[key]!=null)form[key]=customer[key];});
  form.unifiedCreditCode=customer.unifiedCreditCode||form.unifiedCreditCode;
  form.leadType='EXISTING_MARKETING';form.custNo=customer.custNo||'';form.isAccountOpenedSnapshot=customer.isAccountOpened??0;
  if(customer.mainManagerId){form.distributionMode='OWNER';form.mainManagerId=customer.mainManagerId;form.mainOrgId=customer.mainOrgId||'';form.managerEmpIds=[];}
  else if(form.distributionMode==='OWNER')form.distributionMode='PUBLIC';
  ElMessage.warning('已反显客户主档，请确认企业详细参数后保存');
}
async function lookupCustomerByName(){
  const customerName=form.custName.trim();if(!customerName)return;
  if(matchedCustomer.value?.custName!==customerName)clearPreviousMatchedSnapshot();
  try {
    const customer=await lookupMarketingCustomer({customerName});
    if(!customer){clearPreviousMatchedSnapshot();reflectMatchedCustomer(null);return;}
    reflectMatchedCustomer(customer);
  } catch (error) { ElMessage.error(`客户主档查询失败：${error?.message||'请稍后重试'}`); }
}
async function lookupCustomerByCreditCode(){
  normalizeCreditCode();const unifiedCreditCode=form.unifiedCreditCode;
  if(matchedCustomer.value?.unifiedCreditCode!==unifiedCreditCode)clearPreviousMatchedSnapshot(true);
  if(unifiedCreditCode.length!==18)return;
  try{
    const customer=await lookupMarketingCustomer({unifiedCreditCode});
    if(!customer){clearPreviousMatchedSnapshot(true);reflectMatchedCustomer(null);return;}
    reflectMatchedCustomer(customer);
  }
  catch(error){ElMessage.error(`客户主档查询失败：${error?.message||'请稍后重试'}`);}
}
async function searchManagers(keyword){ if(!keyword?.trim()){managerOptions.value=[];return;} managerLoading.value=true; try{managerOptions.value=await searchEmployees(keyword.trim(),30);}finally{managerLoading.value=false;} }
function syncAttachments(_,files){ fileList.value=files||[]; pendingFiles.value=fileList.value.filter(file=>file.raw).map(file=>file.raw); form.attachmentIds=fileList.value.map(file=>file.fileId).filter(Boolean); }
async function uploadPendingAttachments(){
  const attachmentIds=[];const persistedFiles=[];
  for(const uploadFile of fileList.value){
    let fileId=uploadFile.fileId;
    if(uploadFile.raw){
      if(uploadFile.raw.size>10*1024*1024)throw new Error(`附件 ${uploadFile.raw.name} 超过10MB`);
      const uploaded=await uploadMarketingLeadAttachment(uploadFile.raw);fileId=uploaded?.id||uploaded?.fileObjectId;
      if(!fileId)throw new Error(`附件 ${uploadFile.raw.name} 上传未返回文件ID`);
    }
    if(fileId){
      attachmentIds.push(fileId);
      const {raw:_raw,...persisted}=uploadFile;
      persistedFiles.push({...persisted,fileId,status:'success',url:uploadFile.url||`/api/files/${fileId}/download`});
    }
  }
  const ids=[...new Set(attachmentIds)];form.attachmentIds=ids;fileList.value=persistedFiles;pendingFiles.value=[];return ids;
}
function leadPayload(attachmentIds){ return {
  leadType:form.leadType,custName:form.custName.trim(),unifiedCreditCode:form.unifiedCreditCode.trim(),
  isAccountOpenedSnapshot:form.isAccountOpenedSnapshot,custNo:form.custNo||undefined,industry:form.industry||undefined,
  groupType:form.groupType||undefined,groupName:form.groupName||undefined,customerType:form.customerType||undefined,
  isKeystone:form.isKeystone,enterpriseType:form.enterpriseType||undefined,tagIds:[...form.tagIds],
  distributionMode:form.distributionMode,mainManagerId:form.distributionMode==='OWNER'?ownerCandidate.value?.id:undefined,
  mainOrgId:form.distributionMode==='OWNER'?(matchedCustomer.value?.mainOrgId||undefined):undefined,
  managerEmpIds:form.distributionMode==='SCOPE'?[...form.managerEmpIds]:[],customerDesc:form.customerDesc||undefined,
  creditAmount:form.creditAmount??undefined,creditExposureAmount:form.creditExposureAmount??undefined,
  attachmentIds,touchRestricted:form.touchRestricted
}; }
async function persistLeadDraft(){
  const attachmentIds=await uploadPendingAttachments();const payload=leadPayload(attachmentIds);
  if(form.id){await updateMarketingLead(form.id,payload);return form.id;}
  const created=await createMarketingLead(payload);const createdId=typeof created==='object'?created?.id:created;
  if(!createdId)throw new Error('线索草稿已请求保存，但未返回线索ID');
  form.id=createdId;return createdId;
}
async function saveLead(andSubmit=false){
  try{await formRef.value?.validate();}catch{return;}
  saving.value=true;
  try {
    const savedId=await persistLeadDraft();
    if(andSubmit){
      try{await submitMarketingLead(savedId);}
      catch(error){ElMessage.warning(`草稿已保存，但提交审批失败：${error?.message||'请稍后重试'}`);await refreshManualView();return;}
    }
    ElMessage.success(andSubmit?'线索已提交审批':'线索草稿已保存');formVisible.value=false;await refreshManualView();
  } catch (error) { ElMessage.error(`线索保存失败：${error?.message||'请稍后重试'}`); }
  finally { saving.value=false; }
}
async function showLead(row){ try { leadDetail.value=await getMarketingLead(row.id); leadDetailVisible.value=true; } catch (error) { ElMessage.error(`线索详情加载失败：${error?.message||'请稍后重试'}`); } }
async function submitLead(row){ try { await ElMessageBox.confirm('确认提交该线索审批？','提交审批'); await submitMarketingLead(row.id); ElMessage.success('已提交审批'); await refreshManualView(); } catch (error) { if(error!=='cancel'&&error!=='close') ElMessage.error(`提交失败：${error?.message||'请稍后重试'}`); } }
function validateImportFile(file){
  if(!file)return '请选择导入文件';
  const fileName=String(file.name||'');
  const extension=fileName.includes('.')?fileName.slice(fileName.lastIndexOf('.')+1).toLowerCase():'';
  if(!IMPORT_FILE_EXTENSIONS.has(extension))return '导入文件格式不支持：仅支持 .xlsx、.xls、.csv 文件';
  if(Number(file.size||0)===0)return '导入文件内容为空，请选择已填写的模板';
  if(Number(file.size||0)>IMPORT_FILE_MAX_SIZE)return '导入文件过大：单个文件不能超过10MB';
  return '';
}
function importFile(upload){
  if(!upload?.raw)return;
  const validationMessage=validateImportFile(upload.raw);
  if(validationMessage){clearImportFile();ElMessage.error(validationMessage);return;}
  importFileList.value=[upload];
  pendingImportFile.value=upload.raw;
}
function clearImportFile(){ importFileList.value=[]; pendingImportFile.value=null; }
function resetImport(){ importFileList.value=[]; pendingImportFile.value=null; }
const importFailureMessage = result => {
  const details=Array.isArray(result?.details)?result.details:[];
  const reasons=details.map(detail=>{
    const reason=detail?.errorMessage||detail?.warningMessage||detail?.reason;
    if(!reason)return '';
    const rowNo=detail?.rowNo??detail?.rowNumber;
    return rowNo===undefined||rowNo===null||rowNo===''?String(reason):`第${rowNo}行：${reason}`;
  }).filter(Boolean);
  const visibleReasons=reasons.slice(0,3);
  const remainingCount=reasons.length-visibleReasons.length;
  const remainingHint=remainingCount>0?`；另有 ${remainingCount} 条失败原因，请查看导入记录`:'';
  return reasons.length?`导入失败：${visibleReasons.join('；')}${remainingHint}`:'导入失败：未发现有效数据，请检查模板表头和数据内容';
};
function importRequestErrorMessage(error){
  return error?.response?.data?.message||error?.response?.data?.msg||error?.message||'导入请求失败，请稍后重试';
}
function importSuccessMessage(batch){
  return batch.importStatus==='COMPLETED'&&batch.approvalSummaryStatus==='IN_APPROVAL'
    ?'导入成功，线索已进入待审批状态':'导入文件已上传';
}
async function submitImport(){
  const file=pendingImportFile.value;
  if(!file){ElMessage.warning('请先选择导入文件');return;}
  const validationMessage=validateImportFile(file);
  if(validationMessage){ElMessage.error(validationMessage);return;}
  uploading.value=true;
  try{
    const result=await createLeadImportBatch(file);
    const batch=result?.batch||result||{};
    const validCount=Number(batch.validCount??result?.validCount);
    if(batch.importStatus==='ALL_FAILED'||validCount===0){
      ElMessage.error(importFailureMessage(result));
      return;
    }
    ElMessage.success(importSuccessMessage(batch));
    importDialogVisible.value=false;
    resetImport();
    activeTab.value='imports';
    await loadBatches();
  }catch(error){ElMessage.error(`导入失败：${importRequestErrorMessage(error)}`);}
  finally{uploading.value=false;}
}
function openBatch(row){ selectedBatch.value=row; batchDrawerVisible.value=true; }
async function confirmBatch(action){ const normalizedAction=action===PROCESS_VALID?PROCESS_VALID:ABANDON_REIMPORT; const actionLabel=normalizedAction===PROCESS_VALID?'仅处理正常数据':'放弃并重新导入'; try { const {value}=await ElMessageBox.prompt(`确认${actionLabel}？`,'导入批次确认',{inputPlaceholder:'可填写备注'}); await confirmLeadImportBatch(selectedBatch.value.id,{action:normalizedAction,remark:value}); ElMessage.success('批次已处理'); batchDrawerVisible.value=false; await loadBatches(); } catch (error) { if(error!=='cancel'&&error!=='close') ElMessage.error(`批次确认失败：${error?.message||'请稍后重试'}`); } }

watch(()=>form.groupType,value=>{if(value==='SINGLE')form.groupName='';});
watch(()=>form.distributionMode,value=>{
  if(ownerCandidate.value&&value!=='OWNER'){form.distributionMode='OWNER';return;}
  if(value==='PUBLIC'){form.managerEmpIds=[];form.mainManagerId='';form.mainOrgId='';}
  else if(value==='SCOPE'){form.mainManagerId='';form.mainOrgId='';}
  else if(value==='OWNER'&&ownerCandidate.value){form.managerEmpIds=[];form.mainManagerId=ownerCandidate.value.id;form.mainOrgId=matchedCustomer.value?.mainOrgId||'';}
});

// 文件下载由 MarketingLeadImportDetailDrawer 调用，保留显式引用用于页面契约审计。
void downloadLeadImportSourceFile;
refreshManualView();
</script>

<style scoped lang="scss">
.page-head { align-items: flex-start; display: flex; justify-content: space-between; margin-bottom: 14px; }
.page-head h1 { font-size: 18px; margin: 0; }
.page-head span { color: #909399; display: block; font-size: 12px; margin-top: 4px; }
.page-actions { display: flex; gap: 8px; }
.lead-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 12px; margin-bottom: 12px; }
.lead-stat-card { position: relative; overflow: hidden; display: grid; gap: 8px; min-height: 112px; padding: 16px 18px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; color: inherit; font: inherit; text-align: left; cursor: pointer; transition: border-color .2s ease, box-shadow .2s ease; }
.lead-stat-card::before { position: absolute; top: 0; bottom: 0; left: 0; width: 3px; background: var(--el-color-primary); content: ''; }
.lead-stat-card:hover { border-color: var(--el-color-primary-light-5); }
.lead-stat-card:focus-visible { outline: 2px solid var(--el-color-primary-light-3); outline-offset: 2px; }
.lead-stat-card.is-selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); box-shadow: 0 0 0 1px var(--el-color-primary-light-7) inset; }
.stat-card-top { display: flex; align-items: center; justify-content: space-between; color: #606266; font-size: 13px; }
.stat-card-top i { width: 8px; height: 8px; border-radius: 50%; background: var(--el-color-primary); box-shadow: 0 0 0 4px var(--el-color-primary-light-9); }
.lead-stat-card strong { color: #303133; font-size: 26px; line-height: 1; }
.lead-stat-card small { color: #909399; font-size: 12px; }
.lead-stat-card.tone-warning::before, .lead-stat-card.tone-warning .stat-card-top i { background: var(--el-color-warning); }
.lead-stat-card.tone-success::before, .lead-stat-card.tone-success .stat-card-top i { background: var(--el-color-success); }
.lead-stat-card.tone-danger::before, .lead-stat-card.tone-danger .stat-card-top i { background: var(--el-color-danger); }
.filter-card { margin-bottom: 14px; }
.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.toolbar { align-items: flex-start; display: flex; gap: 16px; justify-content: space-between; }
.filter-form { align-items: center; display: flex; flex-wrap: nowrap; gap: 12px; overflow-x: auto; }
.filter-form :deep(.el-form-item) { flex: 0 0 auto; margin-bottom: 18px; margin-right: 0; }
.filter-actions { align-items: center; display: flex; flex: 0 0 auto; gap: 8px; }
.filter-actions :deep(.el-form-item__content) { display: flex; gap: 8px; }
.import-dialog-content { display: grid; gap: 18px; }
.import-template-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; color: #606266; font-size: 13px; }
.import-uploader :deep(.el-upload__tip) { margin-top: 8px; }
.import-dialog-help { padding: 10px 12px; border-radius: 4px; background: #f5f7fa; color: #606266; font-size: 12px; line-height: 1.7; }
.import-dialog-help p { margin: 0; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.lead-entry-table { width: 100%; }
.matched-customer-alert { margin-top: 12px; }
.lead-form { display: grid; gap: 14px; margin-top: 16px; }
.form-section { padding: 18px 20px 2px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #ebeef5; }
.section-heading > div { display: flex; align-items: center; gap: 9px; }
.section-heading span { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; margin: 0; border-radius: 4px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: 11px; font-weight: 700; }
.section-heading h3 { margin: 0; color: #303133; font-size: 14px; }
.section-heading p { margin: 2px 0 0; color: #909399; font-size: 12px; }
.form-grid { display: grid; gap: 0 20px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.ownership-result { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 18px; margin-bottom: 16px; padding: 12px 14px; border: 1px solid #dcdfe6; border-left: 3px solid #909399; background: #f8fafc; }
.ownership-result > div { display: grid; gap: 3px; }
.ownership-result strong { color: #303133; font-size: 13px; }
.ownership-result span, .ownership-result small { color: #909399; font-size: 11px; }
.ownership-result.is-success { border-left-color: var(--el-color-success); background: var(--el-color-success-light-9); }
.owner-identity { min-width: 240px; padding-left: 16px; border-left: 1px solid #dcdfe6; }
.owner-readonly { display: grid; grid-template-columns: 180px minmax(180px, auto) 1fr; align-items: center; gap: 12px; margin: -2px 0 16px; padding: 12px; border: 1px solid var(--el-color-primary-light-7); background: var(--el-color-primary-light-9); }
.owner-readonly span, .owner-readonly small { color: #909399; font-size: 11px; }
.owner-readonly strong { color: var(--el-color-primary); font-size: 13px; }
.distribution-outcome { display: flex; gap: 12px; margin: -2px 0 16px; padding: 10px 12px; border-left: 3px solid var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: 12px; }
.distribution-outcome strong { flex: 0 0 auto; color: var(--el-color-primary); }
.distribution-outcome span { color: #606266; }
.drawer-footer { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.drawer-footer > span { color: #909399; font-size: 12px; }
.drawer-footer > div { display: flex; gap: 8px; }
@media (max-width: 680px) {
  .toolbar, .page-head, .drawer-footer { align-items: flex-start; flex-direction: column; }
  .page-actions { flex-wrap: wrap; }
  .lead-stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .form-grid, .ownership-result, .owner-readonly { grid-template-columns: 1fr; }
  .form-section { padding: 14px 14px 2px; }
  .section-heading { flex-direction: column; gap: 4px; }
  .owner-identity { padding: 10px 0 0; border-top: 1px solid #dcdfe6; border-left: 0; }
}
</style>
