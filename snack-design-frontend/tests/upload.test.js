const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
let api, logins
beforeEach(() => {
  const storage = new Map(); logins = 0
  global.wx = {
    getStorageSync: key => storage.get(key), setStorageSync: (key,value) => storage.set(key,value), removeStorageSync: key => storage.delete(key),
    request: options => { logins++; options.success({ statusCode: 200, data: { token: 'session-token', expiresAt: Date.now()+600000 } }) }
  }
  delete require.cache[require.resolve('../utils/api')]
  api = require('../utils/api')
})
test('OCR上传复用会话并使用图片字段与独立60秒超时', async () => {
  wx.uploadFile = options => {
    assert.equal(options.name, 'image'); assert.equal(options.filePath, '/tmp/photo.jpg')
    assert.equal(options.timeout, 60000); assert.equal(options.header.Authorization, 'Bearer session-token')
    assert.equal(options.header['content-type'], undefined)
    options.success({ statusCode: 200, data: JSON.stringify({ text: '燕麦片、小麦粉' }) })
  }
  assert.equal((await api.upload('/ocr/recognize', '/tmp/photo.jpg')).text, '燕麦片、小麦粉')
  assert.equal(logins, 1)
})
test('拍照入口只调用传统OCR专用接口，不误调用旧的云端识字路由', async () => {
  let route
  api.upload = async (path, filePath) => { route = path; return { text: '燕麦片' } }
  delete require.cache[require.resolve('../utils/ocr')]
  const ocr = require('../utils/ocr')
  assert.equal((await ocr.recognize('/tmp/photo.jpg')).text, '燕麦片')
  assert.equal(route, '/ocr/tesseract')
})
test('上游OCR错误不重新登录或重复上传，只有用户会话401会重登', async () => {
  let uploads = 0
  wx.uploadFile = options => { uploads++; options.success({ statusCode: 502, data: JSON.stringify({ message: '识别服务暂不可用' }) }) }
  await assert.rejects(api.upload('/ocr/recognize', '/tmp/photo.jpg'), /识别服务暂不可用/)
  assert.equal(uploads, 1); assert.equal(logins, 1)
  uploads = 0
  wx.uploadFile = options => { uploads++; options.success({ statusCode: uploads === 1 ? 401 : 200, data: JSON.stringify(uploads === 1 ? { message: 'session expired' } : { text: '原文' }) }) }
  assert.equal((await api.upload('/ocr/recognize', '/tmp/photo.jpg')).text, '原文')
  assert.equal(uploads, 2); assert.equal(logins, 2)
})
