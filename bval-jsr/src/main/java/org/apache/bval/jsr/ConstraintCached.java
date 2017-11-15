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
package org.apache.bval.jsr;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.validation.ConstraintValidator;
import javax.validation.constraintvalidation.SupportedValidationTarget;
import javax.validation.constraintvalidation.ValidationTarget;

import org.apache.bval.jsr.util.ToUnmodifiable;
import org.apache.bval.util.ObjectUtils;
import org.apache.bval.util.Validate;

/**
 * Description: hold the relationship annotation->validatedBy[] ConstraintValidator classes that are already parsed in a
 * cache.<br/>
 */
public class ConstraintCached {

    /**
     * Describes a {@link ConstraintValidator} implementation type.
     * 
     * @since 2.0
     */
    public static final class ConstraintValidatorInfo<T extends Annotation> {
        private static final Set<ValidationTarget> DEFAULT_VALIDATION_TARGETS =
            Collections.singleton(ValidationTarget.ANNOTATED_ELEMENT);

        private final Class<? extends ConstraintValidator<T, ?>> type;
        private Set<ValidationTarget> supportedTargets;

        ConstraintValidatorInfo(Class<? extends ConstraintValidator<T, ?>> type) {
            super();
            this.type = Validate.notNull(type);
            final SupportedValidationTarget svt = type.getAnnotation(SupportedValidationTarget.class);

            supportedTargets = svt == null ? DEFAULT_VALIDATION_TARGETS
                : Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(svt.value())));
        }

        public Class<? extends ConstraintValidator<T, ?>> getType() {
            return type;
        }

        public Set<ValidationTarget> getSupportedTargets() {
            return supportedTargets;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this
                || obj instanceof ConstraintValidatorInfo<?> && ((ConstraintValidatorInfo<?>) obj).type.equals(type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type);
        }
    }

    private final Map<Class<? extends Annotation>, Set<ConstraintValidatorInfo<?>>> constraintValidatorInfo =
        new HashMap<>();

    /**
     * Record the set of validator classes for a given constraint annotation.
     * 
     * @param annotationClass
     * @param definitionClasses
     */
    public <A extends Annotation> void putConstraintValidator(Class<A> annotationClass,
        Class<? extends ConstraintValidator<A, ?>>[] definitionClasses) {
        if (ObjectUtils.isEmpty(definitionClasses)) {
            return;
        }
        Validate.notNull(annotationClass, "annotationClass");
        Stream.of(definitionClasses).map(t -> new ConstraintValidatorInfo<>(t))
            .forEach(constraintValidatorInfo.computeIfAbsent(annotationClass, k -> new HashSet<>())::add);
    }

    /**
     * Learn whether we have cached the validator classes for the requested constraint annotation.
     * 
     * @param annotationClass
     *            to look up
     * @return boolean
     */
    public boolean containsConstraintValidator(Class<? extends Annotation> annotationClass) {
        return constraintValidatorInfo.containsKey(annotationClass);
    }

    /**
     * Get the cached validator classes for the requested constraint annotation.
     * 
     * @param constraintType
     *            to look up
     * @return array of {@link ConstraintValidator} implementation types
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public <A extends Annotation> Class<? extends ConstraintValidator<A, ?>>[] getConstraintValidators(
        Class<A> constraintType) {
        final Set<ConstraintValidatorInfo<A>> infos = infos(constraintType);
        return infos == null ? new Class[0]
            : infos.stream().map(ConstraintValidatorInfo::getType).toArray(Class[]::new);
    }

    public <A extends Annotation> List<Class<? extends ConstraintValidator<A, ?>>> getConstraintValidatorClasses(
        Class<A> constraintType) {
        final Set<ConstraintValidatorInfo<A>> infos = infos(constraintType);
        return infos == null ? Collections.emptyList()
            : infos.stream().map(ConstraintValidatorInfo::getType).collect(ToUnmodifiable.list());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <A extends Annotation> Set<ConstraintValidatorInfo<A>> infos(Class<A> constraintType) {
        return (Set) constraintValidatorInfo.get(constraintType);
    }

    public <A extends Annotation> Optional<Set<ConstraintValidatorInfo<A>>> getConstraintValidatorInfo(
        Class<A> constraintType) {
        return Optional.ofNullable(infos(constraintType)).map(Collections::unmodifiableSet);
    }
}
