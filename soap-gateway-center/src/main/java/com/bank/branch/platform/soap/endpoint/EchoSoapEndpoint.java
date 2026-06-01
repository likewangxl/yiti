package com.bank.branch.platform.soap.endpoint;

import com.bank.branch.platform.soap.parse.SoapMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EchoSoapEndpoint implements SoapEndpoint {

    @Override
    public String getServicePath() {
        return "/soap/echo";
    }

    @Override
    public String invoke(SoapMessage message) {
        String bodyName = message.getBodyLocalName();
        log.info("Echo endpoint received body element: {}", bodyName);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <EchoResponse>
                      <status>OK</status>
                      <receivedOperation>%s</receivedOperation>
                    </EchoResponse>
                  </soap:Body>
                </soap:Envelope>""".formatted(bodyName != null ? bodyName : "unknown");
    }
}
