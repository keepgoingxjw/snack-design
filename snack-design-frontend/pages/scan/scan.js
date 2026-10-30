const { capturePhoto } = require('../../utils/camera')
Page({
  data: { busy: false },
  edit() { wx.navigateTo({ url: '/pages/edit/edit' }) },
  sample() { wx.navigateTo({ url: '/pages/result/result?demo=oat-original' }) },
  async photo() {
    if (this.data.busy) return
    this.setData({ busy: true })
    try { await capturePhoto() } finally { this.setData({ busy: false }) }
  },
  barcode() {
    if (this.data.busy) return
    this.setData({ busy: true })
    wx.scanCode({
      scanType: ['barCode'],
      success: res => wx.showModal({ title: '已读取条码', content: res.result + '\n尚未接入商品数据库，不能通过条码推断配料。请对照包装手动输入。', confirmText: '输入配料', success: r => { if (r.confirm) this.edit() } }),
      fail: err => { if (!/cancel/.test(err.errMsg || '')) wx.showToast({ title: '未读取到条码，请重试', icon: 'none' }) },
      complete: () => this.setData({ busy: false })
    })
  }
})
