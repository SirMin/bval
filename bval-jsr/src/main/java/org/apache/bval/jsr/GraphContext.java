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

import org.apache.bval.jsr.util.NodeImpl;
import org.apache.bval.jsr.util.PathImpl;
import org.apache.bval.util.Validate;

public class GraphContext {

    private final ApacheFactoryContext validatorContext;
    private final PathImpl path;
    private final Object value;
    private final GraphContext parent;

    public GraphContext(ApacheFactoryContext validatorContext, PathImpl path, Object value) {
        this(validatorContext, path, value, null);
    }

    private GraphContext(ApacheFactoryContext validatorContext, PathImpl path, Object value, GraphContext parent) {
        super();
        this.validatorContext = Validate.notNull(validatorContext, "validatorContext");
        this.path = Validate.notNull(path, "path");
        this.value = value;
        this.parent = parent;
    }

    public ApacheFactoryContext getValidatorContext() {
        return validatorContext;
    }

    public PathImpl getPath() {
        return PathImpl.copy(path);
    }

    public Object getValue() {
        return value;
    }

    public GraphContext child(NodeImpl node, Object value) {
        if (value == null || stackContains(value)) {
            return null;
        }
        final PathImpl p = PathImpl.copy(path);
        p.addNode(node);
        return new GraphContext(validatorContext, p, value, this);
    }

    private boolean stackContains(Object value) {
        GraphContext c = this;
        while (c != null) {
            if (c.value == value) {
                return true;
            }
            c = c.parent;
        }
        return false;
    }
}
