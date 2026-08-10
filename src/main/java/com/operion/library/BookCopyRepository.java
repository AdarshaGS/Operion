package com.operion.library;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

	List<BookCopy> findByBookId(Long bookId);

	List<BookCopy> findByCampusId(Long campusId);
}
