package com.shiyoushu.snack;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrServiceTest {
    static byte[] image() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",buffer);
        return buffer.toByteArray();
    }
    @Test void extractsTextWithTraditionalOcrWithoutAnyAiKeyOrCloudClient() throws Exception {
        var engine=mock(TesseractOcrEngine.class);
        String raw="配料表：燕麦片、小麦粉\n乳粉（全脂乳粉）";
        when(engine.recognize(any(BufferedImage.class))).thenReturn(raw);
        assertEquals(raw,new OcrService(engine).recognize(image()));
        var input=org.mockito.ArgumentCaptor.forClass(BufferedImage.class);
        verify(engine,times(1)).recognize(input.capture());
        assertEquals(2,input.getValue().getWidth());
    }
    @Test void invalidOrOversizedImagesNeverReachOcrEngine() {
        var engine=mock(TesseractOcrEngine.class);var ocr=new OcrService(engine);
        assertThrows(ResponseStatusException.class,()->ocr.recognize("not-an-image".getBytes()));
        var error=assertThrows(ResponseStatusException.class,()->ocr.recognize(new byte[OcrService.MAX_BYTES+1]));
        assertEquals(413,error.getStatusCode().value());
        verifyNoInteractions(engine);
    }
    @Test void emptyOrOversizedRecognizedTextIsNotInventedOrSilentlyTruncated() throws Exception {
        var engine=mock(TesseractOcrEngine.class);var ocr=new OcrService(engine);
        when(engine.recognize(any())).thenReturn("  ");
        assertEquals(422,assertThrows(ResponseStatusException.class,()->ocr.recognize(image())).getStatusCode().value());
        when(engine.recognize(any())).thenReturn("字".repeat(5001));
        assertEquals(422,assertThrows(ResponseStatusException.class,()->ocr.recognize(image())).getStatusCode().value());
    }
    @Test void unavailableLocalEngineDoesNotFallBackToCloudAi() throws Exception {
        var engine=mock(TesseractOcrEngine.class);
        when(engine.recognize(any())).thenThrow(new UnsatisfiedLinkError("native unavailable"));
        var error=assertThrows(ResponseStatusException.class,()->new OcrService(engine).recognize(image()));
        assertEquals(503,error.getStatusCode().value());
        assertFalse(error.getReason().contains("DeepSeek"));
        verify(engine,times(1)).recognize(any());
    }
}
