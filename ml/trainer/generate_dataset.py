from __future__ import annotations

import argparse
import json
import random
import uuid
from pathlib import Path
from typing import Dict, List

LABEL2ID: Dict[str, int] = {
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

ID2LABEL = {str(v): k for k, v in LABEL2ID.items()}
OPERATIONS = ["add", "divide", "multiply", "subtract"]
METADATA_KEYS = ["precision", "label", "requestId", "currency", "roundingMode"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate arithmetic router dataset JSONL files")
    parser.add_argument("--out-dir", type=Path, default=Path("ml/data"))
    parser.add_argument("--train-per-class", type=int, default=600)
    parser.add_argument("--val-per-class", type=int, default=100)
    parser.add_argument("--test-per-class", type=int, default=100)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def rand_num(rng: random.Random) -> float:
    if rng.random() < 0.65:
        return float(rng.randint(-100, 100))
    return round(rng.uniform(-100.0, 100.0), 2)


def rand_non_zero(rng: random.Random) -> float:
    value = 0.0
    while value == 0.0:
        value = rand_num(rng)
    return value


def build_v1_payload(op: str, rng: random.Random) -> Dict:
    a = rand_num(rng)
    b = rand_non_zero(rng) if op == "divide" else rand_num(rng)
    return {"operation": op, "a": a, "b": b}


def build_v2_payload(op: str, rng: random.Random) -> Dict:
    n = rng.randint(2, 6)
    numbers = [rand_num(rng) for _ in range(n)]
    if op == "divide":
        numbers = [numbers[0]] + [rand_non_zero(rng) for _ in range(n - 1)]
    return {"operation": op, "numbers": numbers}


def build_v3_payload(op: str, rng: random.Random) -> Dict:
    n = rng.randint(2, 6)
    numbers = [rand_num(rng) for _ in range(n)]
    if op == "divide":
        numbers = [numbers[0]] + [rand_non_zero(rng) for _ in range(n - 1)]

    payload: Dict = {"operation": op, "numbers": numbers}

    count = rng.randint(1, 3)
    chosen = rng.sample(METADATA_KEYS, k=count)
    for key in chosen:
        if key == "precision":
            payload[key] = rng.choice(["integer", "decimal"])
        elif key == "label":
            payload[key] = rng.choice(["cart_total", "batch_result", "invoice_total", "metric"])
        elif key == "requestId":
            payload[key] = f"req_{uuid.uuid4().hex[:10]}"
        elif key == "currency":
            payload[key] = rng.choice(["USD", "EUR", "GBP", "JPY", "INR"])
        elif key == "roundingMode":
            payload[key] = rng.choice(["HALF_UP", "HALF_DOWN", "HALF_EVEN", "FLOOR", "CEILING"])

    return payload


def payload_for(label: str, rng: random.Random) -> Dict:
    op, version = label.split("_")
    if version == "v1":
        return build_v1_payload(op, rng)
    if version == "v2":
        return build_v2_payload(op, rng)
    return build_v3_payload(op, rng)


def build_split(samples_per_class: int, rng: random.Random) -> List[Dict]:
    rows: List[Dict] = []
    for label in LABEL2ID:
        for _ in range(samples_per_class):
            payload = payload_for(label, rng)
            rows.append({
                "text": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                "label": label,
            })
    rng.shuffle(rows)
    return rows


def write_jsonl(path: Path, rows: List[Dict]) -> None:
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def main() -> None:
    args = parse_args()
    rng = random.Random(args.seed)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    train_rows = build_split(args.train_per_class, rng)
    val_rows = build_split(args.val_per_class, rng)
    test_rows = build_split(args.test_per_class, rng)

    write_jsonl(args.out_dir / "train.jsonl", train_rows)
    write_jsonl(args.out_dir / "val.jsonl", val_rows)
    write_jsonl(args.out_dir / "test.jsonl", test_rows)

    (args.out_dir / "label_map.json").write_text(
        json.dumps({"label2id": LABEL2ID, "id2label": ID2LABEL}, indent=2),
        encoding="utf-8",
    )

    print(
        json.dumps(
            {
                "out_dir": str(args.out_dir.resolve()),
                "train": len(train_rows),
                "val": len(val_rows),
                "test": len(test_rows),
                "labels": len(LABEL2ID),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
