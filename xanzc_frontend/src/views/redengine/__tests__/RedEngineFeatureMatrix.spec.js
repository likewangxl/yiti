// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('@/api/http', () => ({
  call: vi.fn()
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}))

const routerPush = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => ({ push: routerPush, resolve: vi.fn() })
}))

vi.mock('@/api/redengine', async () => {
  const actual = await vi.importActual('@/api/redengine')
  return {
    ...actual,
    getMySubmits: vi.fn(),
    listMyTaskAssignments: vi.fn(),
    getOrgTree: vi.fn(),
    listUsers: vi.fn()
  }
})

vi.mock('@/api/users', () => ({
  listUsers: vi.fn().mockResolvedValue({ records: [], total: 0 })
}))

import { call } from '@/api/http'
import { getOrgTree, getMySubmits, listMyTaskAssignments, listMaterialDetailItems } from '@/api/redengine'
import RecordsView from '../records/RecordsView.vue'
import NewTaskView from '../tasks/NewTaskView.vue'
import { validateTaskDraft } from '../tasks/task-domain'

const stubs = {
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { props: ['label'], template: '<button>{{ label }}<slot /></button>' },
  'el-input': {
    props: ['modelValue', 'placeholder', 'type'],
    emits: ['update:modelValue'],
    template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': { props: ['modelValue'], template: '<select><slot /></select>' },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-empty': { template: '<div />' },
  'el-card': { template: '<section><slot /></section>' },
  'el-form': {
    template: '<form><slot /></form>',
    setup(_, { expose }) {
      expose({ validate: () => Promise.resolve(true) })
      return {}
    }
  },
  'el-form-item': { props: ['label'], template: '<label><span>{{ label }}</span><slot /></label>' },
  'el-date-picker': { props: ['modelValue'], template: '<input :value="modelValue" />' },
  'el-input-number': { props: ['modelValue'], template: '<input :value="modelValue" />' },
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-checkbox': { props: ['label'], template: '<label><input :value="label" /><slot /></label>' },
  'el-switch': { props: ['modelValue'], template: '<input :checked="modelValue" />' }
}

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

describe('红色引擎跨页面契约矩阵', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    call.mockResolvedValue([])
    getMySubmits.mockResolvedValue({ records: [], total: 0 })
    listMyTaskAssignments.mockResolvedValue({ records: [], total: 0 })
    getOrgTree.mockResolvedValue([
      { id: 1, orgName: '组织本部', orgLevel: 1, children: [
        { id: 11, orgName: '第一党支部', orgLevel: 2, children: [] }
      ] }
    ])
  })

  it('四维明细读取通用治理字典，而不是不存在的任务字典端点', async () => {
    await listMaterialDetailItems()

    expect(call).toHaveBeenCalledWith('get', '/sys/dicts/RE_ITEM_CODE/items')
  })

  it('任务域拒绝后端必拒的临时四维组合', () => {
    const result = validateTaskDraft({
      title: '临时材料',
      description: '请填报',
      nature: 'TEMPORARY',
      businessType: 'FOUR_DIMENSION',
      audienceType: 'ALL_BRANCH',
      startAt: '2026-08-20 09:00:00',
      endAt: '2026-08-20 18:00:00',
      requiresFile: false
    })

    expect(result.valid).toBe(false)
    expect(result.errors.businessType).toMatch(/定时任务/)
  })

  it('报送员上报信息查询使用服务端页签和状态分页', async () => {
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    })
    await settle()

    expect(listMyTaskAssignments).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 10,
      tab: 'PENDING'
    })
    wrapper.unmount()
  })

  it('新增任务对象下拉只提供 orgLevel=2 的党支部，并保留员工对象入口', async () => {
    const wrapper = mount(NewTaskView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    })
    await settle()

    expect(wrapper.vm.branchOptions).toEqual([{ value: 11, label: '组织本部 / 第一党支部' }])
    expect(wrapper.text()).toContain('指定员工')
    wrapper.unmount()
  })
})
