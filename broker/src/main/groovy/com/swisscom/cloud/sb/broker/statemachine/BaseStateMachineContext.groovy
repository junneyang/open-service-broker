/*
 * Copyright (c) 2019 Swisscom (Switzerland) Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.swisscom.cloud.sb.broker.statemachine

import groovy.transform.CompileStatic

@CompileStatic
abstract class BaseStateMachineContext implements StateMachineContext {

    private Exception exception
    private Map<String, Object> properties = new HashMap<String, Object>()
    private Integer retryCounter = 0

    void setException(Exception exception) {
        this.exception = exception
    }

    Exception getException() {
        return exception
    }

    Boolean hasException() {
        return exception != null
    }

    Map<String, Object> getProperties() {
        return this.properties
    }

    Integer increaseRetryCounter() {
        return ++retryCounter
    }
    Integer getRetryCount() {
        return retryCounter
    }

    void resetRetryCount() {
        retryCounter = 0
    }
}