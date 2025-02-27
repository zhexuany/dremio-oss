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
import { take, race, put, call, select, takeEvery } from "redux-saga/effects";
import invariant from "invariant";
import { cloneDeep } from "lodash";

import {
  loadNextRows,
  EXPLORE_PAGE_EXIT,
  updateExploreJobProgress,
  updateJobRecordCount,
  stopExplorePageListener,
  startExplorePageListener,
} from "actions/explore/dataset/data";
import { updateHistoryWithJobState } from "actions/explore/history";

import socket, {
  WS_MESSAGE_JOB_PROGRESS,
  WS_MESSAGE_QV_JOB_PROGRESS,
  WS_MESSAGE_JOB_RECORDS,
  WS_CONNECTION_OPEN,
} from "@inject/utils/socket";
import { getExplorePageLocationChangePredicate } from "@app/sagas/utils";
import {
  getTableDataRaw,
  getCurrentRouteParams,
  getExploreState,
} from "@app/selectors/explore";
import { log } from "@app/utils/logger";
import { LOGOUT_USER_SUCCESS } from "@app/actions/account";
import { resetQueryState, setQueryStatuses } from "@app/actions/explore/view";
import { loadJobDetails } from "@app/actions/jobs/jobs";
import { intl } from "@app/utils/intl";
import { addNotification } from "@app/actions/notification";
import { JOB_DETAILS_VIEW_ID } from "@app/actions/joblist/jobList";
import { loadNewDataset } from "actions/explore/dataset/edit";
import { fetchDatasetMetadata } from "./transformWatcher";
import { navigateToNextDataset } from "@app/actions/explore/dataset/common";
import apiUtils from "@app/utils/apiUtils/apiUtils";
import { fetchJobFailureInfo } from "./performTransformNew";

const getJobDoneActionFilter = (jobId) => (action) =>
  (action.type === WS_MESSAGE_JOB_PROGRESS ||
    action.type === WS_MESSAGE_QV_JOB_PROGRESS) &&
  action.payload.id.id === jobId &&
  action.payload.update.isComplete;

const getJobProgressActionFilter = (jobId) => (action) =>
  (action.type === WS_MESSAGE_JOB_PROGRESS ||
    action.type === WS_MESSAGE_QV_JOB_PROGRESS) &&
  action.payload.id.id === jobId &&
  !action.payload.update.isComplete;

const getJobUpdateActionFilter = (jobId) => (action) =>
  (action.type === WS_MESSAGE_JOB_PROGRESS ||
    action.type === WS_MESSAGE_QV_JOB_PROGRESS) &&
  action.payload.id.id === jobId;

const getJobRecordsActionFilter = (jobId) => (action) =>
  action.type === WS_MESSAGE_JOB_RECORDS && action.payload.id.id === jobId;

/**
 * Load data for a dataset, if data is missing in redux store. Or forces data load if {@see forceReload}
 * set to true
 * @param {string!} datasetVersion - a dataset version
 * @param {string!} jobId - a job id for which data would be requested
 * @param {boolean!} forceReload
 * @param {string!} paginationUrl - put is a last parameter is there is plan to get rid of server
 * generated links
 * @yields {void} An exception may be thrown.
 * @throws DataLoadError
 */
export function* handleResumeRunDataset(
  datasetVersion,
  jobId,
  forceReload,
  paginationUrl,
  isRunOrPreview = true
) {
  invariant(datasetVersion, "dataset version must be provided");
  invariant(jobId, "jobId must be provided");
  invariant(paginationUrl, "paginationUrl must be provided");

  // we always load data with column information inside, but rows may be missed in case if
  // we load data asynchronously
  const tableData = yield select(getTableDataRaw, datasetVersion);
  const rows = tableData ? tableData.get("rows") : null;
  log(`rows are present = ${!!rows}`);

  // if forceReload = true and data exists, we should not clear data here. As it would be replaced
  // by response in '/reducers/resources/entityReducers/table.js' reducer.
  if (forceReload || !rows) {
    yield race({
      jobDone: call(
        waitForRunToComplete,
        datasetVersion,
        paginationUrl,
        jobId,
        isRunOrPreview
      ),
      locationChange: call(explorePageChanged),
    });
  }
}

export function* loadDatasetMetadata(
  datasetVersion,
  jobId,
  isRun,
  paginationUrl,
  datasetPath,
  callback,
  curIndex,
  sessionId,
  viewId
) {
  const tableData = yield select(getTableDataRaw, datasetVersion);
  const rows = tableData?.get("rows");

  if (isRun || !rows) {
    const { jobDone } = yield race({
      jobDone: call(
        handlePendingMetadataFetch,
        datasetVersion,
        paginationUrl,
        jobId,
        datasetPath,
        callback,
        curIndex,
        sessionId,
        viewId
      ),
      locationChange: call(explorePageChanged),
    });

    const willProceed = jobDone?.willProceed ?? false;
    const newResponse = jobDone?.newResponse;

    if (newResponse) {
      yield put(stopExplorePageListener());
      yield put(
        navigateToNextDataset(newResponse, {
          replaceNav: true,
          preserveTip: true,
          newJobId: jobId,
        })
      );
      yield put(startExplorePageListener(false));
    }

    if (callback && newResponse !== undefined) {
      const resultDataset = apiUtils.getEntityFromResponse(
        "datasetUI",
        newResponse
      );

      yield call(callback, true, resultDataset);
    }

    return willProceed;
  }
}

export class DataLoadError {
  constructor(response) {
    this.name = "DataLoadError";
    this.response = response;
  }
}

export class JobFailedError {
  constructor(response) {
    this.name = "JobFailedError";
    this.response = response;
  }
}

//export for tests
/**
 * Registers a listener for a job progress and triggers data load when job is completed
 * @param {string} datasetVersion
 * @param {string} paginationUrl
 * @param {string} jobId
 * @yields {void}
 * @throws DataLoadError in case if data request returns an error
 */
export function* waitForRunToComplete(
  datasetVersion,
  paginationUrl,
  jobId,
  isRunOrPreview = true
) {
  try {
    log("Check if socket is opened:", socket.isOpen);
    if (!socket.isOpen) {
      const raceResult = yield race({
        // When explore page is refreshed, we register 'pageChangeListener'
        // (see oss/dac/ui/src/sagas/performLoadDataset.js), which may call this saga
        // earlier, than application is booted ('APP_INIT' action) and socket is opened.
        // We must wait for WS_CONNECTION_OPEN before 'socket.startListenToJobProgress'
        socketOpen: take(WS_CONNECTION_OPEN),
        stop: take(LOGOUT_USER_SUCCESS),
      });
      log("wait for socket open result:", raceResult);
      if (raceResult.stop) {
        // if a user is logged out before socket is opened, terminate current saga
        return;
      }
    }
    yield call(
      [socket, socket.startListenToJobProgress],
      jobId,
      // force listen request to force a response from server.
      // There's no other way right now to know if job is already completed.
      true
    );
    console.warn(`=+=+= socket listener registered for job id ${jobId}`);

    call(explorePageChanged);
    const { jobDone } = yield race({
      jobProgress: call(watchUpdateHistoryOnJobProgress, datasetVersion, jobId),
      jobDone: take(getJobDoneActionFilter(jobId)),
      locationChange: call(explorePageChanged),
    });

    // Only load table data when user executes run/preview
    if (jobDone && isRunOrPreview) {
      const promise = yield put(loadNextRows(datasetVersion, paginationUrl, 0));
      const response = yield promise;
      const exploreState = yield select(getExploreState);
      const queryStatuses = cloneDeep(exploreState?.view?.queryStatuses ?? []);

      if (response && response.error) {
        if (queryStatuses.length) {
          const index = queryStatuses.findIndex(
            (query) => query.jobId === jobId
          );
          if (index > -1 && !queryStatuses[index].error) {
            const newStatuses = cloneDeep(queryStatuses);
            newStatuses[index].error = response;
            yield put(setQueryStatuses({ statuses: newStatuses }));
          }
        }
      }

      if (!response || response.error) {
        console.warn(`=+=+= socket returned error for job id ${jobId}`);
        throw new DataLoadError(response);
      }

      console.warn(`=+=+= socket returned payload for job id ${jobId}`);
      yield put(
        updateHistoryWithJobState(datasetVersion, jobDone.payload.update.state)
      );
      yield put(updateExploreJobProgress(jobDone.payload.update));
      yield call(genLoadJobDetails, jobId, queryStatuses);
    }
  } finally {
    yield call([socket, socket.stopListenToJobProgress], jobId);
  }
}

export function* handlePendingMetadataFetch(
  datasetVersion,
  paginationUrl,
  jobId,
  datasetPath,
  callback,
  curIndex,
  sessionId,
  viewId
) {
  let willProceed = true;
  let newResponse;

  try {
    if (!socket.isOpen) {
      const raceResult = yield race({
        socketOpen: take(WS_CONNECTION_OPEN),
        stop: take(LOGOUT_USER_SUCCESS),
      });

      if (raceResult.stop) {
        return;
      }
    }

    yield call([socket, socket.startListenToJobProgress], jobId, true);

    const { jobDone } = yield race({
      jobProgress: call(watchUpdateHistoryOnJobProgress, datasetVersion, jobId),
      jobDone: take(getJobDoneActionFilter(jobId)),
      locationChange: call(explorePageChanged),
    });

    if (jobDone) {
      // if a job fails, throw an error to avoid calling the /preview endpoint
      if (jobDone.payload?.update?.state === "FAILED") {
        const failureInfo = jobDone.payload.update.failureInfo;
        throw new JobFailedError(
          failureInfo?.errors?.[0]?.message || failureInfo?.message
        );
      }

      const apiAction = yield call(
        loadNewDataset,
        datasetPath,
        sessionId,
        datasetVersion,
        jobId,
        paginationUrl,
        viewId
      );

      newResponse = yield call(fetchDatasetMetadata, apiAction, viewId);

      if (!callback) {
        const promise = yield put(
          loadNextRows(datasetVersion, paginationUrl, 0)
        );
        const response = yield promise;
        const exploreState = yield select(getExploreState);
        const queryStatuses = cloneDeep(
          exploreState?.view?.queryStatuses ?? []
        );

        if (response?.error) {
          if (queryStatuses.length) {
            const index = queryStatuses.findIndex(
              (query) => query.jobId === jobId
            );

            if (index > -1 && !queryStatuses[index].error) {
              const newStatuses = cloneDeep(queryStatuses);
              newStatuses[index].error = response;
              yield put(setQueryStatuses({ statuses: newStatuses }));
            }
          }
        }

        if (!response || response.error) {
          throw new DataLoadError(response);
        }

        yield put(
          updateHistoryWithJobState(
            datasetVersion,
            jobDone.payload.update.state
          )
        );
        yield put(updateExploreJobProgress(jobDone.payload.update));
        yield call(genLoadJobDetails, jobId, queryStatuses);
      }
    }
  } catch (e) {
    // if a job fails, fetch the correct job failure info using the Jobs API
    willProceed = yield fetchJobFailureInfo(jobId, curIndex, callback);
  } finally {
    yield call([socket, socket.stopListenToJobProgress], jobId);
  }

  return { willProceed, newResponse };
}

/**
 * Returns a redux action that treated as explore page url change. The action could be one of the following cases:
 * 1) Navigation out of explore page has happen
 * 2) Current dataset or version of a dataset is changed
 * Note: navigation between data/wiki/graph tabs is not treated as page change
 * @yields {object} an redux action
 */
export function* explorePageChanged() {
  const prevRouteParams = yield select(getCurrentRouteParams);

  let shouldReset;
  const promise = yield take([
    (action) => {
      const [result, shouldResetExploreViewState] =
        getExplorePageLocationChangePredicate(prevRouteParams, action);
      shouldReset = shouldResetExploreViewState;
      return result;
    },
    EXPLORE_PAGE_EXIT,
  ]);

  if (shouldReset) {
    yield put(resetQueryState());
  }

  return promise;
}

/**
 * Endless job that monitors job progress with id {@see jobId} and updates job state in redux
 * store for particular {@see datasetVersion}
 * @param {string} datasetVersion
 * @param {string} jobId
 */
export function* watchUpdateHistoryOnJobProgress(datasetVersion, jobId) {
  function* updateHistoryOnJobProgress(action) {
    yield put(
      updateHistoryWithJobState(datasetVersion, action.payload.update.state)
    );
  }

  yield takeEvery(
    getJobProgressActionFilter(jobId),
    updateHistoryOnJobProgress
  );
}

/**
 * handle job status and job running record watches
 */
export function* jobUpdateWatchers(jobId) {
  yield race({
    recordWatcher: call(watchUpdateJobRecords, jobId),
    statusWatcher: call(watchUpdateJobStatus, jobId),
    locationChange: call(explorePageChanged),
    jobDone: take(EXPLORE_JOB_STATUS_DONE),
  });
}

//export for testing
export const EXPLORE_JOB_STATUS_DONE = "EXPLORE_JOB_STATUS_DONE";

/**
 * monitor job status updates with jobId from the socket
 */
export function* watchUpdateJobStatus(jobId) {
  function* updateJobStatus(action) {
    yield put(updateExploreJobProgress(action.payload.update));
    if (action.payload.update.isComplete) {
      yield put({ type: EXPLORE_JOB_STATUS_DONE });
    }
  }

  yield takeEvery(getJobUpdateActionFilter(jobId), updateJobStatus);
}

/**
 * monitor job records updates with jobId from the socket
 */
export function* watchUpdateJobRecords(jobId) {
  function* updateJobProgressWithRecordCount(action) {
    yield put(updateJobRecordCount(action.payload.recordCount));
  }

  yield takeEvery(
    getJobRecordsActionFilter(jobId),
    updateJobProgressWithRecordCount
  );
}

export function* genLoadJobDetails(jobId, queryStatuses) {
  const jobDetails = yield put(loadJobDetails(jobId, JOB_DETAILS_VIEW_ID));
  const jobDetailsResponse = yield jobDetails;
  const responseStats =
    jobDetailsResponse &&
    jobDetailsResponse.payload &&
    !jobDetailsResponse.error
      ? jobDetailsResponse.payload.getIn([
          "entities",
          "jobDetails",
          jobDetailsResponse.meta.jobId,
          "stats",
        ])
      : "";
  // isOutputLimited will be true if the results were truncated
  if (responseStats && responseStats.get("isOutputLimited")) {
    const index = (queryStatuses ?? []).findIndex(
      (query) => query.jobId === jobId
    );
    yield put(
      addNotification(
        `Query ${index + 1}: ` +
          intl.formatMessage(
            { id: "Explore.Run.Warning" },
            { rows: responseStats.get("outputRecords").toLocaleString() }
          ),
        "success",
        10
      )
    );
  }
}
