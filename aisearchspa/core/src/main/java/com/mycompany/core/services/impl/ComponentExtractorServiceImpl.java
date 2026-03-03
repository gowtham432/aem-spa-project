package com.mycompany.core.services.impl;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.mycompany.core.models.ContentDocument;
import com.mycompany.core.services.ComponentExtractorService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.query.Query;
import java.util.*;

@Component(service = ComponentExtractorService.class, immediate = true)
public class ComponentExtractorServiceImpl implements ComponentExtractorService {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentExtractorServiceImpl.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public ContentDocument extractPageData(Page page) {
        ContentDocument doc = new ContentDocument();

        try {
            // Basic page info
            doc.setId(page.getPath());
            doc.setTitle(page.getTitle() != null ? page.getTitle() : page.getName());
            doc.setPath(page.getPath());
            doc.setName(page.getName());

            // Page properties
            Resource contentResource = page.getContentResource();
            if (contentResource != null) {
                // Extract description
                String description = contentResource.getValueMap().get("jcr:description", String.class);
                if (description != null) {
                    doc.setDescription(description);
                }

                // Extract template
                String template = contentResource.getValueMap().get("cq:template", String.class);
                if (template != null) {
                    doc.setTemplate(template);
                }

                // Extract components
                List<String> components = extractComponents(contentResource);
                doc.setComponents(components);
                doc.setComponentCount(components.size());

                // Extract tags
                String[] tags = contentResource.getValueMap().get("cq:tags", String[].class);
                if (tags != null && tags.length > 0) {
                    doc.setTags(Arrays.asList(tags));
                }

                // Last modified
                Calendar lastModified = contentResource.getValueMap().get("cq:lastModified", Calendar.class);
                if (lastModified != null) {
                    doc.setLastModified(lastModified.getTimeInMillis());
                }

                // Author
                String lastModifiedBy = contentResource.getValueMap().get("cq:lastModifiedBy", String.class);
                if (lastModifiedBy != null) {
                    doc.setAuthor(lastModifiedBy);
                }

                // Generate structure description for embedding
                String structureDescription = generatePageDescriptionFromDoc(doc);
                doc.setStructureDescription(structureDescription);
            }

        } catch (Exception e) {
            LOG.error("Error extracting page data from {}: {}", page.getPath(), e.getMessage(), e);
        }

        return doc;
    }

    /**
     * Generate description from ContentDocument
     */
    private String generatePageDescriptionFromDoc(ContentDocument doc) {
        StringBuilder description = new StringBuilder();

        try {
            // Add title
            if (doc.getTitle() != null) {
                description.append("Page title: ").append(doc.getTitle()).append(". ");
            }

            // Add path context (extract meaningful segments)
            if (doc.getPath() != null) {
                description.append("Path: ").append(doc.getPath()).append(". ");

                // Extract path segments as keywords
                String[] pathSegments = doc.getPath().split("/");
                List<String> meaningfulSegments = new ArrayList<>();
                for (String segment : pathSegments) {
                    if (!segment.isEmpty() &&
                            !segment.equals("content") &&
                            !segment.equals("jcr:content")) {
                        meaningfulSegments.add(segment);
                    }
                }
                if (!meaningfulSegments.isEmpty()) {
                    description.append("Section: ")
                            .append(String.join(", ", meaningfulSegments))
                            .append(". ");
                }
            }

            // Add tags (very important for semantic search)
            if (doc.getTags() != null && !doc.getTags().isEmpty()) {
                description.append("Tags: ")
                        .append(String.join(", ", doc.getTags()))
                        .append(". ");
            }

            // Add template
            if (doc.getTemplate() != null) {
                String templateName = getSimpleName(doc.getTemplate());
                description.append("Template: ").append(templateName).append(". ");
            }

            // Add components (most important for component search)
            if (doc.getComponents() != null && !doc.getComponents().isEmpty()) {
                List<String> componentNames = new ArrayList<>();
                for (String component : doc.getComponents()) {
                    componentNames.add(getSimpleName(component));
                }
                description.append("Components used: ")
                        .append(String.join(", ", componentNames))
                        .append(". ");
            }

            // Add page description if available
            if (doc.getDescription() != null && !doc.getDescription().isEmpty()) {
                description.append("Description: ").append(doc.getDescription()).append(". ");
            }

        } catch (Exception e) {
            LOG.error("Error generating description: {}", e.getMessage(), e);
            return "Page with components";
        }

        return description.toString();
    }

    @Override
    public List<ContentDocument> extractAllPages(String rootPath) {
        List<ContentDocument> allPageData = new ArrayList<>();

        ResourceResolver resolver = null;
        try {
            // Get service user resolver
            Map<String, Object> params = new HashMap<>();
            params.put(ResourceResolverFactory.SUBSERVICE, "readService");
            resolver = resolverFactory.getServiceResourceResolver(params);

            PageManager pageManager = resolver.adaptTo(PageManager.class);
            if (pageManager == null) {
                LOG.error("Unable to get PageManager");
                return allPageData;
            }

            // Query for all pages under root path
            String queryString = "SELECT * FROM [cq:Page] WHERE ISDESCENDANTNODE('" + rootPath + "')";
            Iterator<Resource> results = resolver.findResources(queryString, Query.JCR_SQL2);

            int count = 0;
            while (results.hasNext()) {
                Resource resource = results.next();
                Page page = pageManager.getContainingPage(resource);

                if (page != null) {
                    ContentDocument doc = extractPageData(page);
                    allPageData.add(doc);
                    count++;

                    if (count % 10 == 0) {
                        LOG.info("Extracted {} pages...", count);
                    }
                }
            }

            LOG.info("Total pages extracted: {}", count);

        } catch (Exception e) {
            LOG.error("Error extracting all pages from {}: {}", rootPath, e.getMessage(), e);
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }

        return allPageData;
    }

    @Override
    public String generatePageDescription(Map<String, Object> pageData) {
        StringBuilder description = new StringBuilder();

        try {
            String title = (String) pageData.get("title");
            String template = (String) pageData.get("template");
            List<String> components = (List<String>) pageData.get("components");

            // Start with page type
            if (template != null) {
                String templateName = getSimpleName(template);
                description.append("This is a ").append(templateName).append(" page");
            } else {
                description.append("This page");
            }

            // Add title if available
            if (title != null && !title.isEmpty()) {
                description.append(" titled '").append(title).append("'");
            }

            // Add components
            if (components != null && !components.isEmpty()) {
                description.append(". It contains the following components: ");

                List<String> componentNames = new ArrayList<>();
                for (String component : components) {
                    String simpleName = getSimpleName(component);
                    componentNames.add(simpleName);
                }

                description.append(String.join(", ", componentNames));
            }

            description.append(".");

        } catch (Exception e) {
            LOG.error("Error generating page description: {}", e.getMessage(), e);
            return "Page with components";
        }

        return description.toString();
    }

    /**
     * Extract components from a resource and its children
     */
    private List<String> extractComponents(Resource resource) {
        Set<String> components = new HashSet<>();

        try {
            // Check if current resource is a component
            String resourceType = resource.getResourceType();
            if (resourceType != null && isComponent(resourceType)) {
                components.add(resourceType);
            }

            // Recursively check children
            for (Resource child : resource.getChildren()) {
                components.addAll(extractComponents(child));
            }

        } catch (Exception e) {
            LOG.error("Error extracting components from {}: {}", resource.getPath(), e.getMessage(), e);
        }

        return new ArrayList<>(components);
    }

    /**
     * Check if a resource type is a component (not a container or parsys)
     */
    private boolean isComponent(String resourceType) {
        if (resourceType == null) {
            return false;
        }

        // Exclude common containers and system components
        if (resourceType.contains("/parsys") ||
                resourceType.contains("/container") ||
                resourceType.contains("/responsivegrid") ||
                resourceType.equals("wcm/foundation/components/page")) {
            return false;
        }

        // Include custom components
        return resourceType.contains("components/");
    }

    /**
     * Get simple name from full path
     * Example: /apps/mysite/components/hero-banner -> hero-banner
     */
    private String getSimpleName(String fullPath) {
        if (fullPath == null) {
            return "";
        }

        String[] parts = fullPath.split("/");
        return parts[parts.length - 1];
    }
}
