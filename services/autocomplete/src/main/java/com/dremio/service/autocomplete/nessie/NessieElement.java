/*
 * Copyright (C) 2017-2019 Dremio Corporation
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
package com.dremio.service.autocomplete.nessie;

import com.google.common.base.Preconditions;

/**
 * The base class to represent all Nessie elements
 */
public abstract class NessieElement {
  private final Hash hash;

  protected NessieElement(Hash hash) {
    Preconditions.checkNotNull(hash);
    this.hash = hash;
  }

  public Hash getHash() {
    return hash;
  }

  public abstract NessieElementType getType();

  public abstract <R> R accept(NessieElementVisitor<R> visitor);
}
