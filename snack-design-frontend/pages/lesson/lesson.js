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
  toggleReferences() { this.setData({ referencesOpen: !this.data.referencesOpen }) },
  source(e) {
    wx.setClipboardData({ data: e.currentTarget.dataset.url, success: () => wx.showToast({ title: '来源链接已复制', icon: 'none' }) })
  }
})
