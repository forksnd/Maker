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

import java.io.OutputStream;
import java.io.IOException;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Allows new classes and interfaces to be defined dynamically.
 *
 * @author Brian S O'Neill
 * @see #begin
 */
public interface ClassMaker extends Maker {
    /**
     * Begin defining a class with an automatically assigned name.
     */
    public static ClassMaker begin() {
        return begin(null, null, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to automatically assign a name
     */
    public static ClassMaker begin(String className) {
        return begin(className, null, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to automatically assign a name
     * @param parentLoader parent class loader; pass null to use default
     */
    public static ClassMaker begin(String className, ClassLoader parentLoader) {
        return begin(className, parentLoader, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to automatically assign a name
     * @param parentLoader parent class loader; pass null to use default
     * @param key an opaque key used for creating distinct class loaders
     */
    public static ClassMaker begin(String className, ClassLoader parentLoader, Object key) {
        return TheClassMaker.begin(false, className, false, parentLoader, key, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to automatically assign a name
     * @param lookup finish loading the class using this lookup object
     */
    public static ClassMaker begin(String className, MethodHandles.Lookup lookup) {
        Class<?> clazz = lookup.lookupClass();
        if (className == null) {
            className = clazz.getName();
        }
        ClassLoader loader = clazz.getClassLoader();
        return TheClassMaker.begin(false, className, false, loader, null, lookup);
    }

    /**
     * Begin defining a class intended to be loaded from a file. The class name exactly matches
     * the one given, and {@link Variable#setExact setExact} is unsupported. All classes
     * defined from this maker will also be external.
     *
     * @param className fully qualified class name
     */
    public static ClassMaker beginExternal(String className) {
        return TheClassMaker.begin(true, className, true, null, null, null);
    }

    /**
     * Begin defining another class with the same loader and lookup as this one. The actual
     * class name will have a suffix applied to ensure uniqueness, unless this maker creates
     * external classes.
     *
     * <p>The returned {@code ClassMaker} instance isn't attached to this maker, and so it can
     * be acted upon by a different thread.
     *
     * @param className fully qualified class name; pass null to automatically assign a name
     * (unless external)
     * @see #addInnerClass
     */
    public ClassMaker another(String className);

    /**
     * Switch this class to be public. Classes are package-private by default.
     *
     * @return this
     */
    public ClassMaker public_();

    /**
     * Switch this class to be private. Classes are package-private by default.
     *
     * @return this
     */
    public ClassMaker private_();

    /**
     * Switch this class to be protected. Classes are package-private by default.
     *
     * @return this
     */
    public ClassMaker protected_();

    /**
     * Switch this class to be static. Classes are non-static by default.
     *
     * @return this
     */
    public ClassMaker static_();

    /**
     * Switch this class to be final. Classes are non-final by default.
     *
     * @return this
     */
    public ClassMaker final_();

    /**
     * Switch this class to be an interface. Classes are classes by default.
     *
     * @return this
     */
    public ClassMaker interface_();

    /**
     * Switch this class to be abstract. Classes are non-abstract by default.
     *
     * @return this
     */
    public ClassMaker abstract_();

    /**
     * Indicate that this class is synthetic. Classes are non-synthetic by default.
     *
     * @return this
     */
    public ClassMaker synthetic();

    /**
     * Indicate that this class is defining an enum. No checks or modifications are performed
     * to ensure that the enum class is defined correctly.
     *
     * @return this
     */
    public ClassMaker enum_();

    /**
     * Set a class that this class extends.
     *
     * @param superClass non-null Class or String
     * @return this
     * @throws IllegalStateException if already assigned
     */
    public ClassMaker extend(Object superClass);

    /**
     * Add an interface that this class or interface implements.
     *
     * @param iface Class or String
     * @return this
     */
    public ClassMaker implement(Object iface);

    /**
     * Add a field to the class.
     *
     * @param type a class or name
     * @throws IllegalStateException if field is already defined
     */
    public FieldMaker addField(Object type, String name);

    /**
     * Add a method to this class.
     *
     * @param retType a class or name; can be null if method returns void
     * @param paramTypes classes or names; can be null if method accepts no parameters
     */
    public MethodMaker addMethod(Object retType, String name, Object... paramTypes);

    /**
     * Add a method to this class.
     *
     * @param type defines the return type and parameter types
     */
    public default MethodMaker addMethod(String name, MethodType type) {
        return addMethod(type.returnType(), name, (Object[]) type.parameterArray());
    }

    /**
     * Add a constructor to this class.
     *
     * @param paramTypes classes or names; can be null if constructor accepts no parameters
     */
    public MethodMaker addConstructor(Object... paramTypes);

    /**
     * Add a constructor to this class.
     *
     * @param type defines the parameter types
     * @throws IllegalArgumentException if return type isn't void
     */
    public default MethodMaker addConstructor(MethodType type) {
        if (type.returnType() != void.class) {
            throw new IllegalArgumentException();
        }
        return addConstructor((Object[]) type.parameterArray());
    }

    /**
     * Add code to the static initializer of this class. Multiple initializers can be added,
     * and they're all stitched together when the class definition is finished. Returning from
     * one initializer only breaks out of the local scope.
     */
    public MethodMaker addClinit();

    /**
     * Add an inner class to this class. The actual class name will have a suitable suffix
     * applied to ensure uniqueness.
     *
     * <p>The returned {@code ClassMaker} instance isn't attached to this maker, and so it can
     * be acted upon by a different thread.
     *
     * @param className simple class name; pass null to use default
     * @throws IllegalArgumentException if not given a simple class name
     * @see #another
     */
    public ClassMaker addInnerClass(String className);

    /**
     * Set the source file of this class file by adding a source file attribute.
     *
     * @return this
     */
    public ClassMaker sourceFile(String fileName);

    /**
     * Returns an opaque type object which represents this class as an array.
     *
     * @param dimensions must be at least 1
     * @throws IllegalArgumentException if dimensions is out of bounds
     */
    public Object arrayType(int dimensions);

    /**
     * Returns the class loader that the finished class will be loaded into.
     */
    public ClassLoader classLoader();

    /**
     * Finishes the definition of the new class.
     *
     * @throws IllegalStateException if already finished or if the definition is broken
     */
    public Class<?> finish();

    /**
     * Finishes the definition of the new class, returning a lookup which has full privilege
     * access to the class. Calling this method has the side effect of forcing the new class to
     * be initialized.
     *
     * @return the lookup for the class; call {@link MethodHandles.Lookup#lookupClass lookupClass}
     * to obtain the actual class
     * @throws IllegalStateException if already finished or if the definition is broken
     */
    public MethodHandles.Lookup finishLookup();

    /**
     * Finishes the definition of a new hidden class, returning a lookup which has full
     * privilege access to the class. Hidden classes are automatically unloaded when no longer
     * referenced, even if the class loader still is. If a lookup object was passed to the
     * begin method, the hidden class is defined in the same nest as the lookup class.
     *
     * <p>This feature is fully supported only with Java 15 and above. Hidden classes created
     * with earlier versions don't support all the lookup features.
     *
     * @return the lookup for the class; call {@link MethodHandles.Lookup#lookupClass lookupClass}
     * to obtain the actual class
     * @throws IllegalStateException if already finished or if the definition is broken
     */
    public MethodHandles.Lookup finishHidden();

    /**
     * Finishes the definition of the new class into a byte array.
     *
     * @throws IllegalStateException if already finished or if the definition is broken
     */
    public byte[] finishBytes();

    /**
     * Finishes the definition of the new class and writes it to a stream.
     *
     * @throws IllegalStateException if already finished or if the definition is broken
     */
    public void finishTo(OutputStream out) throws IOException;
}
