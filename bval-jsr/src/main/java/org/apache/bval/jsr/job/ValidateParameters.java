/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.bval.jsr.job;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.List;

import javax.validation.ParameterNameProvider;
import javax.validation.metadata.CrossParameterDescriptor;
import javax.validation.metadata.ExecutableDescriptor;
import javax.validation.metadata.ParameterDescriptor;

import org.apache.bval.jsr.ApacheFactoryContext;
import org.apache.bval.jsr.ConstraintViolationImpl;
import org.apache.bval.jsr.GraphContext;
import org.apache.bval.jsr.descriptor.ConstraintD;
import org.apache.bval.jsr.util.NodeImpl;
import org.apache.bval.jsr.util.PathImpl;
import org.apache.bval.util.Lazy;
import org.apache.bval.util.Validate;

public abstract class ValidateParameters<E extends Executable, T, J extends ValidateParameters<E, T, J>>
    extends ValidationJob<T, J> {

    public static class ForMethod<T> extends ValidateParameters<Method, T, ForMethod<T>> {

        private final T object;

        ForMethod(ApacheFactoryContext validatorContext, T object, Method executable, Object[] parameterValues,
            Class<?>[] groups) {
            super(validatorContext, object, executable, parameterValues, groups);
            this.object = Validate.notNull(object, "object");
        }

        @Override
        protected ExecutableDescriptor describe() {
            return validatorContext.getDescriptorManager().getBeanDescriptor(object.getClass())
                .getConstraintsForMethod(executable.getName(), executable.getParameterTypes());
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<T> getRootBeanClass() {
            return (Class<T>) object.getClass();
        }

        @Override
        protected List<String> getParameterNames(ParameterNameProvider parameterNameProvider) {
            return parameterNameProvider.getParameterNames(executable);
        }

        @Override
        protected T getRootBean() {
            return object;
        }
    }

    public static class ForConstructor<T> extends ValidateParameters<Constructor<? extends T>, T, ForConstructor<T>> {

        ForConstructor(ApacheFactoryContext validatorContext, Constructor<? extends T> executable,
            Object[] parameterValues, Class<?>[] groups) {
            super(validatorContext, null, executable, parameterValues, groups);
        }

        @Override
        protected ExecutableDescriptor describe() {
            return validatorContext.getDescriptorManager().getBeanDescriptor(executable.getDeclaringClass())
                .getConstraintsForConstructor(executable.getParameterTypes());
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<T> getRootBeanClass() {
            return (Class<T>) executable.getDeclaringClass();
        }

        @Override
        protected List<String> getParameterNames(ParameterNameProvider parameterNameProvider) {
            return parameterNameProvider.getParameterNames(executable);
        }

        @Override
        protected T getRootBean() {
            return null;
        }
    }

    class ParametersFrame extends Frame<CrossParameterDescriptor> {
        private final ExecutableDescriptor executableDescriptor;

        protected ParametersFrame(ExecutableDescriptor executableDescriptor, GraphContext context) {
            super(executableDescriptor.getCrossParameterDescriptor(), context);
            this.executableDescriptor = executableDescriptor;
        }

        @Override
        void recurse(Class<?> group) {
            executableDescriptor.getParameterDescriptors().stream()
                .map(pd -> new SproutFrame<ParameterDescriptor>(pd, parameter(pd.getIndex())))
                .forEach(f -> f.visit(group));
        }
    }

    protected final E executable;
    protected final Lazy<List<String>> parameterNames =
        new Lazy<>(() -> getParameterNames(validatorContext.getParameterNameProvider()));

    private final Object[] parameterValues;

    ValidateParameters(ApacheFactoryContext validatorContext, T object, E executable, Object[] parameterValues,
        Class<?>[] groups) {
        super(validatorContext, groups);
        this.executable = Validate.notNull(executable, "executable");
        this.parameterValues = Validate.notNull(parameterValues, "parameterValues").clone();
    }

    @Override
    protected J.Frame<?> initBaseFrame() {
        final PathImpl cp = PathImpl.create();
        cp.addNode(new NodeImpl.CrossParameterNodeImpl());
        return new ParametersFrame(describe(), new GraphContext(validatorContext, cp, parameterValues));
    }

    protected abstract ExecutableDescriptor describe();

    protected abstract List<String> getParameterNames(ParameterNameProvider parameterNameProvider);

    protected abstract T getRootBean();

    @Override
    ConstraintViolationImpl<T> createViolation(String messageTemplate, ConstraintValidatorContextImpl<T> context,
        Object leafBean) {

        final String message = validatorContext.getMessageInterpolator().interpolate(messageTemplate, context);

        return new ConstraintViolationImpl<T>(messageTemplate, message, getRootBean(), leafBean,
            context.getFrame().context.getPath(), context.getFrame().context.getValue(),
            context.getConstraintDescriptor(), getRootBeanClass(),
            context.getConstraintDescriptor().unwrap(ConstraintD.class).getDeclaredOn(), null, parameterValues);
    }

    private GraphContext parameter(int i) {
        final PathImpl path = PathImpl.create();
        path.addNode(new NodeImpl.ParameterNodeImpl(parameterNames.get().get(i), i));
        return new GraphContext(validatorContext, path, parameterValues[i]);
    }
}
