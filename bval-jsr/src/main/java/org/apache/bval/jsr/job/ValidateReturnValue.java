package org.apache.bval.jsr.job;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

import javax.validation.metadata.ExecutableDescriptor;

import org.apache.bval.jsr.ApacheFactoryContext;
import org.apache.bval.jsr.ConstraintViolationImpl;
import org.apache.bval.jsr.GraphContext;
import org.apache.bval.jsr.descriptor.ConstraintD;
import org.apache.bval.jsr.util.NodeImpl;
import org.apache.bval.jsr.util.PathImpl;
import org.apache.bval.util.Validate;

public abstract class ValidateReturnValue<E extends Executable, T, J extends ValidateReturnValue<E, T, J>>
    extends ValidationJob<T, J> {
    public static class ForMethod<T> extends ValidateReturnValue<Method, T, ForMethod<T>> {
        private final T object;

        ForMethod(ApacheFactoryContext validatorContext, T object, Method method, Object returnValue,
            Class<?>[] groups) {
            super(validatorContext, method, returnValue, groups);
            this.object = Validate.notNull(object, "object");
        }

        @Override
        protected T getRootBean() {
            return object;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<T> getRootBeanClass() {
            return (Class<T>) object.getClass();
        }

        @Override
        protected ExecutableDescriptor describe() {
            return validatorContext.getDescriptorManager().getBeanDescriptor(object.getClass())
                .getConstraintsForMethod(executable.getName(), executable.getParameterTypes());
        }
    }

    public static class ForConstructor<T> extends ValidateReturnValue<Constructor<? extends T>, T, ForConstructor<T>> {

        ForConstructor(ApacheFactoryContext validatorContext, Constructor<? extends T> ctor, Object returnValue,
            Class<?>[] groups) {
            super(validatorContext, ctor, returnValue, groups);
        }

        @Override
        protected T getRootBean() {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<T> getRootBeanClass() {
            return (Class<T>) executable.getDeclaringClass();
        }

        @Override
        protected ExecutableDescriptor describe() {
            return validatorContext.getDescriptorManager().getBeanDescriptor(executable.getDeclaringClass())
                .getConstraintsForConstructor(executable.getParameterTypes());
        }
    }

    protected final E executable;
    private final Object returnValue;

    ValidateReturnValue(ApacheFactoryContext validatorContext, E executable, Object returnValue, Class<?>[] groups) {
        super(validatorContext, groups);
        this.executable = Validate.notNull(executable, "executable");
        this.returnValue = returnValue;
    }

    @Override
    protected J.Frame<?> initBaseFrame() {
        final PathImpl path = PathImpl.create();
        path.addNode(new NodeImpl.ReturnValueNodeImpl());

        return new SproutFrame<>(describe().getReturnValueDescriptor(),
            new GraphContext(validatorContext, path, returnValue));
    }

    @Override
    ConstraintViolationImpl<T> createViolation(String messageTemplate, ConstraintValidatorContextImpl<T> context,
        Object leafBean) {

        final String message = validatorContext.getMessageInterpolator().interpolate(messageTemplate, context);

        return new ConstraintViolationImpl<>(messageTemplate, message, getRootBean(), leafBean,
            context.getFrame().context.getPath(), context.getFrame().context.getValue(),
            context.getConstraintDescriptor(), getRootBeanClass(),
            context.getConstraintDescriptor().unwrap(ConstraintD.class).getDeclaredOn(), returnValue, null);
    }

    protected abstract ExecutableDescriptor describe();

    protected abstract T getRootBean();
}
