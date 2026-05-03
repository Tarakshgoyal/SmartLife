@echo off
cd /d "%~dp0"
echo ============================================================
echo  SmartLife MedAI -- Fine-tuning Setup
echo ============================================================

if not exist "venv\Scripts\python.exe" (
    echo [1/5] Creating virtual environment...
    python -m venv venv
) else (
    echo [1/5] Virtual environment already exists.
)

echo [2/5] Installing CUDA PyTorch (cu124)...
venv\Scripts\pip.exe install torch==2.5.1 --index-url https://download.pytorch.org/whl/cu124 --quiet

echo [3/5] Installing training libraries...
venv\Scripts\pip.exe install "transformers==4.45.2" "peft==0.12.0" "trl==0.11.4" "bitsandbytes==0.44.1" "accelerate==0.34.2" "datasets==2.21.0" "sentencepiece==0.2.0" "huggingface_hub==0.25.2" --quiet

echo [4/5] Verifying CUDA...
venv\Scripts\python.exe -c "import torch; print('CUDA:', torch.cuda.is_available()); print('GPU:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NONE')"

echo [5/5] Starting fine-tuning...
echo NOTE: First run will download Llama 3.2 3B (~6 GB). Please wait.
venv\Scripts\python.exe finetune.py

echo.
echo ============================================================
echo  Fine-tuning complete!
echo  Next step: convert to GGUF and load into Ollama.
echo  See instructions printed above.
echo ============================================================
pause
