package com.bank.branch.platform.soap.parse;

import lombok.Data;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Data
public class SoapMessage {

    private Document document;
    private Element header;
    private Element body;
    private String soapAction;
    private String rawXml;

    public Element getBodyFirstChild() {
        if (body == null) return null;
        var node = body.getFirstChild();
        while (node != null && node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            node = node.getNextSibling();
        }
        return (Element) node;
    }

    public String getBodyLocalName() {
        Element child = getBodyFirstChild();
        return child != null ? child.getLocalName() : null;
    }
}
