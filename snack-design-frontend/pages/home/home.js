const { capturePhoto } = require('../../utils/camera')
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
  photo() { return capturePhoto() },
  scan() { return openScanner() },
  lesson(e) { wx.navigateTo({ url: '/pages/lesson/lesson?topic=' + (e.currentTarget.dataset.type === 'name' ? 'name' : 'role') }) }
})
