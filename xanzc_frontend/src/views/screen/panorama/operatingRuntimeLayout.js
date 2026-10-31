/**
 * 旧模板运行页的稳定布局契约；缩略图只复用顺序，不复制另一份 KPI 定义。
 */
export const RETAIL_RUNTIME_KPI_ORDER = Object.freeze([
  'retailAum', 'retailDeposit', 'retailRevenue', 'retailValueCustomers',
  'retailLoan', 'retailNplRate', 'retailDepositAverage'
]);
