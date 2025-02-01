package com.unulearner.backend.dictionary.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "backend.dictionary")
public class DictionaryProperties {
    //**********************************************************//
    //*                                                        *//
    //*                         Controller                     *//
    //*                                                        *//
    //**********************************************************//

    /**
     * Controller: print stack trace on exception (all exceptions bubble up to here)
     */
    private Boolean controllerPrintExceptionStackTrace = false;

    public Boolean getControllerPrintExceptionStackTrace() {
        return this.controllerPrintExceptionStackTrace;
    }

    public void setControllerPrintExceptionStackTrace(Boolean controllerPrintExceptionStackTrace) {
        this.controllerPrintExceptionStackTrace = controllerPrintExceptionStackTrace;
    }

    //**********************************************************//
    //*                                                        *//
    //*                     Default pageables                  *//
    //*                                                        *//
    //**********************************************************//

    /**
     * Default page size
     */
    private Integer defaultPageSize = 20;

    public Integer getDefaultPageSize() {
        return this.defaultPageSize;
    }

    public void setDefaultPageSize(Integer defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    /**
     * Default page number
     */
    private Integer defaultPageNumber = 0;

    public Integer getDefaultPageNumber() {
        return this.defaultPageNumber;
    }

    public void setDefaultPageNumber(Integer defaultPageNumber) {
        this.defaultPageNumber = defaultPageNumber;
    }

    //**********************************************************//  
    //*                                                        *//
    //*                     Dictionary                         *//
    //*                                                        *//
    //**********************************************************//

}
