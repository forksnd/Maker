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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * Allows new methods to be defined within a class.
 *
 * @author Brian S O'Neill
 * @see ClassMaker#addMethod
 */
public interface MethodMaker {
    /**
     * Switch this method to be public. Methods are package-private by default.
     *
     * @return this
     */
    public MethodMaker public_();

    /**
     * Switch this method to be private. Methods are package-private by default.
     *
     * @return this
     */
    public MethodMaker private_();

    /**
     * Switch this method to be protected. Methods are package-private by default.
     *
     * @return this
     */
    public MethodMaker protected_();

    /**
     * Switch this method to be static. Methods are non-static by default.
     *
     * @return this
     */
    public MethodMaker static_();

    /**
     * Switch this method to be final. Methods are non-final by default.
     *
     * @return this
     */
    public MethodMaker final_();

    /**
     * Switch this method to be synchronized. Methods are non-synchronized by default.
     *
     * @return this
     */
    public MethodMaker synchronized_();

    /**
     * Switch this method to be abstract. Methods are non-abstract by default.
     *
     * @return this
     */
    public MethodMaker abstract_();

    /**
     * Switch this method to strictfp mode. Methods are non-strict by default.
     *
     * @return this
     */
    public MethodMaker strictfp_();

    /**
     * Switch this method to be native. Methods are non-native by default.
     *
     * @return this
     */
    public MethodMaker native_();

    /**
     * Indicate that this method is synthetic. Methods are non-synthetic by default.
     *
     * @return this
     */
    public MethodMaker synthetic();

    /**
     * Indicate that this method is a bridge, which implements an inherited method exactly, but
     * it delegates to another method which returns a more specialized return type.
     *
     * @return this
     */
    public MethodMaker bridge();

    /**
     * Indicate that this method supports a variable number of arguments.
     *
     * @return this
     * @throws IllegalStateException if last parameter type isn't an object array
     */
    public MethodMaker varargs();

    /**
     * Returns a variable which represents the enclosing class of this method.
     */
    public Variable class_();

    /**
     * Returns the variable which accesses the enclosing object of this method.
     *
     * @throws IllegalStateException if making a static method
     */
    public Variable this_();

    /**
     * Returns a variable which accesses a parameter of the method being built.
     *
     * @param index zero based index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public Variable param(int index);

    /**
     * Returns a new unitialized variable with the given type. Call {@link Variable#set set} to
     * initialize it immediately.
     *
     * @param type a class, class name, or a variable
     * @throws IllegalArgumentException if the type is unsupported
     */
    public Variable var(Object type);

    /**
     * Define a line number to represent the location of the next code instruction.
     */
    public void lineNum(int num);

    /**
     * Returns a new label, initially unpositioned. Call {@link Label#here here} to position it
     * immediately.
     */
    public Label label();

    /**
     * Generates an unconditional goto statement to the given label, which doesn't need to be
     * positioned yet.
     */
    public void goto_(Label label);

    /**
     * Generates a return void statement.
     */
    public void return_();

    /**
     * Generates a statement which returns a variable or a constant.
     *
     * @param value variable or constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void return_(Object value);

    /**
     * Access a static or instance field in the enclosing object of this method.
     *
     * @param name field name
     * @throws IllegalStateException if field isn't found
     */
    public Field field(String name);

    /**
     * Invoke a static or instance method on the enclosing object of this method.
     *
     * @param name the method name
     * @param values variables or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see Variable#invoke
     */
    public Variable invoke(String name, Object... values);

    /**
     * Invoke a static or instance super class method on the enclosing object of this method.
     *
     * @param name the method name
     * @param values variables or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public Variable invokeSuper(String name, Object... values);

    /**
     * Invoke a super class constructor method on the enclosing object of this method, from
     * within a constructor.
     *
     * @param values variables or constants
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void invokeSuperConstructor(Object... values);

    /**
     * Invoke a this constructor method on the enclosing object of this method, from within a
     * constructor.
     *
     * @param values variables or constants
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void invokeThisConstructor(Object... values);

    /**
     * Invoke a method via a {@code MethodHandle}, which only works when the class is built
     * dynamically instead of loaded from a file.
     *
     * @param handle runtime method handle
     * @param values variables or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public Variable invoke(MethodHandle handle, Object... values);

    /**
     * Allocate a new object. If type is an ordinary object, a matching constructor is
     * invoked. If type is an array, no constructor is invoked, and the given values represent
     * array dimension sizes.
     *
     * @param type class name or {@code Class} instance
     * @param values variables or constants
     * @return the new object
     * @throws IllegalArgumentException if the type is unsupported, or if the constructor isn't
     * found
     */
    public Variable new_(Object type, Object... values);

    /**
     * Define an exception handler here, which catches exceptions between the given labels. Any
     * code prior to the handler must not flow into it directly.
     *
     * @param type exception type to catch; pass null to catch anything
     * @return a variable which references the exception instance
     */
    public Variable catch_(Label start, Label end, Object type);

    /**
     * Concatenate variables and constants together into a new {@code String} in the same
     * matter as the Java concatenation operator. If no values are given, the returned variable
     * will refer to the empty string.
     *
     * @param values variables or constants
     * @return the result in a new {@code String} variable
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public Variable concat(Object... values);

    /**
     * Append an instruction which does nothing, which can be useful for debugging.
     */
    public void nop();
}
