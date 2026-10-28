const { test } = require('node:test')
const assert = require('node:assert/strict')
const { seed, splitIngredients, analyze, allergenClues } = require('../utils/ingredients')
test('解析示例与配料表前缀', () => {
  assert.equal(splitIngredients('配料表：' + seed).length, 5)
  assert.equal(analyze(seed).filter(r => r.group === 'structure').length, 3)
})
test('保留复合配料及嵌套括号，不拆散内部逗号', () => {
  assert.deepEqual(splitIngredients('巧克力（糖、可可(可可粉,可可脂)）、小麦粉\n乳粉'), ['巧克力（糖、可可(可可粉,可可脂)）', '小麦粉', '乳粉'])
})
test('拒绝空值、非文本、不配对括号和数量超限', () => {
  for (const value of ['', '，、\n', null, '乳粉）', '乳粉（', '乳粉（糖)', '乳粉(糖）', '糖'.repeat(5001), Array(31).fill('糖').join('、')]) assert.throws(() => splitIngredients(value))
  assert.equal(splitIngredients(Array(30).fill('糖').join('、')).length, 30)
})
test('未知及复合配料不能误匹配或获得虚构来源', () => {
  const rows = analyze('乳粉、未知名称、巧克力（乳粉、糖）、constructor、__proto__')
  assert.equal(rows[0].group, 'flavour')
  for (const row of rows.slice(1)) { assert.equal(row.group, 'pending'); assert.equal(row.url, '') }
})
test('过敏原线索包含复合配料，但不是安全判断', () => {
  assert.deepEqual(allergenClues('小麦粉、巧克力（乳粉、花生）'), ['小麦', '乳类', '花生'])
  assert.deepEqual(allergenClues('白砂糖'), [])
})
