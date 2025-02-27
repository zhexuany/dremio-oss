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

/**
 *
 * @export
 * @interface ReflectionSummaryParams
 */
export interface ReflectionSummaryParams {
  /**
   * Used to get the next page of paginated reflection summaries.  Other parameters must not change when using the pageToken.
   * @type {string}
   * @memberof ReflectionSummaryParams
   */
  pageToken?: string;
  /**
   * Max number of reflection summaries per page. Default is 50.
   * @type {number}
   * @memberof ReflectionSummaryParams
   */
  maxResults?: number;
  /**
   * Allows filtering on reflection name, dataset name, availability and refresh statuses.
   * @type {string}
   * @memberof ReflectionSummaryParams
   */
  filter?: string;
  /**
   * Allows sorting on reflection name, dataset name and type.
   * @type {string}
   * @memberof ReflectionSummaryParams
   */
  orderBy?: string;
  /**
   * Override http request option.
   * @type {any}
   * @memberof ReflectionSummaryParams
   */
  options?: any;
}

/**
 * A paginated array of reflection summaries
 * @export
 * @interface ReflectionSummaries
 */
export interface ReflectionSummaries {
  /**
   *
   * @type {Array<ReflectionSummary>}
   * @memberof ReflectionSummaries
   */
  data: Array<ReflectionSummary>;
  /**
   * An opaque string to be passed as a query parameter to the next request in order to get the next set of results. If not present, it means all the resources have been returned to the user.
   * @type {string}
   * @memberof ReflectionSummaries
   */
  nextPageToken?: string;
  /**
   * Whether user has project level alter reflections privileges
   * @type {boolean}
   * @memberof ReflectionSummaries
   */
  canAlterReflections: boolean;
}

/**
 * A reflection summary.
 * @export
 * @interface ReflectionSummary
 */
export interface ReflectionSummary {
  /**
   * Reflection ID. The value is generated by Dremio and is immutable.
   * @type {string}
   * @memberof ReflectionSummary
   */
  id: string;
  /**
   * The reflection type, must either be AGGREGATION or RAW. This value cannot be changed after a reflection is created.
   * @type {string}
   * @memberof ReflectionSummary
   */
  reflectionType: ReflectionSummary.ReflectionTypeEnum;
  /**
   * Name of the reflection, required to be non empty.
   * @type {string}
   * @memberof ReflectionSummary
   */
  name: string;
  /**
   * RFC3339 date (example: 2017-10-27T21:08:22.858Z) representing the creation datetime. The value is generated by Dremio and is immutable.
   * @type {Date}
   * @memberof ReflectionSummary
   */
  createdAt: Date;
  /**
   * RFC3339 date (example: 2017-10-27T21:08:22.858Z) representing the last time the reflection was updated. The value is generated by Dremio and is immutable.
   * @type {Date}
   * @memberof ReflectionSummary
   */
  updatedAt: Date;
  /**
   * The data size (in bytes) of the latest reflection job (if one exists). The value is generated by Dremio and is immutable.
   * @type {number}
   * @memberof ReflectionSummary
   */
  currentSizeBytes?: number;
  /**
   * Output records of the latest reflection.
   * @type {number}
   * @memberof ReflectionSummary
   */
  outputRecordCount?: number;
  /**
   * The data size (in bytes) of all reflection jobs that have not been pruned (if any exist). The value is generated by Dremio and is immutable.
   * @type {number}
   * @memberof ReflectionSummary
   */
  totalSizeBytes?: number;
  /**
   * Whether to allow using the reflection to accelerate queries.
   * @type {boolean}
   * @memberof ReflectionSummary
   */
  enabled: boolean;
  /**
   * DEPRECATED - Whether Dremio converts data from your reflection’s Parquet files to the Apache Arrow format when copying that data to executor nodes.
   * @type {boolean}
   * @memberof ReflectionSummary
   */
  arrowCachingEnabled?: boolean;
  /**
   * The id of the dataset the reflection is for. Immutable after creation.
   * @type {string}
   * @memberof ReflectionSummary
   */
  datasetId: string;
  /**
   * Type of the reflection's anchor dataset.
   * @type {string}
   * @memberof ReflectionSummary
   */
  datasetType: ReflectionSummary.DatasetTypeEnum;
  /**
   * Path components of dataset that this reflection is anchored on
   * @type {Array<string>}
   * @memberof ReflectionSummary
   */
  datasetPath: Array<string>;
  /**
   *
   * @type {ReflectionSummaryStatus}
   * @memberof ReflectionSummary
   */
  status?: ReflectionSummaryStatus;
  /**
   * Whether current user has view privilege on the reflection
   * @type {boolean}
   * @memberof ReflectionSummary
   */
  canView: boolean;
  /**
   * Whether current user has alter privilege on the reflection
   * @type {boolean}
   * @memberof ReflectionSummary
   */
  canAlter: boolean;
  /**
   * Number of jobs that considered this reflection because of overlapping tables or views
   * @type {number}
   * @memberof ReflectionSummary
   */
  consideredCount?: number;
  /**
   * Link to jobs that considered this reflection
   * @type {string}
   * @memberof ReflectionSummary
   */
  consideredJobsLink?: string;
  /**
   * Number of jobs that matched this reflection with the job's query
   * @type {number}
   * @memberof ReflectionSummary
   */
  matchedCount?: number;
  /**
   * Link to jobs that matched this reflection
   * @type {string}
   * @memberof ReflectionSummary
   */
  matchedJobsLink?: string;
  /**
   * Number of jobs that chose this reflection based on best cost
   * @type {number}
   * @memberof ReflectionSummary
   */
  chosenCount?: number;
  /**
   * Link to jobs that chose this reflection
   * @type {string}
   * @memberof ReflectionSummary
   */
  chosenJobsLink?: string;
}

/**
 * @export
 * @namespace ReflectionSummary
 */
export namespace ReflectionSummary {
  /**
   * @export
   * @enum {string}
   */
  export enum ReflectionTypeEnum {
    RAW = <any>"RAW",
    AGGREGATE = <any>"AGGREGATE",
  }
  /**
   * @export
   * @enum {string}
   */
  export enum DatasetTypeEnum {
    PHYSICALDATASET = <any>"PHYSICAL_DATASET",
    VIRTUALDATASET = <any>"VIRTUAL_DATASET",
  }
}

/**
 * Status of the reflection
 * @export
 * @interface ReflectionSummaryStatus
 */
export interface ReflectionSummaryStatus {
  /**
   * Whether the reflection is available to accelerate queries. Either NONE, INCOMPLETE, EXPIRED or AVAILABLE. The value is generated by Dremio and is immutable.  INCOMPLETE is deprecated.
   * @type {string}
   * @memberof ReflectionSummaryStatus
   */
  availabilityStatus: ReflectionSummaryStatus.AvailabilityStatusEnum;
  /**
   * Combined status derived from availability, config and refresh.  INCOMPLETE is deprecated. The value is generated by Dremio and is immutable.
   * @type {string}
   * @memberof ReflectionSummaryStatus
   */
  combinedStatus: ReflectionSummaryStatus.CombinedStatusEnum;
  /**
   * State of the reflection configuration. Either OK or INVALID (the dataset schema has changed and the reflection definition is no longer applicable, for example a field that was being used no longer exists). The value is generated by Dremio and is immutable.
   * @type {string}
   * @memberof ReflectionSummaryStatus
   */
  configStatus: ReflectionSummaryStatus.ConfigStatusEnum;
  /**
   * RFC3339 date (example: 2017-10-27T21:08:22.858Z) representing when the reflection data will expire. The value is generated by Dremio and is immutable.
   * @type {Date}
   * @memberof ReflectionSummaryStatus
   */
  expiresAt: Date;
  /**
   * Number of consecutive reflection creation failures. The value is generated by Dremio and is immutable.
   * @type {number}
   * @memberof ReflectionSummaryStatus
   */
  failureCount: number;
  /**
   * RFC3339 date (example: 2017-10-27T21:08:22.858Z). query time of oldest PDS in the reflection.  Current time - lastDataFetch = Age as shown in acceleration job profile
   * @type {Date}
   * @memberof ReflectionSummaryStatus
   */
  lastDataFetchAt: Date;
  /**
   * Latest refresh status. The value is generated by Dremio and is immutable.
   * @type {string}
   * @memberof ReflectionSummaryStatus
   */
  refreshStatus: ReflectionSummaryStatus.RefreshStatusEnum;
  /**
   * Refresh method for the reflection.  New reflections start as NONE until planned.  Can change if the reflection definition changes.
   * @type {string}
   * @memberof ReflectionSummaryStatus
   */
  refreshMethod: ReflectionSummaryStatus.RefreshMethodEnum;
  /**
   * Duration of last reflection refresh in milliseconds.
   * @type {number}
   * @memberof ReflectionSummaryStatus
   */
  lastRefreshDurationMillis: number;
}

/**
 * @export
 * @namespace ReflectionSummaryStatus
 */
export namespace ReflectionSummaryStatus {
  /**
   * @export
   * @enum {string}
   */
  export enum AvailabilityStatusEnum {
    NONE = <any>"NONE",
    INCOMPLETE = <any>"INCOMPLETE",
    EXPIRED = <any>"EXPIRED",
    AVAILABLE = <any>"AVAILABLE",
  }
  /**
   * @export
   * @enum {string}
   */
  export enum CombinedStatusEnum {
    NONE = <any>"NONE",
    CANACCELERATE = <any>"CAN_ACCELERATE",
    CANACCELERATEWITHFAILURES = <any>"CAN_ACCELERATE_WITH_FAILURES",
    REFRESHING = <any>"REFRESHING",
    FAILED = <any>"FAILED",
    EXPIRED = <any>"EXPIRED",
    DISABLED = <any>"DISABLED",
    INVALID = <any>"INVALID",
    INCOMPLETE = <any>"INCOMPLETE",
    CANNOTACCELERATESCHEDULED = <any>"CANNOT_ACCELERATE_SCHEDULED",
    CANNOTACCELERATEMANUAL = <any>"CANNOT_ACCELERATE_MANUAL",
  }
  /**
   * @export
   * @enum {string}
   */
  export enum ConfigStatusEnum {
    OK = <any>"OK",
    INVALID = <any>"INVALID",
  }
  /**
   * @export
   * @enum {string}
   */
  export enum RefreshStatusEnum {
    MANUAL = <any>"MANUAL",
    SCHEDULED = <any>"SCHEDULED",
    RUNNING = <any>"RUNNING",
    GIVENUP = <any>"GIVEN_UP",
  }
  /**
   * @export
   * @enum {string}
   */
  export enum RefreshMethodEnum {
    NONE = <any>"NONE",
    FULL = <any>"FULL",
    INCREMENTAL = <any>"INCREMENTAL",
  }
}
