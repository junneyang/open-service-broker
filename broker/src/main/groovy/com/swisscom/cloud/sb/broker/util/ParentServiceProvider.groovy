package com.swisscom.cloud.sb.broker.util

import com.swisscom.cloud.sb.broker.model.ServiceInstance

trait ParentServiceProvider {
    private static final String MAX_CHILDREN = "max_children"

    boolean hasActiveChildren(ServiceInstance serviceInstance) {
        return serviceInstance.childs.any({!it.deleted})
    }

    boolean isFull(ServiceInstance serviceInstance) {
        def param = serviceInstance.plan.parameters.find { it.name == MAX_CHILDREN }
        return (param.value as int) >= serviceInstance.childs.count({!it.deleted})
    }
}