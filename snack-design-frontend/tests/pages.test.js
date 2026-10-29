const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const store = require('../utils/storage')
const records = require('../utils/records')
function loadPage(path) {
  let page
  global.Page = definition => { page = definition }
  delete require.cache[require.resolve(path)]
  require(path)
  page.data = JSON.parse(JSON.stringify(page.data))
  page.setData = patch => Object.assign(page.data, patch)
  return page
}
beforeEach(() => {
  const values = new Map()
  global.wx = { getStorageSync: k => values.get(k), setStorageSync: (k, v) => values.set(k, v), removeStorageSync: k => values.delete(k), redirectTo() {}, showToast() {} }
  global.getApp = () => ({ globalData: {} })
  records.find = async id => { const r = store.find(id); if (!r) throw new Error('记录不存在'); return r }
  records.save = async (...args) => store.save(...args)
  records.latestResearch = async () => ({ exists: false })
  records.ensureResearchApi = async () => ({ apiVersion: 3 })
})
test('编辑已有示例保留来源并更新而非新增记录', async () => {
  const r = store.save('乳粉', '虚构示例')
  const page = loadPage('../pages/edit/edit')
  await page.onLoad({ id: r.id })
  assert.equal(page.data.kind, '虚构示例')
  page.input({ detail: { value: '乳粉、白砂糖' } })
  await page.submit()
  page.onUnload()
  assert.equal(store.all().length, 1)
  assert.equal(store.find(r.id).raw, '乳粉、白砂糖')
  assert.equal(store.draft(), null)
})
test('输入错误不保存记录，退出后可恢复草稿', async () => {
  const page = loadPage('../pages/edit/edit')
  await page.onLoad({})
  page.input({ detail: { value: '乳粉（' } })
  await page.submit()
  assert.match(page.data.error, /括号/)
  assert.equal(store.all().length, 0)
  const resumed = loadPage('../pages/edit/edit')
  await resumed.onLoad({ draft: '1' })
  assert.equal(resumed.data.raw, '乳粉（')
})
test('调研页只读取保存结果，不会打开页面就重复付费查询', async () => {
  const r = store.save('苯甲酸钠', '手动输入')
  let calls = 0
  records.research = async () => { calls++; throw new Error('不应调用') }
  const report = { id: 'test-report', status: 'done', foodCategory: '测试', createdAt: Date.now(), findings: [{ name: '苯甲酸钠', sources: [] }], model: 'test-model' }
  records.latestResearch = async () => ({ exists: true, report })
  const page = loadPage('../pages/result/result')
  page.onLoad({ id: r.id }); await page.onShow()
  assert.equal(page.data.findings[0].name, '苯甲酸钠')
  assert.equal(page.data.busy, false)
  assert.equal(calls, 0)
  store.remove(r.id); await page.onShow()
  assert.equal(page.data.record, null)
})
test('未配置密钥的错误原样展示，不使用示例内容代替', async () => {
  const r = store.save('苯甲酸钠', '手动输入')
  const page = loadPage('../pages/result/result')
  page.onLoad({ id: r.id }); await page.onShow()
  records.research = async () => { throw new Error('后端未配置 OPENAI_API_KEY') }
  await page.query()
  assert.match(page.data.error, /OPENAI_API_KEY/)
  assert.deepEqual(page.data.findings, [])
  assert.equal(page.data.busy, false)
})

test('公开文献结果展示真实摘要与来源，不生成AI食用建议', () => {
  const page = loadPage('../pages/result/result')
  page.display({ id: 'public-report', status: 'done', model: 'public-search/EuropePMC', createdAt: Date.now(),
    findings: [{ name: '苯甲酸钠', group: 'pending', sources: [{ title: 'Food study', url: 'https://europepmc.org/article/MED/123', quote: 'Abstract excerpt', textVerified: false }] }] })
  assert.equal(page.data.publicSearch, true)
  assert.equal(page.data.recommendation, null)
  assert.equal(page.data.groups[0].rows[0].sources[0].quote, 'Abstract excerpt')
  assert.equal(page.data.busy, false)
})

test('切换免费云端模式后旧词表结果标记为需要重新解析', () => {
  const page = loadPage('../pages/result/result')
  page.aiModel = 'openrouter/free'
  page.display({ id: 'old', status: 'done', model: 'public-search/EuropePMC', createdAt: Date.now(), findings: [] })
  assert.equal(page.data.modeChanged, true)
  page.display({ id: 'new', status: 'done', model: 'OpenRouter/example:free · 免费AI + 公开文献', createdAt: Date.now(), findings: [] })
  assert.equal(page.data.modeChanged, false)
})
test('DeepSeek切换后不把免费结果当新结果，查看历史不自动计费', async () => {
  const r = store.save('苯甲酸钠', '手动输入')
  const model = 'DeepSeek/deepseek-flash · 联网搜索'
  let calls = 0
  records.ensureResearchApi = async () => ({ apiVersion: 3, aiModel: model })
  records.latestResearch = async () => ({ exists: true, report: { id: 'old', status: 'done', foodCategory: '', model: 'OpenRouter/test:free · 免费AI + 公开文献', createdAt: Date.now(), findings: [] } })
  records.research = async () => { calls++; return { id: 'new', status: 'done', foodCategory: '', model, createdAt: Date.now(), findings: [] } }
  const page = loadPage('../pages/result/result')
  page.onLoad({ id: r.id }); await page.onShow()
  assert.equal(page.data.providerLabel, 'DeepSeek')
  assert.equal(page.data.modeChanged, true)
  assert.equal(calls, 0)
  const submitted = loadPage('../pages/result/result')
  submitted.onLoad({ id: r.id, auto: '1' }); await submitted.onShow()
  assert.equal(calls, 1)
  assert.equal(submitted.data.modeChanged, false)
  records.latestResearch = async () => ({ exists: true, report: submitted.data.report })
  await submitted.onShow()
  assert.equal(calls, 1)
})
test('好处和风险按短要点显示，关键词突出，旧段落仍可读取', () => {
  const page = loadPage('../pages/result/result')
  page.display({ id: 'summary', status: 'done', model: 'test', createdAt: Date.now(), findings: [{
    name: '测试配料', group: 'other', sources: [],
    benefit: '• 主要作用：根据资料说明用途。\n• 加工价值：只概括已查到的特点。',
    risk: '• 相关人群：说明特定人群的注意事项。\n• 使用限制：法规限制不是人体危害。'
  }] })
  const row = page.data.groups[0].rows[0]
  assert.equal(row.benefitPoints.length, 2)
  assert.equal(row.benefitPoints[0].lead, '主要作用：')
  assert.equal(row.riskPoints[1].text, '法规限制不是人体危害。')
  page.display({ id: 'old', status: 'done', model: 'test', createdAt: Date.now(), findings: [{ name: '测试', group: 'other', sources: [], benefit: '历史单段文字', risk: '旧风险描述' }] })
  assert.equal(page.data.findings[0].benefitPoints[0].text, '历史单段文字')
  assert.equal(page.data.findings[0].benefitPoints[0].bullet, false)
  page.aiModel = 'DeepSeek/deepseek-flash · 联网搜索 · 要点总结v2'
  page.display({ id: 'old-style', status: 'done', model: 'DeepSeek/deepseek-flash · 联网搜索', createdAt: Date.now(), findings: [] })
  assert.equal(page.data.modeChanged, true)
  page.display({ id: 'new-style', status: 'done', model: page.aiModel, createdAt: Date.now(), findings: [] })
  assert.equal(page.data.modeChanged, false)
})
test('离开页面停止轮询，返回页面可恢复任务', async () => {
  const r = store.save('苯甲酸钠', '手动输入')
  records.latestResearch = async () => ({ exists: true, report: { id: 'running', status: 'running', findings: [], createdAt: Date.now() } })
  const page = loadPage('../pages/result/result')
  page.onLoad({ id: r.id }); await page.onShow()
  assert.equal(page.data.busy, true)
  assert.ok(page.timer)
  page.onHide()
  assert.equal(page.stopped, true)
})
test('从原文页提交后直接进入结果页并标记自动解析', async () => {
  const page = loadPage('../pages/edit/edit')
  await page.onLoad({})
  page.input({ detail: { value: '苯甲酸钠' } })
  let url = ''
  wx.redirectTo = options => { url = options.url }
  await page.submit()
  assert.match(url, /auto=1/)
})
test('自动解析只启动一次，并按AI实际分组展示，不出现查询设置页', async () => {
  const r = store.save('苯甲酸钠', '手动输入')
  let calls = 0
  records.research = async () => {
    calls++
    return { id: 'free-test', status: 'done', createdAt: Date.now(), model: 'ollama/test', findings: [{ name: '苯甲酸钠', role: '测试角色', group: 'preservation', sources: [] }] }
  }
  const page = loadPage('../pages/result/result')
  page.onLoad({ id: r.id, auto: '1' }); await page.onShow()
  assert.equal(calls, 1)
  assert.equal(page.data.groups[0].id, 'preservation')
  assert.equal(page.data.groups[0].rows[0].name, '苯甲酸钠')
  records.latestResearch = async () => ({ exists: true, report: page.data.report })
  await page.onShow()
  assert.equal(calls, 1)
})
test('最后建议展示条件与人群，旧记录不被判定为吃一点没事', async () => {
  const r = store.save('测试配料', '手动输入')
  const page = loadPage('../pages/result/result')
  page.onLoad({ id: r.id }); await page.onShow()
  page.display({ id: 'old', status: 'done', createdAt: Date.now(), findings: [] })
  assert.equal(page.data.recommendation.action, 'check_first')
  assert.match(page.data.recommendation.conditions, /不能/)
  const advice = { action: 'avoid_for_group', title: '相关人群建议避免', audience: '测试限制人群', reason: '仅为测试条件', conditions: '不是所有人的安全结论', sourceUrls: [] }
  page.display({ id: 'new', status: 'done', createdAt: Date.now(), findings: [], recommendation: advice })
  assert.equal(page.data.recommendation.audience, '测试限制人群')
  page.display({ id: 'running', status: 'running', createdAt: Date.now(), findings: [] })
  assert.equal(page.data.recommendation, null)
})
test('上传失败保留原文和草稿，并允许重试', async () => {
  const page = loadPage('../pages/edit/edit')
  await page.onLoad({})
  page.input({ detail: { value: '乳粉' } })
  records.save = async () => { throw new Error('无法连接后端') }
  await page.submit()
  assert.equal(page.data.saving, false)
  assert.equal(page.data.raw, '乳粉')
  assert.equal(store.draft().raw, '乳粉')
  assert.match(page.data.error, /无法连接/)
})
