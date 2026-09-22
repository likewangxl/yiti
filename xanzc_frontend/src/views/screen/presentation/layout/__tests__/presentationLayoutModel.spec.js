import { describe, expect, it } from 'vitest';

import {
  getDisplayComponents,
  isConfiguredPresentation,
  presentationForComponent,
  presentationOf
} from '../presentationLayoutModel';

const components = [
  { componentId: 'detail-bottom', componentType: 'DETAIL_TABLE', layoutRegion: 'BOTTOM', order: 1, visible: true },
  { componentId: 'metric-left-2', componentType: 'METRIC_CARD', layoutRegion: 'LEFT', order: 2, visible: true },
  { componentId: 'hidden-map', componentType: 'MAP', layoutRegion: 'CENTER', order: 0, visible: false },
  { componentId: 'metric-header', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 5, visible: true },
  { componentId: 'trend-left', componentType: 'TREND', layoutRegion: 'LEFT', order: 1, visible: true },
  { componentId: 'completion-left', componentType: 'COMPLETION', layoutRegion: 'LEFT', order: 0, visible: true },
  { componentId: 'structure-center', componentType: 'COMPOSITION_TABS', layoutRegion: 'CENTER', order: 2, visible: true },
  { componentId: 'ranking-right', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 0, visible: true },
  { componentId: 'map-center', componentType: 'MAP', layoutRegion: 'CENTER', order: 1, visible: true },
  { componentId: 'legacy-unknown', componentType: 'TARGET', layoutRegion: 'RIGHT', order: 0, visible: true }
];

describe('presentation layout model', () => {
  it('仅接受版本1并把 displayPresentation 包装层解析为展示协议', () => {
    const wrapped = { displayPresentation: { displaySchemaVersion: 1, display: { components } } };
    expect(isConfiguredPresentation(wrapped)).toBe(true);
    expect(presentationOf(wrapped)).toEqual(wrapped.displayPresentation);
    expect(isConfiguredPresentation({ displaySchemaVersion: 2, display: { components } })).toBe(false);
    expect(isConfiguredPresentation({ display: { components } })).toBe(false);
  });

  it('按 layoutRegion 和 order 稳定排序，隐藏项与旧/未知组件不进入整页', () => {
    const result = getDisplayComponents({ displaySchemaVersion: 1, display: { components } });
    expect(result.map(item => item.componentId)).toEqual([
      'metric-header',
      'completion-left',
      'trend-left',
      'metric-left-2',
      'map-center',
      'structure-center',
      'ranking-right',
      'detail-bottom'
    ]);
    expect(result.every(item => item.visible !== false)).toBe(true);
    expect(result.map(item => item.componentType)).toEqual([
      'METRIC_CARD', 'COMPLETION', 'TREND', 'METRIC_CARD',
      'MAP', 'COMPOSITION_TABS', 'RANKING', 'DETAIL_TABLE'
    ]);
  });

  it('单张地图上下文保留排名指标配置，供地图复用字段和展示单位', () => {
    const source = { displaySchemaVersion: 1, display: { components } };
    const map = components.find(item => item.componentId === 'map-center');
    expect(presentationForComponent(source, map).display.components.map(item => item.componentType))
      .toEqual(['RANKING', 'MAP']);
  });
});
