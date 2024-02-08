package org.mule.extension.jsonlogger.internal.datamask;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import java.util.*;
import java.util.regex.Pattern;

public class JsonAccountMasker {

    private static final Pattern digits = Pattern.compile("\\d");
    private static final Pattern capitalLetters = Pattern.compile("[A-Z]");
    private static final Pattern nonSpecialCharacters = Pattern.compile("[^X\\s!-/:-@\\[-`{-~]");

    private static final Configuration jsonPathConfig = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .options(Option.AS_PATH_LIST, Option.SUPPRESS_EXCEPTIONS).build();

    private final Set<String> blacklistedKeys;
    private final Set<JsonPath> blacklistedJsonPaths;
    private final boolean enabled;

    public JsonAccountMasker(Collection<String> blacklist, boolean enabled) {
        this.enabled = enabled;

        blacklistedKeys = new HashSet<>();
        blacklistedJsonPaths = new HashSet<>();

        blacklist.forEach(item -> {
            if (item.startsWith("$")) {
                blacklistedJsonPaths.add(JsonPath.compile(item));
            } else {
                blacklistedKeys.add(item.toUpperCase());
            }
        });
    }

    public JsonAccountMasker() {
        this(Collections.emptySet(), true);
    }

    public JsonAccountMasker(boolean enabled) {
        this(Collections.emptySet(), enabled);
    }

    public JsonAccountMasker(Collection<String> blacklist) {
        this(blacklist, true);
    }

    public JsonNode accountmask(JsonNode target) {
        if (!enabled)
            return target;
        if (target == null)
            return null;

        Set<String> expandedBlacklistedPaths = new HashSet<>();
        for (JsonPath jsonPath : blacklistedJsonPaths) {
            if (jsonPath.isDefinite()) {
                expandedBlacklistedPaths.add(jsonPath.getPath());
            } else {
                for (JsonNode node : jsonPath.<ArrayNode>read(target, jsonPathConfig)) {
                    expandedBlacklistedPaths.add(node.asText());
                }
            }
        }
        

        return traverseAndMaskAccount(target.deepCopy(), expandedBlacklistedPaths, "$", false);
    }

    @SuppressWarnings("ConstantConditions")
    private JsonNode traverseAndMaskAccount(JsonNode target, Set<String> expandedBlacklistedPaths, String path, Boolean isBlackListed) {
        if (target.isTextual() && isBlackListed) {
            return new TextNode(maskAccountNumber(target.asText()));
        }
        if (target.isNumber() && isBlackListed) {
            return new TextNode(maskAccountNumber(target.asText()));
        }

        if (target.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = target.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = appendPath(path, field.getKey());
                if (blacklistedKeys.contains(field.getKey().toUpperCase()) || expandedBlacklistedPaths.contains(childPath) || isBlackListed == true) {
                    ((ObjectNode) target).replace(field.getKey(), traverseAndMaskAccount(field.getValue(), expandedBlacklistedPaths, childPath, true));
                } else {
                    ((ObjectNode) target).replace(field.getKey(), traverseAndMaskAccount(field.getValue(), expandedBlacklistedPaths, childPath, false));
                }
            }
        }
        if (target.isArray()) {
            for (int i = 0; i < target.size(); i++) {
                String childPath = appendPath(path, i);
                if (expandedBlacklistedPaths.contains(childPath) || isBlackListed == true) {
                    ((ArrayNode) target).set(i, traverseAndMaskAccount(target.get(i), expandedBlacklistedPaths, childPath, true));
                } else {
                    ((ArrayNode) target).set(i, traverseAndMaskAccount(target.get(i), expandedBlacklistedPaths, childPath, false));
                }
            }
        }
        return target;
    }

    private static String appendPath(String path, String key) {
        return path + "['" + key + "']";
    }

    private static String appendPath(String path, int ind) {
        return path + "[" + ind + "]";
    }

    private static String maskAccountNumber(String value) {
    	
    	int length = value.length();
    	
    	if (length < 4) {
            
            return value;
        }
    	String lastFourCharacters = value.substring(length - 4);
    	String remainingCharacters = value.substring(0, length - 4);
    	return nonSpecialCharacters.matcher(remainingCharacters).replaceAll("x") + lastFourCharacters;
    }

  //  private static String maskAccountNumber(String value) {
    
    //    return value.replaceAll("[0-9]", "*");
   // }

}