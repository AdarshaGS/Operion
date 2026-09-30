package com.operion.platform.crm.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.operion.platform.crm.Activity;
import com.operion.platform.crm.ActivityService;
import com.operion.platform.crm.ActivityType;
import com.operion.platform.crm.CrmDirectory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Insert-only timeline - there is deliberately no update or delete endpoint. */
@RestController
@RequestMapping("/api/v1/platform/crm/activities")
public class ActivityController {

	private final ActivityService activityService;
	private final CrmDirectory crmDirectory;

	public ActivityController(ActivityService activityService, CrmDirectory crmDirectory) {
		this.activityService = activityService;
		this.crmDirectory = crmDirectory;
	}

	@GetMapping
	public List<ActivityResponse> list(@RequestParam(required = false) Long leadId,
			@RequestParam(required = false) Long organisationId) {
		List<Activity> activities = activityService.list(leadId, organisationId);
		Map<Long, String> names = crmDirectory.namesOf(activities.stream().map(Activity::getAuthorId).toList());
		return activities.stream().map(activity -> ActivityResponse.from(activity, names.get(activity.getAuthorId()))).toList();
	}

	@PostMapping
	public ActivityResponse log(@Valid @RequestBody CreateActivityRequest request) {
		Activity activity = activityService.log(request.leadId(), request.organisationId(), ActivityType.valueOf(request.type()),
				request.body(), request.occurredAt());
		return ActivityResponse.from(activity,
				crmDirectory.namesOf(Collections.singletonList(activity.getAuthorId())).get(activity.getAuthorId()));
	}
}
