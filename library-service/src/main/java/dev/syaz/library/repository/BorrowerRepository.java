package dev.syaz.library.repository;

import dev.syaz.library.entity.Borrower;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {
}
