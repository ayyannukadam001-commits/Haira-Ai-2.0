package com.haira.ai.ai
data class ModelItem(val name:String,val quant:String,val sizeBytes:Long,val url:String,val expectedSha256:String)
object ModelCatalog{
 val models=listOf(
  ModelItem("SmolLM2-360M-Instruct","Q4_K_M",271_000_000L,"https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf","2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2"),
  ModelItem("Qwen3-0.6B","Q4_K_M",484_000_000L,"https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B.Q4_K_M.gguf","7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d")
 )
}
