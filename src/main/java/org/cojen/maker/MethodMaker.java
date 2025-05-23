/*
 *  Copyright (C) 2019 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

import java.lang.constant.Constable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import java.util.function.Consumer;

/**
 * Allows new methods to be defined within a class.
 *
 * @author Brian S O'Neill
 * @see ClassMaker#addMethod
 */
public interface MethodMaker extends Maker {
    /**
     * Begin defining a standalone method, defined in the same nest as the lookup class.
     *
     * @param lookup define the method using this lookup object
     * @param retType a class or name; can be null if the method returns void
     * @param name method name; use null or "_" if unnamed
     * @param paramTypes classes or names
     * @throws IllegalArgumentException if a type is unsupported
     * @see #finish
     */
    static MethodMaker begin(MethodHandles.Lookup lookup,
                             Object retType, String name, Object... paramTypes)
    {
        return begin(lookup, null, retType, name, paramTypes);
    }

    /**
     * @hidden
     */
    static MethodMaker begin(MethodHandles.Lookup lookup, Object retType, String name) {
        return begin(lookup, retType, name, BaseType.NO_ARGS);
    }

    /**
     * Begin defining a standalone method, defined in the same nest as the lookup class.
     *
     * @param lookup define the method using this lookup object
     * @param name method name; use null or "_" if unnamed
     * @param type defines the return type and parameter types
     * @see #finish
     */
    static MethodMaker begin(MethodHandles.Lookup lookup, String name, MethodType type) {
        if (type == null) {
            type = MethodType.methodType(void.class);
        }
        return begin(lookup, type, type.returnType(), name, (Object[]) type.parameterArray());
    }

    private static MethodMaker begin(MethodHandles.Lookup lookup, MethodType type,
                                     Object retType, String methodName, Object... paramTypes)
    {
        final String mname = methodName == null ? "_" : methodName;

        Class<?> lookupClass = lookup.lookupClass();
        String className = lookupClass.getName();
        className = className.substring(0, className.lastIndexOf('.') + 1) + mname;
        ClassLoader loader = lookupClass.getClassLoader();
        TheClassMaker cm = TheClassMaker.begin(false, className, true, loader, null, lookup);

        BaseType.Method method = cm.defineMethod(retType, mname, paramTypes);

        final MethodType mtype;
        if (type != null) {
            mtype = type;
        } else {
            BaseType[] ptypes = method.paramTypes();
            var pclasses = new Class[ptypes.length];
            for (int i=0; i<pclasses.length; i++) {
                pclasses[i] = classFor(ptypes[i]);
            }
            mtype = MethodType.methodType(classFor(method.returnType()), pclasses);
        }

        var mm = new TheMethodMaker(cm, method) {
            private boolean mDisallowFinish;

            @Override
            public MethodHandle finish() {
                if (mDisallowFinish) {
                    throw new IllegalStateException();
                }
                MethodHandles.Lookup lookup = mClassMaker.finishHidden();
                try {
                    return lookup.findStatic(lookup.lookupClass(), mname, mtype);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            protected void disallowFinish(boolean disallow) {
                mDisallowFinish = disallow;
            }
        };

        mm.static_();
        cm.doAddMethod(mm);

        return mm;
    }

    private static Class<?> classFor(BaseType type) {
        Class<?> clazz = type.classType();
        if (clazz == null) {
            throw new IllegalStateException("Unknown type: " + type.name());
        }
        return clazz;
    }

    /**
     * Returns the name of this method.
     */
    @Override
    String name();

    /**
     * Returns the return type of this method.
     */
    Type getReturnType();

    /**
     * Returns the parameter types of this method.
     */
    Type[] getParamTypes();

    /**
     * Switch this method to be public. Methods are package-private by default.
     *
     * @return this
     */
    MethodMaker public_();

    /**
     * Switch this method to be private. Methods are package-private by default.
     *
     * @return this
     */
    MethodMaker private_();

    /**
     * Switch this method to be protected. Methods are package-private by default.
     *
     * @return this
     */
    MethodMaker protected_();

    /**
     * Switch this method to be static. Methods are non-static by default.
     *
     * @throws IllegalStateException if {@link #this_ this} or any parameters have been accessed
     * @return this
     */
    MethodMaker static_();

    /**
     * Switch this method to be final. Methods are non-final by default.
     *
     * @return this
     */
    MethodMaker final_();

    /**
     * Switch this method to be synchronized. Methods are non-synchronized by default.
     *
     * @return this
     */
    MethodMaker synchronized_();

    /**
     * Switch this method to be abstract. Methods are non-abstract by default.
     *
     * @return this
     */
    MethodMaker abstract_();

    /**
     * Switch this method to be native. Methods are non-native by default.
     *
     * @return this
     */
    MethodMaker native_();

    /**
     * Indicate that this method is synthetic. Methods are non-synthetic by default.
     *
     * @return this
     */
    MethodMaker synthetic();

    /**
     * Indicate that this method is a bridge, which implements an inherited method exactly, but
     * it delegates to another method which returns a more specialized return type.
     *
     * @return this
     */
    MethodMaker bridge();

    /**
     * Define a receiver type for the sole purpose of annotating the implicit {@link #this_
     * this} parameter.
     *
     * @param type must be the same as {@code this} type
     * @throws IllegalStateException if not an instance method, or if {@code this} or any
     * parameters have been accessed
     * @return this MethodMaker
     */
    MethodMaker receiverType(Type type);

    /**
     * Indicate that this method supports a variable number of arguments.
     *
     * @return this
     * @throws IllegalStateException if last parameter type isn't an array
     */
    MethodMaker varargs();

    /**
     * Declare that this method throws the given exception type.
     *
     * @param type a class or class name
     * @return this
     * @throws IllegalArgumentException if the type is unsupported
     */
    MethodMaker throws_(Object type);

    /**
     * Verifies that this method overrides an inherited virtual method.
     *
     * @return this
     * @throws IllegalStateException if not overriding an inherited virtual method, or if the
     * inherited method is final
     */
    MethodMaker override();

    /**
     * {@inheritDoc}
     */
    MethodMaker signature(Object... components);

    /**
     * Returns a variable of type {@link Class} which represents the enclosing class of this
     * method.
     *
     * @see #classMaker()
     */
    Variable class_();

    /**
     * Returns a variable which accesses the enclosing object of this method.
     *
     * @throws IllegalStateException if making a static method
     */
    Variable this_();

    /**
     * Returns a variable which is used for invoking superclass methods. The type of the
     * variable is the superclass, and when applicable, the instance is {@link #this_ this_}.
     */
    Variable super_();

    /**
     * Returns a variable which accesses a parameter of the method being built.
     *
     * @param index zero based index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    Parameter param(int index);

    /**
     * Returns the number of parameters which can be accessed by the {@link #param param}
     * method.
     */
    int paramCount();

    /**
     * Returns a new uninitialized variable with the given type. Call {@link Variable#set set} to
     * initialize it immediately.
     *
     * @param type a class, class name, or a variable
     * @throws IllegalArgumentException if the type is unsupported
     */
    Variable var(Object type);

    /**
     * Define a line number to represent the location of the next code instruction.
     */
    void lineNum(int num);

    /**
     * Returns a new label, initially unpositioned. Call {@link Label#here here} to position it
     * immediately.
     */
    Label label();

    /**
     * Generates an unconditional goto statement to the given label, which doesn't need to be
     * positioned yet.
     *
     * @see Label#goto_
     */
    void goto_(Label label);

    /**
     * Generates a return void statement.
     *
     * @throws IllegalStateException if this method cannot return void
     */
    void return_();

    /**
     * Generates a statement which returns a variable or a constant.
     *
     * @param value {@link Variable} or constant
     * @throws IllegalArgumentException if the value isn't a variable or a supported constant
     * @throws IllegalStateException if this method must return void
     */
    void return_(Object value);

    /**
     * Access a static or instance field in the enclosing object of this method.
     *
     * @param name field name
     * @throws IllegalStateException if the field isn't found
     */
    Field field(String name);

    /**
     * Invoke a static or instance method on the enclosing object of this method.
     *
     * @param name the method name
     * enclosing class
     * @param values {@link Variable Variables} or constants
     * @return the result of the method, which is null if it's void
     * @throws IllegalArgumentException if a value isn't a variable or a supported constant
     * @throws IllegalStateException if the method isn't found
     * @see Variable#invoke Variable.invoke
     */
    Variable invoke(String name, Object... values);

    /**
     * @hidden
     */
    default Variable invoke(String name) {
        return invoke(name, BaseType.NO_ARGS);
    }

    /**
     * Invoke a {@code super} constructor method on the enclosing object of this method, from
     * within a constructor.
     *
     * @param values {@link Variable Variables} or constants
     * @throws IllegalArgumentException if a value isn't a variable or a supported constant
     * @throws IllegalStateException if not defining a constructor, or if a matching
     * constructor isn't found
     */
    void invokeSuperConstructor(Object... values);

    /**
     * @hidden
     */
    default void invokeSuperConstructor() {
        invokeSuperConstructor(BaseType.NO_ARGS);
    }

    /**
     * Invoke a {@code this} constructor method on the enclosing object of this method, from
     * within a constructor.
     *
     * @param values {@link Variable Variables} or constants
     * @throws IllegalArgumentException if a value isn't a variable or a supported constant
     * @throws IllegalStateException if not defining a constructor, or if a matching
     * constructor isn't found
     */
    void invokeThisConstructor(Object... values);

    /**
     * @hidden
     */
    default void invokeThisConstructor() {
        invokeThisConstructor(BaseType.NO_ARGS);
    }

    /**
     * Invoke a method via a {@link MethodHandle}. If making a class to be loaded {@link
     * ClassMaker#beginExternal externally}, the handle must be truly {@link Constable}.
     *
     * @param handle runtime method handle
     * @param values {@link Variable Variables} or constants
     * @return the result of the method, which is null if it's void
     * @throws IllegalArgumentException if a value isn't a variable or a supported constant
     * @throws IllegalStateException if defining an external class and the handle isn't truly
     * {@code Constable}
     */
    Variable invoke(MethodHandle handle, Object... values);

    /**
     * @hidden
     */
    default Variable invoke(MethodHandle handle) {
        return invoke(handle, BaseType.NO_ARGS);
    }

    /**
     * Allocate a new object. If the type is an ordinary object, a matching constructor is
     * invoked. If the type is an array, then no constructor is invoked, and the given values
     * represent array dimension sizes.
     *
     * @param type class name or {@link Class} instance
     * @param values {@link Variable Variables} or constants
     * @return the new object
     * @throws IllegalArgumentException if the type is unsupported, or if a value isn't a
     * variable or a supported constant
     * @throws IllegalStateException if the constructor isn't found
     */
    Variable new_(Object type, Object... values);

    /**
     * @hidden
     */
    default Variable new_(Object type) {
        return new_(type, BaseType.NO_ARGS);
    }

    /**
     * Define an exception handler here, which catches exceptions between the given labels. Any
     * code prior to the handler must not flow into it directly.
     *
     * @param type exception type to catch; pass null to catch anything
     * @return a variable which references the exception instance
     * @throws IllegalArgumentException if the type is unsupported
     * @throws IllegalStateException if the start label is unpositioned
     */
    Variable catch_(Label tryStart, Label tryEnd, Object type);

    /**
     * Define an exception handler here, which catches exceptions between the given labels. Any
     * code prior to the handler must not flow into it directly. This variant supports matching
     * on multiple exception types, and the effective exception type is the nearest common
     * superclass.
     *
     * @param types exception types to catch; pass null to catch anything
     * @return a variable which references the exception instance
     * @throws IllegalArgumentException if a type is unsupported, or if no types are given
     * @throws IllegalStateException if the start label is unpositioned
     */
    Variable catch_(Label tryStart, Label tryEnd, Object... types);

    /**
     * Convenience method which defines an exception handler here. Code prior to the handler
     * flows around it.
     *
     * @param type exception type to catch; pass null to catch anything
     * @param handler receives a variable which references the exception instance
     * @throws IllegalArgumentException if the type is unsupported
     * @throws IllegalStateException if the start label is unpositioned
     */
    void catch_(Label tryStart, Object type, Consumer<Variable> handler);

    /**
     * Define a finally handler which is generated for every possible exit path between the
     * start label and here.
     *
     * @param handler called for each exit path to generate handler code
     * @throws IllegalStateException if the start label is unpositioned
     */
    void finally_(Label tryStart, Runnable handler);

    /**
     * Concatenate variables and constants together into a new {@link String} in the same
     * matter as the Java concatenation operator. If no values are given, the returned variable
     * will refer to the empty string.
     *
     * @param values {@link Variable Variables} or constants
     * @return the result in a new {@code String} variable
     * @throws IllegalArgumentException if a value isn't a variable or a supported constant
     */
    Variable concat(Object... values);

    /**
     * Access a {@link VarHandle} via a pseudo field. If making a class to be loaded {@link
     * ClassMaker#beginExternal externally}, the handle must be truly {@link Constable}.
     *
     * <p>All of the coordinate values must be provided up front, which are then used each time
     * the {@code VarHandle} is accessed. Variable coordinates are read each time the access
     * field is used &mdash; they aren't fixed to the initial value. In addition, the array of
     * coordinate values isn't cloned, permitting changes without needing to obtain a new
     * access field.
     *
     * <p>A {@code VarHandle} can also be accessed by calling {@link VarHandle#toMethodHandle
     * toMethodHandle}, which is then passed to the {@link #invoke(MethodHandle, Object...)
     * invoke} method.
     *
     * @param handle runtime variable handle
     * @param values {@link Variable Variables} or constants for each coordinate
     * @return a pseudo field which accesses the variable
     * @throws IllegalArgumentException if a value isn't a variable or a supported constant, or
     * if the number of values doesn't match the number of coordinates
     * @throws IllegalStateException if defining an external class and the handle isn't truly
     * {@code Constable}
     */
    Field access(VarHandle handle, Object... values);

    /**
     * @hidden
     */
    default Field access(VarHandle handle) {
        return access(handle, BaseType.NO_ARGS);
    }

    /**
     * Append an instruction which does nothing, which can be useful for debugging.
     */
    void nop();

    /**
     * Add an inner class to this method. The actual class name will have a suitable suffix
     * applied to ensure uniqueness. The inner class doesn't have access to the local variables
     * of the enclosing method, and so they must be passed along explicitly.
     *
     * <p>The returned {@code ClassMaker} instance isn't attached to this maker, and so it can
     * be acted upon by a different thread.
     *
     * @param className simple class name; pass null to use default
     * @throws IllegalArgumentException if not given a simple class name
     * @throws IllegalStateException if the enclosing class or method is finished
     */
    ClassMaker addInnerClass(String className);

    /**
     * Finishes the definition of a standalone method.
     *
     * @throws IllegalStateException if already finished or if not a standalone method
     * @see #begin
     */
    MethodHandle finish();
}
