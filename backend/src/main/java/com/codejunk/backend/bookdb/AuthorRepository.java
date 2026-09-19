package com.codejunk.backend.bookdb;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    /**
     * Holds the row for the rest of the transaction, so a concurrent edit waits
     * and then re-reads rather than checking a version it is about to overwrite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Author a where a.id = :id")
    Optional<Author> findByIdForUpdate(@Param("id") Long id);
}
