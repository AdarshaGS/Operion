package com.operion.reporting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns SavedReport CRUD/lifecycle and the per-report access model: REPORT_MANAGE (or
 * being the org's Owner) sees/edits everything; short of that, a caller can only touch a
 * report they created or were explicitly shared (SavedReportShare). This per-record check
 * is new for this codebase - everywhere else uses coarse @RequirePermission gating - so
 * it's kept here rather than in PermissionInterceptor, which only ever answers
 * "does this caller hold permission code X," never "for this specific record."
 */
@Service
public class SavedReportService {

	private final SavedReportRepository savedReportRepository;
	private final SavedReportParameterRepository parameterRepository;
	private final SavedReportColumnRepository columnRepository;
	private final SavedReportShareRepository shareRepository;
	private final OrganisationMembershipRepository membershipRepository;

	public SavedReportService(SavedReportRepository savedReportRepository, SavedReportParameterRepository parameterRepository,
			SavedReportColumnRepository columnRepository, SavedReportShareRepository shareRepository,
			OrganisationMembershipRepository membershipRepository) {
		this.savedReportRepository = savedReportRepository;
		this.parameterRepository = parameterRepository;
		this.columnRepository = columnRepository;
		this.shareRepository = shareRepository;
		this.membershipRepository = membershipRepository;
	}

	public record ParameterInput(String name, ReportParameterType type, String label, int sortOrder) {
	}

	public record ColumnInput(String sourceColumn, String label, int sortOrder) {
	}

	private record StandardReportDefinition(String name, String description, String sqlQuery, List<ParameterInput> parameters,
			List<ColumnInput> columns) {
	}

	/**
	 * Canned reports for Sales/Inventory/Purchase (GitHub #66-#68) - built on the curated
	 * reporting_sales/inventory_stock/purchase_orders views (V56), not bespoke endpoints,
	 * per those tickets' own framing ("not a bespoke endpoint/page... runs through the
	 * generic run-report screen"). Seeded on demand via seedStandardReports() rather than
	 * auto-created at org provisioning - keeps this out of that already gotcha-prone
	 * TenantContext-ordering-sensitive code path (see ai-context/load-context.md) for a
	 * nice-to-have. Same "pre-existing orgs need it created manually" trade-off already
	 * accepted for the Guardian role.
	 */
	private static final List<StandardReportDefinition> STANDARD_REPORTS = List.of(
			new StandardReportDefinition("Sales totals by item, customer and campus",
					"Date-range sales totals grouped by campus, customer and item.",
					"SELECT campus_name, customer_name, item_name, SUM(quantity) AS total_quantity, SUM(line_total) AS total_amount "
							+ "FROM reporting_sales WHERE sale_date BETWEEN :fromDate AND :toDate "
							+ "GROUP BY campus_name, customer_name, item_name ORDER BY total_amount DESC",
					List.of(new ParameterInput("fromDate", ReportParameterType.DATE, "From date", 0),
							new ParameterInput("toDate", ReportParameterType.DATE, "To date", 1)),
					List.of(new ColumnInput("campus_name", "Campus", 0), new ColumnInput("customer_name", "Customer", 1),
							new ColumnInput("item_name", "Item", 2), new ColumnInput("total_quantity", "Quantity", 3),
							new ColumnInput("total_amount", "Amount", 4))),
			new StandardReportDefinition("Stock on hand and valuation",
					"Current balance and received cost per item and campus.",
					"SELECT campus_name, item_code, item_name, balance, received_cost FROM reporting_inventory_stock "
							+ "ORDER BY campus_name, item_name",
					List.of(),
					List.of(new ColumnInput("campus_name", "Campus", 0), new ColumnInput("item_code", "Code", 1),
							new ColumnInput("item_name", "Item", 2), new ColumnInput("balance", "Balance", 3),
							new ColumnInput("received_cost", "Received cost", 4))),
			new StandardReportDefinition("Low stock items",
					"Items at or below their reorder level, per campus.",
					"SELECT campus_name, item_code, item_name, balance, reorder_level FROM reporting_inventory_stock "
							+ "WHERE reorder_level IS NOT NULL AND balance <= reorder_level ORDER BY balance ASC",
					List.of(),
					List.of(new ColumnInput("campus_name", "Campus", 0), new ColumnInput("item_code", "Code", 1),
							new ColumnInput("item_name", "Item", 2), new ColumnInput("balance", "Balance", 3),
							new ColumnInput("reorder_level", "Reorder level", 4))),
			new StandardReportDefinition("Purchase spend by supplier",
					"Date-range purchase spend and status breakdown by supplier and item.",
					"SELECT supplier_name, item_name, status, SUM(quantity_ordered) AS total_quantity, SUM(line_amount) AS total_spend "
							+ "FROM reporting_purchase_orders WHERE expected_date BETWEEN :fromDate AND :toDate "
							+ "GROUP BY supplier_name, item_name, status ORDER BY total_spend DESC",
					List.of(new ParameterInput("fromDate", ReportParameterType.DATE, "From date", 0),
							new ParameterInput("toDate", ReportParameterType.DATE, "To date", 1)),
					List.of(new ColumnInput("supplier_name", "Supplier", 0), new ColumnInput("item_name", "Item", 1),
							new ColumnInput("status", "Status", 2), new ColumnInput("total_quantity", "Quantity", 3),
							new ColumnInput("total_spend", "Spend", 4))));

	/** Creates and publishes whichever STANDARD_REPORTS don't already exist by name for this org - safe to call repeatedly. */
	@Transactional
	public List<SavedReport> seedStandardReports() {
		List<SavedReport> created = new ArrayList<>();
		for (StandardReportDefinition definition : STANDARD_REPORTS) {
			if (savedReportRepository.existsByName(definition.name())) {
				continue;
			}
			SavedReport report = createReport(definition.name(), definition.description(), definition.sqlQuery(),
					definition.parameters(), definition.columns());
			report.publish();
			created.add(savedReportRepository.save(report));
		}
		return created;
	}

	@Transactional
	public SavedReport createReport(String name, String description, String sqlQuery, List<ParameterInput> parameters,
			List<ColumnInput> columns) {
		SqlGuard.assertSingleSelect(sqlQuery);
		SavedReport report = savedReportRepository.save(new SavedReport(name, description, sqlQuery));
		saveDefinitionChildren(report, parameters, columns);
		return report;
	}

	@Transactional
	public SavedReport updateDefinition(SavedReport report, Long actorId, String name, String description, String sqlQuery,
			List<ParameterInput> parameters, List<ColumnInput> columns) {
		assertCanEdit(report, actorId);
		SqlGuard.assertSingleSelect(sqlQuery);
		report.updateDefinition(name, description, sqlQuery);
		// deleteAllInBatch, not deleteAll: a plain deleteAll schedules entity-removal actions
		// that Hibernate's flush order runs AFTER the new rows' inserts below, so keeping an
		// existing parameter/column name across an edit hit uq_saved_report_parameters_report_name
		// / uq_saved_report_columns_report_column before the old row was actually gone.
		// deleteAllInBatch issues an immediate bulk DELETE instead, so it's committed before
		// any of the new inserts run.
		parameterRepository.deleteAllInBatch(parameterRepository.findBySavedReportIdOrderBySortOrder(report.getId()));
		columnRepository.deleteAllInBatch(columnRepository.findBySavedReportIdOrderBySortOrder(report.getId()));
		saveDefinitionChildren(report, parameters, columns);
		return savedReportRepository.save(report);
	}

	private void saveDefinitionChildren(SavedReport report, List<ParameterInput> parameters, List<ColumnInput> columns) {
		parameters.forEach(p -> parameterRepository.save(new SavedReportParameter(report, p.name(), p.type(), p.label(), p.sortOrder())));
		columns.forEach(c -> columnRepository.save(new SavedReportColumn(report, c.sourceColumn(), c.label(), c.sortOrder())));
	}

	/** Copies SQL/parameters/column metadata into a new DRAFT report owned by actorId - never mutates or re-points the source (GitHub #190). */
	@Transactional
	public SavedReport duplicate(SavedReport source, Long actorId) {
		assertCanRun(source, actorId);
		List<ParameterInput> parameters = parameterRepository.findBySavedReportIdOrderBySortOrder(source.getId()).stream()
				.map(p -> new ParameterInput(p.getName(), p.getType(), p.getLabel(), p.getSortOrder())).toList();
		List<ColumnInput> columns = columnRepository.findBySavedReportIdOrderBySortOrder(source.getId()).stream()
				.map(c -> new ColumnInput(c.getSourceColumn(), c.getLabel(), c.getSortOrder())).toList();
		return createReport(source.getName() + " (copy)", source.getDescription(), source.getSqlQuery(), parameters, columns);
	}

	public SavedReport publish(SavedReport report, Long actorId) {
		assertCanEdit(report, actorId);
		report.publish();
		return savedReportRepository.save(report);
	}

	public SavedReport archive(SavedReport report, Long actorId) {
		assertCanEdit(report, actorId);
		report.archive();
		return savedReportRepository.save(report);
	}

	public SavedReportShare share(SavedReport report, Long actorId, SharePrincipalType principalType, Long principalId, boolean canRun,
			boolean canEdit) {
		assertCanEdit(report, actorId);
		return shareRepository.save(new SavedReportShare(report, principalType, principalId, canRun, canEdit));
	}

	public List<SavedReport> listVisibleTo(Long actorId) {
		if (hasReportManage(actorId)) {
			return savedReportRepository.findAll();
		}
		List<Long> roleIds = activeRoleIdsFor(actorId);
		List<SavedReportShare> userShares = shareRepository.findByPrincipalTypeAndPrincipalId(SharePrincipalType.USER, actorId);
		List<SavedReportShare> roleShares = roleIds.isEmpty() ? List.of()
				: shareRepository.findByPrincipalTypeAndPrincipalIdIn(SharePrincipalType.ROLE, roleIds);

		Set<SavedReport> visible = new LinkedHashSet<>(savedReportRepository.findByCreatedBy(actorId));
		Stream.concat(userShares.stream(), roleShares.stream()).filter(SavedReportShare::isCanRun)
				.forEach(s -> visible.add(s.getSavedReport()));
		return new ArrayList<>(visible);
	}

	public void assertCanEdit(SavedReport report, Long actorId) {
		if (!hasAccess(report, actorId, true)) {
			throw new AuthorizationDeniedException("No edit access to report " + report.getId());
		}
	}

	public void assertCanRun(SavedReport report, Long actorId) {
		if (!hasAccess(report, actorId, false)) {
			throw new AuthorizationDeniedException("No run access to report " + report.getId());
		}
	}

	private boolean hasAccess(SavedReport report, Long actorId, boolean requireEdit) {
		if (report.getCreatedBy().equals(actorId) || hasReportManage(actorId)) {
			return true;
		}
		List<Long> roleIds = activeRoleIdsFor(actorId);
		return shareRepository.findBySavedReportId(report.getId()).stream()
				.filter(s -> requireEdit ? s.isCanEdit() : s.isCanRun())
				.anyMatch(s -> matchesPrincipal(s, actorId, roleIds));
	}

	private boolean matchesPrincipal(SavedReportShare share, Long actorId, List<Long> roleIds) {
		return switch (share.getPrincipalType()) {
			case USER -> share.getPrincipalId().equals(actorId);
			case ROLE -> roleIds.contains(share.getPrincipalId());
		};
	}

	private List<Long> activeRoleIdsFor(Long actorId) {
		return membershipRepository.findByUserId(actorId).stream()
				.filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
				.map(m -> m.getRole().getId())
				.toList();
	}

	private boolean hasReportManage(Long actorId) {
		if (membershipRepository.existsByUserIdAndStatusAndOwner(actorId, MembershipStatus.ACTIVE, true)) {
			return true;
		}
		return membershipRepository.findActivePermissionCodesForUser(actorId).contains("REPORT_MANAGE");
	}
}
