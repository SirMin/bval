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

import org.apache.bval.jsr.util.LeafNodeBuilderCustomizableContextImpl;
import org.apache.bval.jsr.util.NodeImpl;
import org.apache.bval.jsr.util.PathImpl;

import javax.validation.ConstraintValidatorContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeBuilderCustomizableContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext;
import javax.validation.ElementKind;

public class NodeBuilderCustomizableContextImpl
    implements ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext {
    private final PathImpl path;
    private final ConstraintValidatorContextImpl context;
    private final String template;

    public NodeBuilderCustomizableContextImpl(final ConstraintValidatorContextImpl parent, final String messageTemplate,
        final PathImpl propertyPath) {
        context = parent;
        template = messageTemplate;
        path = propertyPath;
    }

    @Override
    public ConstraintValidatorContext.ConstraintViolationBuilder.NodeContextBuilder inIterable() {
        path.getLeafNode().setInIterable(true);
        return new NodeContextBuilderImpl(context, template, path);
    }

    @Override
    public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext addNode(String name) {
        path.addNode(new NodeImpl(name));
        return this;
    }

    @Override
    public ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext addPropertyNode(
        String name) {
        final NodeImpl node = new NodeImpl.PropertyNodeImpl(name);
        path.addNode(node);
        return this;
    }

    @Override
    public ConstraintValidatorContext.ConstraintViolationBuilder.LeafNodeBuilderCustomizableContext addBeanNode() {
        final NodeImpl node = new NodeImpl.BeanNodeImpl();
        path.addNode(node);
        return null;
//        return new LeafNodeBuilderCustomizableContextImpl(context, template, path);
    }

    @Override
    public ConstraintValidatorContext addConstraintViolation() {
        context.addError(template, path);
        return context;
    }

    @Override
    public NodeBuilderCustomizableContext inContainer(Class<?> containerClass, Integer typeArgumentIndex) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name, Class<?> containerType,
        Integer typeArgumentIndex) {
        // TODO Auto-generated method stub
        return null;
    }
}
