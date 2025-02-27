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

import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;

/**
 * Describes an operator that expects a single child operator as its input.
 */
public abstract class AbstractSingle extends AbstractBase {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AbstractSingle.class);

  protected final PhysicalOperator child;

  public AbstractSingle(
      OpProps props,
      PhysicalOperator child) {
    super(props);
    this.child = child;
  }

  @Override
  public Iterator<PhysicalOperator> iterator() {
    return Iterators.singletonIterator(child);
  }

  public PhysicalOperator getChild(){
    return child;
  }

  @Override
  @JsonIgnore
  public final PhysicalOperator getNewWithChildren(List<PhysicalOperator> children) {
    Preconditions.checkArgument(children.size() == 1);
    return getNewWithChild(children.iterator().next());
  }

  protected abstract PhysicalOperator getNewWithChild(PhysicalOperator child);

}
