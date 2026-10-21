package com.operion.document.pdf;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Fills a classpath template under {@code documents/} with values. XML mode, so a
 * template that isn't well-formed fails here rather than as a confusing PDF-engine
 * error, and every value is escaped - a student or school name can't inject markup.
 */
@Component
public class DocumentHtmlRenderer {

	private final TemplateEngine engine;

	public DocumentHtmlRenderer() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("documents/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.XML);
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCacheable(true);
		engine = new TemplateEngine();
		engine.setTemplateResolver(resolver);
	}

	public String render(String template, Map<String, Object> variables) {
		return engine.process(template, new Context(Locale.ENGLISH, variables));
	}
}
