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
package org.apache.bval.jsr.metadata;

import java.lang.annotation.ElementType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

import javax.validation.constraintvalidation.ValidationTarget;

import org.apache.bval.util.Validate;

/**
 * Validation class model.
 *
 * @param <E>
 */
public abstract class Metas<E extends AnnotatedElement> {

    public static class ForClass extends Metas<Class<?>> {

        public ForClass(Class<?> host) {
            super(host, ElementType.TYPE);
        }

        public final Class<?> getDeclaringClass() {
            return getHost();
        }

        @Override
        public Type getType() {
            return getHost();
        }

        @Override
        public AnnotatedType getAnnotatedType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            return getHost().getName();
        }
    }

    public static abstract class ForMember<M extends Member & AnnotatedElement> extends Metas<M> {

        protected ForMember(M host, ElementType elementType) {
            super(host, elementType);
        }
    }

    public static class ForField extends ForMember<Field> {

        public ForField(Field host) {
            super(host, ElementType.FIELD);
        }

        @Override
        public Type getType() {
            return getHost().getGenericType();
        }

        @Override
        public AnnotatedType getAnnotatedType() {
            return getHost().getAnnotatedType();
        }

        @Override
        public String getName() {
            return getHost().getName();
        }
    }

    public static abstract class ForExecutable<E extends Executable> extends ForMember<E> {

        protected ForExecutable(E host, ElementType elementType) {
            super(host, elementType);
        }

        @Override
        public AnnotatedType getAnnotatedType() {
            return getHost().getAnnotatedReturnType();
        }
    }

    public static class ForConstructor extends ForExecutable<Constructor<?>> {

        public ForConstructor(Constructor<?> host) {
            super(host, ElementType.CONSTRUCTOR);
        }

        @Override
        public Type getType() {
            return getHost().getDeclaringClass();
        }

        @Override
        public String getName() {
            return getHost().getDeclaringClass().getSimpleName();
        }
    }

    public static class ForMethod extends ForExecutable<Method> {

        public ForMethod(Method host) {
            super(host, ElementType.METHOD);
        }

        @Override
        public Type getType() {
            return getHost().getGenericReturnType();
        }

        @Override
        public String getName() {
            return getHost().getName();
        }
    }

    public static class ForCrossParameter<E extends Executable> extends Metas.ForExecutable<E> {

        public ForCrossParameter(Metas<E> parent) {
            super(parent.getHost(), parent.getElementType());
        }

        @Override
        public Type getType() {
            return Object[].class;
        }

        @Override
        public String getName() {
            return "<cross parameter>";
        }

        @Override
        public ValidationTarget getValidationTarget() {
            return ValidationTarget.PARAMETERS;
        }
    }

    public static class ForParameter extends Metas<Parameter> {

        private final String name;

        public ForParameter(Parameter host, String name) {
            super(host, ElementType.PARAMETER);
            this.name = Validate.notNull(name, "name");
        }

        @Override
        public Type getType() {
            return getHost().getType();
        }

        @Override
        public AnnotatedType getAnnotatedType() {
            return getHost().getAnnotatedType();
        }

        public String getName() {
            return name;
        }
    }

    public static class ForContainerElement extends Metas<AnnotatedType> {

        private final ContainerElementKey key;

        public ForContainerElement(ContainerElementKey key) {
            super(key.getAnnotatedType(), ElementType.TYPE_USE);
            this.key = Validate.notNull(key, "key");
        }

        @Override
        public Type getType() {
            return getAnnotatedType().getType();
        }

        @Override
        public AnnotatedType getAnnotatedType() {
            return key.getAnnotatedType();
        }

        public Integer getTypeArgumentIndex() {
            return Integer.valueOf(key.getTypeArgumentIndex());
        }

        @Override
        public String getName() {
            return key.toString();
        }
    }

    private final E host;
    private final ElementType elementType;

    protected Metas(E host, ElementType elementType) {
        super();
        this.host = Validate.notNull(host, "host");
        this.elementType = Validate.notNull(elementType, "elementType");
    }

    public E getHost() {
        return host;
    }

    public ElementType getElementType() {
        return elementType;
    }

    public abstract Type getType();

    public abstract AnnotatedType getAnnotatedType();

    public abstract String getName();

    public ValidationTarget getValidationTarget() {
        return ValidationTarget.ANNOTATED_ELEMENT;
    }
}
