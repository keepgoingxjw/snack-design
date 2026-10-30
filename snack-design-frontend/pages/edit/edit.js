const { seed, splitIngredients } = require('../../utils/ingredients')
const store = require('../../utils/storage')
const records = require('../../utils/records')
const ocr = require('../../utils/ocr')
Page({
  data: { raw: '', imagePath: '', error: '', kind: '手动输入', recognizing: false, ocrDone: false, loading: true, saving: false, recordId: '' },
  async onLoad(q) {
    try {
      const photoData = q.photo ? getApp().globalData : null
      const imagePath = photoData && photoData.imagePath || ''
      if (photoData) photoData.imagePath = '' // 本次照片只交接一次，不能在下次进入时重复识别。
      const record = q.id && await records.find(q.id)
      if (q.id && !record) { this.setData({ error: '记录不存在，请返回重新录入' }); this.invalid = true; return }
      const draft = q.draft && store.draft()
      this.setData({
        raw: record ? record.raw : draft ? draft.raw : q.sample ? seed : '',
        recordId: record ? record.id : draft ? draft.recordId || '' : '',
        kind: record ? record.kind : draft ? draft.kind || '手动输入' : q.sample ? '虚构示例' : q.photo ? '照片手动录入' : '手动输入',
        imagePath
      })
    } catch (e) { this.setData({ error: e.message || '无法读取数据，请返回重试' }); this.invalid = true }
    finally { this.setData({ loading: false }) }
    if (q.photo && this.data.imagePath && !this.invalid) await this.recognize()
  },
  async recognize() {
    if (!this.data.imagePath || this.data.recognizing || this.data.ocrDone || this.unloaded) return
    this.setData({ recognizing: true, error: '' })
    let success = false
    try {
      const result = await ocr.recognize(this.data.imagePath)
      if (this.unloaded) return
      const hasHeading = /配\s*料|成\s*分|ingredients/i.test(result.text)
      this.setData({ raw: result.text, ocrDone: true, error: hasHeading ? '' : '未识别到配料表标题。请确认照片包含完整配料表；生产地址、电话和条码不能用于配料解析。' })
      success = true
    } catch (e) {
      if (!this.unloaded) this.setData({ error: e.code === 'NETWORK_ERROR' ? '网络错误，请稍后重试' : (e.message || '图片文字识别失败') + '。你仍可对照照片手动输入。' })
    } finally {
      if (!this.unloaded) {
        this.setData({ recognizing: false })
        if (success) this.persistDraft()
      }
    }
  },
  input(e) { this.manuallyEdited = true; this.submitted = false; this.setData({ raw: e.detail.value, error: '' }); this.persistDraft() },
  persistDraft() {
    if (this.submitted || this.invalid || this.data.loading || this.data.recognizing) return
    if (this.data.imagePath && !this.data.ocrDone && !this.manuallyEdited) return
    try {
      if (this.data.raw.trim()) store.saveDraft({ raw: this.data.raw, kind: this.data.kind, recordId: this.data.recordId })
      else store.clearDraft()
    } catch (e) { this.setData({ error: '草稿保存失败，请检查微信存储空间，暂勿离开此页' }) }
  },
  onHide() { this.persistDraft() },
  onUnload() { this.unloaded = true; this.persistDraft() },
  preview() { if (this.data.imagePath) wx.previewImage({ urls: [this.data.imagePath] }) },
  async submit() {
    if (this.data.saving || this.data.loading || this.data.recognizing || this.invalid) return
    this.setData({ saving: true, error: '' })
    try {
      splitIngredients(this.data.raw)
      const r = await records.save(this.data.raw.trim(), this.data.kind, this.data.recordId)
      this.submitted = true
      try { store.clearDraft() } catch (e) { /* 已保存的记录不受草稿清理失败影响 */ }
      wx.redirectTo({ url: '/pages/result/result?id=' + r.id + '&auto=1', fail: () => {
        this.setData({ saving: false, recordId: r.id, error: '记录已保存，可重试或从记录页查看' })
      } })
    } catch (e) { this.setData({ saving: false, error: e.message || '保存失败，请稍后重试' }) }
  }
})
