package org.apache.bval.context;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.validation.ClockProvider;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Path;
import javax.validation.ValidationException;
import javax.validation.metadata.CascadableDescriptor;
import javax.validation.metadata.ContainerDescriptor;
import javax.validation.metadata.ElementDescriptor;

import org.apache.bval.jsr.ApacheValidatorFactory;
import org.apache.bval.jsr.descriptor.BeanD;
import org.apache.bval.jsr.util.Exceptions;
import org.apache.bval.jsr.util.PathImpl;
import org.apache.bval.model.ValidationListener;
import org.apache.bval.util.Validate;

/**
 * Context for a single validation call over one object or graph.
 */
public class ValidationContext {

    abstract class ElementContext<D> {

        class ValidatorContext implements ConstraintValidatorContext {
            private final List<ValidationListener.Error> errorMessages = new LinkedList<>();
            private boolean disableDefaultConstraintViolation;

            /**
             * Get the queued error messages.
             * 
             * @return List
             */
            List<ValidationListener.Error> getErrorMessages() {
                if (disableDefaultConstraintViolation && errorMessages.isEmpty()) {
                    throw new ValidationException(
                        "At least one custom message must be created if the default error message gets disabled.");
                }

                List<ValidationListener.Error> returnedErrorMessages = new ArrayList<>(errorMessages);
                if (!disableDefaultConstraintViolation) {
                    returnedErrorMessages
                        .add(new ValidationListener.Error(getDefaultConstraintMessageTemplate(), getPath(), null));
                }
                return returnedErrorMessages;
            }

            @Override
            public void disableDefaultConstraintViolation() {
                this.disableDefaultConstraintViolation = true;
            }

            @Override
            public String getDefaultConstraintMessageTemplate() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ConstraintViolationBuilder buildConstraintViolationWithTemplate(String messageTemplate) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public final ClockProvider getClockProvider() {
                return validatorFactory.getClockProvider();
            }

            @Override
            public final <T> T unwrap(Class<T> type) {
                Exceptions.raiseUnless(type.isInstance(this), ValidationException::new, "Type %s not supported", type);

                return type.cast(this);
            }
        }

        protected final D descriptor;

        ElementContext(D descriptor) {
            super();
            this.descriptor = Validate.notNull(descriptor, "descriptor");
        }

        protected abstract PathImpl getPath();
    }

    private class BeanContext extends ElementContext<BeanD> {
        BeanContext(BeanD descriptor) {
            super(descriptor);
        }

        @Override
        protected PathImpl getPath() {
            return PathImpl.create();
        }
    }

    private class CascadableContainerContext<D extends ElementDescriptor & CascadableDescriptor & ContainerDescriptor>
        extends ElementContext<D> {

        private final PathImpl path;

        CascadableContainerContext(D descriptor, Path path) {
            super(descriptor);
            this.path = PathImpl.copy(Validate.notNull(path, "path"));
        }

        @Override
        protected PathImpl getPath() {
            // careful, live
            return path;
        }
    }

    private final ApacheValidatorFactory validatorFactory;

    public ValidationContext(ApacheValidatorFactory validatorFactory) {
        super();
        this.validatorFactory = Validate.notNull(validatorFactory, "validatorFactory");
    }
}
