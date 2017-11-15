/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.bval.jsr.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.GroupSequence;
import javax.validation.Valid;
import javax.validation.constraintvalidation.ValidationTarget;
import javax.validation.groups.ConvertGroup;

import org.apache.bval.jsr.ApacheValidatorFactory;
import org.apache.bval.jsr.ConstraintAnnotationAttributes;
import org.apache.bval.jsr.descriptor.GroupConversion;
import org.apache.bval.jsr.util.AnnotationsManager;
import org.apache.bval.jsr.util.Methods;
import org.apache.bval.jsr.util.ToUnmodifiable;
import org.apache.bval.util.ObjectUtils;
import org.apache.bval.util.Validate;
import org.apache.bval.util.reflection.Reflection;
import org.apache.commons.weaver.privilizer.Privilizing;
import org.apache.commons.weaver.privilizer.Privilizing.CallTo;

@Privilizing(@CallTo(Reflection.class))
public class ReflectionBuilder {

    private class ForBean implements MetadataBuilder.ForBean {

        @Override
        public MetadataBuilder.ForClass getClass(Metas<Class<?>> meta) {
            return new ReflectionBuilder.ForClass();
        }

        @Override
        public Map<String, MetadataBuilder.ForContainer<Field>> getFields(Metas<Class<?>> meta) {
            final Field[] declaredFields = Reflection.getDeclaredFields(meta.getHost());
            if (declaredFields.length == 0) {
                return Collections.emptyMap();
            }
            // we just read from the passed in meta, so can reuse the same builder instance:
            final MetadataBuilder.ForContainer<Field> value = new ReflectionBuilder.ForContainer<>();
            return Stream.of(declaredFields).collect(Collectors.toMap(Field::getName, f -> value));
        }

        @Override
        public Map<String, MetadataBuilder.ForContainer<Method>> getGetters(Metas<Class<?>> meta) {
            // we just read from the passed in meta, so can reuse the same builder instance:
            final MetadataBuilder.ForContainer<Method> value = new ReflectionBuilder.ForContainer<>();
            return Stream.of(Reflection.getDeclaredMethods(meta.getHost())).filter(Methods::isGetter)
                .collect(ToUnmodifiable.map(Methods::propertyName, g -> value));
        }

        @Override
        public Map<Signature, MetadataBuilder.ForExecutable<Constructor<?>>> getConstructors(Metas<Class<?>> meta) {
            final Constructor<?>[] declaredConstructors = Reflection.getDeclaredConstructors(meta.getHost());
            if (declaredConstructors.length == 0) {
                return Collections.emptyMap();
            }
            // we just read from the passed in meta, so can reuse the same builder instance:
            final MetadataBuilder.ForExecutable<Constructor<?>> value = new ReflectionBuilder.ForExecutable<>();
            return Stream.of(declaredConstructors).collect(Collectors.toMap(Signature::of, c -> value));
        }

        @Override
        public Map<Signature, MetadataBuilder.ForExecutable<Method>> getMethods(Metas<Class<?>> meta) {
            final Method[] declaredMethods = Reflection.getDeclaredMethods(meta.getHost());
            if (declaredMethods.length == 0) {
                return Collections.emptyMap();
            }

            // we just read from the passed in meta, so can reuse the same builder instance:
            final MetadataBuilder.ForExecutable<Method> value = new ReflectionBuilder.ForExecutable<>();
            return Stream.of(declaredMethods).filter(((Predicate<Method>) Methods::isGetter).negate())
                .collect(Collectors.toMap(Signature::of, m -> value));
        }
    }

    private class ForElement<E extends AnnotatedElement> implements MetadataBuilder.ForElement<E> {
        @Override
        public Annotation[] getDeclaredConstraints(Metas<E> meta) {
            return AnnotationsManager.getDeclaredConstraints(meta.getHost());
        }
    }

    private class ForClass extends ForElement<Class<?>> implements MetadataBuilder.ForClass {

        @Override
        public List<Class<?>> getGroupSequence(Metas<Class<?>> meta) {
            final GroupSequence groupSequence = meta.getHost().getAnnotation(GroupSequence.class);
            return groupSequence == null ? null : Collections.unmodifiableList(Arrays.asList(groupSequence.value()));
        }
    }

    private class ForContainer<E extends AnnotatedElement> extends ReflectionBuilder.ForElement<E>
        implements MetadataBuilder.ForContainer<E> {

        @Override
        public Map<ContainerElementKey, MetadataBuilder.ForContainer<AnnotatedType>> getContainerElementTypes(
            Metas<E> meta) {
            final AnnotatedType annotatedType = meta.getAnnotatedType();
            if (annotatedType instanceof AnnotatedParameterizedType) {

                final AnnotatedParameterizedType container = (AnnotatedParameterizedType) annotatedType;

                final Map<ContainerElementKey, MetadataBuilder.ForContainer<AnnotatedType>> result = new TreeMap<>();

                final MetadataBuilder.ForContainer<AnnotatedType> value = new ReflectionBuilder.ForContainer<>();

                final AnnotatedType[] typeArgs = container.getAnnotatedActualTypeArguments();
                for (int i = 0; i < typeArgs.length; i++) {
                    result.put(new ContainerElementKey(container, i), value);
                }

                return result;
            }
            return Collections.emptyMap();
        }

        @Override
        public boolean isCascade(Metas<E> meta) {
            return meta.getHost().isAnnotationPresent(Valid.class);
        }

        @Override
        public Set<GroupConversion> getGroupConversions(Metas<E> meta) {
            return Stream.of(meta.getHost().getDeclaredAnnotationsByType(ConvertGroup.class))
                .map(cg -> GroupConversion.from(cg.from()).to(cg.to())).collect(ToUnmodifiable.set());
        }
    }

    private class ForExecutable<E extends Executable> implements MetadataBuilder.ForExecutable<E> {

        @Override
        public List<MetadataBuilder.ForContainer<Parameter>> getParameters(Metas<E> meta) {
            final int count = meta.getHost().getParameterCount();
            if (count == 0) {
                return Collections.emptyList();
            }
            // we just read from the passed in meta, so can reuse the same builder instance:
            return Collections.nCopies(count, new ReflectionBuilder.ForContainer<>());
        }

        @Override
        public ForContainer<E> getReturnValue(Metas<E> meta) {
            return new ReflectionBuilder.ForContainer<E>() {

                @Override
                public Annotation[] getDeclaredConstraints(Metas<E> meta) {
                    return getConstraints(meta, ValidationTarget.ANNOTATED_ELEMENT);
                }
            };
        }

        @Override
        public MetadataBuilder.ForElement<E> getCrossParameter(Metas<E> meta) {
            return new ReflectionBuilder.ForElement<E>() {
                @Override
                public Annotation[] getDeclaredConstraints(Metas<E> meta) {
                    return getConstraints(meta, ValidationTarget.PARAMETERS);
                }
            };
        }

        private Annotation[] getConstraints(Metas<E> meta, ValidationTarget validationTarget) {
            return Optional.of(getConstraintsByTarget(meta)).map(m -> m.get(validationTarget))
                .map(l -> l.toArray(new Annotation[l.size()])).orElse(ObjectUtils.EMPTY_ANNOTATION_ARRAY);
        }

        private Map<ValidationTarget, List<Annotation>> getConstraintsByTarget(Metas<E> meta) {
            final Annotation[] declaredConstraints = AnnotationsManager.getDeclaredConstraints(meta.getHost());
            if (ObjectUtils.isEmpty(declaredConstraints)) {
                return Collections.emptyMap();
            }
            final Map<ValidationTarget, List<Annotation>> result = new EnumMap<>(ValidationTarget.class);

            for (Annotation constraint : declaredConstraints) {
                final Class<? extends Annotation> constraintType = constraint.annotationType();
                final Optional<ValidationTarget> explicitTarget =
                    Optional.of(ConstraintAnnotationAttributes.VALIDATION_APPLIES_TO.analyze(constraintType))
                        .filter(ConstraintAnnotationAttributes.Worker::isValid).map(w -> w.read(constraint));

                final ValidationTarget target = explicitTarget.orElseGet(() -> {
                    final Set<ValidationTarget> supportedTargets =
                        validatorFactory.getAnnotationsManager().supportedTargets(constraintType);

                    Validate.validState(supportedTargets.size() == 1,
                        "Found %d possible %s types for constraint type %s and no explicit assignment via #%s()",
                        supportedTargets.size(), ValidationTarget.class.getSimpleName(), constraintType.getName(),
                        ConstraintAnnotationAttributes.VALIDATION_APPLIES_TO.getAttributeName());

                    return supportedTargets.iterator().next();
                });

                result.computeIfAbsent(target, k -> new ArrayList<>()).add(constraint);
            }
            return result;
        }
    }

    private final ApacheValidatorFactory validatorFactory;
    private final ReflectionBuilder.ForBean forBean = new ReflectionBuilder.ForBean();

    public ReflectionBuilder(ApacheValidatorFactory validatorFactory) {
        super();
        this.validatorFactory = Validate.notNull(validatorFactory, "validatorFactory");
    }

    public <T> MetadataBuilder.ForBean forBean(Class<?> beanClass) {
        return forBean;
    }
}
