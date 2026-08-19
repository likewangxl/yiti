<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head">
      <div><PageTitle /><span>新增标签审核通过后才能关联客户；支持追加与全量替换。</span></div>
      <div class="page-actions">
        <el-button :disabled="!selectedIds.length" @click="disableSelected">批量禁用</el-button>
        <el-button type="danger" plain :disabled="!selectedIds.length" @click="deleteSelected">批量删除</el-button>
        <el-button type="primary" @click="openCreate">新增标签</el-button>
      </div>
    </header>
    <el-form inline @submit.prevent>
      <el-form-item label="标签名称"><el-input v-model="query.keyword" clearable /></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable style="width:150px"><el-option label="启用" value="ACTIVE" /><el-option label="禁用" value="DISABLED" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-table :data="rows" v-loading="loading" border stripe @selection-change="onSelectionChange">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="tagName" label="标签名称" min-width="160"><template #default="{ row }"><el-button link type="primary" @click="openRelations(row)">{{ row.tagName }}</el-button></template></el-table-column>
      <el-table-column label="类型" width="105"><template #default="{ row }">{{ typeLabel(row.tagType || row.tagCategory) }}</template></el-table-column>
      <el-table-column prop="description" label="标签说明" min-width="220" show-overflow-tooltip />
      <el-table-column prop="tagPriority" label="优先级" width="85" />
      <el-table-column label="审核状态" width="105"><template #default="{ row }"><el-tag :type="approvalType(row.approvalStatus)">{{ approvalLabel(row.approvalStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="使用状态" width="95"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag></template></el-table-column>
      <el-table-column prop="expiresAt" label="失效日期" width="115"><template #default="{ row }">{{ row.expiresAt || '长期有效' }}</template></el-table-column>
      <el-table-column label="操作" width="240" fixed="right" class-name="operation-cell"><template #default="{ row }">
        <el-button link type="primary" @click="openRelations(row)">客户群</el-button>
        <el-button v-if="row.approvalStatus === 'APPROVED' && row.status === 'ACTIVE'" link type="primary" @click="openImport(row, 'APPEND')">追加导入</el-button>
        <el-button v-if="row.approvalStatus === 'APPROVED' && row.status === 'ACTIVE'" link type="warning" @click="openImport(row, 'REPLACE')">全量替换</el-button>
      </template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" @change="load" /></div>

    <el-dialog v-model="createVisible" title="新增客户标签" width="680px" destroy-on-close>
      <el-alert title="提交后进入待审核，审核通过前不可导入客户或在线索中使用。" type="info" :closable="false" show-icon />
      <el-form label-position="top" class="dialog-form">
        <div class="grid">
          <el-form-item label="标签名称" required><el-input v-model="form.tagName" maxlength="100" /></el-form-item>
          <el-form-item label="标签类型" required><el-select v-model="form.tagType" style="width:100%"><el-option label="项目类" value="PROJECT" /><el-option label="认定类" value="CERTIFICATION" /></el-select></el-form-item>
          <el-form-item label="优先级"><el-input-number v-model="form.tagPriority" :min="1" :max="999" style="width:100%" /></el-form-item>
          <el-form-item label="失效日期"><el-date-picker v-model="form.expiresAt" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        </div>
        <el-form-item label="标签说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.tagName.trim()" @click="submitCreate">提交审核</el-button></template>
    </el-dialog>

    <el-dialog v-model="importVisible" :title="importMode === 'APPEND' ? '追加导入客户' : '全量替换客户群'" width="760px" destroy-on-close>
      <el-alert
        v-if="importMode === 'REPLACE'"
        title="整份文件校验通过后，原有客户群中未出现在本次文件的客户将转为历史失效关系。"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="import-toolbar">
        <div>
          <strong>{{ selectedTag?.tagName }}</strong>
          <span>请先下载当前标签模板，按模板填写客户数据。</span>
        </div>
        <el-button type="primary" plain :loading="templateDownloading" @click="downloadTemplate">下载导入模板</el-button>
      </div>
      <el-upload
        ref="uploadRef"
        v-model:file-list="importFiles"
        class="excel-upload"
        drag
        action="#"
        accept=".xlsx,.xls"
        :auto-upload="false"
        :limit="1"
        :on-change="handleImportFileChange"
        :on-remove="handleImportFileRemove"
        :on-exceed="handleImportFileExceed"
      >
        <div class="upload-copy">将 Excel 文件拖到此处，或 <em>点击选择文件</em></div>
        <template #tip><div class="el-upload__tip">仅支持 xlsx/xls，文件不超过 10MB，最多 5000 条客户。统一社会信用代码作为唯一客户标识。</div></template>
      </el-upload>
      <el-alert
        v-if="importErrors.length"
        :title="`整批校验失败，共 ${importErrors.length} 处错误，本次未写入任何数据。`"
        type="error"
        :closable="false"
        show-icon
        class="import-error-alert"
      />
      <el-table v-if="importErrors.length" :data="importErrors" border max-height="220" size="small">
        <el-table-column prop="row" label="Excel行号" width="95" />
        <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="180"><template #default="{ row }">{{ row.unifiedCreditCode || '-' }}</template></el-table-column>
        <el-table-column prop="message" label="错误原因" min-width="260" />
      </el-table>
      <template #footer><el-button @click="importVisible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!importFile" @click="submitImport">校验并导入</el-button></template>
    </el-dialog>

    <el-drawer v-model="relationVisible" :title="`客户群 · ${selectedTag?.tagName || ''}`" size="760px">
      <div class="relation-toolbar"><el-input v-model="relationKeyword" clearable placeholder="客户名称 / 统一社会信用代码" /><span>共 {{ filteredRelations.length }} 条</span></div>
      <el-tabs v-model="relationTab" @tab-change="relationPage=1"><el-tab-pane :label="`当前有效（${activeRelationCount}）`" name="ACTIVE" /><el-tab-pane :label="`历史失效（${historyRelationCount}）`" name="HISTORY" /></el-tabs>
      <el-table :data="pagedRelations" v-loading="relationLoading" border stripe>
        <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="210"><template #default="{ row }">{{ row.unifiedCreditCode || '-' }}</template></el-table-column>
        <el-table-column prop="custName" label="客户名称" min-width="180"><template #default="{ row }">{{ row.custName || '-' }}</template></el-table-column>
        <el-table-column label="关系状态" width="110"><template #default="{ row }"><el-tag :type="row.active === 0 ? 'info' : 'success'">{{ row.active === 0 ? '历史失效' : '当前有效' }}</el-tag></template></el-table-column>
        <el-table-column prop="effectiveTime" label="生效时间" min-width="170" />
        <el-table-column prop="expiredTime" label="失效时间" min-width="170"><template #default="{ row }">{{ row.expiredTime || '-' }}</template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="relationPage" v-model:page-size="relationPageSize" :page-sizes="[10,20,50]" :total="filteredRelations.length" layout="total, sizes, prev, pager, next" /></div>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { batchDeleteTags, batchDisableTags, createTag, downloadTagCustomerImportTemplate, importTagCustomersFile, listTagCustomers, listTags } from '@/api/customerMarketing';

const query = reactive({ keyword:'', status:'', pageNo:1, pageSize:20 });
const rows=ref([]), total=ref(0), loading=ref(false), saving=ref(false), templateDownloading=ref(false);
const selectedRows=ref([]);
const selectedIds=computed(()=>selectedRows.value.map(row=>row.id));
const createVisible=ref(false), importVisible=ref(false), relationVisible=ref(false), relationLoading=ref(false);
const selectedTag=ref(null), importMode=ref('APPEND'), relations=ref([]);
const uploadRef=ref(null), importFile=ref(null), importFiles=ref([]), importErrors=ref([]);
const relationKeyword=ref(''), relationTab=ref('ACTIVE'), relationPage=ref(1), relationPageSize=ref(10);
const activeRelationCount=computed(()=>relations.value.filter(r=>r.active!==0).length);
const historyRelationCount=computed(()=>relations.value.filter(r=>r.active===0).length);
const filteredRelations=computed(()=>relations.value.filter(r=>{
  const statusOk=relationTab.value==='ACTIVE'?r.active!==0:r.active===0;
  const keyword=relationKeyword.value.trim();
  return statusOk&&(!keyword||`${r.custName||''}${r.unifiedCreditCode||''}`.includes(keyword));
}));
const pagedRelations=computed(()=>filteredRelations.value.slice((relationPage.value-1)*relationPageSize.value,relationPage.value*relationPageSize.value));
const emptyForm=()=>({ tagName:'', tagType:'PROJECT', tagCategory:'', tagPriority:50, expiresAt:'', description:'' });
const form=reactive(emptyForm());
const typeLabel=v=>({PROJECT:'项目类',CERTIFICATION:'认定类'}[v]||v||'-');
const approvalLabel=v=>({PENDING:'待审核',APPROVED:'已通过',REJECTED:'已退回'}[v]||'存量已通过');
const approvalType=v=>({PENDING:'warning',APPROVED:'success',REJECTED:'danger'}[v]||'success');
async function load(){loading.value=true;try{const r=await listTags(query);rows.value=r?.records||[];total.value=r?.total||0;}finally{loading.value=false;}}
function search(){query.pageNo=1;load();} function reset(){query.keyword='';query.status='';search();}
function openCreate(){Object.assign(form,emptyForm());createVisible.value=true;}
async function submitCreate(){saving.value=true;try{await createTag(form);ElMessage.success('标签已提交审核');createVisible.value=false;await load();}finally{saving.value=false;}}
function onSelectionChange(selection){selectedRows.value=selection;}
async function disableSelected(){await ElMessageBox.confirm(`确认禁用已选择的 ${selectedIds.value.length} 个标签？`,'批量禁用',{type:'warning'});await batchDisableTags(selectedIds.value);ElMessage.success('批量禁用成功');selectedRows.value=[];await load();}
async function deleteSelected(){await ElMessageBox.confirm(`确认删除已选择的 ${selectedIds.value.length} 个标签？删除后不可恢复。`,'批量删除',{type:'warning'});await batchDeleteTags(selectedIds.value);ElMessage.success('批量删除成功');selectedRows.value=[];await load();}
function openImport(row,mode){selectedTag.value=row;importMode.value=mode;importFile.value=null;importFiles.value=[];importErrors.value=[];importVisible.value=true;}
function handleImportFileChange(file,fileList){
  const raw=file.raw;
  const extension=(file.name||'').toLowerCase();
  if(!extension.endsWith('.xlsx')&&!extension.endsWith('.xls')){ElMessage.error('仅支持 xlsx/xls 格式文件');importFile.value=null;importFiles.value=[];return;}
  if(raw?.size>10*1024*1024){ElMessage.error('文件不能超过 10MB');importFile.value=null;importFiles.value=[];return;}
  importFile.value=raw;importFiles.value=fileList.slice(-1);importErrors.value=[];
}
function handleImportFileRemove(){importFile.value=null;importErrors.value=[];}
function handleImportFileExceed(){ElMessage.warning('每次只能上传一个文件，请先移除已选文件');}
async function downloadTemplate(){
  templateDownloading.value=true;
  try{
    const blob=await downloadTagCustomerImportTemplate(selectedTag.value.id);
    const url=URL.createObjectURL(blob);const link=document.createElement('a');
    link.href=url;link.download=`客户标签导入模板_${selectedTag.value.tagName.replace(/[\\/:*?"<>|]/g,'_')}.xlsx`;
    document.body.appendChild(link);link.click();link.remove();URL.revokeObjectURL(url);
  }finally{templateDownloading.value=false;}
}
async function submitImport(){
  if(!importFile.value){ElMessage.warning('请先选择 Excel 文件');return;}
  saving.value=true;importErrors.value=[];
  try{
    const result=await importTagCustomersFile(selectedTag.value.id,importFile.value,importMode.value);
    if(!result?.success){importErrors.value=result?.errors||[];ElMessage.error('整批校验失败，请修正文件后重新导入');return;}
    const skipped=result.skippedCount?`，文件内重复 ${result.skippedCount} 条已自动跳过`:'';
    ElMessage.success(`${importMode.value==='APPEND'?'追加':'全量替换'}成功，共导入 ${result.importedCount} 个客户${skipped}`);
    importVisible.value=false;await openRelations(selectedTag.value);
  }finally{saving.value=false;}
}
async function openRelations(row){selectedTag.value=row;relationTab.value='ACTIVE';relationKeyword.value='';relationPage.value=1;relationVisible.value=true;relationLoading.value=true;try{relations.value=await listTagCustomers(row.id)||[];}finally{relationLoading.value=false;}}
load();
</script>

<style scoped lang="scss">
.page-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:14px}.page-head h1{margin:0;font-size:18px}.page-head span{display:block;margin-top:4px;color:#909399;font-size:12px}.page-actions{display:flex;gap:8px}.pager{display:flex;justify-content:flex-end;margin-top:14px}.dialog-form{margin-top:16px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px}.import-toolbar{display:flex;align-items:center;justify-content:space-between;gap:18px;margin:18px 0 12px}.import-toolbar strong{display:block;margin-bottom:4px}.import-toolbar span{color:#909399;font-size:12px}.excel-upload{width:100%}.excel-upload :deep(.el-upload),.excel-upload :deep(.el-upload-dragger){width:100%}.upload-copy{color:#606266}.upload-copy em{color:var(--el-color-primary);font-style:normal}.import-error-alert{margin:14px 0 10px}.relation-toolbar{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:14px}.relation-toolbar span{color:#909399;font-size:12px}@media(max-width:720px){.grid{grid-template-columns:1fr}.import-toolbar{align-items:flex-start;flex-direction:column}}
</style>
