<template>
  <main class="screen-preview" aria-label="经营全景本地演示" style="--panorama-viewport-offset: 78px">
    <div class="screen-preview__source screen-preview__source--top" data-testid="preview-source-top">
      <span>本地演示 · 非业务数据</span>
      <button type="button" data-action="preview-retail" @click="router.push('/screen-preview/retail')">零售经营总览</button>
      <button type="button" data-action="configure-real-data" @click="goToDesigner">配置真实数据</button>
    </div>

    <PanoramaDashboard
      :model="demoModel"
      :loading="false"
      error=""
      :demo="true"
      @back="goBack"
      @refresh="refreshDemo"
    />

    <footer class="screen-preview__footer">
      <span data-testid="demo-updated-at">本地演示本次查询/刷新时间 {{ lastUpdated }}</span>
      <span>坐标为 GCJ-02 近似示意，仅用于开发态视觉验收</span>
      <span data-testid="demo-refresh-note">刷新仅更新本地演示时间，不产生服务端结果</span>
    </footer>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PanoramaDashboard from './PanoramaDashboard.vue';
import { demoModel } from './demoModel.js';

const router = useRouter();
const route = useRoute();
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
  // 工作区仍由全局路由守卫决定是否需要登录，演示页不自行探测会话或请求后端。
  router.push(route?.query?.from === 'screen-center' ? '/screens' : '/workspace');
}

function goToDesigner() {
  // 真实数据配置仍由现有路由守卫鉴权；预览页不自动取数或改变演示模型。
  router.push('/screen-admin/designer');
}
</script>

<style scoped>
.screen-preview {
  --panorama-viewport-offset: 78px;
  min-height: 100vh;
  box-sizing: border-box;
  color: #eaf2ff;
  background: #07102c;
  display: flex;
  flex-direction: column;
}

.screen-preview__source {
  color: #f7ca72;
  font-size: 12px;
  letter-spacing: .08em;
}

.screen-preview__source--top {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 20px;
  background: rgba(246, 184, 73, .12);
  border-bottom: 1px solid rgba(246, 184, 73, .35);
}

.screen-preview__source--top button {
  padding: 4px 9px;
  border: 1px solid rgba(246, 184, 73, .62);
  border-radius: 5px;
  color: #ffe4a8;
  background: rgba(87, 52, 4, .28);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.screen-preview__source--top button:hover,
.screen-preview__source--top button:focus-visible {
  border-color: #ffe4a8;
  color: #fff6dc;
  outline: none;
}

.screen-preview :deep(.panorama-dashboard) {
  flex: 1;
  min-height: 0;
}

/* Dashboard 仍收到 demo=true 以保留组件语义，但外壳只保留顶部唯一演示标识。 */
.screen-preview :deep(.panorama-demo-badge) {
  display: none;
}

.screen-preview__footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
  padding: 8px 20px 12px;
  color: #7992bd;
  border-top: 1px solid rgba(100, 151, 227, .22);
  font-size: 11px;
  line-height: 1.5;
}
</style>
