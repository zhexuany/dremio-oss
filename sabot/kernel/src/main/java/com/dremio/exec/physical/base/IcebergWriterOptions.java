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
package com.dremio.exec.physical.base;


import javax.annotation.Nullable;

import org.immutables.value.Value;

import com.dremio.exec.store.dfs.IcebergTableProps;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableIcebergWriterOptions.Builder.class)
@Value.Immutable
public interface IcebergWriterOptions {
  /**
   * Table properties
   */
  @Nullable
  IcebergTableProps getIcebergTableProps();

  static IcebergWriterOptions makeDefault() {
    return new ImmutableIcebergWriterOptions.Builder().build();
  }
}
