package com.operion.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The web app hides a module's routes using its own path map (web/src/auth/modules.ts). That
 * map is a second copy of AppModule.routes(); this keeps the two from drifting, so a module
 * switched off can never leave a reachable page in the UI because someone updated one side.
 */
class ModuleRoutesTest {

	@Test
	void webRouteMapMatchesTheBackendCatalogue() throws Exception {
		String source = Files.readString(Path.of("web/src/auth/modules.ts"));
		Matcher matcher = Pattern.compile("\\[\"(/[a-z-]+)\",\\s*\"([A-Z_]+)\"\\]").matcher(source);
		List<String> web = new ArrayList<>();
		while (matcher.find()) {
			web.add(matcher.group(2) + " " + matcher.group(1));
		}
		List<String> backend = new ArrayList<>();
		for (AppModule module : AppModule.values()) {
			module.routes().forEach(route -> backend.add(module.name() + " " + route));
		}
		assertThat(web).containsExactlyInAnyOrderElementsOf(backend);
	}
}
