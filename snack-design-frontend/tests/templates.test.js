const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
// 静态回归检查，不替代微信开发者工具的完整 WXML 编译。
const files = fs.readdirSync(path.join(__dirname, '../pages')).map(dir => path.join(__dirname, '../pages', dir, dir + '.wxml'))
files.push(path.join(__dirname, '../custom-tab-bar/index.wxml'))
for (const file of files) {
  test(`${path.relative(__dirname, file)}: 模板表达式不得使用 HTML 转义的运算符`, () => {
    const source = fs.readFileSync(file, 'utf8')
    for (const match of source.matchAll(/\{\{([\s\S]*?)\}\}/g)) {
      assert.doesNotMatch(match[1], /&(?:amp|lt|gt|quot);/, `${file}: ${match[0]}`)
      assert.doesNotThrow(() => new Function('return (' + match[1] + ')'), `${file}: ${match[0]}`)
    }
  })
}
