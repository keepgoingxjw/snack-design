const { test } = require('node:test')
const assert = require('node:assert/strict')
const api = require('../utils/api')
const records = require('../utils/records')
test('旧后端缺少能力标识时给出升级提示，而不是继续请求404接口', async () => {
  const original = api.request
  try {
    api.request = async () => ({ status: 'ok' })
    await assert.rejects(records.ensureResearchApi(), /旧版后端/)
    api.request = async () => ({ status: 'ok', apiVersion: 3, researchAvailable: true })
    assert.equal((await records.ensureResearchApi()).apiVersion, 3)
  } finally { api.request = original }
})
