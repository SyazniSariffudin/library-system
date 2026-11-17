package dev.syaz.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "loans")
public class Loan extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "borrowed_at", nullable = false)
    private Instant borrowedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Builder
    public Loan(Borrower borrower, Book book, Instant borrowedAt, Instant dueAt) {
        this.borrower = borrower;
        this.book = book;
        this.borrowedAt = borrowedAt.truncatedTo(ChronoUnit.MICROS);
        this.dueAt = dueAt.truncatedTo(ChronoUnit.MICROS);
    }

    public void returnAt(Instant instant) {
        if (returnedAt != null) {
            return;
        }

        Instant returnTime = instant.truncatedTo(ChronoUnit.MICROS);
        // A clock adjustment must never make a loan appear returned before it was borrowed.
        returnedAt = returnTime.isBefore(borrowedAt) ? borrowedAt : returnTime;
    }
}
