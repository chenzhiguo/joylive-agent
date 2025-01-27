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
package com.jd.live.agent.governance.context.bag;

import com.jd.live.agent.bootstrap.util.AbstractAttributes;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Courier extends AbstractAttributes implements Carrier {

    protected Map<String, Cargo> cargos;

    @Override
    public Collection<Cargo> getCargos() {
        return cargos == null ? null : cargos.values();
    }

    @Override
    public Cargo getCargo(String key) {
        return cargos == null ? null : cargos.get(key);
    }

    @Override
    public void addCargo(Cargo cargo) {
        if (cargo != null) {
            String name = cargo.getKey();
            if (name != null && !name.isEmpty()) {
                if (cargos == null) {
                    cargos = new HashMap<>(8);
                }
                Cargo old = cargos.putIfAbsent(cargo.getKey(), cargo);
                if (old != null && old != cargo) {
                    old.add(cargo.getValues());
                }
            }
        }
    }

    @Override
    public void addCargo(String key, String value) {
        if (key != null && !key.isEmpty()) {
            if (cargos == null) {
                cargos = new HashMap<>(8);
            }
            cargos.computeIfAbsent(key, Cargo::new).add(value);
        }
    }

    @Override
    public void setCargo(String key, String value) {
        if (key != null && !key.isEmpty()) {
            if (cargos == null) {
                cargos = new HashMap<>(8);
            }
            cargos.put(key, new Cargo(key, value));
        }
    }

    @Override
    public void removeCargo(String key) {
        if (key != null && !key.isEmpty() && cargos != null) {
            cargos.remove(key);
        }
    }

}
