const seed = '燕麦片、小麦粉、白砂糖、植物油、乳粉'
// 内容沿用交付原型。仅提供一般用途信息，不作商品安全评级。
const knowledge = {
  '燕麦片': ['structure','谷物原料','提供谷物风味、口感及谷物营养。','这包食品的纤维含量尚不明确，不能直接当作高纤维食品。需严格避免麸质的人群应核对包装标识。','烘焙原料教材','https://opentextbc.ca/ingredients/'],
  '小麦粉': ['structure','形成结构','形成烘焙食品的结构与口感，提供碳水化合物。','小麦过敏者需避开；需避免麸质的人群应核对产品标识。','FDA：食物过敏原','https://www.fda.gov/food/food-labeling-nutrition/food-allergies-what-you-need-know'],
  '植物油': ['structure','调整口感','提供脂肪，帮助形成酥松口感；脂肪酸组成取决于具体油种。','仅凭名称无法判断脂肪酸组成，需结合营养成分表和食用量。','WHO：健康膳食','https://www.who.int/news-room/fact-sheets/detail/healthy-diet'],
  '白砂糖': ['flavour','提供甜味','增加甜味，也影响烘焙色泽与口感；加工用途不代表健康加分。','需结合实际含量和食用量关注糖摄入。','WHO：健康膳食','https://www.who.int/news-room/fact-sheets/detail/healthy-diet'],
  '乳粉': ['flavour','增加奶味','增加乳香，也可能提供乳蛋白等营养；实际贡献取决于添加量。','牛奶过敏者需避开；乳糖不耐受者需结合乳糖含量和个人耐受情况。','NIDDK：乳糖不耐受','https://www.niddk.nih.gov/health-information/digestive-diseases/lactose-intolerance']
}
function splitIngredients(raw) {
  if (typeof raw !== 'string' || raw.length > 5000) throw new Error('原文必须为文字，且不超过5000字')
  const values=[]; let buffer=''; const brackets=[]
  for (const char of raw.replace(/^\s*配料[表]?\s*[:：]\s*/,'')) {
    if ('（('.includes(char)) brackets.push(char)
    if ('）)'.includes(char)) {
      const opening = brackets.pop()
      if (opening !== (char === '）' ? '（' : '(')) throw new Error('括号不完整或不匹配，请核对原文')
    }
    if (!brackets.length && '、，,；;\n\r'.includes(char)) { if(buffer.trim()) values.push(buffer.trim()); buffer='' } else buffer+=char
  }
  if(brackets.length) throw new Error('括号不完整，请核对原文')
  if(buffer.trim()) values.push(buffer.trim())
  if(!values.length) throw new Error('请至少填写一种配料')
  if(values.length>30) throw new Error('一次最多填写30项配料')
  return values
}
function analyze(raw) {
  return splitIngredients(raw).map((name,index)=>{
    const k=Object.prototype.hasOwnProperty.call(knowledge,name) ? knowledge[name] : null
    return k ? { id:index,name,group:k[0],role:k[1],benefit:k[2],risk:k[3],source:k[4],url:k[5] } : {id:index,name,group:'pending',role:'待解析',benefit:'暂无可核实的配料解释。',risk:'不能仅根据名称判断好处或危害。复合配料保留原文，需进一步核对。',source:'',url:''}
  })
}
function allergenClues(raw) {
  const names = []
  if (/小麦|麦粉|面粉/.test(raw)) names.push('小麦')
  if (/乳|奶/.test(raw)) names.push('乳类')
  if (/花生/.test(raw)) names.push('花生')
  if (/大豆|黄豆|豆粉|豆蛋白/.test(raw)) names.push('大豆')
  if (/鸡蛋|鸭蛋|蛋清|蛋黄|全蛋/.test(raw)) names.push('蛋类')
  return names
}
module.exports={seed,splitIngredients,analyze,allergenClues}
