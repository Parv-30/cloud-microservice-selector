package com.router.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.router.config.AgentProperties;
import com.router.exception.InferenceException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

@Service
public class ModelInferenceService {
    private static final Set<String> EXPECTED_LABELS = Set.of(
            "add_v1", "add_v2", "add_v3",
            "divide_v1", "divide_v2", "divide_v3",
            "multiply_v1", "multiply_v2", "multiply_v3",
            "subtract_v1", "subtract_v2", "subtract_v3"
    );

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    private OrtEnvironment environment;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private Map<Integer, String> id2Label = new HashMap<>();

    public ModelInferenceService(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            JsonNode root = objectMapper.readTree(new File(properties.getModelConfigPath()));

            if (!root.has("max_len")) {
                throw new InferenceException("model_config.json missing max_len");
            }
            properties.setMaxLen(root.get("max_len").asInt());

            JsonNode id2LabelNode = root.get("id2label");
            if (id2LabelNode == null || !id2LabelNode.isObject()) {
                throw new InferenceException("model_config.json missing id2label object");
            }

            Map<Integer, String> loaded = new HashMap<>();
            Set<String> seen = new HashSet<>();
            id2LabelNode.fields().forEachRemaining(entry -> {
                int idx = Integer.parseInt(entry.getKey());
                String label = entry.getValue().asText();
                loaded.put(idx, label);
                seen.add(label);
            });

            if (!seen.equals(EXPECTED_LABELS) || loaded.size() != 12) {
                throw new InferenceException("model_config label validation failed. Expected exact 12 known labels.");
            }
            this.id2Label = loaded;

            this.environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            this.session = environment.createSession(properties.getModelPath(), options);

            Path tokenizerPath = Path.of(properties.getModelPath()).getParent().resolve("tokenizer.json");
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        } catch (Exception e) {
            throw new InferenceException("Failed to initialize inference service", e);
        }
    }

    public InferenceResult predictLabel(String rawJson) {
        try {
            Encoding encoding = tokenizer.encode(rawJson);
            long[] inputIds = toFixedLength(encoding.getIds(), properties.getMaxLen());
            long[] attentionMask = toFixedLength(encoding.getAttentionMask(), properties.getMaxLen());

            Map<String, OnnxTensor> inputs = new HashMap<>();
            long[][] inputIds2D = new long[][]{inputIds};
            long[][] attentionMask2D = new long[][]{attentionMask};

            inputs.put("input_ids", OnnxTensor.createTensor(environment, inputIds2D));
            inputs.put("attention_mask", OnnxTensor.createTensor(environment, attentionMask2D));

            if (session.getInputNames().contains("token_type_ids")) {
                long[][] tokenTypeIds = new long[][]{new long[properties.getMaxLen()]};
                inputs.put("token_type_ids", OnnxTensor.createTensor(environment, tokenTypeIds));
            }

            try (OrtSession.Result result = session.run(inputs)) {
                float[][] logits = (float[][]) result.get(0).getValue();
                float[] probs = softmax(logits[0]);

                int bestIdx = 0;
                float bestProb = probs[0];
                for (int i = 1; i < probs.length; i++) {
                    if (probs[i] > bestProb) {
                        bestProb = probs[i];
                        bestIdx = i;
                    }
                }

                String label = id2Label.get(bestIdx);
                if (label == null) {
                    throw new InferenceException("Predicted class index not found in id2label: " + bestIdx);
                }
                return new InferenceResult(label, bestProb);
            } finally {
                for (OnnxTensor tensor : inputs.values()) {
                    tensor.close();
                }
            }
        } catch (OrtException e) {
            throw new InferenceException("ONNX inference failed", e);
        }
    }

    private long[] toFixedLength(long[] values, int maxLen) {
        long[] out = new long[maxLen];
        Arrays.fill(out, 0L);
        int n = Math.min(values.length, maxLen);
        System.arraycopy(values, 0, out, 0, n);
        return out;
    }

    private float[] softmax(float[] logits) {
        float max = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > max) {
                max = logits[i];
            }
        }

        float sum = 0f;
        float[] exp = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }

        float[] probs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = exp[i] / sum;
        }
        return probs;
    }

    public record InferenceResult(String label, double confidence) {
    }
}
