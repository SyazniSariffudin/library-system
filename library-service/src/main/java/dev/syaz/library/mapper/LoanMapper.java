package dev.syaz.library.mapper;

import dev.syaz.library.configuration.MapperConfiguration;
import dev.syaz.library.dto.response.LoanResponse;
import dev.syaz.library.entity.Loan;
import dev.syaz.library.enums.LoanStatus;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;

@Mapper(config = MapperConfiguration.class)
public interface LoanMapper {
    @Mapping(target = "borrowerId", source = "borrower.id")
    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "status", source = "returnedAt", qualifiedByName = "loanStatus")
    @Mapping(target = "overdue", source = "loan", qualifiedByName = "overdue")
    LoanResponse toResponse(Loan loan, @Context Instant now);

    @Named("loanStatus")
    default LoanStatus toLoanStatus(Instant returnedAt) {
        return returnedAt == null ? LoanStatus.BORROWED : LoanStatus.RETURNED;
    }

    @Named("overdue")
    default boolean isOverdue(Loan loan, @Context Instant now) {
        Instant dueAt = loan.getDueAt();
        return loan.getReturnedAt() == null && dueAt != null && dueAt.isBefore(now);
    }
}
