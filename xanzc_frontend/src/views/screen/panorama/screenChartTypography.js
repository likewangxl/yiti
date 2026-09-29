// Canvas charts do not inherit CSS font-family/font-size. Keep their values
// beside the screen tokens so tooltip, axis and legend text render consistently
// with the DOM overlays at every supported viewport.
export const SCREEN_CHART_FONT_FAMILY = '"Noto Sans SC", "Source Han Sans SC", "PingFang SC", "Microsoft YaHei", Arial, sans-serif';
export const SCREEN_CHART_AXIS_FONT_SIZE = 12;
export const SCREEN_CHART_LABEL_FONT_SIZE = 12;
export const SCREEN_CHART_LEGEND_FONT_SIZE = 12;
export const SCREEN_CHART_TOOLTIP_FONT_SIZE = 13;

export const SCREEN_CHART_TEXT = Object.freeze({
  fontFamily: SCREEN_CHART_FONT_FAMILY,
  fontSize: SCREEN_CHART_AXIS_FONT_SIZE
});

export const SCREEN_CHART_TOOLTIP_TEXT = Object.freeze({
  fontFamily: SCREEN_CHART_FONT_FAMILY,
  fontSize: SCREEN_CHART_TOOLTIP_FONT_SIZE
});
