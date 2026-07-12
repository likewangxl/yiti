// ChartWidget 只在 componentsMap 占一个坑位；9 图表类型走 charts/*.js 自动扫描（两层注册解耦）。
export default {
  component: 'ChartWidget', label: '图表', group: 'chart', icon: '▤',
  defaultStyle: { top: 120, left: 240, width: 600, height: 320 },
  defaultProps: {}
};
