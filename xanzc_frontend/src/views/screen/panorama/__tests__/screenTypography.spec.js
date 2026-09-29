import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const panoramaRoot = resolve(process.cwd(), 'src/views/screen/panorama');
const ownedStylesheets = ['panorama.scss', 'corporate.scss', 'retail.scss', 'branchOperating.scss'];
const chartSources = ['PanoramaTrend.vue', 'CorporateTrend.vue', 'RetailTrend.vue', 'BranchOperatingDashboard.vue'];
const screenRoots = [
  '.panorama-dashboard',
  '.city-panorama',
  '.corporate-dashboard',
  '.retail-dashboard',
  '.branch-operating-dashboard'
];

function readStyle(name) {
  return readFileSync(resolve(panoramaRoot, name), 'utf8').replace(/\r\n/g, '\n');
}

function readChartSource(name) {
  return readFileSync(resolve(panoramaRoot, name), 'utf8').replace(/\r\n/g, '\n');
}

describe('大屏共享字体层', () => {
  it('四套入口都加载共享字体规则，避免各屏维护漂移的字体栈', () => {
    for (const name of ownedStylesheets) {
      expect(readStyle(name)).toMatch(/@use\s+['"]\.\/screenTypography['"]\s+as\s+typography\s*;/);
    }
  });

  it('只在五个代码化大屏根节点上声明响应式字体 token', () => {
    const stylesheet = readStyle('screenTypography.scss');
    expect(stylesheet).toContain('--screen-font-family:');
    expect(stylesheet).toContain('--screen-font-size-title: clamp(32px, 2.5vw, 64px);');
    expect(stylesheet).toContain('--screen-font-size-section-title: clamp(16px, .9375vw, 22px);');
    expect(stylesheet).toContain('--screen-font-size-content: clamp(14px, .729vw, 18px);');
    expect(stylesheet).toContain('--screen-font-size-value: clamp(26px, 1.5vw, 38px);');
    expect(stylesheet).toContain('--screen-font-size-auxiliary: clamp(12px, .625vw, 15px);');
    for (const root of screenRoots) expect(stylesheet).toContain(root);
    expect(stylesheet).not.toMatch(/(?:^|[\s,{])(?:html|body|:root)\s*[{,]/m);
    expect(stylesheet).not.toMatch(/\*\s*[{,][^}]*font-size/s);
  });

  it('分组、地图、业务结构和趋势区块标题都使用同一个 18px@1920 token', () => {
    const stylesheet = readStyle('screenTypography.scss');
    const requiredSelectors = [
      '.presentation-layout__metric-group-header h2',
      '.presentation-map-widget h2',
      '.composition-tabs-widget__header h2',
      '.presentation-series-table h2',
      '.panorama-panel-heading h2',
      '.corporate-panel__heading h2',
      '.retail-panel__heading h2',
      '.corporate-trend__heading h2',
      '.retail-trend__heading h2',
      '.branch-operating-panel__heading h2'
    ];
    for (const selector of requiredSelectors) expect(stylesheet).toContain(selector);
    expect(stylesheet).toMatch(/\.presentation-layout__metric-group-header h2[\s\S]*?font-size:\s*var\(--screen-font-size-section-title\)/);
    expect(stylesheet).toMatch(/\.presentation-map-widget h2[\s\S]*?font-size:\s*var\(--screen-font-size-section-title\)/);
  });

  it('内容标签与辅助文字不低于 12px，数值允许在窄卡中换行', () => {
    const stylesheet = readStyle('screenTypography.scss');
    expect(stylesheet).toContain('font-size: var(--screen-font-size-label);');
    expect(stylesheet).toContain('font-size: var(--screen-font-size-auxiliary);');
    expect(stylesheet).toContain('overflow-wrap: anywhere;');
    expect(stylesheet).toContain('word-break: break-word;');
  });

  it('趋势和营销图表复用同一中文字体常量，轴、图例、标签与 tooltip 不再回退到 10px', () => {
    const typography = readFileSync(resolve(panoramaRoot, 'screenChartTypography.js'), 'utf8');
    expect(typography).toContain('SCREEN_CHART_FONT_FAMILY');
    expect(typography).toContain('SCREEN_CHART_AXIS_FONT_SIZE = 12');
    expect(typography).toContain('SCREEN_CHART_TOOLTIP_FONT_SIZE = 13');
    for (const name of chartSources) {
      const source = readChartSource(name);
      expect(source).toContain("from './screenChartTypography.js'");
      expect(source).toContain('SCREEN_CHART_FONT_FAMILY');
      expect(source).not.toMatch(/fontSize:\s*10\b/);
    }
  });

  it('4K 紧凑构成环、排名金额列和业务增长图保留可读空间', () => {
    const stylesheet = readStyle('screenTypography.scss');
    expect(stylesheet).toContain('--screen-compact-ring-size: clamp(80px, 4.1667vw, 112px);');
    expect(stylesheet).toContain('.composition-tabs-widget[data-compact="true"] .composition-ring-card');
    expect(stylesheet).toContain('grid-template-columns: var(--screen-compact-ring-size) minmax(0, 1fr);');
    expect(stylesheet).toContain('.institution-ranking-widget[data-testid="institution-ranking-widget"] table td:last-child');
    expect(stylesheet).toContain('min-width: clamp(86px, 5.5vw, 120px);');
    expect(stylesheet).toContain('.business-growth-widget__charts');
    expect(stylesheet).toContain('grid-template-rows: repeat(2, minmax(200px, 1fr));');
    expect(stylesheet).toContain('min-height: 450px;');
    expect(stylesheet).toContain('.business-growth-widget__chart .panorama-trend.is-compact .panorama-trend-chart');
    expect(stylesheet).toContain('min-height: 170px;');
  });
});
