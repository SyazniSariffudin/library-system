package dev.syaz.library.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidIsbn.Validator.class)
public @interface ValidIsbn {
    String message() default "must be a checksum-valid ISBN-10 or ISBN-13";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidIsbn, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return Isbn.isValid(value);
        }
    }
}
