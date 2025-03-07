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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.ref.WeakReference;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class ClassInjector extends ClassLoader {
    private static final WeakCache<Object, ClassInjector> cInjectors = new WeakCache<>();

    private final Map<String, Boolean> mReservedNames;
    private final WeakCache<String, Group> mPackageGroups;

    private ClassInjector(boolean explicit, ClassLoader parent) {
        super(parent);
        mReservedNames = explicit ? null : new WeakHashMap<>();
        mPackageGroups = new WeakCache<>();
    }

    static ClassInjector find(boolean explicit, ClassLoader parentLoader, Object key) {
        Objects.requireNonNull(parentLoader);

        final Object injectorKey = new Key(explicit, parentLoader, key);

        ClassInjector injector = cInjectors.get(injectorKey);

        if (injector == null) {
            synchronized (cInjectors) {
                injector = cInjectors.get(injectorKey);
                if (injector == null) {
                    injector = new ClassInjector(explicit, parentLoader);
                    cInjectors.put(injectorKey, injector);
                }
            }
        }

        return injector;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Group group = findPackageGroup(name, false);

        if (group != null) {
            Class<?> clazz = group.tryFindClass(name);
            if (clazz != null) {
                return clazz;
            }
        }

        // Classes aren't defined directly in the ClassInjector itself, so go straight to the
        // parent. Calling super.loadClass causes deadlocks.
        return getParent().loadClass(name);
    }

    Class<?> define(Group group, String name, byte[] b) {
        try {
            return group.define(name, b);
        } catch (LinkageError e) {
            // Replace duplicate name definition with a better exception.
            try {
                loadClass(name);
                throw new IllegalStateException("Class is already defined: " + name);
            } catch (ClassNotFoundException e2) {
            }
            throw e;
        } finally {
            unreserve(name);
        }
    }

    void unreserve(String name) {
        if (mReservedNames != null) {
            synchronized (mReservedNames) {
                mReservedNames.remove(name);
            }
        }
    }

    /**
     * @param className can be null
     * @param willUse is true when class will later be defined by this injector
     * @return actual class name
     */
    String reserve(TheClassMaker maker, String className, boolean willUse) {
        if (mReservedNames == null) {
            Objects.requireNonNull(className);
            if (willUse && maker.mInjectorGroup == null) {
                // Maintain a strong reference to the group.
                maker.mInjectorGroup = findPackageGroup(className, true);
            }
            return className;
        }

        if (className == null) {
            className = ClassMaker.class.getName();
        }

        var rnd = ThreadLocalRandom.current();

        // Use a small identifier if possible, making it easier to read stack traces and
        // decompiled classes.
        int range = 10;

        while (true) {
            // Use '-' instead of '$' to prevent conflicts with inner class names.
            String mangled = className + '-' + rnd.nextInt(range);

            if (tryReserve(maker, mangled, willUse)) {
                return mangled;
            }

            if (range < 1_000_000_000) {
                range *= 10;
            }
        }
    }

    /**
     * @return false if the name is already taken
     */
    private boolean tryReserve(TheClassMaker maker, String name, boolean willUse) {
        synchronized (mReservedNames) {
            if (mReservedNames.put(name, Boolean.TRUE) != null) {
                return false;
            }
        }

        Group group = maker.mInjectorGroup;

        if (group == null) {
            group = findPackageGroup(name, true);
            // Maintain a strong reference to the group.
            maker.mInjectorGroup = group;
        }

        if (!group.isLoaded(name)) {
            // Only check the parent loader when it will be used directly. This avoids
            // creating useless class loading lock objects that never get cleaned up.
            ClassLoader parent;
            if (willUse || (parent = getParent()) == null) {
                return true;
            }
            try {
                parent.loadClass(name);
            } catch (ClassNotFoundException e) {
                return true;
            }
        }

        return false;
    }

    private static class Key extends WeakReference<ClassLoader> {
        private final Object mRest;
        private final int mHash;

        Key(boolean explicit, ClassLoader loader, Object rest) {
            super(loader);
            mRest = rest;
            int hash = loader.hashCode() * 31;
            if (rest != null) {
                hash += rest.hashCode();
            }
            hash &= ~(1 << 31);
            if (explicit) {
                hash |= 1 << 31;
            }
            mHash = hash;
        }

        @Override
        public int hashCode() {
            return mHash;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof Key other
                && mHash == other.mHash
                && Objects.equals(get(), other.get()) && Objects.equals(mRest, other.mRest);
        }
    }

    private Group findPackageGroup(String className, boolean create) {
        String packageName;
        {
            int ix = className.lastIndexOf('.');
            packageName = ix <= 0 ? "" : className.substring(0, ix);
        }

        Group group = mPackageGroups.get(packageName);
        if (group == null) {
            synchronized (mPackageGroups) {
                group = mPackageGroups.get(packageName);
                if (group == null && create) {
                    group = new Group();
                    mPackageGroups.put(packageName, group);
                }
            }
        }

        return group;
    }

    /**
     * A group is a loader for one package.
     */
    class Group extends ClassLoader {
        private volatile MethodHandles.Lookup mLookup;

        private volatile WeakCache<String, Class<?>> mInstalled;

        // Accessed by ConstantsRegistry.
        Map<Class, Object> mConstants;

        private Group() {
            // All group members are at the same level in the hierarchy as the ClassInjector
            // itself, and so the parent for all should be the same. This also ensures that the
            // ClassInjector instance isn't visible externally via the getParent method.
            super(ClassInjector.this.getParent());
        }

        /**
         * Returns a lookup object in the group's package.
         *
         * @param className used to extract the package name
         */
        MethodHandles.Lookup lookup(String className) {
            MethodHandles.Lookup lookup = mLookup;
            if (lookup == null) {
                lookup = makeLookup(className);
            }
            return lookup;
        }

        boolean installClass(Class<?> clazz) {
            while (true) {
                Class<?> component = clazz.getComponentType();
                if (component == null) {
                    break;
                }
                clazz = component;
            }

            if (clazz.isPrimitive()) {
                return false;
            }

            WeakCache<String, Class<?>> installed = mInstalled;

            if (installed == null) {
                synchronized (this) {
                    installed = mInstalled;
                    if (installed == null) {
                        mInstalled = installed = new WeakCache<>();
                    }
                }
            }

            String name = clazz.getName();

            synchronized (installed) {
                Class<?> existing = installed.put(name, clazz);
                if (existing == null) {
                    return true;
                }
                if (existing == clazz) {
                    return false;
                }
                installed.put(name, existing); // restore the original entry
                throw new IllegalStateException();
            }
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> clazz = tryFindInstalled(name);
            return clazz == null ? ClassInjector.this.loadClass(name) : clazz;
        }

        private Class<?> tryFindClass(String name) {
            Class<?> clazz = tryFindInstalled(name);
            return clazz == null ? findLoadedClass(name) : clazz;
        }

        private Class<?> tryFindInstalled(String name) {
            WeakCache<String, Class<?>> installed = mInstalled;
            if (installed == null) {
                return null;
            }
            synchronized (installed) {
                return installed.get(name);
            }
        }

        private Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }

        private boolean isLoaded(String name) {
            return findLoadedClass(name) != null;
        }

        private synchronized MethodHandles.Lookup makeLookup(String className) {
            MethodHandles.Lookup lookup = mLookup;
            if (lookup != null) {
                return lookup;
            }

            className = className.substring(0, className.lastIndexOf('.') + 1) + "lookup";
            var cm = new TheClassMaker(className, ClassInjector.this, this).public_().synthetic();

            var mt = MethodType.methodType(MethodHandles.Lookup.class, Object.class);

            MethodMaker mm = cm.addMethod("lookup", mt).public_().static_().synthetic();
            Label ok = mm.label();
            mm.var(Object.class).setExact(ClassInjector.this).ifEq(mm.param(0), ok);
            mm.new_(IllegalAccessError.class).throw_();
            ok.here();
            mm.return_(mm.var(MethodHandles.class).invoke("lookup"));

            // Ideally, this should be a hidden class which can eventually be GC'd.
            // Unfortunately, this requires that a package-level lookup already exists.
            var clazz = cm.finish();

            try {
                var mh = MethodHandles.publicLookup().findStatic(clazz, "lookup", mt);
                lookup = (MethodHandles.Lookup) mh.invoke(ClassInjector.this);
            } catch (Throwable e) {
                throw TheClassMaker.toUnchecked(e);
            }

            mLookup = lookup;
            return lookup;
        }
    }
}
