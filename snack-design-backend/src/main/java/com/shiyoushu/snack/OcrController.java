package com.shiyoushu.snack;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@RestController
public class OcrController {
    private final SessionService sessions;
    private final OcrService ocr;
    public OcrController(SessionService sessions, OcrService ocr) { this.sessions=sessions; this.ocr=ocr; }

    @PostMapping(value={"/api/ocr/tesseract", "/api/ocr/recognize"}, consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> recognize(@RequestHeader(value="Authorization", required=false) String auth,
                                       @RequestPart("image") MultipartFile image) throws IOException {
        sessions.user(auth); // 识别前校验用户会话，传统OCR不调用付费AI。
        return Map.of("text", ocr.recognize(image.getBytes()));
    }
}
