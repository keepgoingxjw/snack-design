const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const project = path.resolve(__dirname, '..')
const config = JSON.parse(fs.readFileSync(path.join(project, 'project.config.json'), 'utf8'))
const root = path.resolve(project, config.miniprogramRoot || '.')
const rules = config.packOptions && config.packOptions.ignore || []
// 原始运行文件预警线，给微信编译后的代码及元数据留余量；实际包体以开发者工具为准。
const SOURCE_BUDGET = 1536 * 1024
const IMAGE_BUDGET = 400 * 1024
function ignored(relative) {
  return rules.some(rule => {
    const value = rule.value.replace(/\\/g, '/').replace(/^\.\//, '').replace(/\/$/, '')
    if (rule.type === 'folder') return relative === value || relative.startsWith(value + '/')
    if (rule.type === 'file') return relative === value
    if (rule.type === 'suffix') return relative.endsWith(value)
    if (rule.type === 'prefix') return relative.startsWith(value)
    return false
  })
}
function collect(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name)
    const relative = path.relative(root, file).replace(/\\/g, '/')
    if (ignored(relative)) return []
    if (entry.isDirectory()) return collect(file)
    assert.ok(entry.isFile(), '请勿在小程序打包目录放置符号链接：' + relative)
    return [{ file: relative, bytes: fs.statSync(file).size }]
  })
}
test('小程序运行文件不超过1.5MB预警线，避免微信主包超过2MB', () => {
  const files = collect(root).sort((a, b) => b.bytes - a.bytes)
  const total = files.reduce((sum, file) => sum + file.bytes, 0)
  const largest = files.slice(0, 8).map(file => `${(file.bytes / 1024).toFixed(1)}KB ${file.file}`).join('\n')
  assert.ok(total <= SOURCE_BUDGET, `运行文件共${(total / 1024).toFixed(1)}KB，超过1536KB预警线。请先优化图片/排除非运行文件，不要强行上传。\n${largest}`)
})
test('单张静态图片不超过400KB，避免新增原图挤占主包', () => {
  const large = collect(root).filter(file => /\.(png|jpe?g|gif|webp)$/i.test(file.file) && file.bytes > IMAGE_BUDGET)
  assert.deepEqual(large.map(file => `${(file.bytes / 1024).toFixed(1)}KB ${file.file}`), [], '图片过大，请按页面显示尺寸导出，不要删除被页面引用的资源')
})
