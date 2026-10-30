const topics = require('./topics')
Page({
  data: { topic: 0, article: topics[0], referencesOpen: false },
  onLoad(options) { this.showTopic(options.topic === 'name' ? 1 : 0) },
  showTopic(topic) {
    this.setData({ topic, article: topics[topic], referencesOpen: false })
  },
  next() {
    this.showTopic(1 - this.data.topic)
    wx.pageScrollTo({ scrollTop: 0, duration: 0 })
  },
  home() {
    const url = '/pages/home/home'
    wx.switchTab({
      url,
      success: () => wx.pageScrollTo({ scrollTop: 0, duration: 0 }),
      fail: () => wx.reLaunch({
        url,
        fail: () => wx.showToast({ title: '返回首页失败，请稍后重试', icon: 'none' })
      })
    })
  },
  toggleReferences() { this.setData({ referencesOpen: !this.data.referencesOpen }) },
  source(e) {
    wx.setClipboardData({ data: e.currentTarget.dataset.url, success: () => wx.showToast({ title: '来源链接已复制', icon: 'none' }) })
  }
})
