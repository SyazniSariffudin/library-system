package dev.syaz.library.service;

import dev.syaz.library.dto.request.RegisterBorrowerRequest;
import dev.syaz.library.dto.response.BorrowerResponse;
import dev.syaz.library.entity.Borrower;
import dev.syaz.library.exception.BorrowerEmailAlreadyRegisteredException;
import dev.syaz.library.mapper.BorrowerMapper;
import dev.syaz.library.repository.BorrowerRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;

import static dev.syaz.library.support.TestDatabaseErrors.constraintViolation;
import static dev.syaz.library.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BorrowerServiceTest {
    private final BorrowerRepository borrowers = mock(BorrowerRepository.class);
    private final BorrowerService service = new BorrowerService(borrowers, Mappers.getMapper(BorrowerMapper.class));
    private final RegisterBorrowerRequest request = new RegisterBorrowerRequest("Reader", "reader@example.com");

    @Test
    void registersBorrower() {
        when(borrowers.saveAndFlush(any(Borrower.class)))
                .thenAnswer(call -> withId(call.getArgument(0, Borrower.class), 1L));

        assertThat(service.register(request)).isEqualTo(new BorrowerResponse(1L, "Reader", "reader@example.com"));
    }

    @Test
    void translatesDuplicateEmailToBusinessException() {
        DataIntegrityViolationException failure = constraintViolation("uq_borrower_email");
        when(borrowers.saveAndFlush(any(Borrower.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BorrowerEmailAlreadyRegisteredException.class)
                .hasCause(failure);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"uq_active_loan_per_book", "unknown_constraint"})
    void doesNotMisclassifyOtherDatabaseConstraints(String constraintName) {
        DataIntegrityViolationException failure = constraintViolation(constraintName);
        when(borrowers.saveAndFlush(any(Borrower.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.register(request)).isSameAs(failure);
    }

    @Test
    void doesNotClassifyDatabaseFailuresByMessage() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("uq_borrower_email");
        when(borrowers.saveAndFlush(any(Borrower.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.register(request)).isSameAs(failure);
    }
}
