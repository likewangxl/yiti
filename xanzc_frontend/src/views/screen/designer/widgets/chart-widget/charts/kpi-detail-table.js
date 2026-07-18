// KPI 细项表：专为 KPI_DETAIL SNAPSHOT 固定列结构（细项名称/目标值/实际值/完成率/缺口/得分）设计，
// 完成率内嵌进度条、缺口红绿双色文案；needKinds 驱动属性面板数据源过滤（后端另有校验）。
export default { innerType: 'KPI_DETAIL_TABLE', label: 'KPI 细项表', needTimeseries: false, needKinds: ['KPI_DETAIL'], enabled: true };
