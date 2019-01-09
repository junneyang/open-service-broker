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

import spock.lang.Specification


class StateMachineSpec extends Specification {

    StateMachine testee

    void setup() {

        def stateMachineBuilder = new StateMachineBuilder()

        stateMachineBuilder.startState
                .addTransition(new NormalTransition("Delay-0", 0), "Node-1")
        stateMachineBuilder.getState("Node-1")
                .addTransition(new NormalTransition("Delay-5", 9), "Node-2")
                .addTransition(new NormalTransition("Error", 15), StateMachine.STATENAME_ERROR)
        stateMachineBuilder.getState("Node-2")
                .addTransition(new NormalTransition("Delay-5", 5), stateMachineBuilder.endState)

        testee = stateMachineBuilder.build()
        testee.context = new TestStateMachineContext()
    }

    void 'Can run statemachine'() {
        when:
        for (def i = 0; i < 20; i ++) {
            testee.trigger()
        }

        then:
        noExceptionThrown()
        testee.completed == true
    }
}