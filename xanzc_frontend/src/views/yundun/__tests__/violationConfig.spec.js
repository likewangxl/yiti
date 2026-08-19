import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import {
  accountabilityConfig,
  creditConfig,
  GENERAL_HANDLING_OPTIONS,
  DISCIPLINARY_ACTION_OPTIONS,
  buildViolationFormRules,
  resolveNumberRangePrecision,
  trimTrailingDecimalZeros,
  buildViolationFormData,
  buildViolationSavePayload,
  buildViolationTableRows
} from '../violationConfig';

const ACCOUNTABILITY_SEARCH_KEYS = [
  'accountabilityNumber', 'institutionName', 'accountabilitySource', 'specificSource',
  'institutionalHierarchy', 'affiliatedInstitution', 'attributionOfViolations', 'name',
  'workNumber', 'gender', 'typeOfPerson', 'postAtTheTime', 'accountabilityPositions',
  'documentType', 'idNumber', 'highestEducation', 'thePoliticalLandscape', 'isDimission',
  'timeOfDeparture', 'typeOfResponsibility', 'violationCharacteristics', 'areasOfViolation',
  'violationAreaLevelOne', 'violationAreaLevelTwo', 'factsOfTheViolation', 'processingBasis',
  'accountabilityDocumentsAndNumbers', 'penaltyTime', 'generalHandling', 'disciplinaryAction',
  'economicTreatment', 'withholdingAmount', 'withholdingInstructions', 'reconsideration',
  'penaltyPeriod', 'penaltyReleaseTime', 'whetherToSubmitSupervision', 'submissionTime', 'remark'
];

const CREDIT_SEARCH_KEYS = [
  'accountabilityCode', 'clientName', 'iouNumber', 'insurancePrincipal',
  'estimatedLoss', 'isResponsibility', 'isAuditByHeadOffice',
  'isMicrofinanceCreditBusiness', 'responsiblePersonName', 'employeeNumber',
  'position', 'whetherToLeave', 'institutionName', 'institutionalLevel', 'affiliatedInstitution',
  'subjectiveIntent', 'majorNegligence', 'generalNegligence', 'coefficientOfResponsibility',
  'responsibilityRatio', 'operatingMainResponsiblePersonResponsibility', 'amountWithheld',
  'generalHandling', 'disciplinaryAction', 'processingType', 'amountWithheld2',
  'withholdingInstructions', 'nameAndNumberOfAccountabilityDocument',
  'specialAreaGeneralNegligenceExemption', 'generalNegligenceSmallAmountExemption',
  'departmentSalaryLimitCondition'
];

describe('违规管理搜索字段配置', () => {
  it('人员违规页面标题不展示导入模板必填标记，但只有员工工号实际必填', () => {
    const expectedTemplateFields = {
      accountabilityNumber: '问责编号',
      accountabilitySource: '问责来源',
      specificSource: '具体来源',
      institutionalHierarchy: '机构层级',
      affiliatedInstitution: '辖属机构',
      name: '被处罚人员名称',
      workNumber: '员工工号',
      postAtTheTime: '违规事实发生时职务',
      accountabilityPositions: '被问责时岗位、职务',
      violationAreaLevelOne: '违规领域（一级）',
      violationAreaLevelTwo: '违规领域（二级）',
      reconsideration: '复议'
    };

    for (const [key, label] of Object.entries(expectedTemplateFields)) {
      expect(accountabilityConfig.searchFields.find(field => field.key === key)?.label).toBe(label);
      expect(accountabilityConfig.formFields.find(field => field.key === key)?.label).toBe(label);
    }
    expect(accountabilityConfig.formFields.find(field => field.key === 'workNumber')?.required).toBe(true);
    expect(accountabilityConfig.formFields
      .filter(field => field.key !== 'workNumber' && field.required)
      .map(field => field.key)).toEqual([]);
  });

  it('信贷风险按新导入模板显示核销金额、责任认定对象和职务标题', () => {
    const expectedTemplateFields = {
      estimatedLoss: '核销金额(万元)',
      responsiblePersonName: '责任认定对象（姓名）',
      position: '职务(岗位)',
      affiliatedInstitution: '辖属机构',
      subjectiveIntent: '主观故意',
      majorNegligence: '重大过失',
      generalNegligence: '一般过失',
      operatingMainResponsiblePersonResponsibility: '是否承担经营主责任人责任',
      specialAreaGeneralNegligenceExemption: '是否为专项领域、一般过失且占比10%以下免于经济处理的情形',
      generalNegligenceSmallAmountExemption: '是否为一般过失且扣减金额在500元（含）以下免于经济处理的情形',
      departmentSalaryLimitCondition: '是否为经济扣减以部门前三年度薪酬为限的情形'
    };

    for (const [key, label] of Object.entries(expectedTemplateFields)) {
      expect(creditConfig.searchFields.find(field => field.key === key)?.label).toBe(label);
      expect(creditConfig.formFields.find(field => field.key === key)?.label).toBe(label);
    }
  });

  it('信贷风险借款人、员工姓名和员工工号搜索支持批量输入，表单仍使用单值输入', () => {
    const keys = ['clientName', 'responsiblePersonName', 'employeeNumber'];

    for (const key of keys) {
      const searchField = creditConfig.searchFields.find(field => field.key === key);
      const formField = creditConfig.formFields.find(field => field.key === key);
      expect(searchField.batchQuery).toBe(true);
      expect(searchField.searchPlaceholder).toBe('多个值用逗号、分号或换行分隔（最多100个）');
      expect(formField.batchQuery).not.toBe(true);
      expect(formField.searchPlaceholder).toBeUndefined();
    }

    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');
    expect(source).toContain("field.searchPlaceholder || `请输入${field.label}`");
  });

  it('人员违规被处罚人员名称和员工工号搜索支持批量输入，表单仍使用单值输入', () => {
    const keys = ['name', 'workNumber'];

    for (const key of keys) {
      const searchField = accountabilityConfig.searchFields.find(field => field.key === key);
      const formField = accountabilityConfig.formFields.find(field => field.key === key);
      expect(searchField.batchQuery).toBe(true);
      expect(searchField.searchPlaceholder).toBe('多个值用逗号、分号或换行分隔（最多100个）');
      expect(formField.batchQuery).not.toBe(true);
      expect(formField.searchPlaceholder).toBeUndefined();
    }
  });

  it('信贷风险三个经济条件使用是/否下拉并位于展开搜索和表单的最后一行', () => {
    const keys = [
      'specialAreaGeneralNegligenceExemption',
      'generalNegligenceSmallAmountExemption',
      'departmentSalaryLimitCondition'
    ];

    for (const sourceFields of [creditConfig.searchFields, creditConfig.formFields]) {
      const fields = sourceFields.filter(field => !field.hidden);
      expect(fields.slice(-3).map(field => field.key)).toEqual(keys);
      for (const key of keys) {
        const field = fields.find(item => item.key === key);
        expect(field.type).toBe('select');
        expect(field.options.map(option => option.value)).toEqual(['是', '否']);
        expect(field.quick).not.toBe(true);
      }
    }
  });

  it('信贷风险三个经济条件在页面以省略标题显示，悬浮可查看完整标题', () => {
    const expectedDisplayLabels = {
      specialAreaGeneralNegligenceExemption: '是否为专项领域...',
      generalNegligenceSmallAmountExemption: '是否为一般过失...',
      departmentSalaryLimitCondition: '是否为经济扣减...'
    };

    for (const [key, displayLabel] of Object.entries(expectedDisplayLabels)) {
      for (const fields of [creditConfig.searchFields, creditConfig.formFields]) {
        const field = fields.find(item => item.key === key);
        expect(field.displayLabel).toBe(displayLabel);
        expect(field.labelTooltip).toBe(true);
        expect(field.label).not.toBe(displayLabel);
      }
    }

    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');
    expect(source).toContain('field.displayLabel || field.label');
    expect(source).toContain('column.displayLabel || column.label');
    expect(source).toContain('ElTooltip');
  });

  it('信贷风险表格不展示经营主责任和三个经济条件列', () => {
    const excludedKeys = [
      'operatingMainResponsiblePersonResponsibility',
      'specialAreaGeneralNegligenceExemption',
      'generalNegligenceSmallAmountExemption',
      'departmentSalaryLimitCondition'
    ];

    for (const key of excludedKeys) {
      expect(creditConfig.columns.find(column => column.key === key)).toBeUndefined();
    }
  });

  it('更新请求只提交可编辑字段，不回传响应元数据', () => {
    const payload = buildViolationSavePayload(creditConfig, {
      id: 19,
      clientName: '测试客户',
      responsibilityDeterminationTime: '2026-08-12T16:15:00',
      createTime: '2026-08-12T15:00:00',
      updateTime: '2026-08-12T16:00:00',
      inUse: 1
    });

    expect(payload).toMatchObject({
      clientName: '测试客户',
      responsibilityDeterminationTime: '2026-08-12T00:00:00'
    });
    expect(payload).not.toHaveProperty('id');
    expect(payload).not.toHaveProperty('createTime');
    expect(payload).not.toHaveProperty('updateTime');
    expect(payload).not.toHaveProperty('inUse');
  });

  it('操作按钮按查询、维护、文件操作分组排序', () => {
    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');
    const actions = source.match(/<div class="filter-actions">([\s\S]*?)<\/div>/)?.[1] || '';
    const labels = ['搜索', '重置', '新增', '删除', '导入', '导出'];
    const positions = labels.map(label => actions.indexOf(`>${label}<`));

    expect(positions.every(position => position >= 0)).toBe(true);
    expect(positions).toEqual([...positions].sort((a, b) => a - b));
  });

  it('两个违规页面都使用三列搜索项和两列操作按钮', () => {
    expect(accountabilityConfig.filterColumns).toBe(3);
    expect(accountabilityConfig.actionColumns).toBe(2);
    expect(creditConfig.filterColumns).toBe(3);
    expect(creditConfig.actionColumns).toBe(2);
  });

  it('违规管理搜索区使用调整后的标签样式', () => {
    expect(creditConfig.filterLabelWidth).toBe('172px');
    expect(creditConfig.filterColumnMinWidth).toBe('320px');
    expect(creditConfig.filterLabelFontSize).toBe('13px');
    expect(accountabilityConfig.filterLabelFontSize).toBe('13px');
  });

  it('人员违规搜索项按新导入模板排序并隐藏主键和系统维护字段', () => {
    expect(accountabilityConfig.searchFields.map(field => field.key)).toEqual(ACCOUNTABILITY_SEARCH_KEYS);
    expect(accountabilityConfig.searchFields.map(field => field.key)).not.toContain('id');
    expect(accountabilityConfig.searchFields.map(field => field.key)).not.toContain('accountabilityForViolationsId');
    expect(accountabilityConfig.searchFields.map(field => field.key)).not.toContain('createTime');
    expect(accountabilityConfig.searchFields.map(field => field.key)).not.toContain('updateTime');
    expect(accountabilityConfig.searchFields.map(field => field.key)).not.toContain('inUse');
  });

  it('信贷风险搜索项按新导入模板排序并隐藏兼容旧字段和系统维护字段', () => {
    expect(creditConfig.searchFields.map(field => field.key)).toEqual(CREDIT_SEARCH_KEYS);
    expect(creditConfig.searchFields.map(field => field.key)).not.toContain('id');
    expect(creditConfig.searchFields.map(field => field.key)).not.toContain('createTime');
    expect(creditConfig.searchFields.map(field => field.key)).not.toContain('updateTime');
    expect(creditConfig.searchFields.map(field => field.key)).not.toContain('inUse');
    expect(creditConfig.searchFields.map(field => field.key)).not.toContain('responsibilityDetermination');
    expect(creditConfig.searchFields.map(field => field.key)).not.toContain('recycleAndReturn');
  });

  it('信贷风险将取消页面展示的责任认定时间保留为隐藏兼容字段', () => {
    const field = creditConfig.formFields.find(item => item.key === 'responsibilityDeterminationTime');
    expect(creditConfig.searchFields[0]?.key).toBe('accountabilityCode');
    expect(field.hidden).toBe(true);
  });

  it('责任认定时间在查看和编辑表单中只展示日期，保存时补齐零点时间', () => {
    const field = creditConfig.formFields.find(item => item.key === 'responsibilityDeterminationTime');
    expect(field.type).toBe('date');

    const payload = buildViolationSavePayload(creditConfig, {
      responsibilityDeterminationTime: '2026-08-12'
    });
    expect(payload.responsibilityDeterminationTime).toBe('2026-08-12T00:00:00');
  });

  it('信贷风险列表按同一客户稳定分组并生成客户级合并行信息', () => {
    const result = buildViolationTableRows(creditConfig, [
      { id: 1, clientName: '客**甲', clientNameGroupKey: 'customer-1', responsiblePersonName: '张三' },
      { id: 2, clientName: '客**乙', clientNameGroupKey: 'customer-2', responsiblePersonName: '李四' },
      { id: 3, clientName: '客**甲', clientNameGroupKey: 'customer-1', responsiblePersonName: '王五' },
      { id: 4, clientName: '', responsiblePersonName: '赵六' }
    ]);

    expect(result.map(row => row.id)).toEqual([1, 3, 2, 4]);
    expect(result.map(row => row.__customerRowSpan)).toEqual([2, 0, 1, 1]);
    expect(result.map(row => row.responsiblePersonName)).toEqual(['张三', '王五', '李四', '赵六']);
    expect(creditConfig.mergeBy).toBe('clientNameGroupKey');
  });

  it('信贷风险脱敏客户名碰撞时按服务端分组键区分客户', () => {
    const result = buildViolationTableRows(creditConfig, [
      { id: 1, clientName: '张*', clientNameGroupKey: 'customer-1' },
      { id: 2, clientName: '张*', clientNameGroupKey: 'customer-2' },
      { id: 3, clientName: '张*', clientNameGroupKey: 'customer-1' }
    ]);

    expect(result.map(row => row.id)).toEqual([1, 3, 2]);
    expect(result.map(row => row.__customerRowSpan)).toEqual([2, 0, 1]);
  });

  it('信贷风险只合并序号和五个客户级字段', () => {
    expect(creditConfig.mergeColumns).toEqual([
      '__sequence',
      'clientName',
      'insurancePrincipal',
      'estimatedLoss',
      'isResponsibility',
      'isMicrofinanceCreditBusiness'
    ]);
    expect(creditConfig.mergeColumns).not.toContain('responsiblePersonName');
    expect(creditConfig.mergeColumns).not.toContain('employeeNumber');
  });

  it('信贷风险核销金额统一使用新导入模板标题', () => {
    const searchField = creditConfig.searchFields.find(item => item.key === 'estimatedLoss');
    const formField = creditConfig.formFields.find(item => item.key === 'estimatedLoss');
    const column = creditConfig.columns.find(item => item.key === 'estimatedLoss');

    for (const field of [searchField, formField, column]) {
      expect(field.label).toBe('核销金额(万元)');
    }
  });

  it('信贷风险责任认定对象使用新导入模板标题', () => {
    const searchField = creditConfig.searchFields.find(item => item.key === 'responsiblePersonName');
    const formField = creditConfig.formFields.find(item => item.key === 'responsiblePersonName');
    const column = creditConfig.columns.find(item => item.key === 'responsiblePersonName');

    for (const field of [searchField, formField, column]) {
      expect(field.label).toBe('责任认定对象（姓名）');
    }
  });

  it('信贷风险新增和编辑时员工工号都必填', () => {
    const field = creditConfig.formFields.find(item => item.key === 'employeeNumber');
    expect(field.required).toBe(true);
    expect(buildViolationFormRules(creditConfig, 'edit')).toMatchObject({
      employeeNumber: [{ required: true, whitespace: true, message: '请输入员工工号', trigger: ['blur', 'change'] }]
    });
    expect(buildViolationFormRules(creditConfig, 'create')).toMatchObject({
      employeeNumber: [{ required: true, whitespace: true, message: '请输入员工工号', trigger: ['blur', 'change'] }]
    });
  });

  it('人员违规信息新增和编辑时工号都必填', () => {
    const field = accountabilityConfig.formFields.find(item => item.key === 'workNumber');
    expect(field.required).toBe(true);
    expect(buildViolationFormRules(accountabilityConfig, 'edit')).toMatchObject({
      workNumber: [{ required: true, whitespace: true, message: '请输入员工工号', trigger: ['blur', 'change'] }]
    });
    expect(buildViolationFormRules(accountabilityConfig, 'create')).toMatchObject({
      workNumber: [{ required: true, whitespace: true, message: '请输入员工工号', trigger: ['blur', 'change'] }]
    });
  });

  it('两个页面的查询、列表和可见表单标题均不显示星号', () => {
    for (const config of [accountabilityConfig, creditConfig]) {
      const labels = [
        ...config.searchFields.map(field => field.label),
        ...config.columns.map(column => column.label),
        ...config.formFields.filter(field => !field.hidden).map(field => field.label)
      ];
      expect(labels.some(label => /\*/.test(label))).toBe(false);
    }

    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');
    expect(source).toContain(':hide-required-asterisk="true"');
  });

  it('违规编辑弹窗保存前执行表单校验', () => {
    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');

    expect(source).toContain('ref="recordFormRef"');
    expect(source).toContain(':rules="dialogRules"');
    expect(source).toContain(':prop="field.key"');
    expect(source).toContain('await recordFormRef.value?.validate()');
  });

  it('仅在页面渲染可见字段，兼容旧字段仍会保留在保存载荷中', () => {
    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');
    const formField = creditConfig.formFields.find(item => item.key === 'remark');

    expect(source).toContain('v-for="field in visibleFormFields"');
    expect(source).toContain('config.value.formFields.filter(field => !field.hidden)');
    expect(formField.hidden).toBe(true);
    expect(buildViolationSavePayload(creditConfig, { remark: '历史备注' })).toHaveProperty('remark', '历史备注');
  });

  it('查看态统一隐藏空表单字段的提示词，新增编辑态保留原提示', () => {
    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');
    const formTemplate = source.match(/<div class="record-grid">([\s\S]*?)<\/div>/)?.[1] || '';

    expect(formTemplate.match(/:placeholder="formFieldPlaceholder\(field\)"/g) || []).toHaveLength(5);
    expect(source).toContain("if (dialog.mode === 'view') return '';");
    expect(source).toContain("if (field.type === 'select') return '请选择';");
    expect(source).toContain("if (field.type === 'datetime') return '请选择时间';");
    expect(source).toContain("if (field.type === 'date') return '请选择日期';");
    expect(source).toContain('return `请输入${field.label}`;');
  });

  it('查看和编辑必须先取得详情再构造并展示表单，不回退列表行', () => {
    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');
    const openWithMode = source.match(/async function openWithMode\(row, mode\) \{([\s\S]*?)\n\}\nfunction openEdit/)?.[1] || '';
    const detailIndex = openWithMode.indexOf('await getViolation(config.value.kind, row.id)');
    const formIndex = openWithMode.indexOf('buildViolationFormData(config.value, detail)');
    const showIndex = openWithMode.indexOf('dialog.show = true');

    expect(detailIndex).toBeGreaterThanOrEqual(0);
    expect(formIndex).toBeGreaterThan(detailIndex);
    expect(showIndex).toBeGreaterThan(formIndex);
    expect(openWithMode).not.toContain('buildViolationFormData(config.value, row)');
    expect(openWithMode).toContain('ElMessage.error');
  });

  it('详情请求只允许最新点击结果提交，新增操作会使未完成详情失效', () => {
    const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');
    const openWithMode = source.match(/async function openWithMode\(row, mode\) \{([\s\S]*?)\n\}\nfunction openEdit/)?.[1] || '';
    const createBlock = source.match(/function openCreate\(\) \{([\s\S]*?)\n\}/)?.[1] || '';

    expect(openWithMode).toContain('const requestToken = ++detailRequestToken');
    expect(openWithMode).toContain('if (requestToken !== detailRequestToken) return;');
    expect(createBlock).toContain('++detailRequestToken');
  });

  it('信贷风险责任系数搜索使用数值范围且不固定补四位小数', () => {
    const searchField = creditConfig.searchFields.find(item => item.key === 'coefficientOfResponsibility');
    const formField = creditConfig.formFields.find(item => item.key === 'coefficientOfResponsibility');

    expect(searchField.type).toBe('number-range');
    expect(searchField.minKey).toBe('coefficientOfResponsibilityMin');
    expect(searchField.maxKey).toBe('coefficientOfResponsibilityMax');
    expect(searchField.fixedPrecision).toBe(false);
    expect(resolveNumberRangePrecision(searchField)).toBeUndefined();
    expect(formField.type).toBe('text');
  });

  it('两个文本文件中同名枚举保持各表自己的取值', () => {
    expect(GENERAL_HANDLING_OPTIONS.accountability).toEqual(['诫勉谈话', '通报批评', '降职', '免职', '解除劳动合同']);
    expect(GENERAL_HANDLING_OPTIONS.credit).toContain('停职检查');
    expect(DISCIPLINARY_ACTION_OPTIONS.accountability).toContain('解除劳动合同');
    expect(DISCIPLINARY_ACTION_OPTIONS.credit).not.toContain('解除劳动合同');
  });

  it('回收返还是兼容旧字段，不参与新模板页面搜索或展示', () => {
    const searchField = creditConfig.searchFields.find(item => item.key === 'recycleAndReturn');
    const formField = creditConfig.formFields.find(item => item.key === 'recycleAndReturn');

    expect(searchField).toBeUndefined();
    expect(formField.label).toBe('回收返还（元）');
    expect(formField.type).toBe('text');
    expect(formField.hidden).toBe(true);
  });

  it('信贷风险责任占比搜索不固定补四位小数，新增编辑表单使用文本输入', () => {
    const searchField = creditConfig.searchFields.find(item => item.key === 'responsibilityRatio');
    const formField = creditConfig.formFields.find(item => item.key === 'responsibilityRatio');

    expect(searchField.type).toBe('number-range');
    expect(searchField.minKey).toBe('responsibilityRatioMin');
    expect(searchField.maxKey).toBe('responsibilityRatioMax');
    expect(searchField.fixedPrecision).toBe(false);
    expect(resolveNumberRangePrecision(searchField)).toBeUndefined();
    expect(formField.type).toBe('text');

    const payload = buildViolationSavePayload(creditConfig, { responsibilityRatio: '12.5%' });
    expect(payload.responsibilityRatio).toBe('12.5%');
  });

  it('新增编辑表单不再生成数值输入控件', () => {
    for (const config of [accountabilityConfig, creditConfig]) {
      expect(config.formFields.filter(field => field.type === 'number')).toEqual([]);
    }
  });

  it('责任系数和责任占比列表去掉无意义的末尾小数零', () => {
    expect(trimTrailingDecimalZeros('12.0000')).toBe('12');
    expect(trimTrailingDecimalZeros('12.5000')).toBe('12.5');
    expect(trimTrailingDecimalZeros('8.7500%')).toBe('8.75%');
    expect(trimTrailingDecimalZeros('非数字')).toBe('非数字');

    for (const key of ['coefficientOfResponsibility', 'responsibilityRatio']) {
      expect(creditConfig.columns.find(column => column.key === key)?.format).toBe('trim-decimal-zeros');
    }
  });

  it('责任系数和责任占比编辑反显时去掉历史值的小数尾零', () => {
    const form = buildViolationFormData(creditConfig, {
      coefficientOfResponsibility: '2.0000',
      responsibilityRatio: '8.7500%'
    });

    expect(form.coefficientOfResponsibility).toBe('2');
    expect(form.responsibilityRatio).toBe('8.75%');
  });

  it('信贷风险字段使用新导入模板文案', () => {
    const expectedLabels = {
      position: '职务(岗位)',
      processingType: '处理类型',
      amountWithheld2: '扣发金额（元）',
      withholdingInstructions: '扣发说明',
      nameAndNumberOfAccountabilityDocument: '问责文件名称及文号'
    };

    for (const [key, label] of Object.entries(expectedLabels)) {
      expect(creditConfig.searchFields.find(item => item.key === key)?.label).toBe(label);
      expect(creditConfig.formFields.find(item => item.key === key)?.label).toBe(label);
    }
    expect(creditConfig.columns.find(item => item.key === 'position')?.label).toBe('职务(岗位)');
  });

  it('信贷风险职务下拉项使用“主办客户经理”', () => {
    const field = creditConfig.searchFields.find(item => item.key === 'position');
    const values = field.options.map(item => item.value);

    expect(values).toContain('主办客户经理');
    expect(values).not.toContain('主板客户经理');
  });

  it('信贷风险机构层级使用文本输入框', () => {
    const searchField = creditConfig.searchFields.find(item => item.key === 'institutionalLevel');
    const formField = creditConfig.formFields.find(item => item.key === 'institutionalLevel');

    expect(searchField.type).toBe('text');
    expect(searchField.options).toBeUndefined();
    expect(formField.type).toBe('text');
    expect(formField.options).toBeUndefined();
  });

  it('人员违规搜索项使用违规事实发生时职务文案', () => {
    const field = accountabilityConfig.searchFields.find(item => item.key === 'postAtTheTime');
    expect(field.label).toBe('违规事实发生时职务');
  });

  it('人员违规表单和列表表头使用违规事实发生时职务文案', () => {
    const formField = accountabilityConfig.formFields.find(item => item.key === 'postAtTheTime');
    const column = accountabilityConfig.columns.find(item => item.key === 'postAtTheTime');
    expect(formField.label).toBe('违规事实发生时职务');
    expect(column.label).toBe('违规事实发生时职务');
  });

  it('人员违规搜索项使用新导入模板的被问责时岗位、职务文案', () => {
    const field = accountabilityConfig.searchFields.find(item => item.key === 'accountabilityPositions');
    expect(field.label).toBe('被问责时岗位、职务');
  });

  it('被问责时岗位、职务紧跟违规事实发生时职务', () => {
    for (const fields of [accountabilityConfig.searchFields, accountabilityConfig.formFields]) {
      const postIndex = fields.findIndex(item => item.key === 'postAtTheTime');
      expect(fields[postIndex + 1]?.key).toBe('accountabilityPositions');
    }
  });

  it('处罚及报送时间使用精简的起止提示', () => {
    for (const key of ['penaltyTime', 'submissionTime']) {
      const field = accountabilityConfig.searchFields.find(item => item.key === key);
      expect(field.startPlaceholder).toBe('开始');
      expect(field.endPlaceholder).toBe('结束');
    }
    const creditSubmission = creditConfig.formFields.find(item => item.key === 'submissionTime');
    expect(creditSubmission.hidden).toBe(true);
    expect(creditSubmission.startPlaceholder).toBe('开始');
    expect(creditSubmission.endPlaceholder).toBe('结束');
  });

  it('人员违规页面按新模板展示处罚解除时间', () => {
    expect(accountabilityConfig.searchFields.map(field => field.key)).toContain('penaltyReleaseTime');
    expect(accountabilityConfig.formFields.find(field => field.key === 'penaltyReleaseTime')?.hidden).not.toBe(true);
  });

  it('扣发金额区间不显示最小值和最大值提示', () => {
    const accountabilityAmount = accountabilityConfig.searchFields.find(item => item.key === 'withholdingAmount');
    const creditAmounts = ['amountWithheld', 'amountWithheld2']
      .map(key => creditConfig.searchFields.find(item => item.key === key));

    for (const field of [accountabilityAmount, ...creditAmounts]) {
      expect(field.minPlaceholder).toBe('');
      expect(field.maxPlaceholder).toBe('');
    }
  });

  it('信贷风险指定数值区间不显示最小值和最大值提示', () => {
    const fields = [
      'coefficientOfResponsibility',
      'responsibilityRatio',
      'estimatedLoss',
      'insurancePrincipal'
    ].map(key => creditConfig.searchFields.find(item => item.key === key));

    for (const field of fields) {
      expect(field.minPlaceholder).toBe('');
      expect(field.maxPlaceholder).toBe('');
    }
  });

  it('信贷风险金额搜索框不自动补四位小数', () => {
    const amountFields = [
      'insurancePrincipal',
      'estimatedLoss',
      'amountWithheld',
      'amountWithheld2'
    ].map(key => creditConfig.searchFields.find(item => item.key === key));

    for (const field of amountFields) {
      expect(field.fixedPrecision).toBe(false);
      expect(resolveNumberRangePrecision(field)).toBeUndefined();
    }
  });

  it('人员违规金额搜索框不自动补四位小数', () => {
    const field = accountabilityConfig.searchFields.find(item => item.key === 'withholdingAmount');
    expect(field.fixedPrecision).toBe(false);
    expect(resolveNumberRangePrecision(field)).toBeUndefined();
  });

  it('人员违规新增和编辑的扣发金额使用字符串并去掉历史值尾零', () => {
    const field = accountabilityConfig.formFields.find(item => item.key === 'withholdingAmount');
    const form = buildViolationFormData(accountabilityConfig, { withholdingAmount: '1200.0000' });

    expect(field.type).toBe('text');
    expect(form.withholdingAmount).toBe('1200');
  });
});
