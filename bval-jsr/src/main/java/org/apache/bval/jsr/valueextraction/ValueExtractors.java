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
package org.apache.bval.jsr.valueextraction;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.valueextraction.ValueExtractor;
import javax.validation.valueextraction.ValueExtractorDeclarationException;

import org.apache.bval.jsr.metadata.ContainerElementKey;
import org.apache.bval.jsr.util.Exceptions;
import org.apache.bval.util.Lazy;
import org.apache.bval.util.StringUtils;
import org.apache.bval.util.Validate;
import org.apache.bval.util.reflection.Reflection;

public class ValueExtractors {
    public static final ValueExtractors DEFAULT;

    static {
        DEFAULT = new ValueExtractors(null) {
            {
                final Properties defaultExtractors = new Properties();
                try {
                    defaultExtractors.load(ValueExtractors.class.getResourceAsStream("DefaultExtractors.properties"));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                split(defaultExtractors.getProperty(ValueExtractor.class.getName())).map(cn -> {
                    try {
                        @SuppressWarnings("unchecked")
                        final Class<? extends ValueExtractor<?>> result =
                            (Class<? extends ValueExtractor<?>>) Reflection.toClass(cn)
                                .asSubclass(ValueExtractor.class);
                        return result;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).map(ValueExtractors::newInstance).forEach(super::addValueExtractor);

                split(defaultExtractors.getProperty(ValueExtractor.class.getName() + ".container"))
                    .flatMap(ValueExtractors::loadValueExtractors).forEach(super::addValueExtractor);
            }

            @Override
            public void addValueExtractor(ValueExtractor<?> extractor) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static Stream<String> split(String s) {
        return Stream.of(StringUtils.split(s, ','));
    }

    private static <T> T newInstance(Class<T> t) {
        try {
            return t.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Stream<ValueExtractor<?>> loadValueExtractors(String containerClassName) {
        try {
            final Class<? extends BooleanSupplier> activation =
                Reflection.toClass(containerClassName + "$Activation").asSubclass(BooleanSupplier.class);
            if (!newInstance(activation).getAsBoolean()) {
                return Stream.empty();
            }
        } catch (ClassNotFoundException e) {
            // always active
        }
        final Class<?> containerClass;
        try {
            containerClass = Reflection.toClass(containerClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        return Stream.of(containerClass.getClasses()).filter(ValueExtractor.class::isAssignableFrom).map(c -> {
            @SuppressWarnings("unchecked")
            final Class<? extends ValueExtractor<?>> result =
                (Class<? extends ValueExtractor<?>>) c.asSubclass(ValueExtractor.class);
            return result;
        }).map(ValueExtractors::newInstance);
    }

    private final Lazy<Map<ContainerElementKey, ValueExtractor<?>>> valueExtractors = new Lazy<>(HashMap::new);
    private final ValueExtractors parent;

    public ValueExtractors() {
        this(DEFAULT);
    }

    private ValueExtractors(ValueExtractors parent) {
        this.parent = parent;
    }

    public ValueExtractors createChild() {
        return new ValueExtractors(this);
    }

    public void addValueExtractor(ValueExtractor<?> extractor) {
        Validate.notNull(extractor);

        valueExtractors.get().compute(ContainerElementKey.forValueExtractor(extractor), (k, v) -> {
            Exceptions.raiseIf(v != null, ValueExtractorDeclarationException::new,
                "Multiple context-level %ss specified for %s", ValueExtractor.class.getSimpleName(), k);
            return extractor;
        });
    }

    public Map<ContainerElementKey, ValueExtractor<?>> getValueExtractors() {
        final Lazy<Map<ContainerElementKey, ValueExtractor<?>>> result = new Lazy<>(HashMap::new);
        populate(result);
        return result.optional().orElseGet(Collections::emptyMap);
    }

    public ValueExtractor<?> find(ContainerElementKey key) {
        final Map<ContainerElementKey, ValueExtractor<?>> allValueExtractors = getValueExtractors();
        if (allValueExtractors.containsKey(key)) {
            return allValueExtractors.get(key);
        }
        // search for assignable ContainerElementKey:
        Set<ContainerElementKey> assignableKeys = key.getAssignableKeys();
        while (!assignableKeys.isEmpty()) {
            final Optional<ValueExtractor<?>> found = assignableKeys.stream().filter(allValueExtractors::containsKey)
                .<ValueExtractor<?>> map(allValueExtractors::get).findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            assignableKeys = assignableKeys.stream().map(ContainerElementKey::getAssignableKeys)
                .flatMap(Collection::stream).collect(Collectors.toSet());
        }
        return null;
    }

    protected void populate(Supplier<Map<ContainerElementKey, ValueExtractor<?>>> target) {
        Optional.ofNullable(parent).ifPresent(p -> p.populate(target));
        valueExtractors.optional().ifPresent(m -> target.get().putAll(m));
    }
}
