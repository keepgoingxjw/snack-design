const KEY = 'shiyoushu.records.v1'
const DRAFT_KEY = 'shiyoushu.draft.v1'
function all() {
  const data = wx.getStorageSync(KEY)
  return Array.isArray(data) ? data.filter(r => r && typeof r.id === 'string' && typeof r.raw === 'string' && Number.isFinite(r.createdAt)) : []
}
function save(raw, kind, id) {
  const records = all()
  const existing = id && records.find(r => r.id === id)
  if (id && !existing) throw new Error('原记录已删除，请返回重新录入')
  const now = Date.now()
  const record = { id: existing ? existing.id : now.toString(36) + Math.random().toString(36).slice(2, 7), raw, kind, createdAt: existing ? existing.createdAt : now, updatedAt: now }
  wx.setStorageSync(KEY, [record, ...records.filter(r => r.id !== record.id)].slice(0, 50))
  return record
}
function draft() {
  const value = wx.getStorageSync(DRAFT_KEY)
  return value && typeof value.raw === 'string' ? value : null
}
module.exports = {
  all, save, find: id => all().find(r => r.id === id),
  remove: id => wx.setStorageSync(KEY, all().filter(r => r.id !== id)),
  clear: () => wx.removeStorageSync(KEY),
  draft, saveDraft: value => wx.setStorageSync(DRAFT_KEY, value),
  clearDraft: () => wx.removeStorageSync(DRAFT_KEY)
}
