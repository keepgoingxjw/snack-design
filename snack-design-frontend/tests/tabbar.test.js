const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
let component, scans, switches
function loadTabBar() {
  global.Component = definition => { component = definition }
  delete require.cache[require.resolve('../custom-tab-bar/index')]
  require('../custom-tab-bar/index')
  component.data = JSON.parse(JSON.stringify(component.data))
  Object.assign(component, component.methods)
  component.setData = patch => Object.assign(component.data, patch)
  return component
}
beforeEach(() => {
  scans = 0; switches = []
  global.getCurrentPages = () => [{ route: 'pages/history/history' }]
  global.wx = {
    scanCode: options => { scans++; options.fail({ errMsg: 'scanCode:fail cancel' }) },
    switchTab: options => { switches.push(options.url); if (options.success) options.success() }
  }
})
test('点中间扫描直接调用原生扫码器，不切换页面，取消后保持原Tab', async () => {
  const tab = loadTabBar()
  tab.syncSelection()
  await tab.tap({ currentTarget: { dataset: { id: 'scan' } } })
  assert.equal(scans, 1)
  assert.deepEqual(switches, [])
  assert.equal(tab.data.selected, 'pages/history/history')
  assert.equal(tab.data.scanning, false)
})
test('首页和记录仍正常切换页面', async () => {
  const tab = loadTabBar()
  tab.syncSelection()
  await tab.tap({ currentTarget: { dataset: { id: 'home' } } })
  assert.deepEqual(switches, ['/pages/home/home'])
  assert.equal(scans, 0)
})
