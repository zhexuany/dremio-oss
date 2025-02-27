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
package com.dremio.exec;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocatorFactory;

import com.dremio.common.VM;
import com.dremio.common.config.SabotConfig;
import com.google.common.collect.Lists;

public class TestMemoryRetention {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TestMemoryRetention.class);

  private static final int SMALL_AVERAGE_BYTES = 1024 * 32;
  private static final int LARGE_BYTES = 32 * 1024 * 1024;
  private static final int PARALLEL_THREADS = 32;
  private static final double SMALL_ALLOCATION_MEM = 0.20;
  private static final double OVERHEAD_ALLOWANCE = 0.20;
  private static final List<Integer> ALLOCATIONS;
  private static final int MAX_ALLOCS = 100;
  private static final AtomicInteger ALLOCS = new AtomicInteger(0);

  static {
    final Random r = new Random();
    final long maxMemory = VM.getMaxDirectMemory();
    final long maxPerThread = maxMemory / PARALLEL_THREADS;
    final double smallCount = (maxPerThread * SMALL_ALLOCATION_MEM) / SMALL_AVERAGE_BYTES;
    final double largeCount = (maxPerThread * (1 - SMALL_ALLOCATION_MEM - OVERHEAD_ALLOWANCE)) / LARGE_BYTES;
    final List<Integer> allocations = Lists.newArrayList();

    for (int i = 0; i < smallCount; i++) {
      allocations.add(SMALL_AVERAGE_BYTES / 2 + r.nextInt(SMALL_AVERAGE_BYTES));
    }

    for (int i = 0; i < largeCount; i++) {
      allocations.add(LARGE_BYTES);
    }
    Collections.shuffle(allocations);
    ALLOCATIONS = allocations;
  }

  public static void main(String[] args) throws Exception {

    final SabotConfig config = SabotConfig.create();
    final BufferAllocator a = RootAllocatorFactory.newRoot(config);
    for (int i = 0; i < PARALLEL_THREADS; i++) {
      Alloc alloc = new Alloc(a);
      alloc.start();
    }
  }

  private static class Alloc extends Thread {
    final BufferAllocator allocator;

    Alloc(BufferAllocator allocator) {
      this.allocator = allocator;
    }

    @SuppressWarnings("DoNotCall") // Thread.run is intentionally getting called here
    @Override
    public void run() {
      final Random r = new Random();
      try {

        if (ALLOCS.incrementAndGet() > MAX_ALLOCS) {
          Thread.sleep(50000000000L);
        }

        Thread.sleep(r.nextInt(8000));
      } catch (InterruptedException e) {
        return;
      }

      logger.info("Starting alloc.");
      final List<ArrowBuf> bufs = Lists.newLinkedList();
      for (final Integer i : ALLOCATIONS) {
        bufs.add(allocator.buffer(i));
      }
      Collections.shuffle(bufs);
      logger.info("Finished alloc.");

      final Dealloc d = new Dealloc(bufs, allocator);

      // sometimes we'll deallocate locally, sometimes in different thread.
      if (r.nextBoolean()) {
        d.start();
      } else {
        d.run();
      }
    }
  }

  private static class Dealloc extends Thread {
    final List<ArrowBuf> bufs;
    final BufferAllocator a;

    public Dealloc(List<ArrowBuf> bufs, BufferAllocator a) {
      this.bufs = bufs;
      this.a = a;
    }

    @Override
    public void run() {
      try {
        Thread.sleep(8000);
        logger.info("Starting release.");
        for (final ArrowBuf buf : bufs) {
          buf.close();
        }
        logger.info("Finished release.");

      } catch (InterruptedException e) {
        return;
      }

      // start another.
      Alloc alloc = new Alloc(a);
      alloc.start();
    }
  }
}
