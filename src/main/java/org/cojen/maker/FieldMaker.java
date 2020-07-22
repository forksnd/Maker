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

/**
 * Allows new fields to be defined within a class.
 *
 * @author Brian S O'Neill
 * @see ClassMaker#addField
 */
public interface FieldMaker {
    /**
     * Switch this field to be public. Fields are package-private by default.
     *
     * @return this
     */
    public FieldMaker public_();

    /**
     * Switch this field to be private. Fields are package-private by default.
     *
     * @return this
     */
    public FieldMaker private_();

    /**
     * Switch this field to be protected. Fields are package-private by default.
     *
     * @return this
     */
    public FieldMaker protected_();

    /**
     * Switch this field to be static. Fields are non-static by default.
     *
     * @return this
     */
    public FieldMaker static_();

    /**
     * Switch this field to be final. Fields are non-final by default.
     *
     * @return this
     */
    public FieldMaker final_();

    /**
     * Switch this field to be volatile. Fields are non-volatile by default.
     *
     * @return this
     */
    public FieldMaker volatile_();

    /**
     * Switch this field to be transient. Fields are non-transient by default.
     *
     * @return this
     */
    public FieldMaker transient_();

    /**
     * Indicate that this field is synthetic. Fields are non-synthetic by default.
     *
     * @return this
     */
    public FieldMaker synthetic();

    /**
     * Set an initial constant value for this field.
     *
     * @return this
     * @throws IllegalStateException if not a static field or if constant isn't supported
     */
    public FieldMaker init(Object value);
}
