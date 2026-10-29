package com.shiyoushu.snack;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Java后端本地传统OCR。没有HTTP客户端、API Key或AI模型依赖。 */
@Component
public class TesseractOcrEngine {
    private final Path dataPath;
    private final String nativeDataPath;
    public TesseractOcrEngine(@Value("${app.ocr.data-path:data/tessdata}") String dataPath) {
        this.dataPath=Path.of(dataPath).toAbsolutePath().normalize();
        Path working=Path.of("").toAbsolutePath().normalize();
        // Windows原生库对中文绝对路径兼容性不佳，项目内语言目录使用ASCII相对路径。
        this.nativeDataPath=(this.dataPath.startsWith(working)?working.relativize(this.dataPath):this.dataPath).toString();
    }
    public String recognize(BufferedImage image) throws TesseractException {
        if (!Files.isRegularFile(dataPath.resolve("chi_sim.traineddata")))
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,"OCR中文语言包未就绪，请运行后端scripts/setup-ocr.ps1");
        // 每次独立实例，避免跨请求共享Tesseract的原生状态。
        ITesseract engine=createEngine();
        engine.setDatapath(nativeDataPath);
        engine.setLanguage("chi_sim"); // 中文优先，亦包含常见拉丁字符；不以英文模型覆盖中文识别。
        engine.setOcrEngineMode(1); // tessdata_fast仅支持LSTM OCR引擎，不是生成式大模型。
        engine.setPageSegMode(6); // 配料表按一块文字处理，保留文字顺序与换行。
        engine.setVariable("user_defined_dpi","300");
        return engine.doOCR(image);
    }
    ITesseract createEngine() { return new Tesseract(); }
}
