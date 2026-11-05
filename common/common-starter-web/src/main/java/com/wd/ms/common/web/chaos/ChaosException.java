package com.wd.ms.common.web.chaos;

/** 故障注入抛出的异常，全局异常处理器把它转成 HTTP 500。 */
public class ChaosException extends RuntimeException {

    public ChaosException(String message) {
        super(message);
    }
}
