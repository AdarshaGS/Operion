package com.operion.inventory.api;

import com.operion.common.EnumParam;
import com.operion.common.InvalidRequestException;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.inventory.Asset;
import com.operion.inventory.AssetRepository;
import com.operion.inventory.AssetStatus;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/assets")
@RequirePermission("INVENTORY_VIEW")
public class AssetController {

	private static void requireField(Object value, String field) {
		if (value == null) {
			throw new InvalidRequestException(field + " is required");
		}
	}

	private final InventoryService inventoryService;
	private final AssetRepository assetRepository;
	private final ItemRepository itemRepository;
	private final CampusRepository campusRepository;
	private final PersonRepository personRepository;

	public AssetController(InventoryService inventoryService, AssetRepository assetRepository, ItemRepository itemRepository,
			CampusRepository campusRepository, PersonRepository personRepository) {
		this.inventoryService = inventoryService;
		this.assetRepository = assetRepository;
		this.itemRepository = itemRepository;
		this.campusRepository = campusRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_ASSET_MANAGE")
	public AssetResponse create(@RequestBody CreateAssetRequest request) {
		requireField(request.itemId(), "itemId");
		requireField(request.campusId(), "campusId");
		Item item = findItem(request.itemId());
		Campus campus = findCampus(request.campusId());
		Asset asset = inventoryService.createAsset(item, campus, request.assetTag(), request.purchaseDate(), request.warrantyExpiryDate());
		return AssetResponse.from(asset);
	}

	@GetMapping
	public List<AssetResponse> byCampus(@RequestParam Long campusId) {
		return assetRepository.findByCampusId(campusId).stream().map(AssetResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	@RequirePermission("INVENTORY_ASSET_MANAGE")
	public AssetResponse changeStatus(@PathVariable Long id, @RequestBody ChangeAssetStatusRequest request) {
		Asset asset = findOrThrow(id);
		return AssetResponse.from(inventoryService.changeAssetStatus(asset, EnumParam.parse(AssetStatus.class, request.status())));
	}

	@PostMapping("/{id}/assign")
	@RequirePermission("INVENTORY_ASSET_MANAGE")
	public AssetResponse assign(@PathVariable Long id, @RequestBody AssignAssetRequest request) {
		Asset asset = findOrThrow(id);
		Person assignedPerson = request.assignedPersonId() == null ? null
				: personRepository.findById(request.assignedPersonId())
						.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.assignedPersonId()));
		return AssetResponse.from(inventoryService.assignAsset(asset, assignedPerson, request.assignedLocation()));
	}

	private Asset findOrThrow(Long id) {
		return assetRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No asset with id " + id));
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}
}
