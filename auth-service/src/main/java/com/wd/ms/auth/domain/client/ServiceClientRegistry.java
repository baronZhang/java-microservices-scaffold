package com.wd.ms.auth.domain.client;

import java.util.Optional;

/** 已登记的服务客户端。演示里来自配置文件，生产可以放数据库或交给专门的授权服务器管理。 */
public interface ServiceClientRegistry {

    Optional<ServiceClient> find(String clientId);
}
