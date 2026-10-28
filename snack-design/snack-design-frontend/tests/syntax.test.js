const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(dir, entry.name)
    return entry.isDirectory() ? walk(file) : file.endsWith('.js') ? [file] : []
  })
}
for (const file of [path.join(root, 'app.js'), ...walk(path.join(root, 'pages')), ...walk(path.join(root, 'utils')), ...walk(path.join(root, 'custom-tab-bar'))]) {
  test('JS 语法: ' + path.relative(root, file), () => { new vm.Script(fs.readFileSync(file, 'utf8'), { filename: file }) })
}
