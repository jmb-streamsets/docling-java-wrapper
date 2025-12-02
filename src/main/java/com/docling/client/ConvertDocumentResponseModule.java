package com.docling.client;

import com.docling.model.ConvertDocumentResponse;
import com.docling.model.ExportDocumentResponse;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * Custom Jackson module that patches {@link ConvertDocumentResponse} payloads when the server
 * omits the legacy {@code document} field and instead nests export payloads elsewhere.
 */
final class ConvertDocumentResponseModule extends SimpleModule {

    ConvertDocumentResponseModule(ObjectMapper fallbackMapper) {
        super("DoclingConvertDocumentResponseModule");
        addDeserializer(ConvertDocumentResponse.class, new MissingDocumentHydrator(fallbackMapper));
    }

    private static final class MissingDocumentHydrator extends JsonDeserializer<ConvertDocumentResponse> {

        private final ObjectMapper fallback;

        MissingDocumentHydrator(ObjectMapper fallback) {
            this.fallback = fallback;
        }

        @Override
        public ConvertDocumentResponse deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            JsonNode node = parser.readValueAsTree();
            ConvertDocumentResponse response = fallback.treeToValue(node, ConvertDocumentResponse.class);
            if (response.getDocument() == null) {
                ExportDocumentResponse document = DocumentNodeFinder.extract(node, fallback);
                if (document != null) {
                    response.setDocument(document);
                }
            }
            return response;
        }
    }

    private static final class DocumentNodeFinder {

        private static final Set<String> DOCUMENT_FIELDS =
                Set.of("md_content", "json_content", "html_content", "text_content", "doctags_content");

        // Maximum depth to prevent DoS attacks with deeply nested JSON
        private static final int MAX_DEPTH = 100;

        private DocumentNodeFinder() {
        }

        static ExportDocumentResponse extract(JsonNode root, ObjectMapper mapper) {
            JsonNode node = locate(root);
            if (node == null) {
                return null;
            }
            try {
                return mapper.treeToValue(node, ExportDocumentResponse.class);
            } catch (Exception ignored) {
                return null;
            }
        }

        /**
         * Locates a node that looks like a document within the JSON tree.
         * Uses breadth-first search with a depth limit to prevent excessive recursion.
         *
         * @param root The root JSON node
         * @return The document node, or null if not found
         */
        private static JsonNode locate(JsonNode root) {
            if (root == null) {
                return null;
            }
            Deque<NodeWithDepth> stack = new ArrayDeque<>();
            stack.push(new NodeWithDepth(root, 0));

            while (!stack.isEmpty()) {
                NodeWithDepth current = stack.pop();
                if (current.depth > MAX_DEPTH) {
                    continue; // Skip nodes beyond max depth
                }
                if (looksLikeDocument(current.node)) {
                    return current.node;
                }
                current.node.elements().forEachRemaining(child ->
                        stack.push(new NodeWithDepth(child, current.depth + 1)));
            }
            return null;
        }

        private static boolean looksLikeDocument(JsonNode node) {
            if (node == null || !node.isObject()) {
                return false;
            }
            for (String field : DOCUMENT_FIELDS) {
                if (node.has(field)) {
                    return true;
                }
            }
            return false;
        }

        private record NodeWithDepth(JsonNode node, int depth) {
        }
    }
}
