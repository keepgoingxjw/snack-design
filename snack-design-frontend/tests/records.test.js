const { test } = require('node:test')
const assert = require('node:assert/strict')
const api = require('../utils/api')
const records = require('../utils/records')

test('密钥或免费搜索未就绪时直接展示原因，不创建解析任务；就绪后提交', async () => {
  const original = api.request
  const calls = []
  try {
    api.request = async path => { calls.push(path); return { ready: false, message: '免费搜索尚未启动' } }
    await assert.rejects(records.research('record', '', false), /免费搜索尚未启动/)
    assert.deepEqual(calls, ['/research/readiness'])
    api.request = async (path, method, data) => {
      if (path === '/research/readiness') return { ready: true }
      assert.equal(method, 'POST')
      assert.deepEqual(data, { foodCategory: '饮料', refresh: false })
      return { id: 'report', status: 'queued' }
    }
    assert.equal((await records.research('record', '饮料')).status, 'queued')
  } finally { api.request = original }
})
test('旧后端缺少能力标识时给出升级提示，而不是继续请求404接口', async () => {
  const original = api.request
  try {
    api.request = async () => ({ status: 'ok' })
    await assert.rejects(records.ensureResearchApi(), /旧版后端/)
    api.request = async () => ({ status: 'ok', apiVersion: 3, researchAvailable: true })
    assert.equal((await records.ensureResearchApi()).apiVersion, 3)
  } finally { api.request = original }
})
