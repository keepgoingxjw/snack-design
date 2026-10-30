package com.shiyoushu.snack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

/** Local photo regression: caller supplies screenshot; never bundled or uploaded to cloud. */
@EnabledIfSystemProperty(named="ocr.photo",matches=".+")
class OcrPhotoTest {
    @Test void readsVisiblePackageTextInsteadOfScatteredCharacters() throws Exception {
        var screenshot=ImageIO.read(Path.of(System.getProperty("ocr.photo")).toFile());
        var photo=screenshot.getSubimage(47,9,524,384);
        var bytes=new ByteArrayOutputStream(); ImageIO.write(photo,"png",bytes);
        String text=new OcrService(new RapidOcrEngine()).recognize(bytes.toByteArray());
        assertTrue(text.replaceAll("\\s+", "").contains("保质期"),text);
        assertTrue(text.contains("食品生产许可证"),text);
        assertTrue(text.contains("消费者服务热线"),text);
        assertTrue(text.contains("不适合四岁以下儿童食用"),text);
    }
}
