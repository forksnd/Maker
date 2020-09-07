/*
 *  Copyright 2020 Cojen.org
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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests for special code reducing steps.
 *
 * @author Brian S O'Neill
 */
public class ReduceTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ReduceTest.class.getName());
    }

    @Test
    public void deadStore() throws Exception {
        // Creates a bunch of useless push/store operations which should get eliminated. Short
        // of disassembly, there's no way to verify that this works. Check the coverage report.

        ClassMaker cm = ClassMaker.begin(null).public_();
        MethodMaker mm = cm.addMethod(null, "test").public_().static_();

        var a = mm.var(int.class).set(0);

        for (int i=0; i<100; i++) {
            a.get();
        }

        cm.finish().getMethod("test").invoke(null);
    }
}
