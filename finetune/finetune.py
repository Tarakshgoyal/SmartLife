

import json, os, sys
from pathlib import Path

# ── Paths ────────────────────────────────────────────────────────────────────
BASE_DIR    = Path(__file__).parent
DATA_FILE   = BASE_DIR / "dataset.jsonl"
LORA_DIR    = BASE_DIR / "model-lora"
MERGED_DIR  = BASE_DIR / "model-merged"
GGUF_DIR    = BASE_DIR / "model-gguf"
for d in [LORA_DIR, MERGED_DIR, GGUF_DIR]:
    d.mkdir(exist_ok=True)

# ── Config ───────────────────────────────────────────────────────────────────
MODEL_NAME   = "Qwen/Qwen2.5-3B-Instruct"
MAX_SEQ_LEN  = 2048
LORA_RANK    = 16
LORA_ALPHA   = 32
LORA_DROPOUT = 0.05
EPOCHS       = 3
BATCH_SIZE   = 1
GRAD_ACCUM   = 4      # effective batch size = 4
LR           = 2e-4
WARMUP_RATIO = 0.05

SYSTEM_PROMPT = (
    "You are SmartLife MedAI, a clinical medical report analyst. "
    "Analyse the given medical report text and return a structured analysis "
    "using the exact format with sections: Report Overview, Normal Findings, "
    "Abnormal Findings, Critical Values, Clinical Interpretation, Recommendations, and Disclaimer."
)

import torch

print("=" * 60)
print(" SmartLife MedAI — QLoRA Fine-tuning")
print("=" * 60)
print(f"GPU  : {torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU (slow!)'}")
if torch.cuda.is_available():
    print(f"VRAM : {round(torch.cuda.get_device_properties(0).total_memory/1024**3, 1)} GB")
print(f"Model: {MODEL_NAME}")
samples = sum(1 for line in open(DATA_FILE, encoding="utf-8") if line.strip())
print(f"Data : {DATA_FILE}  ({samples} samples)")
print()

if not torch.cuda.is_available():
    print("WARNING: CUDA not available. Training on CPU will be very slow.")

# ── Load model in 4-bit (QLoRA) ───────────────────────────────────────────────
from transformers import (
    AutoTokenizer,
    AutoModelForCausalLM,
    BitsAndBytesConfig,
    TrainingArguments,
)
from peft import LoraConfig, get_peft_model, TaskType, prepare_model_for_kbit_training
from trl import SFTTrainer
from datasets import Dataset

print("Loading tokenizer...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME, trust_remote_code=True)
tokenizer.pad_token = tokenizer.eos_token
tokenizer.padding_side = "right"

print("Loading model in 4-bit (QLoRA)...")
bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_compute_dtype=torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16,
    bnb_4bit_use_double_quant=True,
)

model = AutoModelForCausalLM.from_pretrained(
    MODEL_NAME,
    quantization_config=bnb_config,
    device_map="auto",
    trust_remote_code=True,
)
model = prepare_model_for_kbit_training(model)

# ── Attach LoRA adapters ──────────────────────────────────────────────────────
print("Attaching LoRA adapters...")
lora_config = LoraConfig(
    r=LORA_RANK,
    lora_alpha=LORA_ALPHA,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                    "gate_proj", "up_proj", "down_proj"],
    lora_dropout=LORA_DROPOUT,
    bias="none",
    task_type=TaskType.CAUSAL_LM,
)
model = get_peft_model(model, lora_config)
model.print_trainable_parameters()

# ── Build dataset ─────────────────────────────────────────────────────────────
def format_sample(sample: dict) -> str:
    """Format as Qwen2.5 / ChatML instruction template."""
    return (
        f"<|im_start|>system\n{SYSTEM_PROMPT}<|im_end|>\n"
        f"<|im_start|>user\n{sample['input']}<|im_end|>\n"
        f"<|im_start|>assistant\n{sample['output']}<|im_end|>"
    )

raw_samples = []
with open(DATA_FILE, encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if line:
            raw_samples.append(json.loads(line))

formatted = [{"text": format_sample(s)} for s in raw_samples]
dataset = Dataset.from_list(formatted)
print(f"Dataset: {len(dataset)} examples")
print("Sample (first 200 chars):", dataset[0]["text"][:200])
print()

# ── Train ─────────────────────────────────────────────────────────────────────
training_args = TrainingArguments(
    output_dir=str(LORA_DIR),
    num_train_epochs=EPOCHS,
    per_device_train_batch_size=BATCH_SIZE,
    gradient_accumulation_steps=GRAD_ACCUM,
    warmup_ratio=WARMUP_RATIO,
    learning_rate=LR,
    fp16=not torch.cuda.is_bf16_supported(),
    bf16=torch.cuda.is_bf16_supported(),
    logging_steps=1,
    save_strategy="epoch",
    save_total_limit=1,
    optim="paged_adamw_8bit",
    weight_decay=0.01,
    lr_scheduler_type="cosine",
    seed=42,
    report_to="none",
    dataloader_num_workers=0,
)

trainer = SFTTrainer(
    model=model,
    tokenizer=tokenizer,
    train_dataset=dataset,
    dataset_text_field="text",
    max_seq_length=MAX_SEQ_LEN,
    args=training_args,
)

print("Starting training...")
trainer_stats = trainer.train()
print(f"\nTraining complete!")
print(f"  Runtime : {trainer_stats.metrics['train_runtime']:.1f}s")
print(f"  Loss    : {trainer_stats.metrics['train_loss']:.4f}")

# ── Save LoRA adapters ────────────────────────────────────────────────────────
model.save_pretrained(str(LORA_DIR))
tokenizer.save_pretrained(str(LORA_DIR))
print(f"\nLoRA adapters saved to: {LORA_DIR}")

# ── Merge LoRA into base model ────────────────────────────────────────────────
print("\nMerging LoRA adapters into base model...")
from peft import PeftModel
base_model = AutoModelForCausalLM.from_pretrained(
    MODEL_NAME,
    torch_dtype=torch.float16,
    device_map="cpu",          # merge on CPU to save VRAM
)
merged_model = PeftModel.from_pretrained(base_model, str(LORA_DIR))
merged_model = merged_model.merge_and_unload()
merged_model.save_pretrained(str(MERGED_DIR))
tokenizer.save_pretrained(str(MERGED_DIR))
print(f"Merged model saved to: {MERGED_DIR}")

# ── Convert to GGUF via llama.cpp ─────────────────────────────────────────────
print("\nConverting to GGUF...")
print("Run these commands to convert and load into Ollama:")
print()
print("  # 1. Convert to GGUF (requires llama.cpp)")
print(f"  python llama.cpp/convert_hf_to_gguf.py {MERGED_DIR} --outfile {GGUF_DIR}/smartlife-medical.gguf --outtype q4_k_m")
print()
print("  # 2. Create Modelfile")
modelfile_content = f"""FROM {GGUF_DIR}/smartlife-medical.gguf

PARAMETER temperature 0.1
PARAMETER top_p 0.85
PARAMETER top_k 30
PARAMETER repeat_penalty 1.15
PARAMETER num_predict 800
PARAMETER num_ctx 4096
PARAMETER stop "<|im_end|>"
PARAMETER stop "<|endoftext|>"

SYSTEM \"\"\"{SYSTEM_PROMPT}\"\"\"
"""
modelfile_path = GGUF_DIR / "Modelfile"
modelfile_path.write_text(modelfile_content)
print(f"  # Modelfile written to: {modelfile_path}")
print()
print("  # 3. Load into Ollama")
print(f"  ollama create smartlife-medical -f {modelfile_path}")
print()
print("=" * 60)
print("Fine-tuning complete!")
