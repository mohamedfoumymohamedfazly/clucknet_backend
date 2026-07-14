package com.clucknet.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MacAddressValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MacAddress {
    
    String message() default "Invalid hardware address. Must be a valid MAC address (e.g. 5E:FF:56:A2:AF:15).";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
