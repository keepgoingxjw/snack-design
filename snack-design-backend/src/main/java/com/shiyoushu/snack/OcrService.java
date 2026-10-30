package com.shiyoushu.snack;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import static org.springframework.http.HttpStatus.*;

/** 只识别图片文字。独立于DeepSeek/云端AI，不生成分析，不写入配料记录。 */
@Service
public class OcrService {
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private final LocalOcrEngine engine;
    private final Semaphore slots = new Semaphore(2);
    public OcrService(LocalOcrEngine engine) { this.engine=engine; }

    public String recognize(byte[] bytes) {
        imageType(bytes);
        if (!slots.tryAcquire()) throw new ResponseStatusException(TOO_MANY_REQUESTS,"图片识别繁忙，请稍后重试或手动输入");
        BufferedImage image=null;
        try {
            image=decode(bytes);
            String text=engine.recognize(image);
            if (text==null || text.isBlank()) throw new ResponseStatusException(UNPROCESSABLE_ENTITY,"未识别到清晰文字，请重新拍摄或手动输入");
            if (text.strip().length()>5000) throw new ResponseStatusException(UNPROCESSABLE_ENTITY,"识别文字超过5000字，请只拍配料区域");
            return text.strip();
        } catch (ResponseStatusException e) { throw e; }
        catch (LinkageError e) { throw new ResponseStatusException(SERVICE_UNAVAILABLE,"本地OCR运行库未就绪，请检查OCR依赖；可先手动输入"); }
        catch (Exception e) { throw new ResponseStatusException(UNPROCESSABLE_ENTITY,"OCR文字识别未完成，请拍清配料区域或对照照片手动输入"); }
        finally { if(image!=null)image.flush(); slots.release(); }
    }
    private static BufferedImage decode(byte[] bytes) throws IOException {
        try (var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("No image reader");
            var reader=readers.next();
            try { reader.setInput(input,true,true); return reader.read(0); }
            finally { reader.dispose(); }
        }
    }
    static String imageType(byte[] image) {
        if (image==null || image.length==0) throw new ResponseStatusException(BAD_REQUEST,"请上传配料表照片");
        if (image.length>MAX_BYTES) throw new ResponseStatusException(PAYLOAD_TOO_LARGE,"图片不能超过4MB，请压缩或重新拍摄");
        try (var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IllegalArgumentException();
            var reader=readers.next();
            try {
                String type=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","jpg","png").contains(type))throw new IllegalArgumentException();
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || width>8192 || height>8192 || (long)width*height>24000000)
                    throw new ResponseStatusException(BAD_REQUEST,"图片尺寸过大，请压缩或只拍配料区域");
                return "png".equals(type)?"image/png":"image/jpeg";
            } finally { reader.dispose(); }
        } catch(ResponseStatusException e){throw e;}
        catch(Exception e){throw new ResponseStatusException(BAD_REQUEST,"图片内容无效，目前支持JPEG或PNG照片");}
    }
}
