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
package org.apache.bval.jsr.descriptor;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.validation.ConstraintTarget;
import javax.validation.ConstraintValidator;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.UnexpectedTypeException;
import javax.validation.ValidationException;
import javax.validation.metadata.ConstraintDescriptor;
import javax.validation.metadata.Scope;
import javax.validation.metadata.ValidateUnwrappedValue;
import javax.validation.valueextraction.Unwrapping.Skip;
import javax.validation.valueextraction.Unwrapping.Unwrap;

import org.apache.bval.jsr.ApacheValidatorFactory;
import org.apache.bval.jsr.ConstraintAnnotationAttributes;
import org.apache.bval.jsr.ConstraintAnnotationAttributes.Worker;
import org.apache.bval.jsr.metadata.Metas;
import org.apache.bval.jsr.util.AnnotationsManager;
import org.apache.bval.jsr.util.Exceptions;
import org.apache.bval.jsr.util.ToUnmodifiable;
import org.apache.bval.util.Lazy;
import org.apache.bval.util.Validate;
import org.apache.bval.util.reflection.TypeUtils;

public class ConstraintD<A extends Annotation> implements ConstraintDescriptor<A> {
    private static <T> Set<T> set(Supplier<T[]> array) {
        return Stream.of(array.get()).collect(ToUnmodifiable.set());
    }

    private final A annotation;
    private final Scope scope;
    private final Metas<?> meta;
    private final Class<?> validatedType;

    private final Lazy<Set<Class<?>>> groups = new Lazy<>(() -> set(() -> read(ConstraintAnnotationAttributes.GROUPS)));

    private final Lazy<Set<Class<? extends Payload>>> payload =
        new Lazy<>(() -> set(() -> read(ConstraintAnnotationAttributes.PAYLOAD)));

    private final Lazy<Boolean> reportAsSingle =
        new Lazy<>(() -> getAnnotation().annotationType().isAnnotationPresent(ReportAsSingleViolation.class));

    private final Lazy<ValidateUnwrappedValue> valueUnwrapping = new Lazy<>(this::computeValidateUnwrappedValue);

    private final Lazy<Map<String, Object>> attributes;
    private final Lazy<Set<ConstraintDescriptor<?>>> composingConstraints;
    private final Lazy<List<Class<? extends ConstraintValidator<A, ?>>>> constraintValidatorClasses;
    private final Lazy<Class<? extends ConstraintValidator<A, ?>>> constraintValidatorClass;

    public ConstraintD(A annotation, Scope scope, Metas<?> meta, ApacheValidatorFactory validatorFactory) {
        this.annotation = Validate.notNull(annotation, "annotation");
        this.scope = Validate.notNull(scope, "scope");

        this.meta = Validate.notNull(meta, "meta");
        validatedType = TypeUtils.getRawType(meta.getType(), null);
        Exceptions.raiseIf(validatedType == null, UnexpectedTypeException::new,
            "Could not calculate validated type from %s", meta.getType());

        attributes = new Lazy<>(() -> AnnotationsManager.readAttributes(annotation));

        // retain no references to the validatorFactory; only wrap it in lazy suppliers
        Validate.notNull(validatorFactory, "validatorFactory");
        composingConstraints = new Lazy<>(computeComposingConstraints(validatorFactory));
        constraintValidatorClasses = new Lazy<>(computeConstraintValidatorClasses(validatorFactory));

        @SuppressWarnings("unchecked")
        final Supplier<Class<? extends ConstraintValidator<A, ?>>> computeConstraintValidatorClass =
            new ComputeConstraintValidatorClass<>(validatorFactory, meta.getValidationTarget(),
                (Class<A>) annotation.annotationType(), validatedType);

        constraintValidatorClass = new Lazy<>(computeConstraintValidatorClass);
    }

    @Override
    public A getAnnotation() {
        return annotation;
    }

    @Override
    public Set<Class<?>> getGroups() {
        return groups.get();
    }

    @Override
    public Set<Class<? extends Payload>> getPayload() {
        return payload.get();
    }

    @Override
    public List<Class<? extends ConstraintValidator<A, ?>>> getConstraintValidatorClasses() {
        return constraintValidatorClasses.get();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes.get();
    }

    @Override
    public Set<ConstraintDescriptor<?>> getComposingConstraints() {
        return composingConstraints.get();
    }

    @Override
    public boolean isReportAsSingleViolation() {
        return reportAsSingle.get().booleanValue();
    }

    @Override
    public String getMessageTemplate() {
        return read(ConstraintAnnotationAttributes.MESSAGE);
    }

    @Override
    public ConstraintTarget getValidationAppliesTo() {
        return read(ConstraintAnnotationAttributes.VALIDATION_APPLIES_TO);
    }

    @Override
    public ValidateUnwrappedValue getValueUnwrapping() {
        return valueUnwrapping.get();
    }

    @Override
    public <U> U unwrap(Class<U> type) throws ValidationException {
        try {
            return type.cast(this);
        } catch (ClassCastException e) {
            throw new ValidationException(e);
        }
    }

    public Scope getScope() {
        return scope;
    }

    public ElementType getDeclaredOn() {
        return meta.getElementType();
    }

    public Class<?> getValidatedType() {
        return validatedType;
    }

    public Class<? extends ConstraintValidator<A, ?>> getConstraintValidatorClass() {
        return constraintValidatorClass.get();
    }

    private <T> T read(ConstraintAnnotationAttributes attr) {
        return Optional.of(annotation.annotationType()).map(attr::analyze).filter(Worker::isValid)
            .map(w -> w.<T> read(annotation)).orElse(null);
    }

    private Supplier<Set<ConstraintDescriptor<?>>> computeComposingConstraints(
        ApacheValidatorFactory validatorFactory) {
        return () -> Stream.of(validatorFactory.getAnnotationsManager().getComposingConstraints(annotation))
            .map(c -> new ConstraintD<>(c, scope, meta, validatorFactory)).collect(ToUnmodifiable.set());
    }

    @SuppressWarnings("unchecked")
    private Supplier<List<Class<? extends ConstraintValidator<A, ?>>>> computeConstraintValidatorClasses(
        ApacheValidatorFactory validatorFactory) {
        return () -> validatorFactory.getConstraintsCache()
            .getConstraintValidatorClasses((Class<A>) annotation.annotationType());
    }

    private ValidateUnwrappedValue computeValidateUnwrappedValue() {
        final Set<Class<? extends Payload>> p = getPayload();
        final boolean unwrap = p.contains(Unwrap.class);
        final boolean skip = p.contains(Skip.class);
        if (unwrap) {
            Validate.validState(!skip, "Cannot specify both %s and %s", Unwrap.class.getSimpleName(),
                Skip.class.getSimpleName());
            return ValidateUnwrappedValue.UNWRAP;
        }
        return skip ? ValidateUnwrappedValue.SKIP : ValidateUnwrappedValue.DEFAULT;
    }
}
