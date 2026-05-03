import sys, traceback
sys.stderr = open("C:/WINDOWS/TEMP/ft_real_err.txt", "w")
sys.stdout = open("C:/WINDOWS/TEMP/ft_real_out.txt", "w")

try:
    print("Importing torch...", flush=True)
    import torch
    print(f"CUDA: {torch.cuda.is_available()}", flush=True)

    from transformers import AutoTokenizer, AutoModelForCausalLM, BitsAndBytesConfig
    import bitsandbytes as bnb

    print("Loading tokenizer...", flush=True)
    tok = AutoTokenizer.from_pretrained("Qwen/Qwen2.5-3B-Instruct")
    print("Tokenizer OK", flush=True)

    print("Loading model in 4-bit...", flush=True)
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.float16,
        bnb_4bit_use_double_quant=True,
    )
    model = AutoModelForCausalLM.from_pretrained(
        "Qwen/Qwen2.5-3B-Instruct",
        quantization_config=bnb_config,
        device_map="auto",
    )
    print("Model loaded OK!", flush=True)
    print(f"Memory used: {torch.cuda.memory_allocated()/1024**3:.2f} GB", flush=True)

except Exception as e:
    traceback.print_exc()
    print(f"\nERROR: {e}", flush=True)

sys.stdout.flush()
sys.stderr.flush()
