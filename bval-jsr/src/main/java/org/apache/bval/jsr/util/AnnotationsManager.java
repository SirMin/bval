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
package org.apache.bval.jsr.util;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.Constraint;
import javax.validation.ConstraintDefinitionException;
import javax.validation.OverridesAttribute;
import javax.validation.ValidationException;
import javax.validation.constraintvalidation.ValidationTarget;

import org.apache.bval.jsr.ApacheValidatorFactory;
import org.apache.bval.jsr.ConfigurationImpl;
import org.apache.bval.jsr.ConstraintAnnotationAttributes;
import org.apache.bval.jsr.ConstraintCached.ConstraintValidatorInfo;
import org.apache.bval.jsr.xml.AnnotationProxyBuilder;
import org.apache.bval.util.Lazy;
import org.apache.bval.util.StringUtils;
import org.apache.bval.util.Validate;
import org.apache.bval.util.reflection.Reflection;
import org.apache.commons.weaver.privilizer.Privilizing;
import org.apache.commons.weaver.privilizer.Privilizing.CallTo;

/**
 * Manages (constraint) annotations according to the BV spec.
 * 
 * @since 2.0
 */
@Privilizing(@CallTo(Reflection.class))
public class AnnotationsManager {
    private static final class OverriddenAnnotationSpecifier {
        final Class<? extends Annotation> annotationType;
        final int constraintIndex;

        OverriddenAnnotationSpecifier(OverridesAttribute annotation) {
            this.annotationType = annotation.annotationType();
            this.constraintIndex = annotation.constraintIndex();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || !obj.getClass().equals(getClass())) {
                return false;
            }
            final OverriddenAnnotationSpecifier other = (OverriddenAnnotationSpecifier) obj;
            return Objects.equals(annotationType, other.annotationType) && constraintIndex == other.constraintIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(annotationType, constraintIndex);
        }

    }

    private static class Composition {
        final Lazy<Map<OverriddenAnnotationSpecifier, Map<String, String>>> overrides = new Lazy<>(HashMap::new);
        final Annotation[] components;

        Composition(Class<? extends Annotation> annotationType) {
            components = Stream.of(annotationType.getDeclaredAnnotations())
                .filter(a -> a.annotationType().isAnnotationPresent(Constraint.class)).toArray(Annotation[]::new);

            if (!isComposed()) {
                return;
            }
            for (Method m : Reflection.getDeclaredMethods(annotationType)) {
                final String from = m.getName();
                for (OverridesAttribute overridesAttribute : m.getDeclaredAnnotationsByType(OverridesAttribute.class)) {
                    final String to =
                        Optional.of(overridesAttribute.name()).filter(StringUtils::isNotBlank).orElse(from);
                    final Map<String, String> attributeMapping = overrides.get()
                        .computeIfAbsent(new OverriddenAnnotationSpecifier(overridesAttribute), k -> new HashMap<>());
                    if (attributeMapping.containsKey(to)) {
                        throw new IllegalStateException(
                            String.format("Attempt to override %s#%s() index %d from multiple sources",
                                overridesAttribute.constraint(), to, overridesAttribute.constraintIndex()));
                    }
                    attributeMapping.put(to, from);
                }
            }
        }

        boolean isComposed() {
            return components.length > 0;
        }

        Annotation[] getComponents(Annotation source) {
            final Annotation[] result = components.clone();
            if (overrides.optional().isPresent()) {
                final Map<Class<? extends Annotation>, List<Annotation>> constraints = new HashMap<>();
                for (Annotation constraint : result) {
                    constraints.computeIfAbsent(constraint.annotationType(), k -> new ArrayList<>()).add(constraint);
                }
                final Map<String, Object> sourceAttributes = readAttributes(source);
                overrides.get().forEach((spec, mappings) -> {
                    final List<Annotation> ofType = constraints.get(spec.annotationType);
                    final int actualIndex;
                    if (spec.constraintIndex < 0) {
                        Validate.validState(ofType.size() == 1, "Expected a single composing %s constraint",
                            spec.annotationType);
                        actualIndex = 0;
                    } else {
                        actualIndex = spec.constraintIndex;
                    }
                    final AnnotationProxyBuilder<Annotation> proxyBuilder =
                        new AnnotationProxyBuilder<>(ofType.get(actualIndex));

                    boolean changed = false;
                    for (Map.Entry<String, String> e : mappings.entrySet()) {
                        final Object value = sourceAttributes.get(e.getValue());
                        changed = Objects.equals(proxyBuilder.putValue(e.getKey(), value), value) || changed;
                    }
                    if (changed) {
                        ofType.set(actualIndex, proxyBuilder.createAnnotation());
                    }
                });
                // now write the results back into the result array:
                constraints.values().stream().flatMap(Collection::stream).toArray(n -> result);
            }
            return result;
        }
    }

    public static Map<String, Object> readAttributes(Annotation a) {
        final Lazy<Map<String, Object>> result = new Lazy<>(LinkedHashMap::new);

        Stream.of(Reflection.getDeclaredMethods(a.annotationType())).filter(m -> m.getParameterCount() == 0)
            .forEach(m -> {
                final boolean mustUnset = Reflection.setAccessible(m, true);
                try {
                    result.get().put(m.getName(), m.invoke(a));
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new ValidationException("Caught exception reading attributes of " + a, e);
                } finally {
                    if (mustUnset) {
                        Reflection.setAccessible(m, false);
                    }
                }
            });
        return result.optional().map(Collections::unmodifiableMap).orElseGet(Collections::emptyMap);
    }

    /**
     * Meta-annotation aware.
     * 
     * @param e
     * @param t
     * @return {@code boolean}
     * @see AnnotatedElement#isAnnotationPresent(Class)
     */
    public static boolean isAnnotationPresent(AnnotatedElement e, Class<? extends Annotation> t) {
        if (e.isAnnotationPresent(t)) {
            return true;
        }
        return Stream.of(e.getAnnotations()).map(Annotation::annotationType).anyMatch(a -> isAnnotationPresent(a, t));
    }

    /**
     * Get declared annotations with a particular meta-annotation.
     * 
     * @param e
     * @param meta
     * @return {@link Annotation}[]
     */
    public static Annotation[] getDeclared(AnnotatedElement e, Class<? extends Annotation> meta) {
        return Stream.of(e.getDeclaredAnnotations()).filter(ann -> isAnnotationPresent(ann.annotationType(), meta))
            .toArray(Annotation[]::new);
    }

    /**
     * Accounts for {@link Constraint} meta-annotation AND {@link Repeatable} constraint annotations.
     * 
     * @param e
     * @return Annotation[]
     */
    public static Annotation[] getDeclaredConstraints(AnnotatedElement e) {
        return Stream.of(e.getDeclaredAnnotations()).flatMap((Function<Annotation, Stream<Annotation>>) a -> {
            final ConstraintAnnotationAttributes.Worker<? extends Annotation> analyzer =
                ConstraintAnnotationAttributes.VALUE.analyze(a.annotationType());
            if (analyzer.isValid()) {
                return Stream.of(analyzer.<Annotation[]> read(a));
            }
            return Stream.of(a);
        }).filter(a -> a.annotationType().isAnnotationPresent(Constraint.class)).toArray(Annotation[]::new);
    }

    public static boolean declaresAttribute(Class<? extends Annotation> annotationType, String name) {
        try {
            annotationType.getDeclaredMethod(name);
            return true;
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    private final ApacheValidatorFactory validatorFactory;
    private final LRUCache<Class<? extends Annotation>, Composition> compositions;

    public AnnotationsManager(ApacheValidatorFactory validatorFactory) {
        super();
        this.validatorFactory = Validate.notNull(validatorFactory);
        final String cacheSize =
            validatorFactory.getProperties().get(ConfigurationImpl.Properties.CONSTRAINTS_CACHE_SIZE);
        try {
            compositions = new LRUCache<>(Integer.parseInt(cacheSize));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(String.format("Cannot parse value %s for configuration property %s",
                cacheSize, ConfigurationImpl.Properties.CONSTRAINTS_CACHE_SIZE));
        }
    }

    /**
     * Retrieve the composing constraints for the specified constraint {@link Annotation}.
     * 
     * @param a
     * @return {@link Annotation}[]
     */
    public Annotation[] getComposingConstraints(Annotation a) {
        return compositions.computeIfAbsent(a.annotationType(), this::getComposition).getComponents(a);
    }

    public Set<ValidationTarget> supportedTargets(Class<? extends Annotation> constraintType) {
        return validatorFactory.getConstraintsCache().getConstraintValidatorInfo(constraintType)
            .orElseGet(Collections::emptySet).stream().map(ConstraintValidatorInfo::getSupportedTargets)
            .flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private Composition getComposition(Class<? extends Annotation> annotationType) {
        final Set<ValidationTarget> composedTargets = supportedTargets(annotationType);
        final Composition result = new Composition(annotationType);
        Stream.of(result.components).map(Annotation::annotationType).forEach(at -> {
            final Set<ValidationTarget> composingTargets = supportedTargets(at);
            if (Collections.disjoint(composingTargets, composedTargets)) {
                throw new ConstraintDefinitionException(
                    String.format("Attempt to compose %s of %s but validator types are incompatible",
                        annotationType.getName(), at.getName()));
            }
        });
        return result;
    }
}
