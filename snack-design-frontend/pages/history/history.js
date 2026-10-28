const store = require('../../utils/records')
function dateLabel(timestamp) {
  const d = new Date(timestamp)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
Page({
  data: { loading: false, records: [], error: '' },
  async onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 'pages/history/history' })
    this.setData({ loading: true, error: '' })
    try { this.setData({ records: (await store.all()).map(r => ({ ...r, date: dateLabel(r.updatedAt || r.createdAt) })) }) }
    catch (e) { this.setData({ error: e.message || '服务端记录读取失败，请稍后重试' }) }
    finally { this.setData({ loading: false }) }
  },
  open(e) { wx.navigateTo({ url: '/pages/result/result?id=' + e.currentTarget.dataset.id }) },
  remove(e) { const id = e.currentTarget.dataset.id; wx.showModal({ title: '删除这条记录？', content: '删除后无法恢复。', success: r => { if (r.confirm) this.mutate(() => store.remove(id)) } }) },
  clear() { wx.showModal({ title: '清空当前用户记录？', content: '全部记录删除后无法恢复；未完成的编辑草稿不受影响。', success: r => { if (r.confirm) this.mutate(() => store.clear()) } }) },
  async mutate(action) { try { await action(); await this.onShow() } catch (e) { wx.showToast({ title: e.message || '操作失败，请重试', icon: 'none' }) } },
  home() { wx.switchTab({ url: '/pages/home/home' }) }
})
