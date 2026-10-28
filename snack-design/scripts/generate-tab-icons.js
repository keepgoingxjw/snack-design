// 生成微信原生 TabBar 所需的透明 PNG，无第三方依赖。
const fs = require('node:fs')
const path = require('node:path')
const zlib = require('node:zlib')
const SIZE = 81
const SCALE = 4
function crc32(data) {
  let crc = 0xffffffff
  for (const byte of data) {
    crc ^= byte
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0)
  }
  return (crc ^ 0xffffffff) >>> 0
}
function chunk(type, data) {
  const name = Buffer.from(type)
  const length = Buffer.alloc(4); length.writeUInt32BE(data.length)
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([name, data])))
  return Buffer.concat([length, name, data, crc])
}
function distance(x, y, line) {
  const [a, b, c, d] = line
  const t = Math.max(0, Math.min(1, ((x - a) * (c - a) + (y - b) * (d - b)) / ((c - a) ** 2 + (d - b) ** 2)))
  return Math.hypot(x - a - t * (c - a), y - b - t * (d - b))
}
const shapes = {
  home: [[18, 35, 40.5, 18], [40.5, 18, 63, 35], [22, 32, 22, 63], [22, 63, 59, 63], [59, 63, 59, 32]],
  scan: [[26, 15, 26, 66], [55, 15, 55, 66], [15, 26, 66, 26], [15, 55, 66, 55]],
  history: Array.from({ length: 96 }, (_, i) => {
    const a = i * Math.PI / 48; const b = (i + 1) * Math.PI / 48
    return [40.5 + 22 * Math.cos(a), 40.5 + 22 * Math.sin(a), 40.5 + 22 * Math.cos(b), 40.5 + 22 * Math.sin(b)]
  }).concat([[40.5, 23, 40.5, 40.5], [40.5, 40.5, 54, 40.5]])
}
function png(lines, hex) {
  const rgb = hex.match(/../g).map(v => parseInt(v, 16))
  const pixels = Buffer.alloc(SIZE * (SIZE * 4 + 1))
  for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
    let coverage = 0
    for (let sy = 0; sy < SCALE; sy++) for (let sx = 0; sx < SCALE; sx++) {
      if (lines.some(line => distance(x + (sx + 0.5) / SCALE, y + (sy + 0.5) / SCALE, line) <= 1.65)) coverage++
    }
    const offset = y * (SIZE * 4 + 1) + 1 + x * 4
    pixels[offset] = rgb[0]; pixels[offset + 1] = rgb[1]; pixels[offset + 2] = rgb[2]
    pixels[offset + 3] = Math.round(255 * coverage / (SCALE * SCALE))
  }
  const header = Buffer.alloc(13)
  header.writeUInt32BE(SIZE, 0); header.writeUInt32BE(SIZE, 4); header[8] = 8; header[9] = 6
  return Buffer.concat([Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(pixels)), chunk('IEND', Buffer.alloc(0))])
}
const output = path.join(__dirname, '../assets/tabbar')
fs.mkdirSync(output, { recursive: true })
for (const [name, lines] of Object.entries(shapes)) {
  fs.writeFileSync(path.join(output, name + '.png'), png(lines, '81738f'))
  fs.writeFileSync(path.join(output, name + '-active.png'), png(lines, '725294'))
}
console.log('已生成 6 个 81×81 透明 TabBar 图标')
