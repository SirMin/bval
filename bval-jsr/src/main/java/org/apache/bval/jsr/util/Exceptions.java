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
package org.apache.bval.jsr.util;

import java.util.function.BiFunction;
import java.util.function.Function;

public class Exceptions {

    public static <E extends Exception> E create(Function<String, E> fn, String format, Object... args) {
        return fn.apply(String.format(format, args));
    }

    public static <E extends Exception, C extends Throwable> E create(BiFunction<String, ? super C, E> fn, C cause,
        String format, Object... args) {
        return fn.apply(String.format(format, args), cause);
    }

    public static <E extends Exception> void raise(Function<String, E> fn, String format, Object... args) throws E {
        throw create(fn, format, args);
    }

    public static <E extends Exception> void raiseIf(boolean condition, Function<String, E> fn, String format,
        Object... args) throws E {
        if (condition) {
            raise(fn, format, args);
        }
    }

    public static <E extends Exception> void raiseUnless(boolean condition, Function<String, E> fn, String format,
        Object... args) throws E {
        raiseIf(!condition, fn, format, args);
    }

    public static <E extends Exception, C extends Throwable> void raise(BiFunction<String, ? super C, E> fn, C cause,
        String format, Object... args) throws E {
        throw create(fn, cause, format, args);
    }

    public static <E extends Exception, C extends Throwable> void raiseIf(boolean condition,
        BiFunction<String, ? super C, E> fn, C cause, String format, Object... args) throws E {
        if (condition) {
            raise(fn, cause, format, args);
        }
    }

    public static <E extends Exception, C extends Throwable> void raiseUnless(boolean condition,
        BiFunction<String, ? super C, E> fn, C cause, String format, Object... args) throws E {
        raiseIf(!condition, fn, cause, format, args);
    }
}
