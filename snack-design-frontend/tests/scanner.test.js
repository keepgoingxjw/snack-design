const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const { openScanner } = require('../utils/scanner')
beforeEach(() => { global.wx = {} })
test('快速重复点击只启动一个扫码器，取消不会提示错误或跳页', async () => {
  let calls = 0, callback
  wx.scanCode = options => { calls++; callback = options }
  wx.showModal = () => assert.fail('取消不应弹错误')
  wx.navigateTo = () => assert.fail('取消不应跳页')
  const first = openScanner(), second = openScanner()
  assert.equal(first, second)
  assert.equal(calls, 1)
  callback.fail({ errMsg: 'scanCode:fail cancel' })
  assert.equal((await first).status, 'cancelled')
})
test('扫码内容不被当作商品配料，不自动打开二维码链接', async () => {
  wx.scanCode = options => options.success({ result: 'https://untrusted.example/test' })
  wx.showModal = options => {
    assert.match(options.content, /未接入商品查询/)
    options.success({ confirm: false })
  }
  wx.navigateTo = () => assert.fail('未确认不应导航')
  assert.equal((await openScanner()).status, 'scanned')
})
test('用户确认录入后才进入原文编辑页', async () => {
  let url
  wx.scanCode = options => options.success({ result: '123456789' })
  wx.showModal = options => options.success({ confirm: true })
  wx.navigateTo = options => { url = options.url }
  await openScanner()
  assert.equal(url, '/pages/edit/edit')
})
test('权限错误提供说明，不改变当前页面', async () => {
  wx.scanCode = options => options.fail({ errMsg: 'scanCode:fail auth denied' })
  wx.showModal = options => { assert.equal(options.showCancel, false); options.success({}) }
  assert.equal((await openScanner()).status, 'failed')
})
