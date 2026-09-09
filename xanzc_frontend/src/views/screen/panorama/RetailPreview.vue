<template>
  <main class="retail-preview" aria-label="零售经营总览本地演示">
    <div class="retail-preview__source" data-testid="retail-preview-source">
      <span>本地演示 · 非业务数据</span>
      <button type="button" data-action="retail-configure-real-data" @click="goToDesigner">配置真实数据</button>
    </div>

    <RetailDashboard
      :model="retailDemoModel"
      :loading="false"
      error=""
      :demo="true"
      @back="goBack"
      @refresh="refreshDemo"
    />

    <footer class="retail-preview__footer">
      <span data-testid="retail-demo-updated-at">本地演示更新时间 {{ lastUpdated }}</span>
      <span>示例截止 {{ retailDemoModel.dataDate }} · 机构资料为演示样例</span>
      <span>刷新仅更新本地演示时间，不产生服务端结果</span>
    </footer>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import RetailDashboard from './RetailDashboard.vue';
import { retailDemoModel } from './retailDemoModel.js';

const router = useRouter();
const lastUpdated = ref(formatDemoTime(new Date()));

function formatDemoTime(value) {
  const date = value instanceof Date ? value : new Date(value);
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function refreshDemo() {
  lastUpdated.value = formatDemoTime(new Date());
}

function goBack() {
  router.push('/screen-preview');
}

function goToDesigner() {
  router.push('/screen-admin/designer');
}
</script>

<style scoped>
.retail-preview { min-height: 100vh; display: flex; flex-direction: column; color: #eaf2ff; background: #020a21; }
.retail-preview__source { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 8px 20px; border-bottom: 1px solid rgba(246, 184, 73, .35); color: #f7ca72; background: rgba(246, 184, 73, .12); font-size: 12px; letter-spacing: .08em; }
.retail-preview__source button { padding: 4px 9px; border: 1px solid rgba(246, 184, 73, .62); border-radius: 5px; color: #ffe4a8; background: rgba(87, 52, 4, .28); font-size: 11px; cursor: pointer; }
.retail-preview__source button:hover,
.retail-preview__source button:focus-visible { border-color: #ffe4a8; color: #fff6dc; outline: none; }
.retail-preview :deep(.retail-dashboard) { flex: 1; min-height: 0; }
.retail-preview :deep(.retail-demo-badge) { display: none; }
.retail-preview__footer { display: flex; flex-wrap: wrap; justify-content: center; gap: 16px; padding: 8px 20px 12px; border-top: 1px solid rgba(100, 151, 227, .22); color: #7992bd; font-size: 11px; line-height: 1.5; }
@media (min-width: 1100px) {
  /* 页面随完整经营内容增高，页脚始终位于所有面板之后。 */
  .retail-preview { height: auto; min-height: 100vh; }
  .retail-preview__source,
  .retail-preview__footer { flex: 0 0 auto; }
  .retail-preview :deep(.retail-dashboard) { height: auto; flex: 1 0 auto; }
}
@media (max-width: 760px) { .retail-preview__source { flex-wrap: wrap; gap: 6px 10px; padding: 7px 12px; font-size: 11px; } .retail-preview__footer { justify-content: flex-start; padding-right: 12px; padding-left: 12px; } }
</style>
