const { openScanner } = require('../../utils/scanner')
const store = require('../../utils/storage')
Page({
  data: { hasDraft: false },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 'pages/home/home' })
    try { this.setData({ hasDraft: !!store.draft() }) } catch (e) { this.setData({ hasDraft: false }) }
  },
  edit() { wx.navigateTo({ url: '/pages/edit/edit' }) },
  resume() { wx.navigateTo({ url: '/pages/edit/edit?draft=1' }) },
  sample() { wx.navigateTo({ url: '/pages/result/result?demo=oat-original' }) },
  photo() { wx.navigateTo({ url: '/pages/scan/scan' }) },
  scan() { return openScanner() },
  lesson(e) { wx.showModal({ title: e.currentTarget.dataset.type === 'role' ? '配料为什么加进食品里？' : '陌生名字，不等于有害', content: e.currentTarget.dataset.type === 'role' ? '配料有不同的常见用途，例如形成结构、提供甜味、调整口感。加工用途与营养价值需要分开理解。' : '判断风险需要结合用量、食用频率和个人情况。先核对包装原文，再看具体注意事项。', showCancel: false }) }
})
