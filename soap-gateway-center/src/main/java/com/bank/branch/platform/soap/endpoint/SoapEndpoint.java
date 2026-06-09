package com.bank.branch.platform.soap.endpoint;

import com.bank.branch.platform.soap.parse.SoapMessage;

public interface SoapEndpoint {

    String getServicePath();

    String invoke(SoapMessage message);
}
