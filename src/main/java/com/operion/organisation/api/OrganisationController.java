package com.operion.organisation.api;

import java.time.Duration;

import com.operion.authorization.RequirePermission;
import com.operion.common.ClientIpResolver;
import com.operion.common.EnumParam;
import com.operion.common.SlidingWindowRateLimiter;
import com.operion.common.TenantContext;
import com.operion.common.TooManyRequestsException;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationService;
import com.operion.organisation.OrganisationStatus;
import com.operion.platform.settings.PlatformSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal smoke-test surface for the Foundation module - lets the static UI
 * (src/main/resources/static/index.html) exercise provisioning, login and status
 * transitions end to end. Not the final API design (see ai-context/erp-system-plan.md
 * §5). Creation alone is public (bootstrapping); everything else requires the token
 * issued by /api/v1/auth/login, per JwtAuthenticationInterceptor.
 *
 * list()/get() (returning every organisation to any authenticated caller from any org -
 * a real cross-tenant visibility gap, since Organisation is the tenant root and can't
 * carry @TenantId itself) have been removed and replaced by
 * PlatformOrganisationController, reachable only via a platform-admin token.
 */
@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

	private final OrganisationService organisationService;
	private final PlatformSettingsService platformSettingsService;
	private final ClientIpResolver clientIpResolver;
	/** Public sign-up creates an organisation, an admin login and a billing subscription per call, so it is limited per
	 * caller address (SEC-M4). The cap is configurable because automated test runs legitimately provision many. */
	private final SlidingWindowRateLimiter createLimiter;

	public OrganisationController(OrganisationService organisationService, PlatformSettingsService platformSettingsService,
			ClientIpResolver clientIpResolver, @Value("${app.rate-limit.org-create.max-per-hour:10}") int createMaxPerHour) {
		this.organisationService = organisationService;
		this.platformSettingsService = platformSettingsService;
		this.clientIpResolver = clientIpResolver;
		this.createLimiter = new SlidingWindowRateLimiter(createMaxPerHour, Duration.ofHours(1), 10_000);
	}

	@PostMapping
	public OrganisationResponse create(@Valid @RequestBody CreateOrganisationRequest request, HttpServletRequest httpRequest) {
		if (!createLimiter.tryAcquire(clientIpResolver.resolve(httpRequest))) {
			throw new TooManyRequestsException("Too many organisations created from this address. Please try again later.");
		}
		Organisation organisation = organisationService.provision(request.toOrganisation(), request.toProfile(),
				request.toAdminAccount(), request.toAcademicYearDetails(), request.toPlanSelection());
		return OrganisationResponse.from(organisation, platformSettingsService.current().getTrialDays());
	}

	@PatchMapping("/{id}/status")
	@RequirePermission("ORGANISATION_MANAGE")
	public OrganisationResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeOrganisationStatusRequest request) {
		// An organisation's own admin changes only its own status; the platform console handles any other.
		if (!id.equals(TenantContext.getOrganisationId())) {
			throw new IllegalArgumentException("No organisation with id " + id);
		}
		OrganisationStatus target = EnumParam.parse(OrganisationStatus.class, request.status());
		return OrganisationResponse.from(organisationService.changeStatus(id, target), platformSettingsService.current().getTrialDays());
	}
}
