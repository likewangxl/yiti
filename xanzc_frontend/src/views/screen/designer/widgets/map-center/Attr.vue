<template>
  <CommonAttr :element="element">
    <el-form-item label="展示地图">
      <el-select :model-value="selectedRegionCode" filterable placeholder="请选择陕西省或地市"
                 aria-label="陕西地图地域" @change="changeRegion">
        <el-option-group label="省级概览">
          <el-option :value="provinceRegion.code" :label="provinceRegion.label" />
        </el-option-group>
        <el-option-group label="地市区县地图">
          <el-option v-for="region in cityRegions" :key="region.code"
                     :value="region.code" :label="region.label" />
        </el-option-group>
      </el-select>
      <span class="hint region-hint">{{ regionHint }}</span>
    </el-form-item>
    <el-form-item label="默认指标">
      <el-select v-model="config.metricCode" aria-label="地图默认经营指标" @change="sync">
        <el-option v-for="metric in MAP_METRIC_OPTIONS" :key="metric.code"
                   :value="metric.code" :label="metric.label" />
      </el-select>
      <span class="hint region-hint">运行时可切换；地图颜色、标签和悬浮详情同步更新。</span>
    </el-form-item>
    <template v-if="config.schemaVersion === 2">
      <el-form-item label="本地筛选">
        <span class="hint">城市 610100 · 经营等级 PRIMARY · 真实 GCJ-02 坐标</span>
      </el-form-item>
      <el-form-item label="异地节点">
        <div class="satellite-list">
          <div v-for="anchor in anchors" :key="anchor" class="satellite-row">
            <span class="anchor-label">{{ anchorLabels[anchor] }}</span>
            <el-input :model-value="nodeByAnchor(anchor).orgCode" disabled />
            <el-input v-model="nodeByAnchor(anchor).targetScreenCode" placeholder="目标屏" @change="sync" />
          </div>
        </div>
      </el-form-item>
      <el-form-item label="地图声明">
        <el-input v-model="config.disclaimer" disabled />
        <span class="hint">声明固定展示，不可隐藏</span>
      </el-form-item>
    </template>
    <template v-else-if="selectedRegionCode !== '610000'">
      <el-form-item label="区县范围">
        <span class="hint">{{ selectedRegion.shortName }} · {{ selectedRegion.districtCount }} 个区县 · 行政区边界</span>
      </el-form-item>
      <el-form-item label="地图说明">
        <span class="hint">选择后画布即时切换，区县按名称固定分色；保存草稿后保留当前地域。</span>
      </el-form-item>
    </template>
  </CommonAttr>
</template>
<script setup>
// MapCenter 属性面板：v1 仅保留陕西旧点位；v2 只保存模式、锚点机构编码与目标屏，
// 真实经纬度由机构画像运行时读取，禁止在画布 JSON 内手填坐标。
import { computed, reactive, ref } from 'vue';
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { MAP_ANCHORS, FIXED_SATELLITE_ORG_CODES, normalizeMapConfig, COMPOSITE_DISCLAIMER,
  SHAANXI_MAP_REGIONS } from '@/utils/screenScope';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { MAP_METRIC_OPTIONS } from '@/utils/mapMetrics';

const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
if (!props.element.propValue) props.element.propValue = {};
const config = reactive(normalizeMapConfig(props.element.propValue));
if (!MAP_METRIC_OPTIONS.some(item => item.code === config.metricCode)) config.metricCode = MAP_METRIC_OPTIONS[0].code;
const provinceRegion = SHAANXI_MAP_REGIONS[0];
const cityRegions = SHAANXI_MAP_REGIONS.slice(1);
const selectedRegionCode = ref(config.mode === 'XIAN_COMPOSITE' ? '610100' : (config.regionCode || '610000'));
const selectedRegion = computed(() => SHAANXI_MAP_REGIONS.find(region => region.code === selectedRegionCode.value)
  || provinceRegion);
const regionHint = computed(() => selectedRegion.value.code === '610000'
  ? '展示陕西十地市概览'
  : `${selectedRegion.value.shortName} · 展示 ${selectedRegion.value.districtCount} 个区县`);
const anchors = MAP_ANCHORS;
const anchorLabels = { LEFT: '宝鸡/左', RIGHT: '渭南/右', TOP: '咸阳/上', FAR_TOP: '榆林/远上' };
if (config.schemaVersion === 2) config.disclaimer = COMPOSITE_DISCLAIMER;

function nodeByAnchor(anchor) {
  let node = config.satelliteNodes.find(item => item.anchor === anchor);
  if (!node) {
    node = { orgCode: FIXED_SATELLITE_ORG_CODES[anchor] || '', anchor, targetScreenCode: 'SCR_BRANCH' };
    config.satelliteNodes.push(node);
  }
  // 本期四个异地节点是已确认的固定业务映射；只允许管理员调整其目标详情屏。
  node.orgCode = FIXED_SATELLITE_ORG_CODES[anchor] || '';
  return node;
}
function sync() {
  props.element.propValue = JSON.parse(JSON.stringify(config));
  store.pushSnapshotDebounced();
}
function changeRegion(regionCode) {
  selectedRegionCode.value = String(regionCode || '610000');
  if (selectedRegionCode.value === '610100') {
    config.schemaVersion = 2;
    config.mode = 'XIAN_COMPOSITE';
    config.baseRegion = 'XIAN_OUTLINE';
    config.regionCode = '610100';
    config.localSelector = { cityCode: '610100', operatingLevel: 'PRIMARY' };
    config.disclaimer = COMPOSITE_DISCLAIMER;
    if (!Array.isArray(config.satelliteNodes)) config.satelliteNodes = [];
    for (const anchor of anchors) nodeByAnchor(anchor);
  } else {
    config.schemaVersion = 1;
    config.mode = 'SHAANXI_LEGACY';
    config.regionCode = selectedRegionCode.value;
    config.baseRegion = selectedRegionCode.value === '610000' ? 'SHAANXI' : 'CITY_DISTRICT';
    delete config.localSelector;
    delete config.satelliteNodes;
    delete config.disclaimer;
  }
  sync();
}
</script>
<style scoped>
.hint { color: #7d9bc9; font-size: 12px; line-height: 1.5; }
.region-hint { display: block; width: 100%; margin-top: 6px; }
.dsn-attr :deep(.el-select) { width: 100%; }
.satellite-list { width: 100%; }
.satellite-row { display: grid; grid-template-columns: 54px 1fr 1fr; gap: 4px; margin-bottom: 5px; }
.anchor-label { color: #9bb6df; font-size: 11px; line-height: 28px; }
</style>
