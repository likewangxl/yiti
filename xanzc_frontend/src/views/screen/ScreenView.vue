<template>
  <!-- 代码化全景拥有自己的根和视觉域，不进入旧 1920×1080 舞台。 -->
  <div v-if="isPanoramaPresentation" class="screen-panorama-root">
    <div v-if="isDraftPreview" class="screen-draft-banner" data-testid="screen-draft-preview">未发布草稿预览</div>
    <PanoramaRuntime :view="view" :context="context" :back-path="draftBackPath"
      :batch-required="presentation?.type === 'CODE' && presentation?.template === 'branch-overview-v1'" />
  </div>

  <div v-else-if="unsupportedPresentation" class="screen-presentation-unsupported" role="alert">
    <h1>当前大屏展示模板暂不支持</h1>
    <p>已收到 presentation：{{ presentationLabel }}，请联系管理员切换到受支持的模板。</p>
    <button type="button" @click="goBack">返回上一页</button>
  </div>

  <div v-else class="screen-root">
    <div v-if="isDraftPreview" class="screen-draft-banner" data-testid="screen-draft-preview">未发布草稿预览</div>
    <div class="scr-stage" :style="{ transform: `translate(-50%, -50%) scale(${scale})` }">
      <div class="scr-header">
        <button type="button" class="scr-back" aria-label="返回上一页" @click="goBack">‹ 返回</button>
        <span v-if="view?.state !== 'draft'" class="scr-title">{{ view?.screenName || '经营管理大屏' }}</span>
        <span class="scr-clock"><span class="scr-live-dot" /> {{ clock }}</span>
      </div>
      <div class="scr-body" v-if="view && view.renderPackage">
        <ScreenRenderer :render-package="view.renderPackage" :map-points="view.mapPoints"
                        :map-region-metrics="view.mapRegionMetrics || []"
                        :map-payload="view.mapPackage" :context="context" />
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
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getScreenView } from '@/api/screen';
import { stageStyle } from '@/views/screen/components/runtime-utils/scale';
import { GLOBAL_PERIOD_INJECT_KEY } from '@/utils/globalPeriod';
import { runtimeSchemaVersion } from '@/utils/screenScope';
import ScreenRenderer from './components/ScreenRenderer.vue';
import PanoramaRuntime from './panorama/PanoramaRuntime.vue';

const route = useRoute();
const router = useRouter();

// 全屏周期过滤器联动上下文(spec 2026-07-17 §5.3):screen 级响应式 globalPeriod,默认 null=不干预。
// PeriodFilter 组件切换时写入,BlockContainer watch 后按纯函数判定覆盖自身周期重新取数。
const globalPeriod = ref(null);
provide(GLOBAL_PERIOD_INJECT_KEY, globalPeriod);

const view = ref(null);
const loadError = ref('');
const scale = ref(1);
const clock = ref('');
let loadGeneration = 0;

// 路由参数即取数上下文：同一份屏配置服务所有支行/员工
const context = computed(() => ({
  screenCode: route.params.screenCode || '',
  orgCode: route.query.orgCode || '',
  empId: route.query.empId || '',
  // 运行时契约版本独立于 canvasStyle/draft schema；无地图命名机构组也必须进入 schema2。
  schemaVersion: runtimeSchemaVersion(view.value || {}),
  // 只信任后端渲染响应确认的状态，不直接把可篡改 URL 参数当作草稿取数授权。
  previewState: view.value?.state === 'draft' ? 'draft' : undefined
}));

const presentation = computed(() => view.value?.renderPackage?.canvasStyle?.presentation);
const hasPresentation = computed(() => Object.prototype.hasOwnProperty.call(
  view.value?.renderPackage?.canvasStyle || {}, 'presentation'
) && presentation.value !== null && presentation.value !== undefined);
const isPanoramaPresentation = computed(() => presentation.value?.type === 'CODE'
  && ['branch-overview-v1', 'retail-overview-v1', 'corporate-overview-v1'].includes(presentation.value?.template));
const isDraftPreview = computed(() => view.value?.state === 'draft');
const draftBackPath = computed(() => isDraftPreview.value && route.query?.from === 'screen-center' ? '/screens' : '');
const unsupportedPresentation = computed(() => Boolean(
  view.value?.renderPackage && hasPresentation.value && !isPanoramaPresentation.value
));
const presentationLabel = computed(() => {
  const type = presentation.value?.type || 'unknown';
  const template = presentation.value?.template || 'unknown-template';
  return `${type}/${template}`;
});

async function load() {
  const generation = ++loadGeneration;
  loadError.value = '';
  view.value = null;
  // 换屏(跳屏钻取/路由参数变化)重置为默认不干预,避免上一屏 PeriodFilter 的选中周期串到新屏
  globalPeriod.value = null;
  try {
    const resp = await getScreenView(route.params.screenCode, route.query.preview);
    if (generation !== loadGeneration) return;
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
    if (generation !== loadGeneration) return;
    view.value = resp;
    fit(); // 缩放策略随渲染包 canvasStyle.adaptor 变化，拿到 view 后需重新计算(mounted 时早于本函数，先按缺省策略占位)
  } catch (e) {
    if (generation !== loadGeneration) return;
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
  loadGeneration += 1;
  window.removeEventListener('resize', fit);
  clearInterval(clockTimer);
});
</script>

<style lang="scss">
@use '@/styles/screen.scss';

// 代码化全景不使用旧 screen.scss 的 .screen-root/.scr-stage 规则。
.screen-panorama-root {
  min-height: 100vh;
  width: 100%;
}

.screen-draft-banner {
  box-sizing: border-box;
  position: relative;
  z-index: 5;
  width: 100%;
  padding: 10px 16px;
  color: #fff8e1;
  background: #8a5a00;
  border-bottom: 1px solid rgba(255, 226, 157, .75);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: .08em;
  line-height: 1.4;
  text-align: center;
}

.screen-root > .screen-draft-banner {
  position: fixed;
  top: 0;
  left: 0;
}

.screen-presentation-unsupported {
  min-height: 100vh;
  box-sizing: border-box;
  display: grid;
  place-content: center;
  gap: 10px;
  padding: 32px;
  color: #eaf2ff;
  background: #07102c;
}

.screen-presentation-unsupported h1,
.screen-presentation-unsupported p {
  margin: 0;
}

.screen-presentation-unsupported p {
  color: #9eb5dc;
}

.screen-presentation-unsupported button {
  width: fit-content;
  border: 1px solid rgba(119, 178, 255, .45);
  border-radius: 4px;
  padding: 7px 12px;
  color: #dbebff;
  background: rgba(19, 45, 91, .75);
  cursor: pointer;
}

// 「该大屏尚未发布」引导态——中性弱化文案，与 .scr-block-err 的报错红区分语义
.scr-guide-empty {
  color: var(--scr-text-dim);
  font-size: 15px;
  letter-spacing: 1px;
}
</style>
