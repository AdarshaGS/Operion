package com.operion.modules;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catalogue of the features the platform owner can switch on or off per organisation
 * (GitHub #198). A request belongs to a module by the package of the controller that
 * serves it, so a new controller under an existing package is covered with no extra
 * wiring - and ModuleCoverageTest fails if a package with controllers is neither claimed
 * here nor listed as core. A handler can override its package with {@link RequireModule}
 * (the portal controllers mix several modules in one class).
 *
 * Core areas (organisation, identity, authorization, audit, settings, platform, ...) are
 * deliberately not here: switching them off would lock everyone out.
 */
public enum AppModule {

	DASHBOARD("Dashboard", "com.operion.dashboard"),
	STUDENTS("Students & Admissions", "com.operion.student", "com.operion.parent"),
	ACADEMICS("Academics", "com.operion.academic", "com.operion.timetable"),
	ATTENDANCE("Attendance", "com.operion.attendance"),
	EXAMINATIONS("Examinations", "com.operion.examination"),
	LIBRARY("Library", "com.operion.library"),
	LEARNING("Learning", "com.operion.learning"),
	FEES("Fees", "com.operion.finance"),
	TRANSPORT("Transport", "com.operion.transport"),
	COMMUNICATION("Communication", "com.operion.communication"),
	MESSAGING("Messaging", "com.operion.messaging"),
	INVENTORY("Inventory", "com.operion.inventory"),
	PURCHASE("Purchase", "com.operion.purchase"),
	SALES("Sales", "com.operion.sales"),
	REPORTS("Reports", "com.operion.reporting"),
	HR("HR", "com.operion.hr"),
	PAYROLL("Payroll", "com.operion.payroll"),
	COMPLIANCE("Regulatory & Privacy", "com.operion.regulatory", "com.operion.privacy");

	private final String label;
	private final String[] packages;

	AppModule(String label, String... packages) {
		this.label = label;
		this.packages = packages;
	}

	public String label() {
		return label;
	}

	/** Off until the platform owner (or the Starter Pack) switches it on - an organisation with no setting row for it gets it disabled. */
	public boolean defaultEnabled() {
		return switch (this) {
			case TRANSPORT, COMMUNICATION, MESSAGING -> false;
			default -> true;
		};
	}

	/** The module a permission's catalogue group (permissions.module, e.g. "transport") belongs to; empty for core groups that are never gated. */
	public static Optional<AppModule> forPermissionGroup(String group) {
		if (group == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(switch (group.toLowerCase()) {
			case "student", "parent" -> STUDENTS;
			case "academic" -> ACADEMICS;
			case "attendance" -> ATTENDANCE;
			case "examination" -> EXAMINATIONS;
			case "library" -> LIBRARY;
			case "learning" -> LEARNING;
			case "fees", "finance" -> FEES;
			case "transport" -> TRANSPORT;
			case "communication" -> COMMUNICATION;
			case "messaging" -> MESSAGING;
			case "inventory" -> INVENTORY;
			case "purchase" -> PURCHASE;
			case "sales" -> SALES;
			case "reporting" -> REPORTS;
			case "hr" -> HR;
			case "payroll" -> PAYROLL;
			default -> null;
		});
	}

	/** Frontend path prefixes this module owns - the web app's route-to-module map must match (see ModuleRoutesTest). */
	public List<String> routes() {
		return switch (this) {
			case DASHBOARD -> List.of("/dashboard");
			case STUDENTS -> List.of("/students", "/admissions");
			case ACADEMICS -> List.of("/academics");
			case ATTENDANCE -> List.of("/attendance");
			case EXAMINATIONS -> List.of("/examinations");
			case LIBRARY -> List.of("/library");
			case LEARNING -> List.of("/learning");
			case FEES -> List.of("/fees");
			case TRANSPORT -> List.of("/transport");
			case COMMUNICATION -> List.of("/communication");
			case MESSAGING -> List.of("/messaging");
			case INVENTORY -> List.of("/inventory");
			case PURCHASE -> List.of("/purchase");
			case SALES -> List.of("/sales");
			case REPORTS -> List.of("/reports");
			case HR -> List.of("/hr");
			case PAYROLL -> List.of("/payroll");
			case COMPLIANCE -> List.of();
		};
	}

	/** Lenient lookup for codes stored as plain text elsewhere: an unknown code is just not a module. */
	public static Optional<AppModule> fromCode(String code) {
		return Arrays.stream(values()).filter(module -> module.name().equals(code)).findFirst();
	}

	/** Modules this one cannot work without - switching this one on pulls these on with it. */
	public Set<AppModule> requires() {
		return switch (this) {
			case ATTENDANCE, EXAMINATIONS -> EnumSet.of(STUDENTS, ACADEMICS);
			case LEARNING -> EnumSet.of(STUDENTS, ACADEMICS, EXAMINATIONS);
			case FEES, LIBRARY, COMPLIANCE -> EnumSet.of(STUDENTS);
			case TRANSPORT -> EnumSet.of(STUDENTS, FEES);
			case PURCHASE, SALES -> EnumSet.of(INVENTORY);
			case PAYROLL -> EnumSet.of(HR);
			default -> EnumSet.noneOf(AppModule.class);
		};
	}

	/** The wanted modules plus everything they transitively require. */
	public static Set<AppModule> withDependencies(Set<AppModule> wanted) {
		Set<AppModule> closure = EnumSet.noneOf(AppModule.class);
		Deque<AppModule> pending = new ArrayDeque<>(wanted);
		while (!pending.isEmpty()) {
			AppModule next = pending.pop();
			if (closure.add(next)) {
				pending.addAll(next.requires());
			}
		}
		return closure;
	}

	/** Modules that depend on this one, directly or not - what would stop working if it were switched off. */
	public Set<AppModule> dependents() {
		return Arrays.stream(values()).filter(other -> other != this && withDependencies(EnumSet.of(other)).contains(this))
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(AppModule.class)));
	}

	/** Longest matching package prefix wins; empty means the class is core (never gated). */
	public static Optional<AppModule> forPackage(String packageName) {
		return Arrays.stream(values())
				.filter(module -> Arrays.stream(module.packages).anyMatch(prefix -> matches(packageName, prefix)))
				.max(Comparator.comparingInt(module -> longestMatch(module, packageName)));
	}

	private static int longestMatch(AppModule module, String packageName) {
		return Arrays.stream(module.packages).filter(prefix -> matches(packageName, prefix)).mapToInt(String::length).max().orElse(0);
	}

	private static boolean matches(String packageName, String prefix) {
		return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
	}
}
