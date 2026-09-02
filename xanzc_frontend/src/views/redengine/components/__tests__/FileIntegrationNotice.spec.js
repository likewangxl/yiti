// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FileIntegrationNotice from '../FileIntegrationNotice.vue'

describe('红色引擎文件能力提示', () => {
  it('明确当前上传、下载是开发态 mock，不是真实文件联调', () => {
    const wrapper = mount(FileIntegrationNotice)

    expect(wrapper.find('[data-test="file-dev-notice"]').attributes('role')).toBe('note')
    expect(wrapper.text()).toContain('文件上传、下载当前为开发态 mock，非真实文件联调')
  })
})
