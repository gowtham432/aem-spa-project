package com.mycompany.core.services;

import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = AIConfigService.class,
        immediate = true
)
@Designate(ocd = AIConfigService.Config.class)
public class AIConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(AIConfigService.class);

    private String openAIApiKey;
    private String pineconeApiKey;
    private String pineconeIndexName;

    /**
     * OSGi Configuration Definition (Inside Same File)
     */
    @ObjectClassDefinition(
            name = "AI Integration Configuration",
            description = "Configuration for OpenAI and Pinecone integration"
    )
    public @interface Config {

        @AttributeDefinition(
                name = "OpenAI API Key",
                description = "API key for OpenAI"
        )
        String openAIApiKey() default "";

        @AttributeDefinition(
                name = "Pinecone API Key",
                description = "API key for Pinecone"
        )
        String pineconeApiKey() default "";

        @AttributeDefinition(
                name = "Pinecone Index Name",
                description = "Name of Pinecone index"
        )
        String pineconeIndexName() default "";
    }

    /**
     * Activation Method
     */
    @Activate
    @Modified
    protected void activate(Config config) {
        this.openAIApiKey = config.openAIApiKey();
        this.pineconeApiKey = config.pineconeApiKey();
        this.pineconeIndexName = config.pineconeIndexName();

        LOG.info("AI Configuration Loaded Successfully");
    }

    public String getOpenAIApiKey() {
        return openAIApiKey;
    }

    public String getPineconeApiKey() {
        return pineconeApiKey;
    }

    public String getPineconeIndexName() {
        return pineconeIndexName;
    }
}
