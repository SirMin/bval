package org.apache.bval.jsr;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.validation.Payload;
import javax.validation.metadata.ConstraintDescriptor;
import javax.validation.metadata.ValidateUnwrappedValue;
import javax.validation.valueextraction.Unwrapping;

abstract class AbstractConstraintDescriptor<T extends Annotation> implements ConstraintDescriptor<T> {

    @Override
    public ValidateUnwrappedValue getValueUnwrapping() {
        final Set<Class<? extends Payload>> payload = getPayload();
        if (payload != null) {
            if (payload.contains(Unwrapping.Unwrap.class)) {
                return ValidateUnwrappedValue.UNWRAP;
            }
            if (payload.contains(Unwrapping.Skip.class)) {
                return ValidateUnwrappedValue.SKIP;
            }
        }
        // TODO handle UnwrapByDefault extractors
        return ValidateUnwrappedValue.DEFAULT;
    }

    @Override
    public <U> U unwrap(Class<U> type) {
        throw new UnsupportedOperationException();
    }
}
