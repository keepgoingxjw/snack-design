const { seed, splitIngredients } = require('../../utils/ingredients')
const store = require('../../utils/storage')
const records = require('../../utils/records')
Page({
  data: { raw: '', imagePath: '', error: '', kind: '手动输入', loading: true, saving: false, recordId: '' },
  async onLoad(q) {
    try {
      const record = q.id && await records.find(q.id)
      if (q.id && !record) { this.setData({ error: '记录不存在，请返回重新录入' }); this.invalid = true; return }
      const draft = q.draft && store.draft()
      this.setData({
        raw: record ? record.raw : draft ? draft.raw : q.sample ? seed : '',
        recordId: record ? record.id : draft ? draft.recordId || '' : '',
        kind: record ? record.kind : draft ? draft.kind || '手动输入' : q.sample ? '虚构示例' : q.photo ? '照片手动录入' : '手动输入',
        imagePath: q.photo ? getApp().globalData.imagePath || '' : ''
      })
    } catch (e) { this.setData({ error: e.message || '无法读取数据，请返回重试' }); this.invalid = true }
    finally { this.setData({ loading: false }) }
  },
  input(e) { this.submitted = false; this.setData({ raw: e.detail.value, error: '' }); this.persistDraft() },
  persistDraft() {
    if (this.submitted || this.invalid || this.data.loading) return
    try {
      if (this.data.raw.trim()) store.saveDraft({ raw: this.data.raw, kind: this.data.kind, recordId: this.data.recordId })
      else store.clearDraft()
    } catch (e) { this.setData({ error: '草稿保存失败，请检查微信存储空间，暂勿离开此页' }) }
  },
  onHide() { this.persistDraft() },
  onUnload() { this.persistDraft() },
  preview() { if (this.data.imagePath) wx.previewImage({ urls: [this.data.imagePath] }) },
  async submit() {
    if (this.data.saving || this.data.loading || this.invalid) return
    this.setData({ saving: true, error: '' })
    try {
      splitIngredients(this.data.raw)
      const r = await records.save(this.data.raw.trim(), this.data.kind, this.data.recordId)
      this.submitted = true
      try { store.clearDraft() } catch (e) { /* 已保存的记录不受草稿清理失败影响 */ }
      wx.redirectTo({ url: '/pages/result/result?id=' + r.id, fail: () => {
        this.setData({ saving: false, recordId: r.id, error: '记录已保存，可重试或从记录页查看' })
      } })
    } catch (e) { this.setData({ saving: false, error: e.message || '保存失败，请稍后重试' }) }
  }
})
