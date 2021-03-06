/*
 * Copyright (c) 2018 Swisscom (Switzerland) Ltd.
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

package com.swisscom.cloud.sb.broker.functional

import com.fasterxml.jackson.databind.ObjectMapper
import com.swisscom.cloud.sb.broker.error.ErrorCode
import com.swisscom.cloud.sb.broker.error.ServiceBrokerException
import com.swisscom.cloud.sb.broker.model.repository.ServiceContextDetailRepository
import com.swisscom.cloud.sb.broker.model.repository.ServiceInstanceRepository
import com.swisscom.cloud.sb.broker.services.common.ServiceProviderLookup
import com.swisscom.cloud.sb.broker.util.servicecontext.ServiceContextHelper
import com.swisscom.cloud.sb.broker.util.test.DummyServiceProvider
import com.swisscom.cloud.sb.broker.util.test.ParentDummyServiceProvider
import com.swisscom.cloud.sb.client.model.DeleteServiceInstanceRequest
import com.swisscom.cloud.sb.client.model.LastOperationState
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.servicebroker.model.CloudFoundryContext
import org.springframework.cloud.servicebroker.model.KubernetesContext
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Shared

class ParentServiceFunctionalSpec extends BaseFunctionalSpec {
    @Autowired
    private ServiceInstanceRepository serviceInstanceRepository
    @Autowired
    private ServiceContextDetailRepository contextRepository

    private int processDelayInSeconds = DummyServiceProvider.RETRY_INTERVAL_IN_SECONDS * 2

    @Shared
    private String parentServiceInstanceGuid

    @Shared
    private String childServiceInstanceGuid

    def setup() {
        serviceLifeCycler.createServiceIfDoesNotExist('ParentDummy', ServiceProviderLookup.findInternalName(ParentDummyServiceProvider.class))
    }

    def cleanupSpec() {
        serviceLifeCycler.cleanup()
    }

    def "provision async service instance with parent reference"() {
        given:
        serviceLifeCycler.setServiceInstanceId(UUID.randomUUID().toString())
        serviceLifeCycler.setServiceBindingId(UUID.randomUUID().toString())
        serviceLifeCycler.createServiceInstanceAndAssert(ParentDummyServiceProvider.RETRY_INTERVAL_IN_SECONDS * 2, true, true,
                ['delay': String.valueOf(processDelayInSeconds)])
        this.parentServiceInstanceGuid = serviceLifeCycler.serviceInstanceId

        when:
        serviceLifeCycler.setServiceInstanceId(UUID.randomUUID().toString())
        serviceLifeCycler.createServiceInstanceAndAssert(DummyServiceProvider.RETRY_INTERVAL_IN_SECONDS * 2, true, true,
                ['delay': String.valueOf(processDelayInSeconds), 'parent_reference': this.parentServiceInstanceGuid])
        this.childServiceInstanceGuid = serviceLifeCycler.serviceInstanceId
        then:
        serviceLifeCycler.getServiceInstanceStatus().state == LastOperationState.SUCCEEDED

        def si = serviceInstanceRepository.findByGuid(serviceLifeCycler.serviceInstanceId)
        assert si != null
        assert si.parentServiceInstance != null
    }

    def "provision async service instance with non-existing parent reference"() {
        given:
        serviceLifeCycler.setServiceInstanceId(UUID.randomUUID().toString())
        serviceLifeCycler.setServiceBindingId(UUID.randomUUID().toString())

        when:
        serviceLifeCycler.createServiceInstanceAndAssert(DummyServiceProvider.RETRY_INTERVAL_IN_SECONDS * 4, true, true,
                ['delay': String.valueOf(processDelayInSeconds), 'parent_reference': 'service-instance-xxx'])

        then:
        def ex = thrown(HttpClientErrorException)
        ex.statusCode == HttpStatus.NOT_FOUND
        ex.responseBodyAsString != null
        def serviceBrokerException = new ObjectMapper().readValue(ex.responseBodyAsString, ServiceBrokerException)
        serviceBrokerException.code == ErrorCode.PARENT_SERVICE_INSTANCE_NOT_FOUND.code
    }

    def "deprovision async parent service instance with active children"() {
        when:
        serviceBrokerClient.deleteServiceInstance(new DeleteServiceInstanceRequest(parentServiceInstanceGuid, serviceLifeCycler.cfService.guid, serviceLifeCycler.cfService.plans[0].guid, true))

        then:
        def ex = thrown(HttpClientErrorException)
        ex.statusCode == HttpStatus.BAD_REQUEST
    }

    def "deprovision async parent service instance with deleted children"() {
        when:
        serviceBrokerClient.deleteServiceInstance(new DeleteServiceInstanceRequest(childServiceInstanceGuid, serviceLifeCycler.cfService.guid, serviceLifeCycler.cfService.plans[0].guid, true))
        serviceLifeCycler.waitUntilMaxTimeOrTargetState(ParentDummyServiceProvider.RETRY_INTERVAL_IN_SECONDS * 3, childServiceInstanceGuid)
        serviceBrokerClient.deleteServiceInstance(new DeleteServiceInstanceRequest(parentServiceInstanceGuid, serviceLifeCycler.cfService.guid, serviceLifeCycler.cfService.plans[0].guid, true))
        serviceLifeCycler.setServiceInstanceId(parentServiceInstanceGuid)
        serviceLifeCycler.waitUntilMaxTimeOrTargetState(ParentDummyServiceProvider.RETRY_INTERVAL_IN_SECONDS * 3, parentServiceInstanceGuid)
        then:
        serviceLifeCycler.getServiceInstanceStatus().state == LastOperationState.SUCCEEDED
    }

    void assertCloudFoundryContext(String serviceInstanceGuid, String org_guid = "org_id", String space_guid = "space_id") {
        def serviceInstance = serviceInstanceRepository.findByGuid(serviceInstanceGuid)
        assert serviceInstance != null
        assert serviceInstance.serviceContext != null
        assert serviceInstance.serviceContext.platform == CloudFoundryContext.CLOUD_FOUNDRY_PLATFORM
        assert serviceInstance.serviceContext.details.find { it -> it.key == ServiceContextHelper.CF_ORGANIZATION_GUID }.value == org_guid
        assert serviceInstance.serviceContext.details.find { it -> it.key == ServiceContextHelper.CF_SPACE_GUID }.value == space_guid
    }

    void assertKubernetesContext(String serviceInstanceGuid) {
        def serviceInstance = serviceInstanceRepository.findByGuid(serviceInstanceGuid)
        assert serviceInstance != null
        assert serviceInstance.serviceContext != null
        assert serviceInstance.serviceContext.platform == KubernetesContext.KUBERNETES_PLATFORM
        assert serviceInstance.serviceContext.details.find { it -> it.key == ServiceContextHelper.KUBERNETES_NAMESPACE }.value == "namespace_guid"
    }

}

