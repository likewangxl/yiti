// @vitest-environment happy-dom
import { readFileSync } from 'node:fs'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const evalApi = vi.hoisted(() => ({
  listUnifiedTasks: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  getTaskDetail: vi.fn().mockResolvedValue({ task: {}, targets: [] }),
  createTask: vi.fn().mockResolvedValue({ taskId: 'T1' }),
  closeTask: vi.fn().mockResolvedValue({ ok: true }),
  publishAssignBatch: vi.fn().mockResolvedValue({ ok: true }),
  getAssignBatchDetail: vi.fn().mockResolvedValue({ batch: {}, items: { records: [], total: 0 } }),
  getRewardBatchDetail: vi.fn().mockResolvedValue({ batch: {}, items: { records: [], total: 0 } }),
  exportAssignBatchItems: vi.fn().mockResolvedValue(new Blob()),
  exportRewardBatchItems: vi.fn().mockResolvedValue(new Blob()),
  exportRuleTask: vi.fn().mockResolvedValue(new Blob()),
  deleteUnifiedTask: vi.fn().mockResolvedValue({ ok: true }),
  downloadAssignTemplate: vi.fn().mockResolvedValue(new Blob()),
  downloadRewardTemplate: vi.fn().mockResolvedValue(new Blob()),
  importAssign: vi.fn().mockResolvedValue({ batchId: 'B1' }),
  importReward: vi.fn().mockResolvedValue({ batchId: 'B2' }),
  listPendingTasks: vi.fn().mockResolvedValue([]),
  listPendingItems: vi.fn().mockResolvedValue([]),
  submitPendingScore: vi.fn().mockResolvedValue({ ok: true }),
  submitPendingScoreBatch: vi.fn().mockResolvedValue({ ok: true }),
  listRewardPendingTasks: vi.fn().mockResolvedValue([]),
  listRewardPendingItems: vi.fn().mockResolvedValue([]),
  submitRewardBatch: vi.fn().mockResolvedValue({ ok: true })
}))

const messageApi = vi.hoisted(() => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/api/eval', () => evalApi)
vi.mock('@/api/users', () => ({ listUsers: vi.fn().mockResolvedValue([]) }))
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ options: { value: [] } }) }))
vi.mock('element-plus', () => messageApi)

import Tasks from '../Tasks.vue'
import MyTasks from '../MyTasks.vue'
import RewardTask from '../RewardTask.vue'

const pageSources = [
  'Tags.vue',
  'UserTags.vue',
  'Rules.vue',
  'Tasks.vue',
  'MyTasks.vue',
  'RewardTask.vue'
]

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="header" /><slot name="footer" /></div>' })
const empty = (name) => ({ name, template: '<div />' })
const stubs = {
  PageTitle: { name: 'PageTitle', inheritAttrs: false, template: '<h1 v-bind="$attrs">评价<slot /></h1>' },
  'el-button': {
    name: 'ElButton', inheritAttrs: false, props: { disabled: Boolean, loading: Boolean }, emits: ['click'],
    template: '<button v-bind="$attrs" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-table': { name: 'ElTable', inheritAttrs: false, props: ['data'], template: '<div v-bind="$attrs"><slot /><slot name="empty" /></div>' },
  'el-table-column': empty('ElTableColumn'),
  'el-pagination': passthrough('ElPagination'),
  'el-dialog': { name: 'ElDialog', props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-form': { name: 'ElForm', methods: { validate: () => Promise.resolve(true) }, template: '<form><slot /></form>' },
  'el-form-item': passthrough('ElFormItem'),
  'el-input': empty('ElInput'),
  'el-input-number': empty('ElInputNumber'),
  'el-select': empty('ElSelect'),
  'el-option': empty('ElOption'),
  'el-date-picker': empty('ElDatePicker'),
  'el-upload': passthrough('ElUpload'),
  'el-alert': empty('ElAlert'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-icon': passthrough('ElIcon'),
  Search: empty('Search'),
  ArrowLeft: empty('ArrowLeft'),
  Clock: empty('Clock'),
  Check: empty('Check'),
  RewardTask: empty('RewardTask')
}

function mountPage(component, props = {}) {
  return mount(component, {
    props,
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  })
}

function deferred() {
  let resolve
  const promise = new Promise((res) => { resolve = res })
  return { promise, resolve }
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

describe('内部评价页面 bp-crud 结构契约', () => {
  it.each(pageSources)('%s 显式声明主平台 CRUD 语义边界与稳定状态钩子', (file) => {
    const source = readFileSync(`${process.cwd()}/src/views/eval/${file}`, 'utf8')
    expect(source).toMatch(/<(?:main|section)[^>]*class="[^"]*bp-crud/)
    expect(source).toMatch(/aria-labelledby=/)
    expect(source).toMatch(/page-h/)
    expect(source).toMatch(/(?:data-panel|data-panel)/)
    expect(source).toMatch(/(?:pager|pagination)/)
    expect(source).toMatch(/(?:aria-busy|table-state|empty)/i)
    expect(source).not.toMatch(/[📥📤✅❌⚠️🔒]/u)
    expect(source).not.toMatch(/#[0-9a-f]{3,8}\b/i)
  })
})

describe('Tasks.vue 发布与关闭行为', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    evalApi.listUnifiedTasks.mockResolvedValue({
      records: [{ sourceType: 'IMPORT', sourceId: 'B1', taskName: '季度评价', status: 2 }], total: 1
    })
    evalApi.publishAssignBatch.mockResolvedValue({ ok: true })
    evalApi.closeTask.mockResolvedValue({ ok: true })
    messageApi.ElMessageBox.confirm.mockResolvedValue('confirm')
  })

  afterEach(() => wrapper?.unmount())

  it('取消发布只关闭确认，不调用发布接口', async () => {
    wrapper = mountPage(Tasks)
    await settle()
    messageApi.ElMessageBox.confirm.mockRejectedValueOnce('cancel')

    await wrapper.vm.handlePublish({ sourceId: 'B1', taskName: '季度评价', status: 2 })

    expect(evalApi.publishAssignBatch).not.toHaveBeenCalled()
  })

  it('发布请求进行中不重复提交，并保持原批次 ID 契约', async () => {
    wrapper = mountPage(Tasks)
    await settle()
    const request = deferred()
    evalApi.publishAssignBatch.mockReturnValueOnce(request.promise)
    const row = { sourceId: 'B1', taskName: '季度评价', status: 2 }

    const first = wrapper.vm.handlePublish(row)
    const second = wrapper.vm.handlePublish(row)
    await settle()

    expect(evalApi.publishAssignBatch).toHaveBeenCalledTimes(1)
    expect(evalApi.publishAssignBatch).toHaveBeenCalledWith('B1')
    request.resolve({ ok: true })
    await Promise.all([first, second])
  })

  it('关闭请求进行中不重复提交，并保持原任务 ID 契约', async () => {
    wrapper = mountPage(Tasks)
    await settle()
    const request = deferred()
    evalApi.closeTask.mockReturnValueOnce(request.promise)
    const row = { sourceId: 'T1', taskName: '规则任务', sourceType: 'AUTO', status: 0 }

    const first = wrapper.vm.handleCloseTask(row)
    const second = wrapper.vm.handleCloseTask(row)
    await settle()

    expect(evalApi.closeTask).toHaveBeenCalledTimes(1)
    expect(evalApi.closeTask).toHaveBeenCalledWith('T1')
    request.resolve({ ok: true })
    await Promise.all([first, second])
  })
})

describe('MyTasks.vue 批量评分行为', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    evalApi.listPendingTasks.mockResolvedValue([])
    evalApi.listRewardPendingTasks.mockResolvedValue([])
    evalApi.submitPendingScoreBatch.mockResolvedValue({ ok: true })
  })

  afterEach(() => wrapper?.unmount())

  it('批量提交请求进行中不重复提交，且 payload 保持 itemId/score', async () => {
    wrapper = mountPage(MyTasks)
    await settle()
    const request = deferred()
    evalApi.submitPendingScoreBatch.mockReturnValueOnce(request.promise)
    wrapper.vm.processView.items = [
      { itemId: 11, scoreType: 'NUM', submitted: 0, beEvalUserName: '张三' },
      { itemId: 22, scoreType: 'GRADE', submitted: 0, beEvalUserName: '李四' }
    ]
    wrapper.vm.editScores[11] = 88
    wrapper.vm.editScores[22] = 95

    const first = wrapper.vm.handleSubmitAll()
    const second = wrapper.vm.handleSubmitAll()
    await settle()

    expect(evalApi.submitPendingScoreBatch).toHaveBeenCalledTimes(1)
    expect(evalApi.submitPendingScoreBatch).toHaveBeenCalledWith([
      { itemId: 11, score: 88 }, { itemId: 22, score: 95 }
    ])
    request.resolve({ ok: true })
    await Promise.all([first, second])
  })
})

describe('RewardTask.vue 金额分配行为', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    evalApi.listRewardPendingItems.mockResolvedValue([
      { itemId: 1, beAssignedUserId: 'U1', beAssignedUserName: '张三', originalValue: 0, assignTotal: 100, submitted: 0 },
      { itemId: 2, beAssignedUserId: 'U2', beAssignedUserName: '李四', originalValue: 0, assignTotal: 100, submitted: 0 }
    ])
    evalApi.submitRewardBatch.mockResolvedValue({ ok: true })
  })

  afterEach(() => wrapper?.unmount())

  it('金额合计不满足分配合计时不请求', async () => {
    wrapper = mountPage(RewardTask, { group: { batchId: 'B2', dept: '部门A', assignTotal: 100 } })
    await settle()
    wrapper.vm.editValues[1] = 20
    wrapper.vm.editValues[2] = 30

    await wrapper.vm.handleSubmit()

    expect(evalApi.submitRewardBatch).not.toHaveBeenCalled()
  })

  it('金额提交请求进行中不重复提交，且 payload 保持批次/部门/分配值契约', async () => {
    wrapper = mountPage(RewardTask, { group: { batchId: 'B2', dept: '部门A', assignTotal: 100 } })
    await settle()
    const request = deferred()
    evalApi.submitRewardBatch.mockReturnValueOnce(request.promise)
    wrapper.vm.editValues[1] = 60
    wrapper.vm.editValues[2] = 40

    const first = wrapper.vm.handleSubmit()
    const second = wrapper.vm.handleSubmit()
    await settle()

    expect(evalApi.submitRewardBatch).toHaveBeenCalledTimes(1)
    expect(evalApi.submitRewardBatch).toHaveBeenCalledWith('B2', '部门A', [
      { itemId: 1, assignValue: 60 }, { itemId: 2, assignValue: 40 }
    ])
    request.resolve({ ok: true })
    await Promise.all([first, second])
  })
})
