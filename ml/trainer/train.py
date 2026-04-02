from __future__ import annotations

import argparse
import json
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
import torch
from sklearn.metrics import f1_score, recall_score
from torch.optim import AdamW
from torch.utils.data import DataLoader, Dataset
from transformers import AutoModelForSequenceClassification, AutoTokenizer


@dataclass
class JsonSample:
    text: str
    label_id: int


class JsonlDataset(Dataset):
    def __init__(self, path: Path, label2id: Dict[str, int], tokenizer, max_len: int):
        self.items: List[JsonSample] = []
        with path.open("r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                row = json.loads(line)
                label = row["label"]
                self.items.append(JsonSample(text=row["text"], label_id=label2id[label]))

        self.tokenizer = tokenizer
        self.max_len = max_len

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, idx: int) -> Dict[str, torch.Tensor]:
        item = self.items[idx]
        encoded = self.tokenizer(
            item.text,
            truncation=True,
            padding="max_length",
            max_length=self.max_len,
            return_tensors="pt",
        )
        return {
            "input_ids": encoded["input_ids"].squeeze(0),
            "attention_mask": encoded["attention_mask"].squeeze(0),
            "labels": torch.tensor(item.label_id, dtype=torch.long),
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train DeBERTa-v3-small classifier and export ONNX")
    parser.add_argument("--data-dir", type=Path, default=Path("ml/data"))
    parser.add_argument("--artifacts-dir", type=Path, default=Path("ml/artifacts"))
    parser.add_argument("--model-name", type=str, default="microsoft/deberta-v3-small")
    parser.add_argument("--max-len", type=int, default=128)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--lr", type=float, default=2e-5)
    parser.add_argument("--device", type=str, default="cpu", choices=["cpu", "cuda"])
    parser.add_argument("--f1-threshold", type=float, default=0.70)
    parser.add_argument("--recall-threshold", type=float, default=0.60)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def resolve_device(device_arg: str) -> torch.device:
    if device_arg == "cuda" and torch.cuda.is_available():
        return torch.device("cuda")
    if device_arg == "cuda":
        print("CUDA requested but not available. Falling back to CPU.")
    return torch.device("cpu")


def evaluate(model, loader, device: torch.device, labels: List[str]) -> Tuple[float, Dict[str, float]]:
    model.eval()
    ys_true: List[int] = []
    ys_pred: List[int] = []

    with torch.no_grad():
        for batch in loader:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels_t = batch["labels"].to(device)

            logits = model(input_ids=input_ids, attention_mask=attention_mask).logits
            preds = torch.argmax(logits, dim=-1)

            ys_true.extend(labels_t.cpu().tolist())
            ys_pred.extend(preds.cpu().tolist())

    macro_f1 = float(f1_score(ys_true, ys_pred, average="macro", zero_division=0))
    recalls = recall_score(ys_true, ys_pred, labels=list(range(len(labels))), average=None, zero_division=0)
    per_class_recall = {labels[i]: float(recalls[i]) for i in range(len(labels))}
    return macro_f1, per_class_recall


class LogitsOnly(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask):
        return self.model(input_ids=input_ids, attention_mask=attention_mask).logits


def main() -> None:
    args = parse_args()
    set_seed(args.seed)

    label_map = json.loads((args.data_dir / "label_map.json").read_text(encoding="utf-8"))
    label2id: Dict[str, int] = {k: int(v) for k, v in label_map["label2id"].items()}
    id2label: Dict[int, str] = {int(k): v for k, v in label_map["id2label"].items()}
    labels = [id2label[i] for i in sorted(id2label)]

    expected = {
        "add_v1", "add_v2", "add_v3",
        "divide_v1", "divide_v2", "divide_v3",
        "multiply_v1", "multiply_v2", "multiply_v3",
        "subtract_v1", "subtract_v2", "subtract_v3",
    }
    if set(labels) != expected:
        raise ValueError("label_map.json must contain exactly the expected 12 labels")

    device = resolve_device(args.device)
    tokenizer = AutoTokenizer.from_pretrained(args.model_name, use_fast=False)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model_name,
        num_labels=len(labels),
        label2id=label2id,
        id2label={str(i): labels[i] for i in range(len(labels))},
        use_safetensors=True,
    ).to(device)

    train_ds = JsonlDataset(args.data_dir / "train.jsonl", label2id, tokenizer, args.max_len)
    val_ds = JsonlDataset(args.data_dir / "val.jsonl", label2id, tokenizer, args.max_len)
    test_ds = JsonlDataset(args.data_dir / "test.jsonl", label2id, tokenizer, args.max_len)

    train_loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=args.batch_size, shuffle=False)
    test_loader = DataLoader(test_ds, batch_size=args.batch_size, shuffle=False)

    optimizer = AdamW(model.parameters(), lr=args.lr)

    for epoch in range(args.epochs):
        model.train()
        total_loss = 0.0
        for batch in train_loader:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels_t = batch["labels"].to(device)

            optimizer.zero_grad()
            outputs = model(input_ids=input_ids, attention_mask=attention_mask, labels=labels_t)
            loss = outputs.loss
            loss.backward()
            optimizer.step()
            total_loss += float(loss.item())

        val_f1, _ = evaluate(model, val_loader, device, labels)
        print(f"epoch={epoch + 1} train_loss={total_loss / max(1, len(train_loader)):.4f} val_macro_f1={val_f1:.4f}")

    test_f1, test_recall = evaluate(model, test_loader, device, labels)
    print(f"test_macro_f1={test_f1:.4f}")

    args.artifacts_dir.mkdir(parents=True, exist_ok=True)
    tokenizer.save_pretrained(args.artifacts_dir)

    model_cpu = LogitsOnly(model.to("cpu").eval())
    dummy_input_ids = torch.ones((1, args.max_len), dtype=torch.long)
    dummy_attention_mask = torch.ones((1, args.max_len), dtype=torch.long)

    onnx_path = args.artifacts_dir / "model.onnx"
    torch.onnx.export(
        model_cpu,
        (dummy_input_ids, dummy_attention_mask),
        str(onnx_path),
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch"},
            "attention_mask": {0: "batch"},
            "logits": {0: "batch"},
        },
        opset_version=17,
        do_constant_folding=True,
    )

    model_config = {
        "max_len": args.max_len,
        "label2id": label2id,
        "id2label": {str(i): labels[i] for i in range(len(labels))},
        "metrics": {
            "macro_f1": test_f1,
            "per_class_recall": test_recall,
        },
        "thresholds": {
            "f1_threshold": args.f1_threshold,
            "recall_threshold": args.recall_threshold,
        },
    }
    (args.artifacts_dir / "model_config.json").write_text(json.dumps(model_config, indent=2), encoding="utf-8")

    meets_gate = test_f1 >= args.f1_threshold and all(r >= args.recall_threshold for r in test_recall.values())
    if not meets_gate:
        raise SystemExit("Quality gate failed: thresholds not met")

    print(f"artifacts_dir={args.artifacts_dir.resolve()}")


if __name__ == "__main__":
    main()
