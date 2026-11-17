package dev.syaz.library.service;

import dev.syaz.library.dto.request.RegisterBorrowerRequest;
import dev.syaz.library.dto.response.BorrowerResponse;
import dev.syaz.library.entity.Borrower;
import dev.syaz.library.exception.BorrowerEmailAlreadyRegisteredException;
import dev.syaz.library.mapper.BorrowerMapper;
import dev.syaz.library.repository.BorrowerRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.syaz.library.repository.DatabaseConstraints.isViolationOf;

@Service
@RequiredArgsConstructor
public class BorrowerService {
    private final BorrowerRepository borrowerRepository;
    private final BorrowerMapper borrowerMapper;

    @Transactional
    public BorrowerResponse register(RegisterBorrowerRequest request) {
        Borrower borrower = Borrower.builder()
                .name(request.name())
                .email(request.email())
                .build();
        Borrower savedBorrower;
        try {
            savedBorrower = borrowerRepository.saveAndFlush(borrower);
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "uq_borrower_email")) {
                throw new BorrowerEmailAlreadyRegisteredException(exception);
            }
            throw exception;
        }
        return borrowerMapper.toResponse(savedBorrower);
    }
}
