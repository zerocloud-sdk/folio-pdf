package net.zerocloud.pdf.tools.inventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

final class ReleaseTrainValidator {

    private ReleaseTrainValidator() {
    }

    static void validate(InventoryModel model, List<String> errors) {
        Path pom = model.repositoryRoot.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            errors.add("release-train: repository root pom.xml is required");
            return;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(pom.toFile());
            String mavenVersion = directChildText(document.getDocumentElement(), "version");
            if ("${revision}".equals(mavenVersion)) {
                Element properties = directChild(document.getDocumentElement(), "properties");
                mavenVersion = properties == null
                        ? null
                        : directChildText(properties, "revision");
                if (mavenVersion == null) {
                    errors.add("release-train: root pom.xml uses ${revision} but does not "
                            + "declare properties/revision");
                    return;
                }
            }
            if (mavenVersion == null) {
                errors.add("release-train: root pom.xml must declare a project version");
            } else if (model.releaseTrain != null && !model.releaseTrain.equals(mavenVersion)) {
                errors.add("release-train: Capability Matrix " + model.releaseTrain
                        + " does not match root Maven version " + mavenVersion);
            }
        } catch (ParserConfigurationException e) {
            errors.add("release-train: cannot configure secure root POM parser: "
                    + compact(e.getMessage()));
        } catch (SAXException e) {
            errors.add("release-train: invalid root pom.xml: " + compact(e.getMessage()));
        } catch (IOException e) {
            errors.add("release-train: cannot read root pom.xml: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            errors.add("release-train: secure XML properties are unavailable: "
                    + compact(e.getMessage()));
        }
    }

    private static String directChildText(Element parent, String localName) {
        Element child = directChild(parent, localName);
        if (child == null) {
            return null;
        }
        String value = child.getTextContent();
        return value == null ? null : value.trim();
    }

    private static Element directChild(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String compact(String value) {
        if (value == null) {
            return "unknown parser error";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
