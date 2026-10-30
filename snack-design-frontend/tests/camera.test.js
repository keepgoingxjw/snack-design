const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const { capturePhoto } = require('../utils/camera')
let app
beforeEach(() => {
  app = { globalData: { imagePath: '/old/photo.jpg' } }
  global.getApp = () => app
  global.wx = {}
})
test('先调起摄像头，只有确认拍摄后才带新照片打开核对页', async () => {
  let camera, navigation
  wx.chooseMedia = options => { camera = options }
  wx.navigateTo = options => {
    navigation = options.url
    assert.equal(app.globalData.imagePath, '/new/captured.jpg')
    options.success()
  }
  const pending = capturePhoto()
  assert.deepEqual(camera.sourceType, ['camera'])
  assert.deepEqual(camera.mediaType, ['image'])
  assert.equal(navigation, undefined)
  assert.equal(app.globalData.imagePath, '')
  camera.success({ tempFiles: [{ tempFilePath: '/new/captured.jpg' }] })
  assert.equal((await pending).status, 'captured')
  assert.equal(navigation, '/pages/edit/edit?photo=1')
})
test('首页拍照按钮直接打开摄像头，不先去介绍页', async () => {
  let page, camera
  global.Page = definition => { page = definition }
  delete require.cache[require.resolve('../pages/home/home')]
  require('../pages/home/home')
  wx.chooseMedia = options => { camera = options }
  wx.navigateTo = () => assert.fail('拍摄前不应跳介绍页或核对页')
  const pending = page.photo()
  assert.deepEqual(camera.sourceType, ['camera'])
  camera.fail({ errMsg: 'chooseMedia:fail cancel' })
  assert.equal((await pending).status, 'cancelled')
})
test('取消、未返回照片都不跳页，不上传，不提示识别失败，旧照片被清除', async () => {
  wx.showModal = () => assert.fail('取消拍摄不应弹识别错误')
  wx.navigateTo = () => assert.fail('没有照片不应跳页')
  wx.uploadFile = () => assert.fail('没有照片不应上传')
  wx.chooseMedia = options => options.fail({ errMsg: 'chooseMedia:fail cancel' })
  assert.equal((await capturePhoto()).status, 'cancelled')
  assert.equal(app.globalData.imagePath, '')
  app.globalData.imagePath = '/old/again.jpg'
  wx.chooseMedia = options => options.success({ tempFiles: [] })
  assert.equal((await capturePhoto()).status, 'cancelled')
  assert.equal(app.globalData.imagePath, '')
  wx.chooseMedia = options => options.success({ tempFiles: [{ tempFilePath: '/tmp/empty.jpg', size: 0 }] })
  assert.equal((await capturePhoto()).status, 'cancelled')
})
test('快速连续点击只打开一次摄像头，拒绝权限后不上传照片', async () => {
  let camera, calls = 0
  wx.chooseMedia = options => { calls++; camera = options }
  wx.showModal = options => { assert.match(options.title, /摄像头/); options.success() }
  const one = capturePhoto(), two = capturePhoto()
  assert.equal(one, two); assert.equal(calls, 1)
  camera.fail({ errMsg: 'chooseMedia:fail auth denied' })
  assert.equal((await one).status, 'failed')
  assert.equal(app.globalData.imagePath, '')
})
