package com.operion.inventory.api;

import com.operion.common.InvalidRequestException;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.StockIssue;
import com.operion.inventory.StockIssueRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/stock-issues")
@RequirePermission("INVENTORY_VIEW")
public class StockIssueController {

	private static void requireField(Object value, String field) {
		if (value == null) {
			throw new InvalidRequestException(field + " is required");
		}
	}

	private final InventoryService inventoryService;
	private final StockIssueRepository stockIssueRepository;
	private final ItemRepository itemRepository;
	private final CampusRepository campusRepository;
	private final DepartmentRepository departmentRepository;
	private final PersonRepository personRepository;
	private final StudentRepository studentRepository;

	public StockIssueController(InventoryService inventoryService, StockIssueRepository stockIssueRepository,
			ItemRepository itemRepository, CampusRepository campusRepository, DepartmentRepository departmentRepository,
			PersonRepository personRepository, StudentRepository studentRepository) {
		this.inventoryService = inventoryService;
		this.stockIssueRepository = stockIssueRepository;
		this.itemRepository = itemRepository;
		this.campusRepository = campusRepository;
		this.departmentRepository = departmentRepository;
		this.personRepository = personRepository;
		this.studentRepository = studentRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_STOCK_MANAGE")
	public StockIssueResponse record(@RequestBody RecordStockIssueRequest request) {
		requireField(request.itemId(), "itemId");
		requireField(request.campusId(), "campusId");
		requireField(request.issuedDate(), "issuedDate");
		Item item = findItem(request.itemId());
		Campus campus = findCampus(request.campusId());

		int recipientRefs = (request.departmentId() != null ? 1 : 0) + (request.recipientPersonId() != null ? 1 : 0)
				+ (request.recipientStudentId() != null ? 1 : 0);
		if (recipientRefs > 1) {
			throw new InvalidRequestException("A stock issue can link to a department, a staff member, or a student - not more than one");
		}
		Department department = request.departmentId() == null ? null
				: departmentRepository.findById(request.departmentId())
						.orElseThrow(() -> new IllegalArgumentException("No department with id " + request.departmentId()));
		Person recipientPerson = request.recipientPersonId() == null ? null
				: personRepository.findById(request.recipientPersonId())
						.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.recipientPersonId()));
		Student recipientStudent = request.recipientStudentId() == null ? null
				: studentRepository.findById(request.recipientStudentId())
						.orElseThrow(() -> new IllegalArgumentException("No student with id " + request.recipientStudentId()));

		StockIssue issue = inventoryService.recordIssue(item, campus, request.quantity(), request.issuedDate(), request.issuedTo(),
				request.purpose(), request.unitCost(), department, recipientPerson, recipientStudent, request.remarks());
		return StockIssueResponse.from(issue);
	}

	@GetMapping
	public List<StockIssueResponse> byItemAndCampus(@RequestParam Long itemId, @RequestParam Long campusId) {
		return stockIssueRepository.findByItemIdAndCampusId(itemId, campusId).stream().map(StockIssueResponse::from).toList();
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}
}
