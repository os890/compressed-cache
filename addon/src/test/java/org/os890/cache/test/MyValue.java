/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.os890.cache.test;

/**
 * Test value object used to exercise cache put/get round-trips.
 */
public class MyValue {

    private final String label;
    private final int value;

    /**
     * Creates a new test value.
     *
     * @param label a descriptive label
     * @param value an integer value
     */
    public MyValue(String label, int value) {
        this.label = label;
        this.value = value;
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the integer value.
     *
     * @return the integer value
     */
    public int getValue() {
        return value;
    }

    /**
     * Compares this value to the specified object for equality.
     *
     * @param o the object to compare with
     * @return {@code true} if the given object has the same label and value
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MyValue myValue = (MyValue) o;

        if (value != myValue.value) {
            return false;
        }
        return label != null ? label.equals(myValue.label) : myValue.label == null;
    }

    /**
     * Returns a hash code based on the label and value fields.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int result = label != null ? label.hashCode() : 0;
        result = 31 * result + value;
        return result;
    }
}
