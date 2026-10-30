package com.shiyoushu.snack;

import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import io.github.mymonstercat.ocr.config.ParamConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/** PP-OCRv4 detection, angle classification and recognition, bundled in Maven dependencies. */
@Component
@ConditionalOnProperty(name="app.ocr.engine", havingValue="rapidocr", matchIfMissing=true)
public class RapidOcrEngine implements LocalOcrEngine {
    // The native engine is a process-wide singleton and is not safe for parallel requests.
    private static final Object LOCK = new Object();

    @Override public String recognize(BufferedImage image) throws Exception {
        synchronized (LOCK) {
            // Version 0.0.7 accepts filenames only. Use an unpredictable temporary PNG,
            // delete it even on errors; never persist the photo in records or logs.
            Path temporary=Files.createTempFile("snack-ocr-", ".png");
            try {
                ImageIO.write(image,"png",temporary.toFile());
                var config=ParamConfig.getDefaultConfig();
                config.setMaxSideLen(2048);
                config.setDoAngle(true);
                var engine=InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
                // 0.0.7 logs recognized text at INFO. Disable this vendor logger before
                // invoking its required initialization path, including in native tests.
                ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(InferenceEngine.class))
                        .setLevel(ch.qos.logback.classic.Level.OFF);
                var result=engine.runOcr(temporary.toString(),config);
                return checkedText(result);
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    static String checkedText(OcrResult result) {
        if(result==null || result.getStrRes()==null || result.getStrRes().isBlank())
            throw unclear();
        double sum=0; int count=0, reliable=0;
        if(result.getTextBlocks()!=null) for(var block:result.getTextBlocks()) {
            if(block.getCharScores()==null) continue;
            for(float score:block.getCharScores()) {
                if(!Float.isFinite(score) || score<0 || score>1) continue;
                sum+=score; count++; if(score>=0.7f) reliable++;
            }
        }
        // Confidence is a model score, not a guarantee of correctness. Reject obvious
        // failures instead of presenting arbitrary nonempty strings as successful OCR.
        if(count==0 || sum/count<0.65 || reliable<2) throw unclear();
        return result.getStrRes();
    }
    private static ResponseStatusException unclear() {
        return new ResponseStatusException(UNPROCESSABLE_ENTITY,"照片文字识别可信度不足，请靠近并正对配料表重拍，避开反光和条码");
    }
}
