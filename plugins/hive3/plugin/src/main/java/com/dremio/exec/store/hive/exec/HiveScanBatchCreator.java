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
package com.dremio.exec.store.hive.exec;

import static com.dremio.exec.store.parquet.RecordReaderIterator.EMPTY_RECORD_ITERATOR;
import static com.dremio.hive.proto.HiveReaderProto.ReaderType.NATIVE_PARQUET;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.security.UserGroupInformation;
import org.pf4j.Extension;

import com.dremio.common.AutoCloseables;
import com.dremio.common.exceptions.ExecutionSetupException;
import com.dremio.common.expression.SchemaPath;
import com.dremio.exec.catalog.StoragePluginId;
import com.dremio.exec.physical.base.OpProps;
import com.dremio.exec.physical.config.TableFunctionConfig;
import com.dremio.exec.record.BatchSchema;
import com.dremio.exec.store.RecordReader;
import com.dremio.exec.store.RuntimeFilter;
import com.dremio.exec.store.ScanFilter;
import com.dremio.exec.store.SplitAndPartitionInfo;
import com.dremio.exec.store.dfs.implicit.CompositeReaderConfig;
import com.dremio.exec.store.hive.Hive3StoragePlugin;
import com.dremio.exec.store.hive.HiveImpersonationUtil;
import com.dremio.exec.store.hive.proxy.HiveProxiedScanBatchCreator;
import com.dremio.exec.store.parquet.RecordReaderIterator;
import com.dremio.hive.proto.HiveReaderProto;
import com.dremio.hive.proto.HiveReaderProto.HiveTableXattr;
import com.dremio.sabot.exec.context.OperatorContext;
import com.dremio.sabot.exec.fragment.FragmentExecutionContext;
import com.dremio.sabot.op.scan.ScanOperator;
import com.dremio.sabot.op.spi.ProducerOperator;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;

@SuppressWarnings("unused")
@Extension
public class HiveScanBatchCreator implements HiveProxiedScanBatchCreator {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HiveScanBatchCreator.class);

  private final FragmentExecutionContext fragmentExecContext;
  private final OperatorContext context;
  private final Hive3StoragePlugin storagePlugin;
  private final HiveTableXattr tableXattr;
  private final HiveConf conf;
  private final CompositeReaderConfig compositeConfig;
  private final UserGroupInformation proxyUgi;
  private final BatchSchema fullSchema;
  private final ScanFilter scanFilter;
  private final Collection<List<String>> referencedTables;
  private final boolean isPartitioned;

  private HiveProxyingSubScan subScanConfig;
  private RecordReaderIterator recordReaderIterator;
  private boolean produceFromBufferedSplits;

  private HiveScanBatchCreator(FragmentExecutionContext fragmentExecContext, OperatorContext context, StoragePluginId storagePluginId,
                               byte[] extendedProperty, List<SchemaPath> columns, List<String> partitionColumns, OpProps opProps,
                               BatchSchema fullSchema, Collection<List<String>> referencedTables, ScanFilter scanFilter,
                               boolean produceBuffered) throws ExecutionSetupException {
    this.fragmentExecContext = fragmentExecContext;
    this.context = context;


    this.storagePlugin = fragmentExecContext.getStoragePlugin(storagePluginId);
    this.conf = storagePlugin.getHiveConf();

    try {
      this.tableXattr = HiveTableXattr.parseFrom(extendedProperty);
    } catch (InvalidProtocolBufferException e) {
      throw new ExecutionSetupException("Failure parsing table extended properties.", e);
    }

    // handle unexpected reader type
    if (tableXattr.getReaderType() != HiveReaderProto.ReaderType.NATIVE_PARQUET &&
      tableXattr.getReaderType() != HiveReaderProto.ReaderType.BASIC) {
      throw new UnsupportedOperationException(tableXattr.getReaderType().name());
    }

    this.isPartitioned = partitionColumns != null && partitionColumns.size() > 0;

    this.proxyUgi = getUGI(storagePlugin, opProps);
    this.fullSchema = fullSchema;
    this.referencedTables = referencedTables;
    this.scanFilter = scanFilter;
    this.compositeConfig = CompositeReaderConfig.getCompound(context, fullSchema, columns, partitionColumns);
    this.produceFromBufferedSplits = produceBuffered;
  }

  public HiveScanBatchCreator(FragmentExecutionContext fragmentExecContext, OperatorContext context,
                              HiveProxyingSubScan config) throws ExecutionSetupException {
    this(fragmentExecContext, context, config.getPluginId(), config.getExtendedProperty(), config.getColumns(),
      config.getPartitionColumns(), config.getProps(), config.getFullSchema(), config.getReferencedTables(),
      config.getFilter(), true);
    this.subScanConfig = config;
  }


  public HiveScanBatchCreator(FragmentExecutionContext fragmentExecContext, OperatorContext context, OpProps opProps,
                              TableFunctionConfig tableFunctionConfig) throws ExecutionSetupException {
    this(fragmentExecContext, context, tableFunctionConfig.getFunctionContext().getPluginId(),
      tableFunctionConfig.getFunctionContext().getExtendedProperty().toByteArray(),
      tableFunctionConfig.getFunctionContext().getColumns(),
      tableFunctionConfig.getFunctionContext().getPartitionColumns(), opProps,
      tableFunctionConfig.getFunctionContext().getFullSchema(),
      tableFunctionConfig.getFunctionContext().getReferencedTables(),
      tableFunctionConfig.getFunctionContext().getScanFilter(),
      false);
  }

  private boolean isParquetSplit(final HiveTableXattr tableXattr, SplitAndPartitionInfo split, boolean isPartitioned) {
    if (tableXattr.getReaderType() == NATIVE_PARQUET) {
      return true;
    }

    Optional<String> tableInputFormat;
    if (isPartitioned) {
      final HiveReaderProto.PartitionXattr partitionXattr = HiveReaderProtoUtil.getPartitionXattr(split);
      tableInputFormat = HiveReaderProtoUtil.getPartitionInputFormat(tableXattr, partitionXattr);
    } else {
      tableInputFormat = HiveReaderProtoUtil.getTableInputFormat(tableXattr);
    }

    if (!tableInputFormat.isPresent()) {
      return false;
    }

    try {
      return MapredParquetInputFormat.class.isAssignableFrom(
        (Class<? extends InputFormat>) Class.forName(tableInputFormat.get()));
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private void classifySplitsAsParquetAndNonParquet(List<SplitAndPartitionInfo> allSplits,
                                                    final HiveTableXattr tableXattr,
                                                    boolean isPartitioned,
                                                    List<SplitAndPartitionInfo> parquetSplits,
                                                    List<SplitAndPartitionInfo> nonParquetSplits) {
    // separate splits into parquet splits and non parquet splits
    for (SplitAndPartitionInfo split : allSplits) {
      if (isParquetSplit(tableXattr, split, isPartitioned)) {
        parquetSplits.add(split);
      } else {
        nonParquetSplits.add(split);
      }
    }
  }

  @Override
  public ProducerOperator create() throws ExecutionSetupException {
    createRecordReaderIterator();
    addSplits(subScanConfig.getSplits());
    return new ScanOperator(subScanConfig, context, recordReaderIterator);
  }


  @Override
  public RecordReaderIterator createRecordReaderIterator() {
    recordReaderIterator = EMPTY_RECORD_ITERATOR;
    return recordReaderIterator;
  }

  @Override
  public RecordReaderIterator getRecordReaderIterator() {
    return recordReaderIterator;
  }

  @Override
  public void produceFromBufferedSplits(boolean toProduce) {
    this.produceFromBufferedSplits = toProduce;
  }

  @Override
  public void addSplits(List<SplitAndPartitionInfo> splits) {
    List<SplitAndPartitionInfo> parquetSplits = new ArrayList<>();
    List<SplitAndPartitionInfo> nonParquetSplits = new ArrayList<>();
    classifySplitsAsParquetAndNonParquet(splits, tableXattr, isPartitioned, parquetSplits, nonParquetSplits);

    if (!parquetSplits.isEmpty()) {
      context.getStats().setLongStat(ScanOperator.Metric.HIVE_FILE_FORMATS,
        context.getStats().getLongStat(ScanOperator.Metric.HIVE_FILE_FORMATS) | (1 << ScanOperator.HiveFileFormat.PARQUET.ordinal()));
    }

    // produce the buffered splits in previous batch.
    recordReaderIterator.produceFromBuffered(true);

    RecordReaderIterator iteratorFromSplits = RecordReaderIterator.join(
      Objects.requireNonNull(ScanWithDremioReader.createReaders(conf, storagePlugin, context,
        tableXattr, compositeConfig, proxyUgi, scanFilter, fullSchema, referencedTables, parquetSplits, produceFromBufferedSplits)),
      Objects.requireNonNull(ScanWithHiveReader.createReaders(conf, fragmentExecContext, context,
        tableXattr, compositeConfig, proxyUgi, scanFilter, isPartitioned, referencedTables, nonParquetSplits)));

    List<RuntimeFilter> existingRuntimeFilters = recordReaderIterator.getRuntimeFilters();

    // the previous iterator might have buffered splits so need to keep that too
    // closingjoiniterator ensures that the previous iterator is closed and up for garbage collection after its done
    recordReaderIterator = closingJoinIterator(recordReaderIterator, iteratorFromSplits);

    // add the existing runtimefilters to the new iterator
    logger.debug("Adding existing {} runtime filters to the new iterator", existingRuntimeFilters.size());
    existingRuntimeFilters.forEach(recordReaderIterator::addRuntimeFilter);
  }

  @VisibleForTesting
  public UserGroupInformation getUGI(Hive3StoragePlugin storagePlugin, OpProps props) {
    final String userName = storagePlugin.getUsername(props.getUserName());
    return HiveImpersonationUtil.createProxyUgi(userName);
  }

  static RecordReaderIterator closingJoinIterator(RecordReaderIterator iter1, RecordReaderIterator iter2) {
    RecordReaderIterator finalIter1 = closeIfFinished(iter1);
    return new RecordReaderIterator() {
      RecordReaderIterator it1 = finalIter1;
      RecordReaderIterator it2 = iter2;

      List<RuntimeFilter> runtimeFilters;

      @Override
      public boolean hasNext() {
        return it1.hasNext() || it2.hasNext();
      }

      @Override
      public RecordReader next() {
        if (it1.hasNext()) {
          RecordReader next = it1.next();
          it1 = closeIfFinished(it1);
          return next;
        }
        return it2.next();
      }

      @Override
      public void addRuntimeFilter(RuntimeFilter runtimeFilter) {
        // it1 is either closed or contains prefetched splits so no need to add to it.
        it2.addRuntimeFilter(runtimeFilter);
        if (runtimeFilters == null) {
          runtimeFilters = new ArrayList<>();
        }
        runtimeFilters.add(runtimeFilter);
      }

      @Override
      public List<RuntimeFilter> getRuntimeFilters() {
        return runtimeFilters != null ? runtimeFilters : Collections.emptyList();
      }

      @Override
      public void produceFromBuffered(boolean toProduce) {
        it1.produceFromBuffered(toProduce);
        it2.produceFromBuffered(toProduce);
      }

      @Override
      public void close() throws Exception {
        AutoCloseables.close(it1, it2);
        runtimeFilters = null;
      }
    };
  }

  static RecordReaderIterator closeIfFinished(RecordReaderIterator iter) {
    if (!iter.hasNext() && !iter.equals(EMPTY_RECORD_ITERATOR)) {
      logger.debug("Closing the finished record reader iterator");
      try {
        iter.close();
      } catch (Exception e) {
        throw new RuntimeException("Failed closing recordreaderiterator", e);
      }
      return EMPTY_RECORD_ITERATOR;
    }
    return iter;
  }
}
