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

import javax.validation.ConstraintValidatorContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeBuilderCustomizableContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeBuilderDefinedContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder.ContainerElementNodeContextBuilder;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder.LeafNodeBuilderCustomizableContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext;

import org.apache.bval.jsr.job.ConstraintValidatorContextImpl;

public class ContainerElementNodeContextBuilderImpl implements ContainerElementNodeContextBuilder {
    private final ConstraintValidatorContextImpl<?> context;
    private final String template;
    private final PathImpl path;
    private final NodeImpl node;

    ContainerElementNodeContextBuilderImpl(ConstraintValidatorContextImpl<?> context, String template,
        PathImpl path, NodeImpl node) {
        super();
        this.context = context;
        this.template = template;
        this.path = path;
        this.node = node;
    }

    @Override
    public ContainerElementNodeBuilderDefinedContext atKey(Object key) {
        node.setKey(key);
        path.addNode(node);
        return new ContainerElementNodeBuilderDefinedContextImpl(context, template, path);
    }

    @Override
    public ContainerElementNodeBuilderDefinedContext atIndex(Integer index) {
        node.setIndex(index);
        path.addNode(node);
        return new ContainerElementNodeBuilderDefinedContextImpl(context, template, path);
    }

    @Override
    public NodeBuilderCustomizableContext addPropertyNode(String name) {
        path.addNode(node);
        return new NodeBuilderCustomizableContextImpl(context, name, path, name);
    }

    @Override
    public LeafNodeBuilderCustomizableContext addBeanNode() {
        path.addNode(node);
        return new LeafNodeBuilderCustomizableContextImpl(context, template, path);
    }

    @Override
    public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name, Class<?> containerType,
        Integer typeArgumentIndex) {
        path.addNode(node);
        return new ContainerElementNodeBuilderCustomizableContextImpl(context, template, path, name, containerType,
            typeArgumentIndex);
    }

    @Override
    public ConstraintValidatorContext addConstraintViolation() {
        context.addError(template, path);
        return context;
    }

}
