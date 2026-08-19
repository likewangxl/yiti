const options = (values) => values.map(value => ({ label: value, value }));

const YES_NO = options(['是', '否']);

export const GENERAL_HANDLING_OPTIONS = {
  accountability: ['诫勉谈话', '通报批评', '降职', '免职', '解除劳动合同'],
  credit: ['诫勉谈话', '通报批评', '停职检查', '降职', '免职', '解除劳动合同']
};

export const DISCIPLINARY_ACTION_OPTIONS = {
  accountability: ['警告', '记过', '记大过', '降级', '撤职', '留用察看', '开除', '解除劳动合同'],
  credit: ['警告', '记过', '记大过', '降级', '撤职', '留用察看', '开除']
};

const typeOfPerson = options(['--', '一般员工', '总行直管干部', '分行直管干部', '劳务派遣员工']);
const roleClassification = options([
  '二级分行主管行长', '柜员', '二级分行行长', '公司客户经理', '一级分行部门负责人',
  '零售客户经理', '一级分行主管行长', '理财经理', '一级分行行长', '审查审批',
  '总行部室员工', '风险管理', '总行处室负责人', '信用运营', '总行部室负责人',
  '支行主管行长', '支行行长', '二级分行部门负责人', '其他'
]);
const violationLines = options([
  '人力资源', '公司治理', '行政管理', '公司业务', '零售业务', '金融市场',
  '风险管理', '法律合规', '信用卡业务', '信息科技', '财务管理', '其他'
]);
const institutionLevels = options([
  '总行本部', '总行信用卡中心', '一级分行', '二级分行', '二级分行辖属支行', '同城支行', '异地支行'
]);
const violationAreas = options([
  '贷款资金流向', '贷款三查不到位', '信用卡套现', '贷款用途不合规', '违规越权',
  '风险分类不准确', '理财/股质业务不合规', '贸易背景不真实', '违反客户身份识别',
  '档案管理', '财务管理费用套现', '印章', '违反招投标和采购管理', '员工行为',
  '违反档案管理', '征信管理', '理财双录', '合同签署', '其他'
]);

/** 页面不展示导入模板的必填提示；是否必填仍由字段的 required 配置决定。 */
const pageLabel = label => String(label).replace(/\(\*\)|（\*）|\*/g, '');

const text = (key, label, extra = {}) => ({ key, label: pageLabel(label), type: 'text', ...extra });
const select = (key, label, values, extra = {}) => ({ key, label: pageLabel(label), type: 'select', options: values, ...extra });
const dateRange = (key, label, extra = {}) => ({
  key,
  label: pageLabel(label),
  type: 'datetime-range',
  startKey: `${key}Start`,
  endKey: `${key}End`,
  ...extra
});
const numberRange = (key, label, extra = {}) => ({
  key,
  label: pageLabel(label),
  type: 'number-range',
  minKey: `${key}Min`,
  maxKey: `${key}Max`,
  ...extra
});

/** 金额字段可关闭固定精度，避免整数失焦后自动显示为 0.0000。 */
export const resolveNumberRangePrecision = field => field.fixedPrecision === false ? undefined : 4;

/** 仅去掉纯数值（可带百分号）中无意义的小数尾零，其他文本原样展示。 */
export function trimTrailingDecimalZeros(value) {
  if (value === null || value === undefined || value === '') return value;
  const text = String(value);
  return text
    .replace(/^([+-]?\d+)\.0+(%?)$/, '$1$2')
    .replace(/^([+-]?\d+\.\d*?[1-9])0+(%?)$/, '$1$2');
}

/** 人员违规页面按新导入模板排序；页面不显示模板必填提示，提交校验另由 required 控制。 */
const accountabilitySearchFields = [
  text('accountabilityNumber', '问责编号(*)', { quick: true }),
  text('institutionName', '机构名称', { quick: true }),
  text('accountabilitySource', '问责来源', { quick: true }),
  text('specificSource', '具体来源'),
  select('institutionalHierarchy', '机构层级(*)', institutionLevels),
  text('affiliatedInstitution', '辖属机构(*)', { quick: true }),
  select('attributionOfViolations', '所属条线(*)', violationLines, { quick: true }),
  text('name', '被处罚人员名称(*)', {
    quick: true, batchQuery: true, searchPlaceholder: '多个值用逗号、分号或换行分隔（最多100个）'
  }),
  text('workNumber', '员工工号(*)', {
    quick: true, required: true, batchQuery: true,
    searchPlaceholder: '多个值用逗号、分号或换行分隔（最多100个）'
  }),
  select('gender', '性别(*)', options(['男', '女'])),
  select('typeOfPerson', '人员类型(*)', typeOfPerson, { quick: true }),
  text('postAtTheTime', '违规事实发生时职务(*)', { quick: true }),
  text('accountabilityPositions', '被问责时岗位、职务(*)'),
  select('documentType', '证件类型(*)', options(['身份证', '护照', '港澳通行证', '其他'])),
  text('idNumber', '证件号码(*)'),
  select('highestEducation', '最高学历(*)', options(['研究生及以上', '本科', '大专及以下'])),
  select('thePoliticalLandscape', '政治面貌(*)', options(['中共党员', '中共预备党员', '共青团员', '民主党派', '群众', '其他'])),
  select('isDimission', '是否离职(*)', YES_NO),
  dateRange('timeOfDeparture', '离职时间'),
  select('typeOfResponsibility', '责任类型(*)', options(['直接责任', '管理责任', '领导责任'])),
  select('violationCharacteristics', '违规特性', options(['屡查屡犯', '主观故意'])),
  select('areasOfViolation', '违规领域（旧）*', violationAreas),
  text('violationAreaLevelOne', '违规领域（一级）', { quick: true }),
  text('violationAreaLevelTwo', '违规领域（二级）', { quick: true }),
  text('factsOfTheViolation', '违规事实(*)', { inputType: 'textarea' }),
  text('processingBasis', '处理依据(*)', { inputType: 'textarea' }),
  text('accountabilityDocumentsAndNumbers', '问责文件及文号(*)'),
  dateRange('penaltyTime', '处罚时间(*)', { quick: true, startPlaceholder: '开始', endPlaceholder: '结束' }),
  select('generalHandling', '一般处理*', options(GENERAL_HANDLING_OPTIONS.accountability), { quick: true }),
  select('disciplinaryAction', '纪律处分*', options(DISCIPLINARY_ACTION_OPTIONS.accountability), { quick: true }),
  text('economicTreatment', '经济处理方式(*)'),
  numberRange('withholdingAmount', '扣发金额(*)', {
    minPlaceholder: '', maxPlaceholder: '', fixedPrecision: false
  }),
  text('withholdingInstructions', '扣发说明（落实情况）(*)'),
  text('reconsideration', '复议', { quick: true }),
  text('penaltyPeriod', '处罚期限(*)'),
  dateRange('penaltyReleaseTime', '处罚解除时间(*)', { startPlaceholder: '开始', endPlaceholder: '结束' }),
  select('whetherToSubmitSupervision', '是否报送监管', YES_NO),
  dateRange('submissionTime', '报送时间', { startPlaceholder: '开始', endPlaceholder: '结束' }),
  text('remark', '备注')
];

/** 兼容历史数据与导出，不在页面搜索、详情或表单中展示。 */
const accountabilityHiddenFields = [
  text('deptName', '部门名称'),
  select('roleClassification', '角色分类', roleClassification),
  text('businessArea', '业务领域'),
  select('primaryAndSecondaryResponsibility', '主、次要责任', options(['主要责任', '次要责任'])),
  select('circumstancesOfTheViolation', '违规情节', options(['严重', '较重', '轻微'])),
  text('rankAtTheTimeOfTheViolation', '违规事实发生时职级'),
  text('accountabilityRanks', '被问责时职级'),
  text('otherAreas', '其他领域'),
  text('partyAttitude', '当事人态度', { inputType: 'textarea' })
].map(field => ({ ...field, hidden: true }));

const creditSearchFields = [
  text('accountabilityCode', '问责代码'),
  text('clientName', '客户名称', {
    quick: true, batchQuery: true, searchPlaceholder: '多个值用逗号、分号或换行分隔（最多100个）'
  }),
  text('iouNumber', '借据号'),
  numberRange('insurancePrincipal', '出险本金(万元)', {
    quick: true, minPlaceholder: '', maxPlaceholder: '', fixedPrecision: false
  }),
  numberRange('estimatedLoss', '核销金额(万元)', {
    quick: true, minPlaceholder: '', maxPlaceholder: '', fixedPrecision: false
  }),
  select('isResponsibility', '认定结果有无责任', options(['有', '无']), { quick: true }),
  select('isAuditByHeadOffice', '是否属于总行审核', YES_NO),
  select('isMicrofinanceCreditBusiness', '是否属于普惠金融信贷业务', YES_NO, { quick: true }),
  text('responsiblePersonName', '责任认定对象（姓名）', {
    quick: true, batchQuery: true, searchPlaceholder: '多个值用逗号、分号或换行分隔（最多100个）'
  }),
  text('employeeNumber', '员工工号', {
    quick: true, required: true, batchQuery: true, searchPlaceholder: '多个值用逗号、分号或换行分隔（最多100个）'
  }),
  select('position', '职务(岗位)', options([
    '主办客户经理', '协办客户经理', '经办机构负责人', '经办机构管理层', '审查人',
    '审批人', '放款审核人', '资产保全人员', '其他'
  ]), { quick: true }),
  select('whetherToLeave', '是否离职', YES_NO),
  text('institutionName', '机构名称'),
  text('institutionalLevel', '机构层级'),
  text('affiliatedInstitution', '辖属机构', { quick: true }),
  text('subjectiveIntent', '主观故意', { quick: true }),
  text('majorNegligence', '重大过失', { quick: true }),
  text('generalNegligence', '一般过失', { quick: true }),
  numberRange('coefficientOfResponsibility', '责任系数', {
    minPlaceholder: '', maxPlaceholder: '', fixedPrecision: false
  }),
  numberRange('responsibilityRatio', '责任占比', {
    minPlaceholder: '', maxPlaceholder: '', fixedPrecision: false
  }),
  text('operatingMainResponsiblePersonResponsibility', '是否承担经营主责任人责任', { quick: true }),
  numberRange('amountWithheld', '扣发金额（元）', {
    minPlaceholder: '', maxPlaceholder: '', fixedPrecision: false
  }),
  select('generalHandling', '一般处理', options(GENERAL_HANDLING_OPTIONS.credit), { quick: true }),
  select('disciplinaryAction', '纪律处分', options(DISCIPLINARY_ACTION_OPTIONS.credit), { quick: true }),
  select('processingType', '处理类型', options([
    '扣发绩效收入(一次性)', '扣发绩效收入(连续数月)', '扣发绩效收入(当年年终奖)',
    '减记薪酬风险金', '减记长效激励', '降低薪酬级次', '要求赔偿经济损失'
  ])),
  numberRange('amountWithheld2', '扣发金额（元）', {
    minPlaceholder: '', maxPlaceholder: '', fixedPrecision: false
  }),
  text('withholdingInstructions', '扣发说明'),
  text('nameAndNumberOfAccountabilityDocument', '问责文件名称及文号'),
  select('specialAreaGeneralNegligenceExemption', '是否为专项领域、一般过失且占比10%以下免于经济处理的情形', YES_NO, {
    displayLabel: '是否为专项领域...', labelTooltip: true
  }),
  select('generalNegligenceSmallAmountExemption', '是否为一般过失且扣减金额在500元（含）以下免于经济处理的情形', YES_NO, {
    displayLabel: '是否为一般过失...', labelTooltip: true
  }),
  select('departmentSalaryLimitCondition', '是否为经济扣减以部门前三年度薪酬为限的情形', YES_NO, {
    displayLabel: '是否为经济扣减...', labelTooltip: true
  })
];

/** 兼容旧列用于保留详情值和导出，不在页面搜索、详情或表单中展示。 */
const creditHiddenFields = [
  select('responsibilityDetermination', '责任认定', options(['主观故意', '重大过失', '一般过失'])),
  select('isEconomicDeduction', '是否免于经济扣发', YES_NO),
  text('recycleAndReturn', '回收返还（元）'),
  select('isFalseBuckle', '是否虚扣', YES_NO),
  select('otherProcessing', '其他处理', options(['调离原岗位', '降低或取消业务授权', '批评教育', '待岗学习', '专职清收', '其他处理措施'])),
  dateRange('responsibilityDeterminationTime', '责任认定时间', {
    formType: 'date', startPlaceholder: '开始', endPlaceholder: '结束'
  }),
  select('isReportToTheBankingRegulatoryCommission', '是否报送银监机构', YES_NO),
  dateRange('submissionTime', '报送时间', { startPlaceholder: '开始', endPlaceholder: '结束' }),
  text('remark', '备注')
].map(field => ({ ...field, hidden: true }));

const toFormField = (field) => {
  const formField = { ...field, batchQuery: false, searchPlaceholder: undefined };
  if (field.type === 'datetime-range') return { ...formField, type: field.formType || 'datetime' };
  if (field.type === 'number-range') {
    return { ...formField, type: field.formType || 'text', trimDecimalZeros: true };
  }
  return formField;
};

/**
 * 按编辑表单字段构造保存请求，避免将详情响应中的 id/审计时间等元数据回传。
 */
export function buildViolationSavePayload(config, form) {
  return Object.fromEntries(config.formFields.map(field => {
    const value = form[field.key];
    if (field.type !== 'date' || !value) return [field.key, value];
    const date = String(value).slice(0, 10);
    return [field.key, /^\d{4}-\d{2}-\d{2}$/.test(date) ? `${date}T00:00:00` : value];
  }));
}

/**
 * 构造查看/编辑弹窗数据。纯日期字段只保留 YYYY-MM-DD，避免反显后端 LocalDateTime 的时分秒。
 */
export function buildViolationFormData(config, source = {}) {
  return Object.fromEntries(config.formFields.map(field => {
    const value = source[field.key];
    if (field.type === 'date' && value) return [field.key, String(value).slice(0, 10)];
    if (field.trimDecimalZeros) {
      return [field.key, trimTrailingDecimalZeros(value)];
    }
    return [field.key, value];
  }));
}

/** 根据表单模式生成校验规则；required 约束新增和编辑，requiredOnEdit 只约束编辑已有记录。 */
export function buildViolationFormRules(config, mode) {
  return Object.fromEntries(config.formFields
    .filter(field => field.required || (mode === 'edit' && field.requiredOnEdit))
    .map(field => [field.key, [{
      required: true,
      whitespace: true,
      message: `请输入${field.label}`,
      trigger: ['blur', 'change']
    }]]));
}

/**
 * 同一客户在当前页稳定归组，并给客户级字段生成 Element Plus rowspan 信息。
 * 空客户名称不参与合并，避免多条缺失数据被误认为同一客户。
 */
export function buildViolationTableRows(config, sourceRows = []) {
  if (!config.mergeBy) {
    return sourceRows.map((row, index) => ({
      ...row,
      __sequence: index + 1,
      __customerRowSpan: 1
    }));
  }

  const groups = [];
  const groupMap = new Map();
  sourceRows.forEach((row, index) => {
    const rawKey = row?.[config.mergeBy];
    const valueKey = rawKey === null || rawKey === undefined ? '' : String(rawKey).trim();
    const groupKey = valueKey ? `customer:${valueKey}` : `row:${index}`;
    let group = groupMap.get(groupKey);
    if (!group) {
      group = [];
      groupMap.set(groupKey, group);
      groups.push(group);
    }
    group.push(row);
  });

  return groups.flatMap((group, groupIndex) => group.map((row, rowIndex) => ({
    ...row,
    __sequence: groupIndex + 1,
    __customerRowSpan: rowIndex === 0 ? group.length : 0
  })));
}

export const accountabilityConfig = {
  kind: 'accountability',
  title: '人员违规信息',
  subtitle: '人员违规问责信息查询与维护',
  filterColumns: 3,
  actionColumns: 2,
  filterLabelFontSize: '13px',
  endpoint: '/yundun/accountability-violations',
  exportName: '人员违规信息',
  searchFields: accountabilitySearchFields,
  formFields: [
    text('accountabilityForViolationsId', '主键(不可更改)'),
    ...accountabilitySearchFields.map(toFormField),
    ...accountabilityHiddenFields.map(toFormField)
  ],
  columns: [
    { key: 'accountabilityNumber', label: '问责编号(*)', width: 150 },
    { key: 'accountabilitySource', label: '问责来源(*)', width: 140 },
    { key: 'specificSource', label: '具体来源(*)', width: 140 },
    { key: 'affiliatedInstitution', label: '辖属机构(*)', width: 150 },
    { key: 'name', label: '被处罚人员名称(*)', width: 140 },
    { key: 'workNumber', label: '员工工号(*)', width: 120 },
    { key: 'postAtTheTime', label: '违规事实发生时职务(*)', width: 180 },
    { key: 'attributionOfViolations', label: '所属条线(*)', width: 140 },
    { key: 'violationAreaLevelOne', label: '违规领域（一级）', width: 150 },
    { key: 'violationAreaLevelTwo', label: '违规领域（二级）', width: 150 },
    { key: 'factsOfTheViolation', label: '违规事实(*)', width: 220, overflow: true },
    { key: 'penaltyTime', label: '处罚时间(*)', width: 165, format: 'datetime' },
    { key: 'reconsideration', label: '复议', width: 100 },
    { key: 'generalHandling', label: '一般处理*', width: 130 },
    { key: 'disciplinaryAction', label: '纪律处分*', width: 130 }
  ].map(column => ({ ...column, label: pageLabel(column.label) }))
};

export const creditConfig = {
  kind: 'credit',
  title: '信贷风险信息',
  subtitle: '信贷风险责任认定及追究处理信息查询与维护',
  filterColumns: 3,
  actionColumns: 2,
  filterLabelWidth: '172px',
  filterLabelFontSize: '13px',
  filterColumnMinWidth: '320px',
  endpoint: '/yundun/credit-violations',
  exportName: '信贷风险信息',
  mergeBy: 'clientNameGroupKey',
  mergeColumns: [
    '__sequence',
    'clientName',
    'insurancePrincipal',
    'estimatedLoss',
    'isResponsibility',
    'isMicrofinanceCreditBusiness'
  ],
  searchFields: creditSearchFields,
  formFields: [
    ...creditSearchFields.map(toFormField),
    ...creditHiddenFields.map(toFormField)
  ],
  columns: [
    { key: 'clientName', label: '客户名称', width: 130 },
    { key: 'insurancePrincipal', label: '出险本金(万元)', width: 130, align: 'right' },
    { key: 'estimatedLoss', label: '核销金额(万元)', width: 130, align: 'right' },
    { key: 'isResponsibility', label: '认定结果有无责任', width: 140 },
    { key: 'isMicrofinanceCreditBusiness', label: '是否属于普惠金融信贷业务', width: 180 },
    { key: 'responsiblePersonName', label: '责任认定对象（姓名）', width: 150 },
    { key: 'employeeNumber', label: '员工工号', width: 120 },
    { key: 'position', label: '职务(岗位)', width: 150 },
    { key: 'affiliatedInstitution', label: '辖属机构', width: 140 },
    { key: 'subjectiveIntent', label: '主观故意', width: 100 },
    { key: 'majorNegligence', label: '重大过失', width: 100 },
    { key: 'generalNegligence', label: '一般过失', width: 100 },
    { key: 'generalHandling', label: '一般处理', width: 130 },
    { key: 'coefficientOfResponsibility', label: '责任系数', width: 110, format: 'trim-decimal-zeros' },
    { key: 'responsibilityRatio', label: '责任占比', width: 110, format: 'trim-decimal-zeros' },
    { key: 'disciplinaryAction', label: '纪律处分', width: 120 }
  ].map(column => ({ ...column, label: pageLabel(column.label) }))
};

export const violationConfigs = {
  accountability: accountabilityConfig,
  credit: creditConfig
};
