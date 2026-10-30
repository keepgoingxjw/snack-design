const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const ocr = require('../utils/ocr')
function editor() {
  let page
  global.Page = definition => { page = definition }
  delete require.cache[require.resolve('../pages/edit/edit')]
  require('../pages/edit/edit')
  page.data = JSON.parse(JSON.stringify(page.data))
  page.setData = patch => Object.assign(page.data, patch)
  return page
}
beforeEach(() => {
  const values = new Map()
  global.wx = { getStorageSync: key => values.get(key), setStorageSync: (key, value) => values.set(key, value), removeStorageSync: key => values.delete(key) }
  const app = { globalData: { imagePath: '/tmp/captured.jpg' } }
  global.getApp = () => app
})
test('拍摄图片自动识别并回填原文，不自动提交配料分析', async () => {
  let calls = 0
  ocr.recognize = async path => { assert.equal(path, '/tmp/captured.jpg'); calls++; return { text: '配料表：燕麦片、小麦粉、乳粉' } }
  const page = editor()
  await page.onLoad({ photo: '1' })
  assert.equal(calls, 1)
  assert.equal(page.data.raw, '配料表：燕麦片、小麦粉、乳粉')
  assert.equal(page.data.recognizing, false)
  assert.equal(page.data.saving, false)
  assert.equal(getApp().globalData.imagePath, '')
  const reopened = editor(); await reopened.onLoad({ photo: '1' })
  assert.equal(calls, 1)
  assert.equal(reopened.data.raw, '')
})
test('没有本次拍摄照片时不执行OCR，也不显示识别失败', async () => {
  getApp().globalData.imagePath = ''
  ocr.recognize = async () => assert.fail('没拍照片不能识别')
  const page = editor(); await page.onLoad({ photo: '1' }); await page.recognize()
  assert.equal(page.data.raw, '')
  assert.equal(page.data.error, '')
  assert.equal(page.data.recognizing, false)
})
test('识别失败可继续手动输入，不填充虚构文字', async () => {
  ocr.recognize = async () => { throw new Error('OCR服务暂不可用') }
  const page = editor()
  await page.onLoad({ photo: '1' })
  assert.equal(page.data.raw, '')
  assert.match(page.data.error, /手动/)
  page.input({ detail: { value: '燕麦片' } })
  assert.equal(page.data.raw, '燕麦片')
})
test('识别到生产信息但没有配料标题时提示核对拍摄区域，保留原文', async () => {
  ocr.recognize = async () => ({ text: '生产日期：见包装\n保质期：15个月' })
  const page = editor(); await page.onLoad({ photo: '1' })
  assert.equal(page.data.raw, '生产日期：见包装\n保质期：15个月')
  assert.match(page.data.error, /完整配料表/)
  assert.equal(page.data.saving, false)
})
test('OCR遇到网络错误只显示指定文案，不追加技术或手动输入描述', async () => {
  ocr.recognize = async () => { const error = new Error('网络错误，请稍后重试'); error.code = 'NETWORK_ERROR'; throw error }
  const page = editor(); await page.onLoad({ photo: '1' })
  assert.equal(page.data.error, '网络错误，请稍后重试')
  assert.equal(page.data.recognizing, false)
  assert.equal(page.data.raw, '')
})
test('识别失败后直接退出不会清空之前的文字草稿', async () => {
  const store = require('../utils/storage')
  store.saveDraft({ raw: '原先未完成的配料', kind: '手动输入', recordId: '' })
  ocr.recognize = async () => { throw new Error('网络不可用') }
  const page = editor(); await page.onLoad({ photo: '1' }); page.onUnload()
  assert.equal(store.draft().raw, '原先未完成的配料')
})
test('普通手动录入不调用OCR，离开页面后不回写识别结果', async () => {
  ocr.recognize = async () => assert.fail('手动输入不应上传图片')
  const manual = editor(); await manual.onLoad({})
  assert.equal(manual.data.raw, '')
  let finish
  ocr.recognize = () => new Promise(resolve => { finish = resolve })
  const photo = editor(); const pending = photo.onLoad({ photo: '1' })
  await new Promise(resolve => setImmediate(resolve))
  photo.onUnload(); finish({ text: '迟到的识别结果' }); await pending
  assert.equal(photo.data.raw, '')
})
