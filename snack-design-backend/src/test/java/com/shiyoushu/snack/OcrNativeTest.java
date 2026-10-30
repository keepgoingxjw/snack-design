package com.shiyoushu.snack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 显式运行真实OCR，不使用任何API Key或网络请求；不强制CI安装本地原生依赖。 */
@EnabledIfSystemProperty(named="ocr.native.test",matches="true")
class OcrNativeTest {
    @Test void readsGeneratedChineseAndEnglishImageWithActualTesseract() throws Exception {
        var image=new BufferedImage(1400,220,BufferedImage.TYPE_BYTE_GRAY);
        var g=image.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setColor(Color.BLACK);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,48));
        g.drawString("配料表：燕麦片、小麦粉、白砂糖、苯甲酸钠",35,80);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,42));
        g.drawString("INGREDIENTS OATS FLOUR SUGAR",35,160);g.dispose();
        Path fixture=Path.of("target/ocr-native-sample.png");Files.createDirectories(fixture.getParent());ImageIO.write(image,"png",fixture.toFile());
        var data=new java.io.ByteArrayOutputStream();ImageIO.write(image,"png",data);image.flush();
        String recognized=new OcrService(new RapidOcrEngine()).recognize(data.toByteArray());
        String compact=recognized.replaceAll("\\s+","");
        assertTrue(compact.contains("燕麦片"),recognized);
        assertTrue(compact.contains("小麦粉"),recognized);
        assertTrue(compact.contains("INGREDIENTS"),recognized);
        // OCR会有错字，保留实际输出供人工核对，不用配料词表或AI替换识别字符。
        System.out.println("真实传统OCR已识别中英文测试图，未调用DeepSeek；识别结果仍须人工核对");
    }
}
