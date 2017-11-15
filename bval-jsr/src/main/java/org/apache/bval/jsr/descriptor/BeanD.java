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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.validation.metadata.BeanDescriptor;
import javax.validation.metadata.ConstructorDescriptor;
import javax.validation.metadata.MethodDescriptor;
import javax.validation.metadata.MethodType;
import javax.validation.metadata.PropertyDescriptor;

import org.apache.bval.jsr.metadata.Signature;
import org.apache.bval.jsr.util.ToUnmodifiable;
import org.apache.bval.util.Lazy;

public class BeanD extends ElementD<Class<?>, MetadataReader.ForBean> implements BeanDescriptor {

    private static <K, V> Map<K, V> toMap(Set<V> set, Function<? super V, ? extends K> toKey) {
        return set.stream().collect(ToUnmodifiable.map(toKey, Function.identity()));
    }

    private final Class<?> beanClass;

    private final Lazy<List<Class<?>>> groupSequence;
    private Lazy<Set<PropertyDescriptor>> properties;
    private Lazy<Map<String, PropertyDescriptor>> propertiesMap;
    private Lazy<Map<Signature, ConstructorD>> constructors;
    private Lazy<Map<Signature, MethodD>> methods;

    BeanD(MetadataReader.ForBean reader) {
        super(reader);
        this.beanClass = reader.meta.getHost();

        groupSequence = new Lazy<>(reader::getGroupSequence);
        properties = new Lazy<>(() -> reader.getProperties(this));
        propertiesMap = new Lazy<>(() -> toMap(properties.get(), PropertyDescriptor::getPropertyName));
        constructors = new Lazy<>(() -> reader.getConstructors(this));
        methods = new Lazy<>(() -> reader.getMethods(this));
    }

    @Override
    public Class<?> getElementClass() {
        return beanClass;
    }

    @Override
    public boolean isBeanConstrained() {
        return hasConstraints() || properties.get().stream().anyMatch(pd -> pd.isCascaded() || pd.hasConstraints());
    }

    @Override
    public PropertyDescriptor getConstraintsForProperty(String propertyName) {
        return propertiesMap.get().get(propertyName);
    }

    @Override
    public Set<PropertyDescriptor> getConstrainedProperties() {
        return properties.get();
    }

    @Override
    public MethodDescriptor getConstraintsForMethod(String methodName, Class<?>... parameterTypes) {
        return methods.get().get(new Signature(methodName, parameterTypes));
    }

    @Override
    public Set<MethodDescriptor> getConstrainedMethods(MethodType methodType, MethodType... methodTypes) {
        return methods.get().values().stream().filter(EnumSet.of(methodType, methodTypes)::contains)
            .collect(ToUnmodifiable.set());
    }

    @Override
    public ConstructorDescriptor getConstraintsForConstructor(Class<?>... parameterTypes) {
        return constructors.get().get(new Signature(beanClass.getSimpleName(), parameterTypes));
    }

    @Override
    public Set<ConstructorDescriptor> getConstrainedConstructors() {
        return constructors.get().values().stream().collect(ToUnmodifiable.set());
    }

    @Override
    protected BeanD getBean() {
        return this;
    }

    @Override
    List<Class<?>> getGroupSequence() {
        return groupSequence.get();
    }
}
