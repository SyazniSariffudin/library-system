package dev.syaz.library.mapper;

import dev.syaz.library.configuration.MapperConfiguration;
import dev.syaz.library.dto.response.BorrowerResponse;
import dev.syaz.library.entity.Borrower;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public interface BorrowerMapper {
    BorrowerResponse toResponse(Borrower borrower);
}
