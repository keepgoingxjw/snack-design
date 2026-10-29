const api = require('./api')
module.exports = {
  async recognize(imagePath) {
    const result = await api.upload('/ocr/tesseract', imagePath)
    if (!result || typeof result.text !== 'string' || !result.text.trim()) throw new Error('没有识别到清晰文字，请对照照片手动输入')
    return { text: result.text }
  }
}
