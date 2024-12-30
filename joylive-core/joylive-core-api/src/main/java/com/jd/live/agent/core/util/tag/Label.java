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
package com.jd.live.agent.core.util.tag;

import com.jd.live.agent.core.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.jd.live.agent.core.util.StringUtils.COMMA;
import static com.jd.live.agent.core.util.StringUtils.splitList;

/**
 * The {@code Label} interface defines a contract for label handling. It provides constants
 * representing common label regions and methods for retrieving and parsing label values.
 */
public interface Label {

    /**
     * Gets the key of the label.
     *
     * @return the label key
     */
    String getKey();

    /**
     * Gets a list of values associated with the label.
     *
     * @return a list of label values
     */
    List<String> getValues();

    /**
     * Gets the first value associated with the label, or the default value if no values are present.
     *
     * @return the first label value or the default value
     */
    String getFirstValue();

    /**
     * Gets a single value associated with the label, or the default value if no values are present.
     *
     * @return a single label value or the default value
     */
    String getValue();

    /**
     * Parses a label value string into a list of strings. The parsing logic handles special
     * formatting where values are enclosed in square brackets and separated by commas.
     *
     * @param value the label value string to parse
     * @return a list of parsed label values
     */
    static List<String> parseValue(String value) {
        List<String> result;
        if (value == null || value.isEmpty()) {
            result = new ArrayList<>();
        } else if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
            if (value.length() == 2) {
                result = new ArrayList<>();
            } else {
                result = splitList(value.substring(1, value.length() - 1), COMMA);
            }
        } else {
            result = new ArrayList<>(1);
            result.add(value);
        }
        return result;
    }

    /**
     * Joins a collection of strings into a single string with a specified separator,
     * enclosed by a specified prefix and suffix.
     *
     * @param values The collection of strings to join.
     * @return A string that is the concatenation of the strings in the collection,
     * separated by commas and enclosed in square brackets. If the collection
     * is null or empty, an empty string is returned.
     */
    static String join(Collection<String> values) {
        return StringUtils.join(values, ",", "[", "]", false);
    }

}
