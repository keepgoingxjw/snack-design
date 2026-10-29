const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

test('首页两篇课堂分别打开对应文章，切换后重置阅读位置和来源展开状态', () => {
  let page, destination, scrollTop
  global.Page = definition => { page = definition }
  global.wx = {
    navigateTo: options => { destination = options.url },
    pageScrollTo: options => { scrollTop = options.scrollTop }
  }
  require('../pages/home/home')
  const home = page
  require('../pages/lesson/lesson')
  page.setData = patch => Object.assign(page.data, patch)
  for (const topic of ['role', 'name']) {
    home.lesson({ currentTarget: { dataset: { type: topic } } })
    assert.equal(destination, '/pages/lesson/lesson?topic=' + topic)
    page.onLoad({ topic })
    assert.equal(page.data.topic, topic === 'name' ? 1 : 0)
    assert.equal(page.data.article.sections.length, 3)
    assert.ok(fs.existsSync(path.join(__dirname, '../assets/lessons/' + topic + '.png')))
  }
  page.toggleReferences()
  assert.equal(page.data.referencesOpen, true)
  page.next()
  assert.equal(page.data.topic, 0)
  assert.equal(page.data.referencesOpen, false)
  assert.equal(scrollTop, 0)
  assert.ok(require('../app.json').pages.includes('pages/lesson/lesson'))
})
