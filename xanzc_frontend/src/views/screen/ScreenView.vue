<template>
  <div class="screen-root">
    <div class="scr-stage" :style="{ transform: `translate(-50%, -50%) scale(${scale})` }">
      <div class="scr-header">
        <span class="scr-back" @click="goBack">‹ 返回</span>
        <span class="scr-title">{{ view?.screenName || '经营管理大屏' }}</span>
        <span class="scr-clock"><span class="scr-live-dot" /> {{ clock }}</span>
      </div>
      <div class="scr-body" v-if="view && view.renderPackage">
        <ScreenRenderer :render-package="view.renderPackage" :map-points="view.mapPoints" :context="context" />
      </div>
      <!-- 屏从未发布时后端 renderPackageJson=null(已知行为,本期不改)——判空渲染引导态，不裸 JSON.parse(null) -->
      <div v-else-if="view && !view.renderPackage" class="scr-guide-empty" style="margin:auto">
        该大屏尚未发布
      </div>
      <div v-else-if="loadError" class="scr-block-err" style="margin:auto">
        大屏配置加载失败：{{ loadError }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getScreenView } from '@/api/screen';
import { stageStyle } from '@/views/screen/designer/utils/scale';
import ScreenRenderer from './components/ScreenRenderer.vue';

const route = useRoute();
const router = useRouter();

const view = ref(null);
const loadError = ref('');
const scale = ref(1);
const clock = ref('');

// 路由参数即取数上下文：同一份屏配置服务所有支行/员工
const context = computed(() => ({
  orgCode: route.query.orgCode || '',
  empId: route.query.empId || ''
}));

async function load() {
  loadError.value = '';
  view.value = null;
  try {
    const resp = await getScreenView(route.params.screenCode, route.query.preview);
    if (resp.renderPackageJson) {
      try {
        resp.renderPackage = JSON.parse(resp.renderPackageJson);
      } catch {
        throw new Error('渲染包解析失败');
      }
    } else {
      // 屏从未发布——renderPackage 留 null，交给模板判空渲染「该大屏尚未发布」引导态
      resp.renderPackage = null;
    }
    view.value = resp;
    fit(); // 缩放策略随渲染包 canvasStyle.adaptor 变化，拿到 view 后需重新计算(mounted 时早于本函数，先按缺省策略占位)
  } catch (e) {
    loadError.value = e?.message || '未知错误';
  }
}

function fit() {
  // 1920×1080 设计稿按 canvasStyle.adaptor 适配窗口(缺省 keepProportion，即原 Math.min 等比适配行为)。
  // 统一走 utils/scale.stageStyle(四策略 keep/keepProportion/widthFirst/heightFirst 唯一实现来源)，
  // 不再自行硬编码 Math.min——旧实现恒等于 keepProportion，adaptor 字段从未真正接线(rev-t10 复审 Important-1)。
  const adaptor = view.value?.renderPackage?.canvasStyle?.adaptor || 'keepProportion';
  scale.value = stageStyle(adaptor, window.innerWidth, window.innerHeight).scale;
}

let clockTimer = null;
function tick() {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function goBack() {
  if (window.history.length > 1) router.back();
  else router.push('/workspace');
}

watch(() => [route.params.screenCode, route.query.orgCode, route.query.empId, route.query.preview], load);

onMounted(() => {
  fit();
  window.addEventListener('resize', fit);
  tick();
  clockTimer = setInterval(tick, 1000);
  load();
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', fit);
  clearInterval(clockTimer);
});
</script>

<style lang="scss">
@use '@/styles/screen.scss';

// 「该大屏尚未发布」引导态——中性弱化文案，与 .scr-block-err 的报错红区分语义
.scr-guide-empty {
  color: var(--scr-text-dim);
  font-size: 15px;
  letter-spacing: 1px;
}
</style>
