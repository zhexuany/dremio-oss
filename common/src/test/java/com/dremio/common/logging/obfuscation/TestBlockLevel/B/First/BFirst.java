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
package com.dremio.common.logging.obfuscation.TestBlockLevel.B.First;

import java.util.List;

import com.dremio.TestBlockLevel.TestBlockLevelLogging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * class for testing custom log filtering
 */
public class BFirst {

  private static ch.qos.logback.classic.Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(BFirst.class);

  public List<ILoggingEvent> testLogFiltering() {
    return TestBlockLevelLogging.testLogFilteringUtil(logger);

  }
}