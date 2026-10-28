const { test } = require('node:test')
const assert = require('node:assert/strict')
function pageAt(path) {
  let page
  global.Page = definition => { page = definition }
  delete require.cache[require.resolve(path)]
  require(path)
  page.data = JSON.parse(JSON.stringify(page.data))
  page.setData = patch => Object.assign(page.data, patch)
  return page
}
test('首页示例直接打开demo解析，不经过编辑页', () => {
  let url
  global.wx = { navigateTo: options => { url = options.url } }
  const page = pageAt('../pages/home/home')
  page.sample()
  assert.equal(url, '/pages/result/result?demo=oat-original')
})
test('demo离线直接显示完整配料和解析，可切换，不请求后端/AI且不保存记录', async () => {
  global.wx = { getStorageSync() { throw new Error('demo不应读存储') }, setStorageSync() { throw new Error('demo不应保存') }, request() { throw new Error('demo不应联网') } }
  const page = pageAt('../pages/result/result')
  page.onLoad({ demo: 'oat-original' }); await page.onShow()
  assert.equal(page.data.demoMode, true)
  assert.equal(page.data.count, 5)
  assert.equal(page.data.findings.length, 5)
  assert.equal(page.data.groups.length, 2)
  assert.equal(page.data.busy, false)
  assert.match(page.data.record.raw, /燕麦片/)
  page.switchDemo({ currentTarget: { dataset: { id: 'oat-nut' } } })
  assert.equal(page.data.count, 6)
  assert.match(page.data.allergens, /花生/)
  assert.equal(page.data.findings.length, 6)
  await page.query()
  await page.history()
  assert.deepEqual(page.data.history, [])
})
