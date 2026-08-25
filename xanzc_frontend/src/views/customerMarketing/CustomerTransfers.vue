<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head"><div><PageTitle /><span>记录客户主办人员、机构、接收人和任务流转轨迹。</span></div><el-button type="primary" @click="openTransfer">发起客户转交</el-button></header>
    <el-form inline @submit.prevent><el-form-item label="关键词"><el-input v-model="keyword" clearable placeholder="客户 / 原客户经理 / 接收人" @keyup.enter="load" /></el-form-item><el-form-item><el-button type="primary" @click="load">查询</el-button><el-button @click="keyword='';load()">重置</el-button></el-form-item></el-form>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="transferNo" label="转交编号" min-width="180" /><el-table-column prop="custName" label="客户名称" min-width="190" />
      <el-table-column label="原客户经理 / 机构" min-width="190"><template #default="{row}"><div class="stack"><strong>{{row.fromManagerName||row.fromManagerId||'-'}}</strong><small>{{row.fromOrgName||row.fromOrgId||'-'}}</small></div></template></el-table-column>
      <el-table-column label="接收客户经理" min-width="210"><template #default="{row}"><div v-for="target in row.targets||[]" :key="target.empId" class="target"><el-tag size="small" :type="target.role==='PRIMARY'?'success':'info'">{{target.role==='PRIMARY'?'主办':'协办'}}</el-tag><span>{{target.empName}}（{{target.empId}}）· {{target.orgName}}</span></div></template></el-table-column>
      <el-table-column label="开户快照" width="95"><template #default="{row}">{{row.accountOpenedSnapshot===1?'已开户':'未开户'}}</template></el-table-column>
      <el-table-column prop="reason" label="转交原因" min-width="180" show-overflow-tooltip /><el-table-column prop="completedTime" label="转交时间" min-width="165" />
      <el-table-column prop="operatorName" label="操作人" width="110" />
    </el-table>
    <el-empty v-if="!loading&&!rows.length" description="暂无客户转交记录" />

    <el-dialog v-model="dialogVisible" title="客户转交" width="680px" destroy-on-close>
      <el-alert title="首位接收人为新主办，其余人员作为协办；未完成旧触达任务将关闭并为新主办生成任务。" type="warning" :closable="false" show-icon />
      <el-form label-position="top" class="dialog-form">
        <el-form-item label="目标客户" required><el-select v-model="form.custId" filterable style="width:100%"><el-option v-for="c in customers" :key="c.id" :value="c.id" :label="`${c.custName}（当前主办：${c.mainManagerId||'未配置'}）`" /></el-select></el-form-item>
        <el-form-item label="接收客户经理" required><el-select v-model="form.targetEmpIds" multiple filterable collapse-tags collapse-tags-tooltip style="width:100%" placeholder="选择顺序决定主办/协办"><el-option v-for="u in candidates" :key="u.empId" :value="u.empId" :label="`${u.displayName}（${u.empId}）· ${u.mainOrgName||u.mainOrgCode}`" /></el-select></el-form-item>
        <el-form-item label="转交原因" required><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.custId||!form.targetEmpIds.length||!form.reason.trim()" @click="submit">确认转交</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { reactive,ref } from 'vue';import { ElMessage,ElMessageBox } from 'element-plus';
import { listCustomerTransfers,listMarketingCustomers,listTransferCandidates,transferCustomer } from '@/api/customerMarketing';
const keyword=ref(''),rows=ref([]),loading=ref(false),dialogVisible=ref(false),saving=ref(false),customers=ref([]),candidates=ref([]);const form=reactive({custId:'',targetEmpIds:[],reason:''});
async function load(){loading.value=true;try{rows.value=await listCustomerTransfers({keyword:keyword.value||undefined})||[];}finally{loading.value=false;}}
async function openTransfer(){Object.assign(form,{custId:'',targetEmpIds:[],reason:''});const[c,u]=await Promise.all([listMarketingCustomers({pageNo:1,pageSize:100}),listTransferCandidates({})]);customers.value=c?.records||[];candidates.value=u||[];dialogVisible.value=true;}
async function submit(){await ElMessageBox.confirm('确认转交客户并关闭其未完成旧触达任务？','确认客户转交',{type:'warning'});saving.value=true;try{await transferCustomer({...form});ElMessage.success('客户转交成功，新触达任务已生成');dialogVisible.value=false;load();}finally{saving.value=false;}}
load();
</script>

<style scoped lang="scss">.page-head{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:14px}.page-head h1{margin:0;font-size:18px}.page-head span{display:block;margin-top:4px;font-size:12px;color:#909399}.stack{display:flex;flex-direction:column}.stack small{color:#909399}.target{display:flex;gap:7px;align-items:center;margin:3px 0}.dialog-form{margin-top:16px}</style>
