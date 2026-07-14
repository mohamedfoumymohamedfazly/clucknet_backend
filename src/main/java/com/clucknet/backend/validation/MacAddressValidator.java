package com.clucknet.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class MacAddressValidator implements ConstraintValidator<MacAddress, String> {

    // Regex supporting standard 6-octet MAC addresses separated by colons or hyphens (e.g. 00:1A:2B:3C:4D:5E or 00-1A-2B-3C-4D-5E)
    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return MAC_PATTERN.matcher(value).matches();
    }
}
