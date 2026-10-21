package com.operion.document.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Component;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Default {@link PdfRenderer}. Fonts are bundled (src/main/resources/fonts, SIL OFL) so
 * output is identical on every server and the rupee sign always has a glyph. Every
 * external resource is refused except data: URIs - a template can't make the server
 * fetch a URL (no SSRF through a branding field) or read a local file.
 */
@Component
public class OpenHtmlToPdfRenderer implements PdfRenderer {

	public static final String FONT_FAMILY = "Noto Sans";

	@Override
	public byte[] render(String xhtml) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.withProducer("Operion");
			builder.useFont(() -> font("NotoSans-Regular.ttf"), FONT_FAMILY, 400, FontStyle.NORMAL, true);
			builder.useFont(() -> font("NotoSans-Bold.ttf"), FONT_FAMILY, 700, FontStyle.NORMAL, true);
			builder.useExternalResourceAccessControl((uri, type) -> isDataUri(uri), ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
			builder.useExternalResourceAccessControl((uri, type) -> isDataUri(uri), ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
			builder.withHtmlContent(xhtml, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException | RuntimeException e) {
			throw new DocumentRenderException("Failed to render PDF", e);
		}
	}

	private static boolean isDataUri(String uri) {
		return uri != null && uri.startsWith("data:");
	}

	private static InputStream font(String file) {
		InputStream in = OpenHtmlToPdfRenderer.class.getResourceAsStream("/fonts/" + file);
		if (in == null) {
			throw new IllegalStateException("Bundled font missing: " + file);
		}
		return in;
	}
}
