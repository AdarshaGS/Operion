package com.operion.document.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** QR codes as PNG data URIs, ready for an img src in a document template (the renderer only loads data: URIs). */
public final class QrCodes {

	private static final int SIZE_PX = 300;

	private QrCodes() {
	}

	public static String pngDataUri(String text) {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX,
					Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
			BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
			for (int y = 0; y < matrix.getHeight(); y++) {
				for (int x = 0; x < matrix.getWidth(); x++) {
					image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (WriterException | IOException e) {
			throw new DocumentRenderException("Could not draw QR code", e);
		}
	}
}
