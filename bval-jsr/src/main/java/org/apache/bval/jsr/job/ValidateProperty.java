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

import javax.validation.metadata.PropertyDescriptor;

import org.apache.bval.jsr.ApacheFactoryContext;
import org.apache.bval.jsr.ConstraintViolationImpl;
import org.apache.bval.jsr.GraphContext;
import org.apache.bval.jsr.descriptor.ConstraintD;
import org.apache.bval.jsr.descriptor.PropertyD;
import org.apache.bval.jsr.util.PathImpl;
import org.apache.bval.util.StringUtils;
import org.apache.bval.util.Validate;

public final class ValidateProperty<T> extends ValidationJob<T, ValidateProperty<T>> {
    private final Class<T> rootBeanClass;
    private final PropertyD<?> descriptor;
    private GraphContext baseContext;
    private boolean cascade;

    private ValidateProperty(ApacheFactoryContext validatorContext, Class<T> rootBeanClass, String property,
        Class<?>[] groups) {
        super(validatorContext, groups);
        this.rootBeanClass = Validate.notNull(rootBeanClass, "rootBeanClass");
        Validate.isTrue(StringUtils.isNotBlank(property), "Null/empty/blank property");
        this.descriptor = (PropertyD<?>) validatorContext.getDescriptorManager().getBeanDescriptor(rootBeanClass)
            .getConstraintsForProperty(property);
    }

    ValidateProperty(ApacheFactoryContext validatorContext, Class<T> rootBeanClass, String property, Object value,
        Class<?>[] groups) {
        this(validatorContext, rootBeanClass, property, groups);

        baseContext = new GraphContext(validatorContext, PathImpl.createPathFromString(property), value);
    }

    @SuppressWarnings("unchecked")
    ValidateProperty(ApacheFactoryContext validatorContext, T bean, String property, Class<?>[] groups)
        throws Exception {
        this(validatorContext, (Class<T>) Validate.notNull(bean, "bean").getClass(), property, groups);

        baseContext = new GraphContext(validatorContext, PathImpl.create(), bean)
            .child(PathImpl.createPathFromString(property).getLeafNode(), descriptor.getValue(bean));
    }

    public ValidateProperty<T> cascade(boolean cascade) {
        this.cascade = cascade;
        return this;
    }

    @Override
    protected SproutFrame<PropertyDescriptor> initBaseFrame() {
        return new SproutFrame<PropertyDescriptor>(descriptor, baseContext) {
            @Override
            void recurse(Class<?> group) {
                if (cascade) {
                    super.recurse(group);
                }
            }
        };
    }

    @Override
    protected Class<T> getRootBeanClass() {
        return rootBeanClass;
    }

    @Override
    ConstraintViolationImpl<T> createViolation(String messageTemplate, ConstraintValidatorContextImpl<T> context,
        Object leafBean) {
        final String message = validatorContext.getMessageInterpolator().interpolate(messageTemplate, context);

        return new ConstraintViolationImpl<>(messageTemplate, message, null, leafBean,
            context.getFrame().context.getPath(), context.getFrame().context.getValue(),
            context.getConstraintDescriptor(), rootBeanClass,
            context.getConstraintDescriptor().unwrap(ConstraintD.class).getDeclaredOn(), null, null);
    }
}
