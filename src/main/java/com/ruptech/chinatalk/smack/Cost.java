package com.ruptech.chinatalk.smack;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;

/**
 * Created by zhaolei on 15/1/30.
 */
public class Cost implements PacketExtension {
    public static final String NAMESPACE = "http://jabber.org/protocol/tranlate";
    public static final String ELEMENT_NAME = "cost";
    private String name = null;

    public Cost(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getElementName() {
        return ELEMENT_NAME;
    }

    public String getNamespace() {
        return NAMESPACE;
    }

    public String toXML() {
        StringBuilder buf = new StringBuilder();
        buf.append("<").append(ELEMENT_NAME).append(" xmlns=\"").append(NAMESPACE).append("\">");
        buf.append(this.getName());
        buf.append("</").append(ELEMENT_NAME).append('>');
        return buf.toString();
    }

    public static class Provider implements PacketExtensionProvider {
        public Provider() {
        }

        public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
            parser.next();
            String name = parser.getText();

            while (parser.getEventType() != 3) {
                parser.next();
            }

            return new Cost(name);
        }
    }
}
