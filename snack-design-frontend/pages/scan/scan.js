Page({
  data: { busy: false },
  edit() { wx.navigateTo({ url: '/pages/edit/edit' }) },
  sample() { wx.navigateTo({ url: '/pages/result/result?demo=oat-original' }) },
  photo(e) {
    if (this.data.busy) return
    const source = e.currentTarget.dataset.source === 'album' ? 'album' : 'camera'
    wx.showModal({
      title: source === 'album' ? '选择配料表照片' : '拍摄配料表',
      content: '照片仅上传到应用后端，用OCR提取图片文字，不发送给DeepSeek或其他AI服务，不保存到配料记录。识别结果会回填原文，请你核对后再确认。',
      confirmText: '继续',
      success: r => {
        if (!r.confirm) return
        this.setData({ busy: true })
        wx.chooseMedia({
          count: 1, mediaType: ['image'], sourceType: [source], sizeType: ['compressed'],
          success: res => {
            getApp().globalData.imagePath = res.tempFiles[0].tempFilePath
            wx.navigateTo({ url: '/pages/edit/edit?photo=1' })
          },
          fail: err => { if (!/cancel/.test(err.errMsg || '')) wx.showModal({ title: '无法获取照片', content: '请检查微信及系统相机、相册权限，或使用手动输入。', showCancel: false }) },
          complete: () => this.setData({ busy: false })
        })
      }
    })
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
