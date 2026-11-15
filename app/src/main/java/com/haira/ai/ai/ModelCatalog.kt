package com.haira.ai.ai
data class ModelItem(val name:String,val quant:String,val sizeBytes:Long,val url:String,val license:String)
object ModelCatalog{
 val models=listOf(
  ModelItem("SmolLM2-360M-Instruct","Q4_K_M",271_000_000L,"https://huggingface.co/tensorblock/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf","Open model; check model card terms"),
  ModelItem("Qwen3-0.6B","Q4_K_M",484_000_000L,"https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B.Q4_K_M.gguf","Apache-2.0 base model; review quantized repo card")
 )
}
