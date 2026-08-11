package com.operion.library.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.library.BorrowRecord;
import com.operion.library.BorrowRecordRepository;
import com.operion.library.Fine;
import com.operion.library.FineReason;
import com.operion.library.FineRepository;
import com.operion.library.LibraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library/fines")
@RequirePermission("LIBRARY_VIEW")
public class FineController {

	private final LibraryService libraryService;
	private final FineRepository fineRepository;
	private final BorrowRecordRepository borrowRecordRepository;

	public FineController(LibraryService libraryService, FineRepository fineRepository, BorrowRecordRepository borrowRecordRepository) {
		this.libraryService = libraryService;
		this.fineRepository = fineRepository;
		this.borrowRecordRepository = borrowRecordRepository;
	}

	@PostMapping
	@RequirePermission("LIBRARY_FINE_MANAGE")
	public FineResponse raise(@RequestBody RaiseFineRequest request) {
		BorrowRecord borrowRecord = borrowRecordRepository.findById(request.borrowRecordId())
				.orElseThrow(() -> new IllegalArgumentException("No borrow record with id " + request.borrowRecordId()));
		Fine fine = libraryService.raiseFine(borrowRecord, request.amount(), FineReason.valueOf(request.reason()));
		return FineResponse.from(fine);
	}

	@GetMapping
	public List<FineResponse> byBorrowRecord(@RequestParam Long borrowRecordId) {
		return fineRepository.findByBorrowRecordId(borrowRecordId).stream().map(FineResponse::from).toList();
	}

	@PostMapping("/{id}/pay")
	@RequirePermission("LIBRARY_FINE_MANAGE")
	public FineResponse pay(@PathVariable Long id, @RequestBody PayFineRequest request) {
		return FineResponse.from(libraryService.payFine(findFine(id), request.paidDate()));
	}

	@PostMapping("/{id}/waive")
	@RequirePermission("LIBRARY_FINE_MANAGE")
	public FineResponse waive(@PathVariable Long id, @RequestBody WaiveFineRequest request) {
		return FineResponse.from(libraryService.waiveFine(findFine(id), request.waivedBy(), request.waivedReason()));
	}

	private Fine findFine(Long id) {
		return fineRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No fine with id " + id));
	}
}
