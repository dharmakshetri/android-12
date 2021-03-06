/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.monitor.ui.network.model;

/**
 * Data of http url connection. Each {@code HttpData} object matches a http connection with a unique id, and it includes both request data
 * and response data. Request data is filled immediately when the connection starts. Response data may be empty, it will filled when
 * connection completes.
 */
public class HttpData {
  private long myId;
  private String myUrl;
  private String myMethod;
  private long myStartTimeUs;
  private long myDownloadingTimeUs;
  private long myEndTimeUs;
  private String myHttpResponsePayloadId;
  // Holds the non-negative response body size in bytes if present, otherwise it is -1 by default.
  private long myHttpResponseBodySize = -1;

  public long getId() {
    return myId;
  }

  public void setId(long id) {
    myId = id;
  }

  public String getUrl() {
    return myUrl;
  }

  public void setUrl(String url) {
    myUrl = url;
  }

  public String getMethod() {
    return myMethod;
  }

  public void setMethod(String method) {
    myMethod = method;
  }

  public long getStartTimeUs() {
    return myStartTimeUs;
  }

  public void setStartTimeUs(long startTimeUs) {
    myStartTimeUs = startTimeUs;
  }

  public long getDownloadingTimeUs() {
    return myDownloadingTimeUs;
  }

  public void setDownloadingTimeUs(long downloadingTimeUs) {
    myDownloadingTimeUs = downloadingTimeUs;
  }

  public long getEndTimeUs() {
    return myEndTimeUs;
  }

  public void setEndTimeUs(long endTimeUs) {
    myEndTimeUs = endTimeUs;
  }

  public String getHttpResponsePayloadId() {
    return myHttpResponsePayloadId;
  }

  public void setHttpResponsePayloadId(String payloadId) {
    myHttpResponsePayloadId = payloadId;
  }

  public void setHttpResponseBodySize(long size) {
    myHttpResponseBodySize = size;
  }

  public long getHttpResponseBodySize() {
    return myHttpResponseBodySize;
  }
}
