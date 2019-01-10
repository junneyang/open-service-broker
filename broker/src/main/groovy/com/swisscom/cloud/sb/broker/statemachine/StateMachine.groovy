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
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class StateMachine<TContext extends StateMachineContext> {

    public static String STATENAME_START = "START"
    public static String STATENAME_ERROR = "ERROR"
    public static String STATENAME_END = "END"

    protected TContext context

    private StateMachineState currentState

    StateMachineState getCurrentState() {
        return currentState
    }

    private StateMachineState startState

    StateMachine(StateMachineState startState) {
        this.startState = startState
        this.currentState = this.startState
    }

    void trigger() {
        Boolean executedTransition = true

        while (executedTransition) {
            if (isCompleted())
                return

            def possibleTransition =  this.currentState.transitions.findAll { t ->
                try {
                    return t.canTransition(context)
                } catch (Exception ex) {
                    log.warn("StateMachine transition check failed (Exception:${ex.message})")
                    return false
                }
            }

            if (possibleTransition == null || possibleTransition.size() == 0) {
                log.info("StateMachine transitions blocked (State:${currentState.name},Retry:${context.increaseRetryCounter()})")
                executedTransition = false
                continue
            }

            if (possibleTransition.size() > 1) {
                log.warn("StateMachine is not deterministic (multiple transitions are possible, taking first)")
            }

            def transition = possibleTransition.first()

            try {
                log.info("StateMachine transition starting (State:${currentState.name},Transition:${transition.name})")
                transition.execute(context)
                log.info("StateMachine transition completed (State:${currentState.name},Transition:${transition.name},NextState:${transition.destination.name})")
                currentState = transition.destination
            } catch (Exception ex) {
                log.error("StateMachine transition failed (State:${currentState.name},Transition:${transition.name},Exception:${ex.message})", ex)
                context.setException(ex)
                log.info("StateMachine transition rollback starting (State:${currentState.name},Transition:${transition.name})")
                transition.rollbackOnError(context)
            }

            context.resetRetryCount()
        }
    }

    Boolean isCompleted() {
        return currentState.name.equalsIgnoreCase(STATENAME_END) ||
                currentState.name.equalsIgnoreCase(STATENAME_ERROR)
    }
}
