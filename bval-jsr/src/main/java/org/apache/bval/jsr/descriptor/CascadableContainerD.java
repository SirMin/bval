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

import java.lang.reflect.AnnotatedElement;
import java.util.Set;
import java.util.stream.Stream;

import javax.validation.ValidationException;
import javax.validation.metadata.CascadableDescriptor;
import javax.validation.metadata.ContainerDescriptor;
import javax.validation.metadata.ContainerElementTypeDescriptor;
import javax.validation.metadata.GroupConversionDescriptor;

import org.apache.bval.jsr.GraphContext;
import org.apache.bval.util.Lazy;
import org.apache.bval.util.Validate;
import org.apache.bval.util.reflection.TypeUtils;

public abstract class CascadableContainerD<P extends ElementD<?,?>, E extends AnnotatedElement>
    extends ElementD.NonRoot<P, E, MetadataReader.ForContainer<E>> implements CascadableDescriptor, ContainerDescriptor {

    private final Class<?> type;

    private final boolean cascaded;
    private final Set<GroupConversion> groupConversions;
    private final Lazy<Set<ContainerElementTypeD>> containerElementTypes;

    protected CascadableContainerD(MetadataReader.ForContainer<E> reader, P parent) {
        super(reader, parent);

        type = TypeUtils.getRawType(reader.meta.getType(), parent.getElementClass());

        cascaded = reader.isCascaded();
        groupConversions = reader.getGroupConversions();
        containerElementTypes = new Lazy<>(() -> reader.getContainerElementTypes(this));
    }

    @Override
    public Class<?> getElementClass() {
        return type;
    }

    @Override
    public boolean isCascaded() {
        return cascaded;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Set<GroupConversionDescriptor> getGroupConversions() {
        return (Set) groupConversions;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Set<ContainerElementTypeDescriptor> getConstrainedContainerElementTypes() {
        return (Set) containerElementTypes.get();
    }

    public final Stream<GraphContext> read(GraphContext context) {
        Validate.notNull(context);
        if (context.getValue() == null) {
            return Stream.empty();
        }
        try {
            return readImpl(context);
        } catch (Exception e) {
            throw new ValidationException(e);
        }
    }

    protected Stream<GraphContext> readImpl(GraphContext context) throws Exception {
        throw new UnsupportedOperationException();
    }
}
