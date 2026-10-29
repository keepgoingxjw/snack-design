// 真实联网验收：驱动小程序的 records/API/result 页面逻辑，测试后清理临时记录。
const assert = require('node:assert/strict')
const path = require('node:path')
const frontend = path.resolve(__dirname, '../../snack-design-frontend')
const config = require(path.join(frontend, 'utils/config'))
if (process.env.SMOKE_BASE_URL) config.baseUrl = process.env.SMOKE_BASE_URL
const values = new Map()
global.wx = {
  getStorageSync: key => values.get(key), setStorageSync: (key, value) => values.set(key, value), removeStorageSync: key => values.delete(key),
  request: options => fetch(options.url, { method: options.method, headers: options.header,
    body: options.data ? JSON.stringify(options.data) : undefined, signal: AbortSignal.timeout(options.timeout) })
    .then(async response => options.success({ statusCode: response.status, data: await response.json() })).catch(options.fail)
}
const records = require(path.join(frontend, 'utils/records'))
let page, record
const cloud = process.env.SMOKE_MODE === 'cloud'
global.Page = definition => { page = definition }
require(path.join(frontend, 'pages/result/result'))
page.data = JSON.parse(JSON.stringify(page.data))
page.setData = patch => Object.assign(page.data, patch)
;(async () => {
  const health = await records.ensureResearchApi()
  if (cloud) assert.ok(health.aiModel === 'openrouter/free' || /:free$/.test(health.aiModel))
  else assert.equal(health.aiModel, 'public-search/EuropePMC')
  assert.equal(health.aiConfigured, true, '云端密钥尚未配置')
  assert.equal(health.zeroCostOnly, true)
  record = await records.save(cloud ? '丙酸钙、山梨酸钾' : '苯甲酸钠、柠檬酸', '手动输入')
  page.onLoad({ id: record.id, auto: '1' })
  await page.onShow()
  for (let i = 0; page.data.busy && i < (cloud ? 400 : 40); i++) {
    clearTimeout(page.timer)
    await new Promise(resolve => setTimeout(resolve, 1000))
    await page.poll(page.data.report.id)
  }
  assert.equal(page.data.report && page.data.report.status, 'done', page.data.error)
  assert.equal(page.data.publicSearch, !cloud)
  if (!cloud) assert.equal(page.data.recommendation, null)
  assert.equal(page.data.findings.length, 2)
  for (const finding of page.data.findings) {
    assert.ok(finding.sources.length > 0, finding.name + ' 没有真实来源')
    assert.ok(finding.sources.every(source => source.url.startsWith('https://europepmc.org/article/') && source.quote.length > 0))
    assert.equal(finding.dosage, null)
    if (cloud) {
      assert.match(finding.benefit, /[\u4e00-\u9fff]/, '解释必须为中文')
      assert.ok(finding.sources.every(source => source.textVerified), '引用必须匹配实际检索摘要')
    }
  }
  const reportId = page.data.report.id
  page.onHide()
  await page.onShow()
  assert.equal(page.data.report.id, reportId, '重新打开应复用已保存结果')
  console.log(JSON.stringify({ baseUrl: config.baseUrl, status: 'passed', mode: page.data.report.model,
    displayed: page.data.groups.map(group => ({ group: group.title, ingredients: group.rows.map(row => ({ name: row.name, sources: row.sources.length })) })),
    checks: ['真实公开API', '真实小程序API适配器', '结果页轮询与展示数据', '快照复用', cloud ? '真实免费AI理解与中文解释' : '无AI安全建议'] }, null, 2))
})().catch(error => { console.error(error); process.exitCode = 1 }).finally(async () => {
  if (page) page.onUnload()
  if (record) await records.remove(record.id)
})
