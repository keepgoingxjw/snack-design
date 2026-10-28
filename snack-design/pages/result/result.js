const store = require('../../utils/records')
const groupTypes = [{ id: 'structure', title: '构成与口感' }, { id: 'flavour', title: '甜味与香气' }, { id: 'pending', title: '待解析配料' }]
Page({
  data: { groups: [], filter: 'all', record: null, original: false, loading: true, error: '', allergens: '', visibleCount: 0, tabs: [] },
  onLoad(q) { this.recordId = q.id },
  async onShow() {
    this.setData({ loading: true, error: '' })
    try {
      const r = await store.find(this.recordId)
      if (!r) { this.setData({ record: null }); return }
      const analysis = await store.analyze(r.raw)
      const rows = analysis.rows
      const groups = groupTypes.map(g => ({ ...g, rows: rows.filter(x => x.group === g.id) })).filter(g => g.rows.length)
      this.setData({ record: r, count: rows.length, groups, filter: 'all', visibleCount: rows.length,
        allergens: analysis.allergens.join('、'),
        tabs: [{ id: 'all', name: '全部' }, ...groups.map(g => ({ id: g.id, name: g.title }))] })
    } catch (e) { this.setData({ record: null, error: e.message || '记录读取失败，请返回重试' }) }
    finally { this.setData({ loading: false }) }
  },
  filter(e) { const id = e.currentTarget.dataset.id; this.setData({ filter: id, visibleCount: this.data.groups.filter(g => id === 'all' || g.id === id).reduce((n, g) => n + g.rows.length, 0) }) },
  toggle() { this.setData({ original: !this.data.original }) },
  edit() { if (this.data.record) wx.navigateTo({ url: '/pages/edit/edit?id=' + this.data.record.id }) },
  home() { wx.switchTab({ url: '/pages/home/home' }) },
  source(e) { wx.setClipboardData({ data: e.currentTarget.dataset.url, success: () => wx.showModal({ title: '资料链接已复制', content: '可在浏览器中打开原始资料。来源用于说明一般知识，不代表对该商品的评审或认可。', showCancel: false }) }) }
})
