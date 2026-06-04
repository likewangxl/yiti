package com.bank.branch.platform.soap.parse;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.w3c.dom.Element;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * 把 {@link SoapMessage} 的 body 首个业务元素反序列化成强类型对象（JAXB）。
 *
 * <p>外部渠道业务命名空间含服务号（如 {@code .../services/S080021264}），每个服务都不同。
 * 为用一套绑定类匹配任意服务,这里统一“剥掉命名空间”后再 unmarshal,只按元素本地名匹配。</p>
 */
public class SoapBodyBinder {

    private SoapBodyBinder() {
    }

    /**
     * 将 SOAP body 业务元素绑定为指定类型。
     *
     * @param message 已解析的 SOAP 报文
     * @param type    目标绑定类型（需带 JAXB 注解）
     * @return 绑定后的对象
     */
    public static <T> T bind(SoapMessage message, Class<T> type) {
        Element bodyChild = message == null ? null : message.getBodyFirstChild();
        if (bodyChild == null) {
            throw new IllegalArgumentException("SOAP body 下没有业务元素");
        }
        try {
            // JDK 自带 StAX 工厂不支持直接从 DOMSource 读,先序列化成字符串
            String bodyXml = serialize(bodyChild);

            XMLInputFactory xif = XMLInputFactory.newFactory();
            // 安全加固:禁用外部实体 / DTD,防 XXE
            xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);

            XMLStreamReader raw = xif.createXMLStreamReader(new StringReader(bodyXml));
            // 委托:把所有命名空间置空,JAXB 仅按元素本地名匹配（兼容任意服务号命名空间）
            XMLStreamReader nsStripped = new StreamReaderDelegate(raw) {
                @Override
                public String getNamespaceURI() {
                    return "";
                }
            };

            Unmarshaller unmarshaller = JAXBContext.newInstance(type).createUnmarshaller();
            return unmarshaller.unmarshal(nsStripped, type).getValue();
        } catch (Exception e) {
            throw new RuntimeException("绑定 SOAP body 到 " + type.getSimpleName() + " 失败: " + e.getMessage(), e);
        }
    }

    /** 把 DOM 元素序列化成 XML 字符串。 */
    private static String serialize(Element element) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        return writer.toString();
    }
}
