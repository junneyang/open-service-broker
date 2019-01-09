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
class StateMachineBuilder {
    private class StateMachineEdge {
        StateMachineEdge(State source, Transition edge, State destination) {
            this.destination = destination
            this.edge = edge
            this.source = source
        }

        State source
        Transition edge
        State destination
    }

    private class GraphInfo {
        Boolean isStartNodeConnected = false
        Boolean hasLoops = false
        Boolean isConnected = false
        Boolean isCompletable = false
        Boolean hasNoneEndStateLeafs = false

        StateMachineState startNode
    }

    private List<State> states = new ArrayList<State>()
    private List<StateMachineEdge> edges = new ArrayList<StateMachineEdge>()

    StateMachineBuilder() {
        states.add(new State(this, StateMachine.STATENAME_START))
        states.add(new State(this, StateMachine.STATENAME_ERROR, true))
        states.add(new State(this, StateMachine.STATENAME_END, true))
    }

    State getState(String stateName) {
        def state = states.find { s -> s.getName().equalsIgnoreCase(stateName) }

        if (state == null) {
            state = new State(this, stateName)
            states.add(state)
        }

        return state
    }

    protected StateMachineBuilder addTransition(State source, Transition transition, State destination) {
        if (source.name.equalsIgnoreCase(StateMachine.STATENAME_END)) {
            throw new IllegalArgumentException("source may not be ${StateMachine.STATENAME_END}.")
        }

        this.edges.add(new StateMachineEdge(source, transition, destination))

        return this
    }

    State getStartState() { return getState(StateMachine.STATENAME_START) }

    State getEndState() { return getState(StateMachine.STATENAME_END) }

    Boolean isValid() {
        def graphInfo = buildGraph(this.edges)
        return graphInfo.isCompletable && graphInfo.isConnected
    }

    StateMachine build() {
        def graphInfo = buildGraph(this.edges)

        if (!graphInfo.isStartNodeConnected) throw new IllegalArgumentException("StateMachines StartNode is not connected.")
        if (!graphInfo.isCompletable) throw new IllegalArgumentException("StateMachines EndNode can not be reached from StartNode.")
        if (!graphInfo.isConnected) throw new IllegalArgumentException("StateMachines has nodes which are not reachable")

        return new StateMachine(graphInfo.startNode)
    }

    GraphInfo buildGraph(List<StateMachineEdge> edges) {
        def graphInfo = new GraphInfo()
        HashSet<StateMachineEdge> visitedEdges = new HashSet<StateMachineEdge>()
        HashSet<State> visitedStates = new HashSet<State>()
        Stack<State> nodeStack = new Stack<State>()
        List<StateMachineState> stateMachine = this.states.collect { s -> new StateMachineState(name: s.name.toUpperCase() ) }

        def startEdges = edges.findAll { e -> e.source.name.equals(StateMachine.STATENAME_START) }
        if (startEdges == null || startEdges.size() == 0) {
            return graphInfo
        } else {
            graphInfo.isStartNodeConnected = true
            graphInfo.startNode = stateMachine.find { s -> s.name.equals(StateMachine.STATENAME_START) }
            nodeStack.push(startState)
            visitedStates.add(startState)
        }

        while (nodeStack.size() > 0) {
            def currentNode = nodeStack.pop()
            def outgoingEdges = edges.findAll { e -> e.source.name.equalsIgnoreCase(currentNode.name) }

            if ((outgoingEdges == null || outgoingEdges.size() == 0) && !currentNode.isAllowedLeafNode) {
                graphInfo.hasNoneEndStateLeafs = true
            }

            for (def edge in outgoingEdges) {
                if (visitedEdges.contains(edge)) {
                    graphInfo.hasLoops = true
                    continue
                }
                visitedEdges.add(edge)

                stateMachine
                        .find { s -> s.name.equalsIgnoreCase(edge.source.name) }
                        .addTransition(edge.edge, stateMachine.find { s -> s.name.equalsIgnoreCase(edge.destination.name) })

                if (nodeStack.contains(edge.destination)) {
                    continue
                }

                nodeStack.push(edge.destination)
                visitedStates.add(edge.destination)

                if (edge.destination.name.equalsIgnoreCase(StateMachine.STATENAME_END)) {
                    graphInfo.isCompletable = true
                }
            }
        }

        graphInfo.isConnected = visitedStates.size() == this.states.size()

        return graphInfo
    }
}
