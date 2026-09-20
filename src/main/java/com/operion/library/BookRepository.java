package com.operion.library;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {

	List<Book> findByStatus(BookStatus status);

	long countByStatus(BookStatus status);

	Optional<Book> findByIsbn(String isbn);
}
