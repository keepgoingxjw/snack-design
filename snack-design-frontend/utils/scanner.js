// 仅用于明确的商品条码入口。拍照OCR走camera.js，不用条码/二维码接口识别图片文字。
let active = null
function openScanner() {
  if (active) return active
  const task = new Promise(resolve => {
    function failed(error) {
      if (/cancel/i.test(error && error.errMsg || '')) { resolve({ status: 'cancelled' }); return }
      wx.showModal({ title: '暂时无法打开扫码器', content: '请检查微信及系统的相机权限，或使用首页的手动输入。', showCancel: false,
        success: () => resolve({ status: 'failed' }), fail: () => resolve({ status: 'failed' }) })
    }
    try {
      wx.scanCode({
        onlyFromCamera: true, scanType: ['barCode', 'qrCode'],
        success: result => {
          const text = String(result.result || '')
          wx.showModal({
            title: '已读取扫描内容',
            content: text.slice(0, 300) + (text.length > 300 ? '…' : '') + '\n\n目前未接入商品查询，不能从条码推断配料，也不会自动打开二维码链接。可对照包装手动输入配料。',
            confirmText: '输入配料', cancelText: '关闭',
            success: choice => {
              if (choice.confirm) wx.navigateTo({ url: '/pages/edit/edit' })
              resolve({ status: 'scanned' })
            },
            fail: () => resolve({ status: 'scanned' })
          })
        },
        fail: failed
      })
    } catch (error) { failed(error) }
  })
  active = task.finally(() => { active = null })
  return active
}
module.exports = { openScanner }
