const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const store = require('../utils/storage')
const records = require('../utils/records')
const { analyze, allergenClues } = require('../utils/ingredients')
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
  records.analyze = async raw => ({ rows: analyze(raw), allergens: allergenClues(raw) })
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
test('解析页刷新更新后的数据并按实际分组显示筛选', async () => {
  const r = store.save('乳粉、小麦粉、未知配料', '手动输入')
  const page = loadPage('../pages/result/result')
  page.onLoad({ id: r.id }); await page.onShow()
  assert.equal(page.data.groups.length, 3)
  assert.equal(page.data.allergens, '小麦、乳类')
  store.save('乳粉', '手动输入', r.id)
  await page.onShow()
  assert.equal(page.data.count, 1)
  assert.equal(page.data.tabs.length, 2)
  store.remove(r.id); await page.onShow()
  assert.equal(page.data.record, null)
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
