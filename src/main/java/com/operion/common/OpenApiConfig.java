package com.operion.common;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI (/swagger-ui/index.html) setup: an Authorize button for the two token planes, and an
 * example value on every request/response field so a body can be copied out of the docs and sent as-is.
 *
 * Examples are generated from each field's name and type rather than written into all ~230 request
 * records, so a new DTO gets them with no extra work. A field that needs a specific value sets
 * {@code @Schema(example = "...")} itself - an explicit example is never overwritten here.
 */
@Configuration
public class OpenApiConfig {

	static final String SCHOOL_TOKEN = "schoolToken";
	static final String PLATFORM_TOKEN = "platformToken";

	@Bean
	OpenAPI operionOpenApi() {
		SecurityScheme bearer = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
		return new OpenAPI()
				.info(new Info().title("Operion API")
						.description("School endpoints take the token from POST /api/v1/auth/login (\"schoolToken\"); "
								+ "/api/v1/platform/** takes the token from POST /api/v1/platform/auth/login (\"platformToken\"). "
								+ "Paste the token (without \"Bearer \") into Authorize."))
				.components(new Components()
						.addSecuritySchemes(SCHOOL_TOKEN, bearer.description("Token from POST /api/v1/auth/login"))
						.addSecuritySchemes(PLATFORM_TOKEN, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
								.bearerFormat("JWT").description("Token from POST /api/v1/platform/auth/login")))
				.addSecurityItem(new SecurityRequirement().addList(SCHOOL_TOKEN))
				.addSecurityItem(new SecurityRequirement().addList(PLATFORM_TOKEN));
	}

	@Bean
	PropertyCustomizer exampleValues() {
		return (property, type) -> {
			if (property == null || property.getExample() != null || property.get$ref() != null || property.getEnum() != null
					|| type.getPropertyName() == null) {
				return property;
			}
			Object example = exampleFor(type.getPropertyName(), typeOf(property), property.getFormat());
			if (example != null) {
				property.setExample(example);
			}
			return property;
		};
	}

	/** OpenAPI 3.0 keeps the type in {@code type}, 3.1 in {@code types} - read whichever is set. */
	private static String typeOf(Schema<?> property) {
		if (property.getType() != null) {
			return property.getType();
		}
		Set<String> types = property.getTypes();
		return types == null ? null : types.stream().filter(t -> !"null".equals(t)).findFirst().orElse(null);
	}

	private static final Set<String> MONEY_WORDS = Set.of("amount", "fee", "fine", "salary", "price", "cost", "balance", "total",
			"paid", "due", "rate", "deduction", "allowance", "discount", "budget", "value");

	static Object exampleFor(String propertyName, String type, String format) {
		String name = propertyName.toLowerCase(Locale.ROOT);
		if (type == null) {
			return null;
		}
		switch (type) {
			case "boolean":
				return name.startsWith("include") || name.startsWith("allow") || name.startsWith("enabled") || name.equals("active") ? true : false;
			case "integer":
				return integerExample(name);
			case "number":
				if (name.contains("percent") || name.endsWith("pct")) {
					return new BigDecimal("75.5");
				}
				if (name.contains("lat")) {
					return new BigDecimal("12.9716");
				}
				if (name.contains("lng") || name.contains("lon")) {
					return new BigDecimal("77.5946");
				}
				return containsAny(name, MONEY_WORDS) ? new BigDecimal("1500.00") : new BigDecimal("10.5");
			case "string":
				return stringExample(name, format);
			default:
				return null;
		}
	}

	private static Object integerExample(String name) {
		if (name.endsWith("id") || name.endsWith("ids")) {
			return 1;
		}
		if (name.contains("year")) {
			return 2026;
		}
		if (name.contains("month")) {
			return 6;
		}
		if (name.contains("day")) {
			return 5;
		}
		if (name.contains("minute")) {
			return 45;
		}
		if (name.contains("percent")) {
			return 75;
		}
		if (name.contains("order") || name.contains("sequence") || name.contains("position") || name.contains("number") || name.endsWith("no")) {
			return 1;
		}
		if (name.contains("max") || name.contains("capacity") || name.contains("limit")) {
			return 40;
		}
		if (containsAny(name, MONEY_WORDS)) {
			return 1500;
		}
		return 10;
	}

	private static String stringExample(String name, String format) {
		if (format != null) {
			switch (format) {
				case "date":
					return name.contains("end") || name.endsWith("to") || name.startsWith("to") || name.contains("due") || name.contains("expir")
							? "2027-03-31"
							: "2026-06-01";
				case "date-time":
					return "2026-06-01T09:30:00Z";
				case "email":
					return "contact@example.org";
				case "uuid":
					return "3fa85f64-5717-4562-b3fc-2c963f66afa6";
				case "uri":
				case "url":
					return "https://example.org/logo.png";
				case "partial-time":
					return "09:30:00";
				default:
					break;
			}
		}
		if (name.contains("email")) {
			return "contact@example.org";
		}
		if (name.contains("password")) {
			return "Str0ng-Passw0rd!";
		}
		if (name.contains("phone") || name.contains("mobile")) {
			return "9876543210";
		}
		if (name.contains("url") || name.contains("link")) {
			return "https://example.org/logo.png";
		}
		if (name.contains("pincode") || name.contains("postal") || name.contains("zip")) {
			return "560001";
		}
		if (name.contains("slug")) {
			return "example-org";
		}
		if (name.contains("timezone")) {
			return "Asia/Kolkata";
		}
		if (name.contains("currency")) {
			return "INR";
		}
		if (name.contains("color") || name.contains("colour")) {
			return "#1E40AF";
		}
		if (name.contains("time")) {
			return "09:30";
		}
		if (name.contains("date")) {
			return "2026-06-01";
		}
		if (name.equals("firstname")) {
			return "Asha";
		}
		if (name.equals("lastname")) {
			return "Rao";
		}
		if (name.contains("contactname") || name.endsWith("personname") || name.equals("fullname")) {
			return "Asha Rao";
		}
		if (name.contains("legalname")) {
			return "Example Trust";
		}
		if (name.contains("address")) {
			return name.endsWith("2") ? "Near City Park" : "12 MG Road";
		}
		if (name.equals("city")) {
			return "Bengaluru";
		}
		if (name.equals("state")) {
			return "Karnataka";
		}
		if (name.equals("country")) {
			return "India";
		}
		if (name.contains("tax") || name.contains("gst")) {
			return "29ABCDE1234F1Z5";
		}
		if (name.contains("token")) {
			return "paste-the-token-you-received";
		}
		if (name.endsWith("code")) {
			return "CODE-01";
		}
		if (name.contains("description") || name.contains("notes") || name.contains("remarks") || name.contains("reason")
				|| name.contains("comment") || name.contains("message") || name.contains("body")) {
			return "Free-text note";
		}
		if (name.contains("title") || name.contains("subject")) {
			return "Example title";
		}
		if (name.endsWith("name")) {
			return "Example name";
		}
		return null;
	}

	private static boolean containsAny(String name, Set<String> words) {
		return words.stream().anyMatch(name::contains);
	}
}
