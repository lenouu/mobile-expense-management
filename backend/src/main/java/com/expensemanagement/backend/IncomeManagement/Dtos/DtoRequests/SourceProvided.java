package com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An income needs a source, given either as {@code sourceId} (one of the user's income sources) or
 * as {@code source}, the plain name clients sent before income sources existed.
 *
 * <p>The violation is reported on the {@code source} field, so a client gets
 * {@code fields.source} exactly as it did when {@code source} was a required text field.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SourceProvided.Validator.class)
public @interface SourceProvided
{
    String message() default "Source is required";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SourceProvided, IncomeRequests.SaveIncome>
    {
        @Override
        public boolean isValid(IncomeRequests.SaveIncome request, ConstraintValidatorContext context)
        {
            if (request == null || request.getSourceId() != null
                    || (request.getSource() != null && !request.getSource().isBlank()))
            {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("source")
                    .addConstraintViolation();
            return false;
        }
    }
}
