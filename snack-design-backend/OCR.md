# 拍照文字识别：传统 OCR（不使用 DeepSeek）

## 这次改动的范围

拍照 → 上传到应用自己的Java后端 → Tesseract识字 → 回填现有原文输入框 → 用户核对。

不新增页面或按钮，不自动提交配料分析，不使用DeepSeek视觉模型，不把图片发给任何AI服务，也不收取AI API调用费。之后用户主动确认原文的“配料联网解析”沿用原有功能，没有在本次改动中更换或移除。

## 引擎与语言数据

- Java通过Tess4J 5.20.0调用开源Tesseract，CPU运行，不需要GPU、Python、Docker或API Key。
- 使用tessdata_fast 4.1.0的`chi_sim.traineddata`，约2.4MB，是OCR字符识别数据，不是生成式大模型。
- 中文优先识别，同时支持该字符集中常见的英文、数字与符号。没有额外词表去“猜测纠正”配料名称。
- 本机语言数据已下载并核对SHA-256。Windows所需原生DLL由Maven依赖提供，本机已用真实引擎测试。
- 其他Windows机器如原生库无法加载，可能需要兼容的Visual C++运行库；Linux部署需安装兼容的Tesseract/Leptonica原生库。

语言目录默认`data/tessdata`，可通过`OCR_DATA_PATH`调整。建议保持默认的ASCII相对路径，已处理Windows项目父目录含中文的问题。不要把自定义语言目录设置成包含中文的原生绝对路径后假设所有平台都能工作。

新机器在后端目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-ocr.ps1
```

脚本只下载版本固定、SHA-256校验的官方OCR语言数据，不安装系统软件，不改API Key，不覆盖校验不一致的已有语言文件。运行识别时不再联网下载语言包。

## 接口与保留的限制

小程序使用认证的`POST /api/ocr/tesseract`，multipart字段`image`，返回`{"text":"识别文字"}`。旧`/api/ocr/recognize`仅作为兼容别名，更新后的后端两者都只使用传统OCR。

新客户端不再调用旧云端识字路径；旧后端没有专用接口时只会失败，不会偷偷退回DeepSeek。需重启后端并重新编译小程序才能完成切换。

JPEG/PNG、最多4MB、单边8192像素、最多2400万像素，识别文本最多5000字；最多2个并行识别请求。图片只在本次请求内解码，不写入配料记录，应用不主动保存照片或打印识别原文。OCR失败仍可手动输入，不回填示例文字。

生产微信后台仍需配置应用后端的uploadFile合法域名，隐私用途应写“图片上传应用后端用于OCR”，不再声称图片交给DeepSeek识别。

## 准确性与验证

已使用真实Tesseract识别生成的清晰中英文测试图，以及通过真实HTTP上传链路验证。测试不用DeepSeek Key，也不产生云端识别费用。

**OCR不保证逐字准确。** 实测中某些字仍可能出错，例如“苯”可能误作形似字。没有用AI或配料词表把结果强行改成预期答案。反光、倾斜、小字号、折痕也会影响识别，必须在现有核对原文页面检查和修改后，再确认解析。不把测试图通过当成真实包装照片全部正确。

真实引擎测试（语言数据和原生库就绪时）：

```powershell
mvn -Dtest=OcrNativeTest -Docr.native.test=true test
```

普通测试默认跳过依赖本机原生环境的这一项，仍验证上传鉴权、输入限制、回填、失败手输和不调用云端。已有后端占用JAR时，可用源码类路径在临时端口运行smoke，不强停用户服务。

## 官方依据与许可

- Tess4J用法与Windows原生库要求：https://tess4j.sourceforge.net/usage.html
- Tess4J示例：https://tess4j.sourceforge.net/codesample.html
- 官方语言数据与速度/准确性取舍：https://github.com/tesseract-ocr/tessdata_fast/tree/4.1.0
- 语言数据下载：https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/chi_sim.traineddata

Tess4J及该语言数据为Apache-2.0许可，部署/分发时应保留相应许可声明。项目未重新训练模型，也没有植入配料答案。
