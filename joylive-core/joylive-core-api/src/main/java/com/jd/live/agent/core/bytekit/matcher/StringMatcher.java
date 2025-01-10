/*
 * Copyright © ${year} ${owner} (${email})
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.live.agent.core.bytekit.matcher;

import lombok.Getter;

/**
 * StringMatcher
 *
 * <p>Implement according to `net.bytebuddy.matcher.StringMatcher`</p>
 *
 * @since 1.0.0
 */
@Getter
public class StringMatcher extends AbstractJunction<String> {

    private final String value;

    private final OperationMode mode;

    public StringMatcher(String value, OperationMode mode) {
        this.value = value;
        this.mode = mode;
    }

    @Override
    public boolean match(String target) {
        return target != null && mode.matches(value, target);
    }

    /**
     * Operation Mode
     *
     * <p>Implement according to `net.bytebuddy.matcher.StringMatcher.Mode`</p>
     */
    public enum OperationMode {

        /**
         * Checks if two strings equal and respects casing differences.
         */
        EQUALS_FULLY("equals") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.equals(expected);
            }
        },

        /**
         * Checks if two strings equal without respecting casing differences.
         */
        EQUALS_FULLY_IGNORE_CASE("equalsIgnoreCase") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.equalsIgnoreCase(expected);
            }
        },

        /**
         * Checks if a string starts with the a second string with respecting casing differences.
         */
        STARTS_WITH("startsWith") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.startsWith(expected);
            }
        },

        /**
         * Checks if a string starts with a second string without respecting casing differences.
         */
        STARTS_WITH_IGNORE_CASE("startsWithIgnoreCase") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.toLowerCase().startsWith(expected.toLowerCase());
            }
        },

        /**
         * Checks if a string ends with a second string with respecting casing differences.
         */
        ENDS_WITH("endsWith") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.endsWith(expected);
            }
        },

        /**
         * Checks if a string ends with a second string without respecting casing differences.
         */
        ENDS_WITH_IGNORE_CASE("endsWithIgnoreCase") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.toLowerCase().endsWith(expected.toLowerCase());
            }
        },

        /**
         * Checks if a string contains another string with respecting casing differences.
         */
        CONTAINS("contains") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.contains(expected);
            }
        },

        /**
         * Checks if a string contains another string without respecting casing differences.
         */
        CONTAINS_IGNORE_CASE("containsIgnoreCase") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.toLowerCase().contains(expected.toLowerCase());
            }
        },

        /**
         * Checks if a string can be matched by a regular expression.
         */
        MATCHES("matches") {
            @Override
            protected boolean matches(String expected, String actual) {
                return actual.matches(expected);
            }
        };

        /**
         * A description of the string for providing meaningful {@link Object#toString()} implementations for
         * method matchers that rely on a match mode.
         */
        private final String description;

        /**
         * Creates a new match mode.
         *
         * @param description The description of this mode for providing meaningful {@link Object#toString()}
         *                    implementations.
         */
        OperationMode(String description) {
            this.description = description;
        }

        /**
         * Returns the description of this match mode.
         *
         * @return The description of this match mode.
         */
        protected String getDescription() {
            return description;
        }

        /**
         * Matches a string against another string.
         *
         * @param expected The target of the comparison against which the {@code actual} string is compared.
         * @param actual   The source which is subject of the comparison to the {@code expected} value.
         * @return {@code true} if the source matches the target.
         */
        protected abstract boolean matches(String expected, String actual);
    }
}
