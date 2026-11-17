package dev.syaz.library.mapper;

import dev.syaz.library.configuration.MapperConfiguration;
import dev.syaz.library.dto.response.BookResponse;
import dev.syaz.library.entity.Book;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface BookMapper {
    @Mapping(target = "id", source = "book.id")
    @Mapping(target = "isbn", source = "book.edition.isbn")
    @Mapping(target = "title", source = "book.edition.title")
    @Mapping(target = "author", source = "book.edition.author")
    @Mapping(target = "available", source = "available")
    BookResponse toResponse(Book book, boolean available);
}
