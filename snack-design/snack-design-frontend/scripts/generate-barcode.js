// 首页装饰条码，不编码商品信息。使用高清 PNG 避免旋转渐变的采样条纹。
const fs = require('node:fs')
const path = require('node:path')
const zlib = require('node:zlib')
const width = 1200, height = 144
const pixels = Buffer.alloc(height * (width * 4 + 1))
// 像素级垂直边缘；窄条也保留足够宽度，降低缩放后的摩尔纹。
const pattern = [9, 9, 6, 12, 12, 9, 6, 9, 9, 12, 6, 9]
const period = pattern.reduce((sum, n) => sum + n, 0)
for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
  let phase = x % period, index = 0
  while (phase >= pattern[index]) phase -= pattern[index++]
  const offset = y * (width * 4 + 1) + 1 + x * 4
  pixels[offset] = 85; pixels[offset + 1] = 75; pixels[offset + 2] = 61
  pixels[offset + 3] = index % 2 === 0 ? 255 : 0
}
function crc32(data) {
  let crc = 0xffffffff
  for (const byte of data) {
    crc ^= byte
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0)
  }
  return (crc ^ 0xffffffff) >>> 0
}
function chunk(type, data) {
  const name = Buffer.from(type), size = Buffer.alloc(4), crc = Buffer.alloc(4)
  size.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(Buffer.concat([name, data])))
  return Buffer.concat([size, name, data, crc])
}
const header = Buffer.alloc(13)
header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4); header[8] = 8; header[9] = 6
fs.writeFileSync(path.join(__dirname, '../assets/barcode.png'), Buffer.concat([
  Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(pixels)), chunk('IEND', Buffer.alloc(0))
]))
console.log('已生成 1200×144 高清装饰条码')
