package com.haira.ai.data
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
class ModelImporter(private val context:Context){
 suspend fun import(uri:Uri):ModelEntity=withContext(Dispatchers.IO){
  val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else null}?: "model.gguf"
  require(name.endsWith(".gguf",true)){"Select a .gguf file."}
  val dir=File(context.getExternalFilesDir(null),"HAIRA_Models").apply{mkdirs()}
  val target=File(dir,name)
  context.contentResolver.openInputStream(uri)?.use{input->target.outputStream().use{input.copyTo(it)}}?:error("Cannot read selected file.")
  require(target.length()>1024*1024){"File is too small to be a GGUF model."}
  val header=ByteArray(4);target.inputStream().use{require(it.read(header)==4 && header.contentEquals(byteArrayOf(0x47,0x47,0x55,0x46))){"Invalid GGUF header."}}
  val digest=MessageDigest.getInstance("SHA-256")
  target.inputStream().use{input->val b=ByteArray(1024*1024);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)}}
  val hash=digest.digest().joinToString(""){"%02x".format(it)}
  ModelEntity(target.absolutePath,name,target.length(),hash)
 }
}
