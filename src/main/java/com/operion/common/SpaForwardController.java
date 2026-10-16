package com.operion.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React app's client-side routes when the UI is bundled into the same
 * application (the WAR). BrowserRouter paths like /students/12 have no file behind
 * them, so they forward to index.html instead of 404ing on a deep link or refresh.
 * Backend paths (api, ws, uploads, actuator, OpenAPI docs) and static asset folders
 * are excluded so they keep their own handlers and real 404s.
 */
@Controller
public class SpaForwardController {

	@GetMapping({
			"/",
			"/{path:^(?!api$|ws$|uploads$|actuator$|v3$|swagger-ui$|assets$)[^\\.]*}",
			"/{path:^(?!api$|ws$|uploads$|actuator$|v3$|swagger-ui$|assets$)[^\\.]*}/**"
	})
	public String forwardToIndex() {
		return "forward:/index.html";
	}
}
