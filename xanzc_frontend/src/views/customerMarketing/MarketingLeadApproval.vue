<template>
  <main class="page bp-crud marketing-lead-approval">
    <PageTitle />
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="待审批" name="pending" />
      <el-tab-pane label="审批记录" name="history" />
    </el-tabs>
    <el-form inline><el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="企业名称 / 统一社会信用代码 / 线索编号" /></el-form-item><el-button type="primary" @click="search">查询</el-button></el-form>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="leadNo" label="线索编号" min-width="155" />
      <el-table-column prop="custName" label="企业名称" min-width="180" />
      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190" />
      <el-table-column prop="leadSource" label="来源" width="110" />
      <el-table-column label="开户状态" width="105"><template #default="{row}">{{ row.currentCustomer?.isAccountOpened === 1 ? '已开户' : '未开户' }}</template></el-table-column>
      <el-table-column label="当前主办" min-width="140"><template #default="{row}">{{ row.currentCustomer?.mainManagerId || '无主办' }}</template></el-table-column>
      <el-table-column label="当前节点" min-width="130"><template #default="{row}">{{ row.task?.taskName || row.task?.name || '-' }}</template></el-table-column>
      <el-table-column v-if="tab==='pending'" label="操作" width="190" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openDetail(row)">查看</el-button><el-button link type="success" @click="decide(row,true)">通过</el-button><el-button link type="danger" @click="decide(row,false)">驳回</el-button></template></el-table-column>
      <el-table-column v-else label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="openDetail(row)">查看</el-button></template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination v-model:current-page="query.pageNo" :total="total" layout="total, prev, pager, next" @change="load" /></div>
    <el-drawer v-model="detailVisible" title="线索审批详情" size="min(860px,96vw)">
      <template v-if="detail?.lead"><el-alert v-if="detail.currentCustomer?.isAccountOpened===1" title="该客户已开户，请结合当前主办权和存量客户信息判断本次营销线索。" type="warning" :closable="false" show-icon /><el-descriptions :column="2" border class="detail"><el-descriptions-item label="企业名称">{{detail.lead.custName}}</el-descriptions-item><el-descriptions-item label="统一社会信用代码">{{detail.lead.unifiedCreditCode}}</el-descriptions-item><el-descriptions-item label="联系人">{{detail.lead.contactPerson||'-'}}</el-descriptions-item><el-descriptions-item label="联系电话">{{detail.lead.contactMobile||'-'}}</el-descriptions-item><el-descriptions-item label="当前开户状态">{{detail.currentCustomer?.isAccountOpened===1?'已开户':'未开户'}}</el-descriptions-item><el-descriptions-item label="当前主办">{{detail.currentCustomer?.mainManagerId||'无主办'}}</el-descriptions-item><el-descriptions-item label="主档资料是否变化">{{detail.profileChanged?'是':'否'}}</el-descriptions-item><el-descriptions-item label="录入人">{{detail.lead.entryEmpId}}</el-descriptions-item></el-descriptions></template>
    </el-drawer>
  </main>
</template>
<script setup>
import { reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import PageTitle from '@/components/PageTitle.vue';
import { approveLead, getLeadApprovalDetail, listLeadApprovalHistory, listLeadApprovalPending, rejectLead } from '@/api/marketingManagement';
const tab=ref('pending'); const query=reactive({keyword:'',pageNo:1,pageSize:20}); const rows=ref([]); const total=ref(0); const loading=ref(false); const detailVisible=ref(false); const detail=ref(null);
async function load(){ loading.value=true; try{ const result=await (tab.value==='pending'?listLeadApprovalPending(query):listLeadApprovalHistory(query)); rows.value=result?.records||[]; total.value=Number(result?.total||0); }catch(error){ rows.value=[]; ElMessage.error(`审批列表加载失败：${error?.message||'请稍后重试'}`); }finally{loading.value=false;} }
function search(){query.pageNo=1;load();}
async function openDetail(row){ try{detail.value=await getLeadApprovalDetail(row.leadId);detailVisible.value=true;}catch(error){ElMessage.error(`审批详情加载失败：${error?.message||'请稍后重试'}`);} }
async function decide(row,approved){ try{ const {value}=await ElMessageBox.prompt(approved?'请输入审批意见（可选）':'请输入驳回原因','线索审批',{inputValidator:v=>approved||!!v?.trim()||'驳回原因不能为空'}); const payload={taskId:row.task?.taskId||row.task?.id,opinion:value}; approved?await approveLead(row.leadId,payload):await rejectLead(row.leadId,payload); ElMessage.success(approved?'审批已通过':'线索已驳回'); await load(); }catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(`审批失败：${error?.message||'请刷新后重试'}`);} }
load();
</script>
<style scoped>.pager{display:flex;justify-content:flex-end;margin-top:14px}.detail{margin-top:16px}</style>
