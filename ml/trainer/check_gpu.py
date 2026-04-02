import sys


def main() -> int:
    try:
        import torch
        import onnxruntime as ort
    except Exception as ex:
        print(f"Dependency import failed: {ex}")
        return 1

    print(f"torch version: {torch.__version__}")
    cpu_ok = torch.device("cpu") is not None
    print(f"torch CPU usable: {cpu_ok}")

    print(f"onnxruntime version: {ort.__version__}")
    providers = ort.get_available_providers()
    print(f"onnxruntime providers: {providers}")

    if "CPUExecutionProvider" not in providers:
        print("ERROR: CPUExecutionProvider not available")
        return 1

    if "CUDAExecutionProvider" in providers:
        print("WARNING: CUDAExecutionProvider detected; CPU path remains valid")

    print("CPU runtime validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
