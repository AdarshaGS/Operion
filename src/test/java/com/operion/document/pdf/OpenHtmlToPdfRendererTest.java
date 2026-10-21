package com.operion.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class OpenHtmlToPdfRendererTest {

	private final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer();

	@Test
	void bundledFontHasTheRupeeGlyph() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
			assertThat(Font.createFont(Font.TRUETYPE_FONT, in).canDisplay('₹')).isTrue();
		}
	}

	@Test
	void rendersTextAndNeverFetchesExternalResources() throws Exception {
		AtomicInteger hits = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			hits.incrementAndGet();
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.start();
		try {
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			String html = "<html><head><link rel=\"stylesheet\" href=\"" + base + "/a.css\"/>"
					+ "<style>body { font-family: 'Noto Sans'; }</style></head><body>"
					+ "<p>Total ₹ 4,500.00</p><img src=\"" + base + "/logo.png\"/><img src=\"file:///etc/hosts\"/></body></html>";

			byte[] pdf = renderer.render(html);

			assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
			try (PDDocument document = Loader.loadPDF(pdf)) {
				assertThat(new PDFTextStripper().getText(document)).contains("Total ₹ 4,500.00");
			}
			assertThat(hits.get()).isZero();
		} finally {
			server.stop(0);
		}
	}
}
