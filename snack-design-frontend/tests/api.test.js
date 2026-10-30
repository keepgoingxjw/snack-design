const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
let values, api, calls
beforeEach(() => {
  values = new Map(); calls = []
  global.wx = {
    getStorageSync: k => values.get(k), setStorageSync: (k, v) => values.set(k, v), removeStorageSync: k => values.delete(k),
    request: options => {
      calls.push(options)
      if (options.url.endsWith('/auth/dev')) options.success({ statusCode: 200, data: { token: 'test-token', expiresAt: Date.now() + 600000 } })
      else options.success({ statusCode: 200, data: [] })
    }
  }
  delete require.cache[require.resolve('../utils/api')]
  api = require('../utils/api')
})
test('并发请求只登录一次，并携带 Bearer 会话', async () => {
  await Promise.all([api.request('/records'), api.request('/records')])
  assert.equal(calls.filter(c => c.url.endsWith('/auth/dev')).length, 1)
  assert.equal(calls[1].header.Authorization, 'Bearer test-token')
})
test('401重新登录一次，400不重试', async () => {
  let failures = 0
  const original = wx.request
  wx.request = options => {
    if (options.url.endsWith('/records') && failures++ === 0) { calls.push(options); options.success({ statusCode: 401, data: { message: 'expired' } }) }
    else original(options)
  }
  await api.request('/records')
  assert.equal(calls.filter(c => c.url.endsWith('/auth/dev')).length, 2)
  wx.request = options => options.success({ statusCode: 400, data: { message: '原文错误' } })
  await assert.rejects(api.request('/records', 'POST', {}), /原文错误/)
})
test('网络失败显示指定短文案，并保留微信域名校验原因供排查', async () => {
  wx.request = options => options.fail({ errMsg: 'request:fail url not in domain list' })
  await assert.rejects(api.request('/records'), error => {
    assert.equal(error.message, '网络错误，请稍后重试')
    assert.equal(error.code, 'NETWORK_ERROR')
    assert.equal(error.networkReason, 'domain_not_allowed')
    assert.match(error.nativeMessage, /domain list/)
    return true
  })
})
