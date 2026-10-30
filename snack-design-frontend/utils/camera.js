// 只负责“打开摄像头→取得本次确认的照片→进入原文页”，不在拍摄前调用OCR。
let active = null
function capturePhoto() {
  if (active) return active
  const app = getApp()
  const data = app.globalData || (app.globalData = {})
  data.imagePath = ''
  const task = new Promise(resolve => {
    function failed(error) {
      data.imagePath = ''
      if (/cancel/i.test(error && error.errMsg || '')) { resolve({ status: 'cancelled' }); return }
      wx.showModal({
        title: '无法打开摄像头', content: '请在手机微信中重试，并检查微信及系统的相机权限。尚未拍到照片，不会进行文字识别。', showCancel: false,
        success: () => resolve({ status: 'failed' }), fail: () => resolve({ status: 'failed' })
      })
    }
    try {
      wx.chooseMedia({
        count: 1, mediaType: ['image'], sourceType: ['camera'], sizeType: ['compressed'],
        success: result => {
          const file = result.tempFiles && result.tempFiles[0]
          const path = file && file.tempFilePath
          if (typeof path !== 'string' || !path.trim() || (typeof file.size === 'number' && file.size <= 0)) { data.imagePath = ''; resolve({ status: 'cancelled' }); return }
          data.imagePath = path
          wx.navigateTo({
            url: '/pages/edit/edit?photo=1',
            success: () => resolve({ status: 'captured' }),
            fail: () => {
              if (data.imagePath === path) data.imagePath = ''
              wx.showToast({ title: '无法打开原文核对页，请重试', icon: 'none' })
              resolve({ status: 'failed' })
            }
          })
        },
        fail: failed
      })
    } catch (error) { failed(error) }
  })
  active = task.finally(() => { active = null })
  return active
}
module.exports = { capturePhoto }
