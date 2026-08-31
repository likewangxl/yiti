<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head">
      <div><PageTitle /><span>录入、维护本人可见的客户线索；审批办理请进入“线索审批”。</span></div>
      <div class="page-actions">
        <el-button @click="openImport">批量导入线索</el-button>
        <el-button type="primary" @click="openCreate">录入客户线索</el-button>
      </div>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="客户名称 / 统一社会信用代码" @keyup.enter="search" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width:150px"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="归属机构"><el-input v-model="query.ownerOrgId" clearable placeholder="机构代码" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="leadNo" label="线索编号" min-width="190"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">{{ row.leadNo || row.id }}</el-button></template></el-table-column>
      <el-table-column prop="custName" label="客户名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="185" />
      <el-table-column label="线索类型" width="125"><template #default="{ row }">{{ leadTypeLabel(row.leadType) }}</template></el-table-column>
      <el-table-column label="分配方式" min-width="145"><template #default="{ row }">{{ distributionLabel(row.distributionMode) }}</template></el-table-column>
      <el-table-column prop="ownerOrgId" label="归属机构" min-width="130" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.leadStatus)">{{ statusLabel(row.leadStatus) }}</el-tag></template></el-table-column>
      <el-table-column prop="versionNo" label="版本" width="70"><template #default="{ row }">V{{ row.versionNo || 1 }}</template></el-table-column>
      <el-table-column label="更新时间" min-width="165"><template #default="{ row }">{{ formatTime(row.updatedTime || row.createdTime) }}</template></el-table-column>
      <el-table-column label="操作" width="220" fixed="right" class-name="operation-cell"><template #default="{ row }">
        <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        <template v-if="row.leadStatus === 'DRAFT'">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="success" @click="submitExisting(row)">提交审批</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[10,20,50,100]" @change="load" /></div>

    <el-dialog v-model="importVisible" title="批量导入线索" width="min(720px, 94vw)" destroy-on-close :close-on-click-modal="false">
      <el-alert
        title="模板中“是否触达限制”为必填列；每行只能填写“是”或“否”。请在文件中逐行填写，导入页面不提供统一设置。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-upload
        v-model:file-list="importFileList"
        drag
        action="#"
        accept=".csv,.xlsx,.xls"
        :auto-upload="false"
        :limit="1"
        :on-change="onImportFileChange"
        :on-remove="onImportFileRemove"
        class="lead-import-upload"
      >
        <div class="upload-copy">将线索导入文件拖到此处，或 <em>点击选择文件</em></div>
        <template #tip><div class="el-upload__tip">支持 csv/xlsx/xls，文件不超过 10MB，最多 5000 行。</div></template>
      </el-upload>
      <el-alert v-if="importError" :title="importError" type="error" :closable="false" show-icon class="import-error" />
      <div v-if="importPreview.batchId" class="import-preview-summary">
        <span>批次号：{{ importPreview.batchNo || importPreview.batchId }}</span>
        <span>预览 {{ importPreview.totalRows ?? 0 }} 行</span>
        <span v-if="importPreview.errorRows || importPreview.failCount" class="import-preview-error">
          错误 {{ importPreview.errorRows ?? importPreview.failCount }} 行
        </span>
      </div>
      <template #footer>
        <el-button @click="closeImport">取消</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!importFile" @click="previewImport">上传并预览</el-button>
        <el-button v-if="importPreview.batchId" type="success" :loading="importLoading" :disabled="!canExecuteImport" @click="executeImport">确认执行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑客户线索' : '录入客户线索'" width="min(980px, 94vw)" destroy-on-close :close-on-click-modal="false">
      <el-alert title="保存草稿后可继续修改；提交审批后本页面仅可查看。" type="info" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="lead-form">
        <h3>基础信息</h3>
        <div class="form-grid">
          <el-form-item label="线索类型" prop="leadType"><el-select v-model="form.leadType" style="width:100%"><el-option label="新客户开户线索" value="NEW_ACCOUNT" /><el-option label="存量客户营销线索" value="EXISTING_MARKETING" /></el-select></el-form-item>
          <el-form-item label="客户号"><el-input v-model="form.custNo" maxlength="50" /></el-form-item>
          <el-form-item label="客户名称" prop="custName"><el-input v-model="form.custName" maxlength="100" /></el-form-item>
          <el-form-item label="统一社会信用代码" prop="unifiedCreditCode"><el-input v-model="form.unifiedCreditCode" maxlength="18" @input="form.unifiedCreditCode = String(form.unifiedCreditCode || '').toUpperCase()" /></el-form-item>
          <el-form-item label="联系人"><el-input v-model="form.contactPerson" maxlength="50" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="form.contactMobile" maxlength="20" /></el-form-item>
        </div>

        <div v-if="CCRM_OWNERSHIP_QUERY_ENABLED" class="ownership-row">
          <div><strong>存量客户与主办权查询</strong><span>{{ ownershipText }}</span></div>
          <el-button :loading="ownershipLoading" @click="lookupOwnership">查询主办权</el-button>
        </div>

        <h3>经营信息</h3>
        <div class="form-grid">
          <el-form-item label="所属行业" prop="industry"><el-select v-model="form.industry" clearable filterable style="width:100%"><el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="客户类型" prop="customerType"><el-select v-model="form.customerType" clearable style="width:100%"><el-option v-for="item in customerTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="集团类型" prop="groupType"><el-select v-model="form.groupType" clearable style="width:100%"><el-option v-for="item in groupTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="所属集团名称"><el-input v-model="form.groupName" :disabled="form.groupType === 'SINGLE'" maxlength="100" /></el-form-item>
          <el-form-item label="企业类型" prop="enterpriseType"><el-select v-model="form.enterpriseType" clearable style="width:100%"><el-option v-for="item in enterpriseTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="线索来源"><el-select v-model="form.leadSource" clearable style="width:100%"><el-option v-for="item in leadSourceOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="是否基石客户" prop="isKeystone"><el-radio-group v-model="form.isKeystone"><el-radio :value="1">是</el-radio><el-radio :value="0">否</el-radio></el-radio-group></el-form-item>
          <el-form-item label="是否开户" prop="isAccountOpened"><el-radio-group v-model="form.isAccountOpened"><el-radio :value="1">是</el-radio><el-radio :value="0">否</el-radio></el-radio-group></el-form-item>
          <el-form-item label="是否触达限制" prop="touchRestricted" required><el-radio-group v-model="form.touchRestricted"><el-radio :value="1">是</el-radio><el-radio :value="0">否</el-radio></el-radio-group></el-form-item>
          <el-form-item label="授信金额（元）"><el-input-number v-model="form.creditAmount" :min="0" :precision="2" :controls="false" style="width:100%" /></el-form-item>
          <el-form-item label="授信敞口（元）"><el-input-number v-model="form.creditExposureAmount" :min="0" :precision="2" :controls="false" style="width:100%" /></el-form-item>
        </div>
        <el-form-item label="客户说明"><el-input v-model="form.customerDesc" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>

        <h3>分配信息</h3>
        <el-form-item label="分配方式" prop="distributionMode">
          <el-radio-group v-model="form.distributionMode">
            <el-radio-button value="PUBLIC">全行公开认领</el-radio-button>
            <el-radio-button value="SCOPE">指定客户经理范围</el-radio-button>
            <el-radio-button v-if="CCRM_OWNERSHIP_QUERY_ENABLED" value="OWNER" :disabled="!ownership?.hasMainOwnership">主办专属</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert v-if="distributionHelp" :title="distributionHelp" type="info" :closable="false" class="distribution-help" />
        <el-form-item v-if="form.distributionMode === 'SCOPE'" label="指定客户经理" prop="managerScopeIds">
          <el-select v-model="form.managerScopeIds" multiple filterable remote :remote-method="searchManagers" :loading="managerLoading" placeholder="按工号或姓名搜索" style="width:100%">
            <el-option v-for="manager in managerOptions" :key="manager.id" :value="manager.id" :label="`${manager.name || manager.id}（${manager.id}） · ${manager.org || manager.orgCode || '-'}`" />
          </el-select>
        </el-form-item>
        <el-descriptions v-if="CCRM_OWNERSHIP_QUERY_ENABLED && form.distributionMode === 'OWNER' && ownership?.hasMainOwnership" :column="2" border class="owner-card">
          <el-descriptions-item label="主办客户经理">{{ ownership.mainManagerName || ownership.mainManagerId }}（{{ ownership.mainManagerId }}）</el-descriptions-item>
          <el-descriptions-item label="主办机构">{{ ownership.mainOrgName || ownership.mainOrgId || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h3>标签、附件与备注</h3>
        <el-form-item label="客户标签"><el-select v-model="form.tagIdList" multiple filterable collapse-tags collapse-tags-tooltip style="width:100%"><el-option v-for="tag in tagOptions" :key="tag.id" :value="tag.id" :label="tag.tagName || tag.name || tag.id" /></el-select></el-form-item>
        <el-form-item label="附件">
          <el-upload v-model:file-list="fileList" :auto-upload="false" multiple :limit="10" :on-change="syncFiles" :on-remove="syncFiles"><el-button>选择文件</el-button><template #tip><div class="el-upload__tip">提交时统一上传，单文件不超过 10MB。</div></template></el-upload>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button :loading="saving" @click="save(false)">保存草稿</el-button><el-button type="primary" :loading="saving" @click="save(true)">提交审批</el-button></template>
    </el-dialog>

    <LeadDetailDrawer v-model="detailVisible" :lead="selected" :loading="detailLoading" />
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import LeadDetailDrawer from '@/components/LeadDetailDrawer.vue';
import { searchEmployees } from '@/api/employees';
import { useDict } from '@/composables/useDict';
import { createLeadEntryInitialState, createLeadEntryRules, getDistributionHelp } from './leadEntryForm';
import {
  createLead, deleteLead, getLead, listEnabledTags, listLeads, lookupLeadMainManager,
  executeLeadImport, previewLeadImport, submitLead, updateLead, uploadLeadAttachment
} from '@/api/customerMarketing';

// CCRM 暂不可用，暂时关闭存量客户与主办权查询；保留字段、API 和 OWNER 映射便于恢复。
const CCRM_OWNERSHIP_QUERY_ENABLED = false;
const statusOptions = [{label:'草稿',value:'DRAFT'},{label:'已提交',value:'SUBMITTED'},{label:'审批中',value:'IN_APPROVAL'},{label:'已通过',value:'APPROVED'},{label:'已驳回',value:'REJECTED'}];
const query = reactive({ keyword:'', status:'', ownerOrgId:'', pageNo:1, pageSize:20 });
const rows=ref([]), total=ref(0), loading=ref(false), saving=ref(false);
const formVisible=ref(false), editingId=ref(''), formRef=ref(null), fileList=ref([]), pendingFiles=ref([]);
const importVisible=ref(false), importLoading=ref(false), importFile=ref(null), importFileList=ref([]), importPreview=ref({}), importError=ref('');
const ownership=ref(null), ownershipLoading=ref(false), managerOptions=ref([]), managerLoading=ref(false), tagOptions=ref([]);
const detailVisible=ref(false), detailLoading=ref(false), selected=ref(null);
const { options:industryOptions }=useDict('INDUSTRY');
const { options:groupTypeOptions }=useDict('GROUP_TYPE');
const { options:customerTypeOptions }=useDict('CUSTOMER_TYPE');
const { options:enterpriseTypeOptions }=useDict('ENTERPRISE_TYPE');
const { options:leadSourceOptions }=useDict('LEAD_SOURCE');

const emptyForm=createLeadEntryInitialState;
const form=reactive(emptyForm());
const rules=createLeadEntryRules(form);
const statusLabel=value=>statusOptions.find(item=>item.value===value)?.label||value||'-';
const statusType=value=>({DRAFT:'info',SUBMITTED:'warning',IN_APPROVAL:'warning',APPROVED:'success',REJECTED:'danger'}[value]||'info');
const leadTypeLabel=value=>({NEW_ACCOUNT:'新客户开户',EXISTING_MARKETING:'存量客户营销'}[value]||value||'-');
const distributionLabel=value=>({PUBLIC:'全行公开认领',SCOPE:'指定客户经理范围',OWNER:'主办专属'}[value]||value||'-');
const formatTime=value=>value?String(value).replace('T',' ').slice(0,19):'-';
const ownershipText=computed(()=>!ownership.value?'请按统一社会信用代码或客户名称查询':ownership.value.existingCustomer?(ownership.value.hasMainOwnership?`已匹配存量客户，主办：${ownership.value.mainManagerName||ownership.value.mainManagerId}`:'已匹配存量客户，但未维护主办客户经理'):'未匹配到存量客户');
const distributionHelp=computed(()=>!CCRM_OWNERSHIP_QUERY_ENABLED&&form.distributionMode==='OWNER'?'':getDistributionHelp(form.distributionMode));
const canExecuteImport=computed(()=>Boolean(importPreview.value?.batchId)
  && Number(importPreview.value?.errorRows ?? importPreview.value?.failCount ?? 0) === 0);

watch(()=>form.groupType,value=>{if(value==='SINGLE')form.groupName='';});
watch(()=>form.distributionMode,value=>{if(value==='OWNER'&&ownership.value?.hasMainOwnership){form.mainManagerId=ownership.value.mainManagerId;form.managerScopeIds=[];}else if(value==='PUBLIC'){form.mainManagerId='';form.managerScopeIds=[];}else if(value==='SCOPE'){form.mainManagerId='';}});

async function load(){loading.value=true;try{const result=await listLeads({keyword:query.keyword||undefined,status:query.status||undefined,ownerOrgId:query.ownerOrgId||undefined,pageNo:query.pageNo,pageSize:query.pageSize});rows.value=result?.records||[];total.value=result?.total||0;}finally{loading.value=false;}}
function search(){query.pageNo=1;load();}function reset(){Object.assign(query,{keyword:'',status:'',ownerOrgId:'',pageNo:1});load();}
async function ensureOptions(){if(!tagOptions.value.length)tagOptions.value=await listEnabledTags()||[];}
function resetForm(){Object.assign(form,emptyForm());editingId.value='';ownership.value=null;managerOptions.value=[];fileList.value=[];pendingFiles.value=[];}
async function openCreate(){resetForm();await ensureOptions();formVisible.value=true;}
async function openEdit(row){resetForm();await ensureOptions();formVisible.value=true;saving.value=true;try{const lead=await getLead(row.id);editingId.value=row.id;Object.assign(form,emptyForm(),lead,{tagIdList:(lead.tags||[]).map(tag=>tag.tagId),managerScopeIds:(lead.managerScopes||[]).map(item=>item.managerEmpId),attachmentIds:(lead.attachments||[]).map(file=>file.id)});managerOptions.value=(lead.managerScopes||[]).map(item=>({id:item.managerEmpId,name:item.managerName,org:item.managerOrgName,orgCode:item.managerOrgId}));fileList.value=(lead.attachments||[]).map(file=>({name:file.fileName||file.id,url:`/api/files/${file.id}/download`,status:'success',fileId:file.id}));if(lead.mainManagerId)ownership.value={existingCustomer:true,hasMainOwnership:true,mainManagerId:lead.mainManagerId,mainManagerName:lead.mainManagerName,mainOrgId:lead.mainManagerOrgId,mainOrgName:lead.mainManagerOrgName};}finally{saving.value=false;}}
function resetImport(){importFile.value=null;importFileList.value=[];importPreview.value={};importError.value='';}
function openImport(){resetImport();importVisible.value=true;}
function closeImport(){importVisible.value=false;resetImport();}
function onImportFileChange(uploadFile, files){importFile.value=uploadFile?.raw||uploadFile||null;importFileList.value=files||[];importPreview.value={};importError.value='';}
function onImportFileRemove(){resetImport();}
function normalizeImportPreview(result){const value=result?.data&&!result.batchId?result.data:result;return value||{};}
async function previewImport(){
  if(!importFile.value){ElMessage.warning('请选择线索导入文件');return null;}
  importLoading.value=true;importError.value='';
  try{
    const result=normalizeImportPreview(await previewLeadImport(importFile.value));
    importPreview.value=result;
    if(!result.batchId){importError.value='导入预览未返回批次号';}
    return result;
  }catch(error){importPreview.value={};importError.value=error?.message||'导入预览失败';ElMessage.error(importError.value);return null;}
  finally{importLoading.value=false;}
}
async function executeImport(){
  if(!canExecuteImport.value){ElMessage.warning(importPreview.value?.batchId?'预览存在错误行，不能执行导入':'请先完成导入预览');return false;}
  importLoading.value=true;importError.value='';
  try{await executeLeadImport(importPreview.value.batchId);ElMessage.success('线索批量导入已提交审批');closeImport();await load();return true;}
  catch(error){importError.value=error?.message||'导入执行失败';ElMessage.error(importError.value);return false;}
  finally{importLoading.value=false;}
}
async function lookupOwnership(){if(!CCRM_OWNERSHIP_QUERY_ENABLED)return;if(!form.unifiedCreditCode&&!form.custName)return ElMessage.warning('请先填写客户名称或统一社会信用代码');ownershipLoading.value=true;try{ownership.value=await lookupLeadMainManager({unifiedCreditCode:form.unifiedCreditCode||undefined,custName:form.custName||undefined});if(ownership.value?.hasMainOwnership){form.distributionMode='OWNER';form.mainManagerId=ownership.value.mainManagerId;}else if(form.distributionMode==='OWNER'){form.distributionMode='PUBLIC';form.mainManagerId='';}}finally{ownershipLoading.value=false;}}
async function searchManagers(keyword){if(!keyword?.trim()){managerOptions.value=[];return;}managerLoading.value=true;try{managerOptions.value=await searchEmployees(keyword.trim(),30);}finally{managerLoading.value=false;}}
function syncFiles(_,files){fileList.value=files;pendingFiles.value=files.filter(file=>file.raw).map(file=>file.raw);form.attachmentIds=files.filter(file=>file.fileId).map(file=>file.fileId);}
async function uploadPending(){const ids=[...form.attachmentIds];for(const file of pendingFiles.value){if(file.size>10*1024*1024)throw new Error(`附件 ${file.name} 超过10MB`);const uploaded=await uploadLeadAttachment(file);ids.push(uploaded?.id||uploaded?.fileObjectId);}return ids.filter(Boolean);}
function payload(attachmentIds){return{leadType:form.leadType,custNo:form.custNo||undefined,custName:form.custName.trim(),unifiedCreditCode:form.unifiedCreditCode.trim(),contactPerson:form.contactPerson||undefined,contactMobile:form.contactMobile||undefined,industry:form.industry,groupType:form.groupType,customerType:form.customerType,isKeystone:form.isKeystone,enterpriseType:form.enterpriseType,groupName:form.groupName||undefined,isAccountOpened:form.isAccountOpened,touchRestricted:form.touchRestricted,customerDesc:form.customerDesc||undefined,creditAmount:form.creditAmount??undefined,creditExposureAmount:form.creditExposureAmount??undefined,leadSource:form.leadSource||undefined,tagIdList:form.tagIdList,distributionMode:form.distributionMode,mainManagerId:form.distributionMode==='OWNER'?form.mainManagerId:undefined,managerScopeIds:form.distributionMode==='SCOPE'?form.managerScopeIds:[],attachmentIds,remark:form.remark||undefined};}
async function save(andSubmit){await formRef.value?.validate();saving.value=true;try{const attachments=await uploadPending();const data=payload(attachments);let id=editingId.value;if(id)await updateLead(id,data);else id=await createLead(data);if(andSubmit)await submitLead(id);ElMessage.success(andSubmit?'线索已提交审批':'草稿已保存');formVisible.value=false;await load();}finally{saving.value=false;}}
async function submitExisting(row){await ElMessageBox.confirm(`确认提交线索“${row.custName}”进入审批？`,'提交审批',{type:'warning'});await submitLead(row.id);ElMessage.success('线索已提交审批');load();}
async function remove(row){await ElMessageBox.confirm(`确认删除草稿“${row.custName}”？`,'删除线索',{type:'warning'});await deleteLead(row.id);ElMessage.success('草稿已删除');load();}
async function openDetail(row){detailVisible.value=true;detailLoading.value=true;selected.value=null;try{selected.value=await getLead(row.id);}finally{detailLoading.value=false;}}
load();
</script>

<style scoped lang="scss">
.page-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:14px}.page-head h1{margin:0;font-size:18px}.page-head span{display:block;margin-top:4px;color:#909399;font-size:12px}.page-actions{display:flex;gap:8px}.filter-card{margin-bottom:14px}.filter-card :deep(.el-card__body){padding-bottom:2px}.pager{display:flex;justify-content:flex-end;margin-top:14px}.lead-form{margin-top:16px}.lead-form h3{margin:22px 0 12px;padding-left:10px;border-left:3px solid #1f5b8f;font-size:16px}.form-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:0 14px}.ownership-row{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border:1px solid #dcdfe6;border-radius:4px;background:#f8fafc}.ownership-row div{display:flex;flex-direction:column;gap:4px}.ownership-row span{font-size:12px;color:#606266}.distribution-help{margin-bottom:16px}.owner-card{margin-bottom:16px}.lead-import-upload{margin-top:18px}.upload-copy{color:#606266}.upload-copy em{color:var(--el-color-primary);font-style:normal}.import-error{margin-top:14px}.import-preview-summary{display:flex;gap:16px;margin-top:14px;color:#606266;font-size:13px}.import-preview-error{color:var(--el-color-danger)}@media(max-width:850px){.form-grid{grid-template-columns:1fr 1fr}}@media(max-width:600px){.form-grid{grid-template-columns:1fr}.page-head{gap:12px}.page-actions{flex-wrap:wrap}}
</style>
