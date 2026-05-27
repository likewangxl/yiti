/**
 * 统一认证 S120030044 报文 DTO（JAXB）。
 * <p>全包默认 namespace = {@code http://esb.spdbbiz.com/services/S120030044}，
 * 元素 elementFormDefault=QUALIFIED，输出时使用前缀 {@code s:}。
 */
@XmlSchema(
        namespace = "http://esb.spdbbiz.com/services/S120030044",
        elementFormDefault = XmlNsForm.QUALIFIED,
        xmlns = {
                @XmlNs(prefix = "s", namespaceURI = "http://esb.spdbbiz.com/services/S120030044")
        }
)
package com.bank.branch.platform.auth.uniauth.dto;

import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
