package com.beust.kobalt.wrapper;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {

  static Proxy getProxy() {
    String configFilePath = getConfigFilePath();
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      ByteArrayInputStream input =  new ByteArrayInputStream(getConfigFileContent(configFilePath));
      Document doc = builder.parse(input);
      NodeList proxies =doc.getElementsByTagName("proxies");
      for (int temp = 0; temp < proxies.getLength(); temp++) {
        Node node = proxies.item(temp);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          Element element = (Element) node;
          String type = element.getElementsByTagName("type").item(0).getTextContent();
          if (type.toLowerCase().equals("http")) {
            String host = element.getElementsByTagName("host").item(0).getTextContent();
            String portString = element.getElementsByTagName("port").item(0).getTextContent();
            try {
              int port = Integer.parseInt(portString);
              Main.log(2, String.format("Using HTTP proxy: %s:%s", host, port));
              return new Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(host, port));
            } catch (NumberFormatException e) {
              Main.log(1, String.format("Invalid proxy port number: %s in config file: %s", portString, configFilePath));
            }
          }
        }
      }
    } catch (Exception e) {
      Main.log(2, String.format("%s while parsing config file: %s", e.getMessage(), configFilePath));
      return null;
    }
    Main.log(2, String.format("No HTTP proxy found in config file: %s", configFilePath));
    return null;
  }

  private static String getConfigFilePath() {
    String userHome = System.getProperty("user.home");
    String fileSeparator = System.getProperty("file.separator");
    String configDir = ".config";
    String appName = "kobalt";
    String configFileName = "settings.xml";
    return userHome
        + fileSeparator + configDir
        + fileSeparator + appName
        + fileSeparator + configFileName;
  }

  private static byte[] getConfigFileContent(String configFilePath) throws IOException {
    Path path = Paths.get(configFilePath);
    return Files.readAllBytes(path);
  }

}
