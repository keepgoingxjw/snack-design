package com.shiyoushu.snack;

import java.awt.image.BufferedImage;

/** Local-only OCR. Implementations must not send images to cloud services. */
public interface LocalOcrEngine {
    String recognize(BufferedImage image) throws Exception;
}
