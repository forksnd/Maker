/*
 *  Copyright 2022 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

import java.lang.reflect.InvocationTargetException;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class AttributeTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(AttributeTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        cm.addAttribute("Synthetic");
        cm.addAttribute("SourceFile", "basic");

        MethodMaker mm = cm.addMethod(null, "test").public_().static_();
        mm.new_(Exception.class).throw_();

        Class<?> clazz = cm.finish();

        assertTrue(clazz.isSynthetic());

        try {
            clazz.getMethod("test").invoke(null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertEquals("basic", cause.getStackTrace()[0].getFileName());
        }
    }

    @Test
    public void misc() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        ClassMaker cm2 = ClassMaker.begin().public_().extend(cm);

        cm.addAttribute("Junk1", "hello");
        cm.addAttribute("Junk2", String.class);
        cm.addAttribute("Junk3", 10);
        cm.addAttribute("Junk4", 10L);
        cm.addAttribute("Junk5", 10.1f);
        cm.addAttribute("Junk6", 10.1d);
        cm.addAttribute("Junk7", (Object) new String[] {"hello"});
        cm.addAttribute("Junk8", (Object) new Integer[] {1, 2, Integer.MAX_VALUE});
        cm.addAttribute("Junk9", (Object) new Object[] {Integer.MAX_VALUE, "hello", cm});
        cm.addAttribute("Junk10", (Object) null);
        cm.addAttribute("Junk11", (Object[]) new Object[] {null});

        // Encode an array of arrays, each with a one-byte array length prefix.
        cm.addAttribute("Junk12", new byte[] {2}, new Object[][] {
            {new byte[] {2}, "hello", 10}, {new byte[] {3}, 10L, 10.1d, cm}
        });

        cm.addAttribute("SourceDebugExtension", new byte[10]);

        // The first value encodes the short array length in big-endian format.
        // Note: This doesn't seal the class unless the major version is 61 or higher (Java 17).
        cm.addAttribute("PermittedSubclasses", new byte[] {0, 1}, cm2);

        // Format for an enum default.
        cm.addAttribute("AnnotationDefault", new byte[] {'e'}, "java.lang.Thread.State", "NEW");

        Class<?> clazz = cm.finish();
        Class<?> clazz2 = cm2.finish();
    }
}
