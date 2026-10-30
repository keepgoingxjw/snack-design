const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '..')
const files = fs.readdirSync(path.join(root, 'pages')).map(name => path.join(root, 'pages', name, name + '.wxml'))
files.push(path.join(root, 'custom-tab-bar/index.wxml'))
for (const file of files) {
  test(path.relative(root, file) + ': 按钮及可点击view有统一按压反馈', () => {
    const tags = fs.readFileSync(file, 'utf8').match(/<(?:button|view)\b(?:"[^"]*"|'[^']*'|[^'">])*>/g) || []
    for (const tag of tags.filter(t => /\bbindtap=/.test(t))) {
      assert.match(tag, /\bhover-class=/, tag)
      assert.match(tag, /\bhover-start-time="0"/, tag)
      assert.match(tag, /\bhover-stay-time="120"/, tag)
      if (tag.startsWith('<button') && /\bdisabled=/.test(tag)) assert.match(tag, /hover-class="\{\{[^}]*'none'/, tag)
    }
  })
}
test('通用按压反馈不改变控件宽高与间距', () => {
  const css = fs.readFileSync(path.join(root, 'app.wxss'), 'utf8')
  for (const name of ['press-primary', 'press-secondary', 'tap-pressed', 'tap-danger-pressed']) {
    const block = css.match(new RegExp('\\.' + name + '\\{([^}]+)\\}'))
    assert.ok(block, name)
    assert.doesNotMatch(block[1], /(?:width|height|margin|padding)\s*:/, name)
    assert.match(block[1], /transform:translateY/)
  }
})
