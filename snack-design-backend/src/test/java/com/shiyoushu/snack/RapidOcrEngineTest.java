package com.shiyoushu.snack;
import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RapidOcrEngineTest {
    private OcrResult result(String text,float... scores) {
        var block=mock(TextBlock.class); when(block.getCharScores()).thenReturn(scores);
        return new OcrResult(0,new ArrayList<>(java.util.List.of(block)),0,text);
    }
    @Test void rejectsNonemptyGarbageWithLowConfidence() {
        var error=assertThrows(ResponseStatusException.class,()->RapidOcrEngine.checkedText(result("一和\n生 =\n吕 CC",.1f,.3f,.2f)));
        assertEquals(422,error.getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->RapidOcrEngine.checkedText(result("内容",Float.NaN)));
    }
    @Test void preservesRecognizedTextWithoutInventingOrCorrectingIngredients() {
        String raw="配料：燕麦片、小麦粉\n保质期：12个月";
        assertEquals(raw,RapidOcrEngine.checkedText(result(raw,.96f,.91f,.85f)));
    }
}
