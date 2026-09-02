export function createLeadEntryInitialState() {
  return {
    leadType: 'NEW_ACCOUNT',
    custNo: '',
    custName: '',
    unifiedCreditCode: '',
    contactPerson: '',
    contactMobile: '',
    industry: '',
    groupType: '',
    customerType: '',
    isKeystone: null,
    enterpriseType: '',
    groupName: '',
    isAccountOpened: null,
    touchRestricted: 1,
    customerDesc: '',
    creditAmount: undefined,
    creditExposureAmount: undefined,
    leadSource: '',
    distributionMode: 'PUBLIC',
    mainManagerId: '',
    managerScopeIds: [],
    tagIdList: [],
    attachmentIds: [],
    remark: ''
  };
}

export function createLeadEntryRules(form) {
  return {
    custName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
    unifiedCreditCode: [
      { required: true, message: '请输入统一社会信用代码', trigger: 'blur' },
      { pattern: /^[0-9A-Z]{18}$/, message: '统一社会信用代码须为18位大写字母或数字', trigger: 'blur' }
    ],
    distributionMode: [{ required: true, message: '请选择分配方式', trigger: 'change' }],
    touchRestricted: [{ required: true, message: '请选择是否触达限制', trigger: 'change' }],
    managerScopeIds: [{
      validator: (_, value, done) => form.distributionMode === 'SCOPE' && (!value || !value.length)
        ? done(new Error('请选择至少一名客户经理'))
        : done(),
      trigger: 'change'
    }]
  };
}

export function getDistributionHelp(mode) {
  return {
    PUBLIC: '审批通过后进入全行公开待认领客户池。',
    SCOPE: '审批通过后直接进入指定客户经理的已认领客户池。',
    OWNER: '审批通过后为存量客户主办人建立认领关系并自动生成首次触达任务。'
  }[mode];
}
