import json
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper
from tokenizers import Tokenizer
from tokenizers.models import WordLevel
from tokenizers.pre_tokenizers import Whitespace


LABELS = {
    "add_v1": 0,
    "add_v2": 1,
    "add_v3": 2,
    "divide_v1": 3,
    "divide_v2": 4,
    "divide_v3": 5,
    "multiply_v1": 6,
    "multiply_v2": 7,
    "multiply_v3": 8,
    "subtract_v1": 9,
    "subtract_v2": 10,
    "subtract_v3": 11,
}


def build_constant_onnx(model_path: Path, max_len: int = 128) -> None:
    input_ids = helper.make_tensor_value_info("input_ids", TensorProto.INT64, [1, max_len])
    attention_mask = helper.make_tensor_value_info("attention_mask", TensorProto.INT64, [1, max_len])
    logits_out = helper.make_tensor_value_info("logits", TensorProto.FLOAT, [1, 12])

    logits = np.full((1, 12), -2.0, dtype=np.float32)
    logits[0, 8] = 4.0

    logits_tensor = helper.make_tensor(
        name="const_logits",
        data_type=TensorProto.FLOAT,
        dims=[1, 12],
        vals=logits.flatten().tolist(),
    )

    const_node = helper.make_node("Constant", inputs=[], outputs=["logits"], value=logits_tensor)

    graph = helper.make_graph(
        nodes=[const_node],
        name="constant_classifier",
        inputs=[input_ids, attention_mask],
        outputs=[logits_out],
    )

    model = helper.make_model(graph, producer_name="placeholder-generator", ir_version=9)
    model.opset_import[0].version = 13
    onnx.save(model, str(model_path))


def build_tokenizer(tokenizer_path: Path) -> None:
    vocab = {
        "[PAD]": 0,
        "[UNK]": 1,
        "[CLS]": 2,
        "[SEP]": 3,
        "[MASK]": 4,
        "{": 5,
        "}": 6,
        "\"operation\"": 7,
        "\"numbers\"": 8,
        "\"a\"": 9,
        "\"b\"": 10,
        "add": 11,
        "subtract": 12,
        "multiply": 13,
        "divide": 14,
    }
    model = WordLevel(vocab=vocab, unk_token="[UNK]")
    tokenizer = Tokenizer(model)
    tokenizer.pre_tokenizer = Whitespace()
    tokenizer.save(str(tokenizer_path))


def main() -> None:
    artifacts_dir = Path(__file__).resolve().parents[1] / "artifacts"
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    max_len = 128
    build_constant_onnx(artifacts_dir / "model.onnx", max_len=max_len)
    build_tokenizer(artifacts_dir / "tokenizer.json")

    model_config = {
        "max_len": max_len,
        "id2label": {str(v): k for k, v in LABELS.items()},
        "label2id": LABELS,
    }
    (artifacts_dir / "model_config.json").write_text(json.dumps(model_config, indent=2), encoding="utf-8")
    print(f"Generated placeholder artifacts in: {artifacts_dir}")


if __name__ == "__main__":
    main()
