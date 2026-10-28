const { openScanner } = require('../utils/scanner')
Component({
  data: {
    selected: 'pages/home/home', scanning: false,
    items: [
      { id: 'home', label: '首页', path: 'pages/home/home', icon: '/assets/tabbar/home.png', activeIcon: '/assets/tabbar/home-active.png' },
      { id: 'scan', label: '扫描', path: '', icon: '/assets/tabbar/scan.png', activeIcon: '/assets/tabbar/scan-active.png' },
      { id: 'history', label: '记录', path: 'pages/history/history', icon: '/assets/tabbar/history.png', activeIcon: '/assets/tabbar/history-active.png' }
    ]
  },
  lifetimes: { attached() { this.syncSelection() } },
  pageLifetimes: { show() { this.syncSelection() } },
  methods: {
    syncSelection() {
      const pages = getCurrentPages()
      const route = pages.length ? pages[pages.length - 1].route : ''
      if (this.data.items.some(item => item.path === route)) this.setData({ selected: route })
    },
    async tap(e) {
      const item = this.data.items.find(entry => entry.id === e.currentTarget.dataset.id)
      if (!item) return
      if (item.id === 'scan') {
        if (this.data.scanning) return
        this.setData({ scanning: true })
        try { await openScanner() } finally { this.setData({ scanning: false }) }
        return
      }
      if (item.path === this.data.selected) return
      wx.switchTab({ url: '/' + item.path, success: () => this.setData({ selected: item.path }) })
    }
  }
})
