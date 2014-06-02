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

    private static final String PAGE_TYPE = "cq:Page";
    private static final String FILE_NODE_QUERY = "/jcr:root/content//element(*, cq:Page)//file";

    /**
     * JCR-SQL2 query
     * SELECT * FROM [cq:Page] AS s WHERE ISDESCENDANTNODE([/content]) and CONTAINS(s.*,'file')";
     */

    private String crxServerUri;
    private String user;
    private String password;

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
            Query query = queryManager.createQuery(FILE_NODE_QUERY, Query.XPATH);

            QueryResult result = query.execute();
            LOG.info("Executed {} query [{}]", query.getLanguage(), query.getStatement());
            NodeIterator nodes = result.getNodes();
            while (nodes.hasNext()) {
                Node nextNode = (Node) nodes.next();
                Node parentNode = nextNode.getParent();
                Node parentPage = getParentPage(parentNode);
                if (parentPage != null) {
                    pages.add(parentPage.getPath());
                } else {
                    LOG.warn("No parent page found for {}", nextNode.getPath());
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
