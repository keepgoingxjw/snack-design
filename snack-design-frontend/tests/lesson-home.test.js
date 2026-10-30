const { test } = require('node:test')
const assert = require('node:assert/strict')
function lesson() {
  let page
  global.Page = definition => { page = definition }
  delete require.cache[require.resolve('../pages/lesson/lesson')]
  require('../pages/lesson/lesson')
  page.data = JSON.parse(JSON.stringify(page.data))
  page.setData = patch => Object.assign(page.data, patch)
  return page
}
test('两个科普主题的返回按钮切换首页并回到顶部', () => {
  const routes = [], scrolls = []
  global.wx = {
    switchTab: options => { routes.push(options.url); assert.equal(typeof options.success, 'function'); options.success() },
    pageScrollTo: options => scrolls.push(options.scrollTop)
  }
  const page = lesson()
  for (const topic of ['role', 'name']) { page.onLoad({ topic }); page.home() }
  assert.deepEqual(routes, ['/pages/home/home', '/pages/home/home'])
  assert.deepEqual(scrolls, [0, 0])
})
test('Tab导航失败时兜底打开首页，不停留在科普页', () => {
  let route
  global.wx = { switchTab: options => options.fail({ errMsg: 'switchTab:fail' }), reLaunch: options => { route = options.url } }
  lesson().home()
  assert.equal(route, '/pages/home/home')
})
