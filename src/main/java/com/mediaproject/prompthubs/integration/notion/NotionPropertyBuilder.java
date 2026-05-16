package com.mediaproject.prompthubs.integration.notion;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Helpers to construct Notion property JSON values.
 */
@Component
public class NotionPropertyBuilder {

    private final ObjectMapper mapper;

    public NotionPropertyBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode title(String text) {
        ObjectNode prop = mapper.createObjectNode();
        ArrayNode arr = prop.putArray("title");
        ObjectNode textNode = arr.addObject();
        textNode.put("type", "text");
        textNode.putObject("text").put("content", text == null ? "" : text);
        return prop;
    }

    public ObjectNode richText(String text) {
        ObjectNode prop = mapper.createObjectNode();
        ArrayNode arr = prop.putArray("rich_text");
        ObjectNode textNode = arr.addObject();
        textNode.put("type", "text");
        textNode.putObject("text").put("content", text == null ? "" : text);
        return prop;
    }

    public ObjectNode select(String value) {
        ObjectNode prop = mapper.createObjectNode();
        if (value == null) {
            prop.putNull("select");
        } else {
            prop.putObject("select").put("name", value);
        }
        return prop;
    }

    public ObjectNode pageParent(String databaseId) {
        ObjectNode parent = mapper.createObjectNode();
        parent.put("database_id", databaseId);
        return parent;
    }

    public ObjectNode wrapPageRequest(String databaseId, ObjectNode propertiesNode) {
        ObjectNode body = mapper.createObjectNode();
        body.set("parent", pageParent(databaseId));
        body.set("properties", propertiesNode);
        return body;
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
