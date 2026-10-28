const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const store = require('../utils/storage')
let values
beforeEach(() => {
  values = new Map()
  global.wx = { getStorageSync: k => values.get(k), setStorageSync: (k, v) => values.set(k, v), removeStorageSync: k => values.delete(k) }
})
test('保存、更新同一记录不重复，保留来源与创建时间', () => {
  const r = store.save('乳粉', '虚构示例')
  const updated = store.save('乳粉、白砂糖', r.kind, r.id)
  assert.equal(store.all().length, 1)
  assert.equal(updated.id, r.id)
  assert.equal(updated.createdAt, r.createdAt)
  assert.equal(store.find(r.id).raw, '乳粉、白砂糖')
})
test('最多保留50条，删除和清空', () => {
  for (let i = 0; i < 55; i++) store.save(String(i), '手动输入')
  assert.equal(store.all().length, 50)
  const id = store.all()[0].id
  store.remove(id)
  assert.equal(store.find(id), undefined)
  assert.throws(() => store.save('糖', '手动输入', id))
  store.clear()
  assert.deepEqual(store.all(), [])
})
test('草稿独立于记录清理，支持恢复和删除', () => {
  const draft = { raw: '乳粉', kind: '虚构示例', recordId: '' }
  store.saveDraft(draft)
  store.clear()
  assert.deepEqual(store.draft(), draft)
  store.clearDraft()
  assert.equal(store.draft(), null)
})
test('过滤格式损坏的数据，存储异常不静默吞掉', () => {
  values.set('shiyoushu.records.v1', [null, {}, { id: '1', raw: '糖', createdAt: 123 }])
  assert.equal(store.all().length, 1)
  wx.setStorageSync = () => { throw new Error('quota') }
  assert.throws(() => store.save('糖', '手动输入'), /quota/)
})
