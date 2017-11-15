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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintViolation;
import javax.validation.TraversableResolver;
import javax.validation.metadata.BeanDescriptor;
import javax.validation.metadata.CascadableDescriptor;
import javax.validation.metadata.ConstraintDescriptor;
import javax.validation.metadata.ContainerDescriptor;
import javax.validation.metadata.ElementDescriptor;
import javax.validation.metadata.PropertyDescriptor;

import org.apache.bval.jsr.ApacheFactoryContext;
import org.apache.bval.jsr.ConstraintViolationImpl;
import org.apache.bval.jsr.GraphContext;
import org.apache.bval.jsr.descriptor.CascadableContainerD;
import org.apache.bval.jsr.descriptor.ComposedD;
import org.apache.bval.jsr.descriptor.ConstraintD;
import org.apache.bval.jsr.descriptor.PropertyD;
import org.apache.bval.jsr.groups.Group;
import org.apache.bval.jsr.groups.Groups;
import org.apache.bval.jsr.util.NodeImpl;
import org.apache.bval.jsr.util.PathImpl;
import org.apache.bval.util.Lazy;
import org.apache.bval.util.Validate;

public abstract class ValidationJob<T, J extends ValidationJob<T, J>> {

    public abstract class Frame<D extends ElementDescriptor> {
        protected final D descriptor;
        protected final GraphContext context;

        protected Frame(D descriptor, GraphContext context) {
            super();
            this.descriptor = Validate.notNull(descriptor, "descriptor");
            this.context = Validate.notNull(context, "context");
        }

        @SuppressWarnings("unchecked")
        final J getJob() {
            return (J) ValidationJob.this;
        }

        final void visit(Class<?> group) {
            if (skip()) {
                return;
            }
            descriptor.findConstraints().unorderedAndMatchingGroups(group).getConstraintDescriptors().stream()
                .map(ConstraintD.class::cast).forEach(this::validate);

            recurse(group);
        }

        boolean skip() {
            return false;
        }

        abstract void recurse(Class<?> group);

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private boolean validate(ConstraintD<?> constraint) {
            final Class<? extends ConstraintValidator> constraintValidatorClass =
                constraint.getConstraintValidatorClass();

            final ConstraintValidator constraintValidator =
                validatorContext.getConstraintValidatorFactory().getInstance(constraintValidatorClass);

            constraintValidator.initialize(constraint.getAnnotation());

            final ConstraintValidatorContextImpl<T> constraintValidatorContext =
                new ConstraintValidatorContextImpl<>(this, constraint);

            boolean valid = constraintValidator.isValid(context.getValue(), constraintValidatorContext);
            if (!valid) {
                results.get().addAll(constraintValidatorContext.getRequiredViolations());
            }

            final Iterator<ConstraintDescriptor<?>> components = constraint.getComposingConstraints().iterator();

            while (valid || !constraint.isReportAsSingleViolation()) {
                if (components.hasNext()) {
                    valid = validate((ConstraintD<?>) components.next());
                    continue;
                }
                break;
            }
            return valid;
        }
    }

    public class BeanFrame extends Frame<BeanDescriptor> {

        BeanFrame(GraphContext context) {
            super(getBeanDescriptor(context.getValue()), context);
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        void recurse(Class<?> group) {
            // TODO experiment with parallel streaming, perhaps with an option. In this case would probably need each
            // frame to record local violations before adding to the job

            final Stream<PropertyD<?>> properties =
                descriptor.getConstrainedProperties().stream().flatMap(d -> ComposedD.unwrap(d, PropertyD.class));

            final TraversableResolver traversableResolver = validatorContext.getTraversableResolver();

            final Stream<PropertyD<?>> reachableProperties =
                properties.filter(d -> traversableResolver.isReachable(context.getValue(),
                    new NodeImpl.PropertyNodeImpl(d.getPropertyName()), getRootBeanClass(), context.getPath(),
                    d.getElementType()));

            reachableProperties.forEach(d -> d.read(context).filter(Objects::nonNull)
                .map(child -> new SproutFrame(d, child)).forEach(f -> f.visit(group)));
        }

        @Override
        boolean skip() {
            return seenBeans.put(context.getValue(), Boolean.TRUE) != null;
        }
    }

    public class SproutFrame<D extends ElementDescriptor & CascadableDescriptor & ContainerDescriptor>
        extends Frame<D> {

        public SproutFrame(D descriptor, GraphContext context) {
            super(descriptor, context);
        }

        @Override
        void recurse(Class<?> group) {
            final Stream<CascadableContainerD<?, ?>> containerElements =
                descriptor.getConstrainedContainerElementTypes().stream()
                    .flatMap(d -> ComposedD.unwrap(d, CascadableContainerD.class));

            containerElements.flatMap(d -> d.read(context).map(child -> new SproutFrame<>(d, child)))
                .forEach(f -> f.visit(group));

            // Stream<CascadableContainerD<?, ?>> descriptors = ComposedD.unwrap(descriptor,
            // CascadableContainerD.class);

            if (!descriptor.isCascaded()) {
                return;
            }
            if (descriptor instanceof PropertyDescriptor) {
                final TraversableResolver traversableResolver = validatorContext.getTraversableResolver();

                final PathImpl pathToTraversableObject = PathImpl.copy(context.getPath());
                final NodeImpl traversableProperty = pathToTraversableObject.removeLeafNode();

                if (!traversableResolver.isCascadable(context.getValue(), traversableProperty, getRootBeanClass(),
                    pathToTraversableObject, ((PropertyD<?>) descriptor).getElementType())) {
                    return;
                }
                // descriptors = descriptors.filter(d -> traversableResolver.isCascadable(context.getValue(),
                // traversableProperty, getRootBeanClass(), pathToTraversableObject, d.getElementType()));
            }
            // descriptors.flatMap(d -> d.read(context)).map(BeanFrame::new).forEach(f -> f.visit(group));
            new BeanFrame(context).visit(group);
        }
    }

    protected final ApacheFactoryContext validatorContext;

    private final Groups groups;
    private final Lazy<Set<ConstraintViolation<T>>> results = new Lazy<>(LinkedHashSet::new);
    private final IdentityHashMap<Object, Boolean> seenBeans = new IdentityHashMap<>();
    private Frame<?> baseFrame;

    ValidationJob(ApacheFactoryContext validatorContext, Class<?>[] groups) {
        super();
        this.validatorContext = Validate.notNull(validatorContext, "validatorContext");
        this.groups = validatorContext.getGroupsComputer().computeGroups(groups);
    }

    public Set<ConstraintViolation<T>> getResults() {
        Validate.validState(!results.optional().isPresent(), "#getResults() already called");

        groups.getGroups().stream().map(Group::getGroup).forEach(baseFrame::visit);

        sequences: for (List<Group> seq : groups.getSequences()) {
            for (Group g : seq) {
                baseFrame.visit(g.getGroup());
                if (violationsFound()) {
                    break sequences;
                }
            }
        }
        return results.optional().orElse(Collections.emptySet());
    }

    BeanDescriptor getBeanDescriptor(Object bean) {
        Validate.notNull(bean, "bean");
        return validatorContext.getFactory().getDescriptorManager().getBeanDescriptor(bean.getClass());
    }

    boolean isCascading() {
        return true;
    }

    @SuppressWarnings("unchecked")
    J init() {
        this.baseFrame = initBaseFrame();
        Validate.validState(baseFrame != null, "%s calculated null baseFrame", getClass().getName());
        return (J) this;
    }

    abstract ConstraintViolationImpl<T> createViolation(String messageTemplate,
        ConstraintValidatorContextImpl<T> context, Object leafBean);

    protected abstract Frame<?> initBaseFrame();

    protected abstract Class<T> getRootBeanClass();

    private boolean violationsFound() {
        return results.optional().filter(s -> !s.isEmpty()).isPresent();
    }
}
