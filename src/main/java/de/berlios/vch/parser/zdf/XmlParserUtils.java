package de.berlios.vch.parser.zdf;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlParserUtils {
    public static Node getFirstElementByTagName(Document doc, String tagName) {
        NodeList list = doc.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0);
        } else {
            return null;
        }
    }

    public static String getTextContent(Document doc, String tagName) {
        Node node = getFirstElementByTagName(doc, tagName);
        if (node != null) {
            return node.getTextContent();
        } else {
            return null;
        }
    }

    public static String getTextContent(Node parent, String tagName) {
        Node node = findChildWithTagName(parent, tagName);
        if (node != null) {
            return node.getTextContent();
        } else {
            return null;
        }
    }

    public static Node findChildWithTagName(Node parent, String tagName) {
        if (parent == null) {
            return null;
        }

        NodeList childs = parent.getChildNodes();
        for (int i = 0; i < childs.getLength(); i++) {
            Node child = childs.item(i);
            if (child.getNodeName().equals(tagName)) {
                return child;
            } else if (child.hasChildNodes()) {
                Node result = findChildWithTagName(child, tagName);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    public static void getElementsByTagName(Node parent, String tagName, List<Node> result) {
        if (parent == null) {
            return;
        }

        NodeList childs = parent.getChildNodes();
        for (int i = 0; i < childs.getLength(); i++) {
            Node child = childs.item(i);
            if (child.getNodeName().equals(tagName)) {
                result.add(child);
            } else if (child.hasChildNodes()) {
                getElementsByTagName(child, tagName, result);
            }
        }
    }
}
