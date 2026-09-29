package com.operion.portal;

import java.util.List;

public record MeResponse(String sessionType, List<PortalContext> portalContexts) {
}
