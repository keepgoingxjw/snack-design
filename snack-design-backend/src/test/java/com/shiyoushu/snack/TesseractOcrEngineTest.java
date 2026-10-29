package com.shiyoushu.snack;

import net.sourceforge.tess4j.ITesseract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TesseractOcrEngineTest {
    @TempDir Path data;
    @Test void missingLanguagesAreReportedBeforeLoadingNativeEngine() {
        var engine=spy(new TesseractOcrEngine(data.toString()));
        var error=assertThrows(ResponseStatusException.class,()->engine.recognize(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB)));
        assertEquals(503,error.getStatusCode().value());
        verify(engine,never()).createEngine();
    }
    @Test void onlyConfiguresTraditionalChineseAndEnglishOcr() throws Exception {
        Files.write(data.resolve("chi_sim.traineddata"),new byte[]{1});
        Files.write(data.resolve("eng.traineddata"),new byte[]{1});
        var nativeEngine=mock(ITesseract.class);
        var engine=spy(new TesseractOcrEngine(data.toString()));
        doReturn(nativeEngine).when(engine).createEngine();
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
        when(nativeEngine.doOCR(image)).thenReturn("燕麦片、小麦粉");
        assertEquals("燕麦片、小麦粉",engine.recognize(image));
        verify(nativeEngine).setDatapath(data.toString());
        verify(nativeEngine).setLanguage("chi_sim");
        verify(nativeEngine).setOcrEngineMode(1);
        verify(nativeEngine).setPageSegMode(6);
    }
}
