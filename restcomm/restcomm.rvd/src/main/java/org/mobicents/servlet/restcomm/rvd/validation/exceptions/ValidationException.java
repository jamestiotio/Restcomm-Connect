package org.mobicents.servlet.restcomm.rvd.validation.exceptions;

import org.mobicents.servlet.restcomm.rvd.exceptions.RvdException;
import org.mobicents.servlet.restcomm.rvd.validation.ValidationResult;

public class ValidationException extends RvdException {
    private ValidationResult validationResult;

    public ValidationException(ValidationResult validationResult) {
        this.validationResult = validationResult;
    }

    public ValidationException(String message, Throwable cause,ValidationResult validationResult) {
        super(message, cause);
        this.validationResult = validationResult;
    }

    public ValidationException(String message,ValidationResult validationResult) {
        super(message);
        this.validationResult = validationResult;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

}
