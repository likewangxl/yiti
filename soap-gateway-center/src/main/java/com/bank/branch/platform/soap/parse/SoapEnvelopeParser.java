package com.bank.branch.platform.soap.parse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

public class SoapEnvelopeParser {

    private static final String NS_SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";

    public static SoapMessage parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            SoapMessage msg = new SoapMessage();
            msg.setDocument(doc);
            msg.setRawXml(xml);

            NodeList headers = doc.getElementsByTagNameNS(NS_SOAP_ENV, "Header");
            if (headers.getLength() > 0) {
                msg.setHeader((Element) headers.item(0));
            }

            NodeList bodies = doc.getElementsByTagNameNS(NS_SOAP_ENV, "Body");
            if (bodies.getLength() > 0) {
                msg.setBody((Element) bodies.item(0));
            }

            return msg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SOAP envelope", e);
        }
    }
}
