package org.lotus.edu.imagefinder;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ImageFinder {

    private static Logger LOG = LoggerFactory.getLogger(ImageFinder.class.getName() + ".common");
    private static Logger RESULTS_LOG = LoggerFactory.getLogger(ImageFinder.class.getName() + ".result");

    private static final String CRX_SERVER_PROPERTIES = "crx-server.properties";
    private static final String CRX_SERVER_URI_PARAM = "crx.server.uri";

    private static final String CRX_SERVER_USER = "crx.server.user";
    private static final String CRX_SERVER_PASSWORD = "crx.server.password";

    private static final String FILE_NODE_QUERY = "file.node.queryString";

    private static final String PAGE_TYPE = "cq:Page";

    private String crxServerUri;
    private String user;
    private String password;
    private String queryString;

    private List<String> pages = new ArrayList<String>();

    public static void main(String[] args) {

        Properties props = loadConnectionProperties();
        ImageFinder imageFinder = new ImageFinder(props);
        imageFinder.find();
        imageFinder.printResults();
    }

    public ImageFinder(Properties props) {

        crxServerUri = props.getProperty(CRX_SERVER_URI_PARAM);
        validate(CRX_SERVER_URI_PARAM, crxServerUri);

        user = props.getProperty(CRX_SERVER_USER);
        validate(CRX_SERVER_USER, user);

        password = props.getProperty(CRX_SERVER_PASSWORD);
        validate(CRX_SERVER_PASSWORD, password);

        queryString = props.getProperty(FILE_NODE_QUERY);
        validate(FILE_NODE_QUERY, queryString);

        LOG.info("Loaded crx server connection properties");
        LOG.info("Connection uri [{}]", crxServerUri);
    }

    private void validate(String paramName, String paramValue) {
        if (StringUtils.isEmpty(paramValue)) {
            String errorMessage = paramName + " should not be null";
            LOG.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public void find() {
        Repository repository;
        Session session = null;
        try {
            repository = JcrUtils.getRepository(crxServerUri);
            session = repository.login(new SimpleCredentials(user, password.toCharArray()));
            LOG.info("Connected to the crx server..");

            QueryManager queryManager = session.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(queryString, Query.XPATH);

            QueryResult result = query.execute();
            LOG.info("Executed {} queryString [{}]", query.getLanguage(), query.getStatement());

            NodeIterator nodes = result.getNodes();

            while (nodes.hasNext()) {

                Node currentNode = (Node) nodes.next();
                Node immediateParentNode = currentNode.getParent();
                Node parentPage = getParentPage(immediateParentNode);

                if (parentPage != null) {
                    pages.add(parentPage.getPath());
                    printPaths(currentNode, parentPage);

                } else {
                    LOG.warn("No parent page found for {}", currentNode.getPath());
                }
            }

            LOG.info("Found {} pages with uploaded images", pages.size());
            RESULTS_LOG.info("Found {} total pages", pages.size());

        } catch (RepositoryException e) {
            LOG.error("Repository error", e);
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }

    }

    private void printPaths(Node currentNode, Node parentPage) throws RepositoryException {
        LOG.info("Found {} image file in {}", currentNode.getPath(), parentPage.getName());
    }

    private static Properties loadConnectionProperties() {
        Properties props = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader.getResourceAsStream(CRX_SERVER_PROPERTIES);
        try {
            props.load(stream);
        } catch (IOException e) {
            LOG.error("Failed to load connection properties from {}", CRX_SERVER_PROPERTIES);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                LOG.error("Failed to load connection properties from {}", CRX_SERVER_PROPERTIES);
            }
        }
        return props;
    }

    private static Node getParentPage(Node node) throws RepositoryException {
        Node parent = node.getParent();
        if (parent != null) {
            NodeType type = parent.getPrimaryNodeType();
            if (PAGE_TYPE.equals(type.getName())) {
                return parent;
            } else {
                return getParentPage(parent);
            }
        }

        return null;
    }

    public void printResults() {
        for (String pageNode : pages) {
            RESULTS_LOG.info(pageNode);
        }
    }
}
